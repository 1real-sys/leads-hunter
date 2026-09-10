#!/usr/bin/env python3
"""Gera o subset local de CNPJs usado pelo Leads Hunter.

O script consome os arquivos Empresas, Estabelecimentos e Municipios dos Dados
Abertos do CNPJ. As fontes e seus SHA-256 ficam em um manifesto revisado; nada
e baixado nem consultado durante o runtime da aplicacao.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import os
import re
import ssl
import tempfile
import unicodedata
import urllib.parse
import urllib.request
import zipfile
from concurrent.futures import ProcessPoolExecutor
from contextlib import contextmanager
from datetime import date
from pathlib import Path
from typing import Any, Callable, Iterator, Sequence, TextIO, TypeVar


RAIZ_PROJETO = Path(__file__).resolve().parents[2]
SAIDA_PADRAO = RAIZ_PROJETO / "src/main/resources/cnpj/cnpj-subset.json"
MUNICIPIOS_IBGE_PADRAO = Path(__file__).with_name("municipios-ibge.csv")
HOSTS_PERMITIDOS = {
    "arquivos.receitafederal.gov.br",
    "dadosabertos.rfb.gov.br",
}
TIPOS_FONTE = {"empresas", "estabelecimentos", "municipios"}
SITUACAO_ATIVA = "02"
MAXIMO_FONTES = 32
MAXIMO_MEMBROS_ZIP = 8
MAXIMO_COMPACTADO = 8 * 1024 * 1024 * 1024
MAXIMO_DESCOMPACTADO_POR_MEMBRO = 40 * 1024 * 1024 * 1024
MAXIMO_TRABALHADORES = 16
MAXIMO_TRABALHADORES_AUTOMATICOS = 8
TOTAL_MUNICIPIOS_IBGE = 5_570
COLUNAS_MUNICIPIOS_IBGE = ["codigo_ibge", "nome", "uf"]
PADRAO_NAO_ALFANUMERICO = re.compile(r"[^0-9a-z]+")
PADRAO_NAO_DIGITO = re.compile(r"\D")
PADRAO_CNPJ = re.compile(r"\d{14}")
PADRAO_CODIGO_IBGE = re.compile(r"\d{7}")
PADRAO_UF = re.compile(r"[A-Za-z]{2}")
T = TypeVar("T")


class ErroIngestao(RuntimeError):
    """Indica fonte, manifesto ou registro incompativel com o contrato."""


def normalizar_texto(valor: str | None) -> str:
    if not valor:
        return ""
    decomposto = unicodedata.normalize("NFKD", valor)
    sem_acentos = "".join(
        caractere for caractere in decomposto
        if not unicodedata.combining(caractere)
    )
    return " ".join(PADRAO_NAO_ALFANUMERICO.sub(" ", sem_acentos.lower()).split())


def somente_digitos(valor: str | None) -> str:
    return PADRAO_NAO_DIGITO.sub("", valor or "")


def cnpj_valido(cnpj: str) -> bool:
    if not PADRAO_CNPJ.fullmatch(cnpj) or len(set(cnpj)) == 1:
        return False

    def calcular_digito(base: str, pesos: list[int]) -> str:
        resto = sum(int(numero) * peso for numero, peso in zip(base, pesos)) % 11
        return str(0 if resto < 2 else 11 - resto)

    primeiro = calcular_digito(cnpj[:12], [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2])
    segundo = calcular_digito(
        cnpj[:12] + primeiro,
        [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2],
    )
    return cnpj[-2:] == primeiro + segundo


def sha256_arquivo(caminho: Path) -> str:
    digest = hashlib.sha256()
    with caminho.open("rb") as arquivo:
        for bloco in iter(lambda: arquivo.read(1024 * 1024), b""):
            digest.update(bloco)
    return digest.hexdigest()


def carregar_catalogo_municipios_ibge(caminho: Path) -> list[dict[str, str]]:
    try:
        with caminho.open("r", encoding="utf-8", newline="") as arquivo:
            leitor = csv.DictReader(arquivo)
            if leitor.fieldnames != COLUNAS_MUNICIPIOS_IBGE:
                raise ErroIngestao(
                    "Cabecalho invalido no catalogo IBGE; esperado: "
                    + ",".join(COLUNAS_MUNICIPIOS_IBGE)
                )
            municipios = []
            chaves: set[tuple[str, str]] = set()
            codigos: set[str] = set()
            for linha, registro in enumerate(leitor, start=2):
                codigo = (registro.get("codigo_ibge") or "").strip()
                nome = (registro.get("nome") or "").strip()
                uf = (registro.get("uf") or "").strip().upper()
                nome_normalizado = normalizar_texto(nome)
                if not PADRAO_CODIGO_IBGE.fullmatch(codigo):
                    raise ErroIngestao(
                        f"Codigo IBGE invalido no catalogo, linha {linha}: {codigo!r}"
                    )
                if not nome_normalizado:
                    raise ErroIngestao(
                        f"Nome municipal invalido no catalogo, linha {linha}: {nome!r}"
                    )
                if not PADRAO_UF.fullmatch(uf):
                    raise ErroIngestao(
                        f"UF invalida no catalogo, linha {linha}: {uf!r}"
                    )
                chave = (uf, nome_normalizado)
                if codigo in codigos or chave in chaves:
                    raise ErroIngestao(
                        f"Municipio duplicado no catalogo IBGE: {codigo} {nome}/{uf}"
                    )
                codigos.add(codigo)
                chaves.add(chave)
                municipios.append({"codigoIbge": codigo, "nome": nome, "uf": uf})
    except (OSError, UnicodeDecodeError, csv.Error) as erro:
        raise ErroIngestao(f"Catalogo de municipios IBGE invalido: {caminho}") from erro

    if caminho.resolve() == MUNICIPIOS_IBGE_PADRAO.resolve():
        if len(municipios) != TOTAL_MUNICIPIOS_IBGE:
            raise ErroIngestao(
                "Catalogo IBGE versionado deve conter "
                f"{TOTAL_MUNICIPIOS_IBGE} municipios; encontrados {len(municipios)}"
            )
        ufs = {municipio["uf"] for municipio in municipios}
        if len(ufs) != 27:
            raise ErroIngestao(
                f"Catalogo IBGE versionado deve conter 27 UFs; encontradas {len(ufs)}"
            )
    return sorted(municipios, key=lambda item: item["codigoIbge"])


def _validar_municipio_interesse(municipio: Any) -> dict[str, str]:
    if not isinstance(municipio, dict):
        raise ErroIngestao("Municipio de interesse invalido")
    codigo = municipio.get("codigoIbge")
    nome = municipio.get("nome")
    uf = municipio.get("uf")
    if not isinstance(codigo, str) or not PADRAO_CODIGO_IBGE.fullmatch(codigo):
        raise ErroIngestao(f"Codigo IBGE invalido: {codigo!r}")
    if not isinstance(nome, str) or not normalizar_texto(nome):
        raise ErroIngestao(f"Nome municipal invalido: {nome!r}")
    if not isinstance(uf, str) or not PADRAO_UF.fullmatch(uf):
        raise ErroIngestao(f"UF invalida: {uf!r}")
    return {"codigoIbge": codigo, "nome": nome.strip(), "uf": uf.upper()}


def _expandir_municipios_manifesto(
    manifesto: dict[str, Any],
    caminho_municipios_ibge: Path,
) -> None:
    municipios_declarados = manifesto.get("municipiosInteresse", [])
    ufs_declaradas = manifesto.get("ufs", [])
    if not isinstance(municipios_declarados, list):
        raise ErroIngestao("municipiosInteresse deve ser uma lista")
    if not isinstance(ufs_declaradas, list):
        raise ErroIngestao("ufs deve ser uma lista")
    if not municipios_declarados and not ufs_declaradas:
        raise ErroIngestao("O manifesto deve declarar municipiosInteresse e/ou ufs")

    municipios_manuais: list[dict[str, str]] = []
    chaves_manuais: set[tuple[str, str]] = set()
    codigos_manuais: set[str] = set()
    for municipio_bruto in municipios_declarados:
        municipio = _validar_municipio_interesse(municipio_bruto)
        chave = (municipio["uf"], normalizar_texto(municipio["nome"]))
        if chave in chaves_manuais or municipio["codigoIbge"] in codigos_manuais:
            raise ErroIngestao(
                f"Municipio de interesse duplicado: {municipio['nome']}/{municipio['uf']}"
            )
        chaves_manuais.add(chave)
        codigos_manuais.add(municipio["codigoIbge"])
        municipios_manuais.append(municipio)

    ufs: list[str] = []
    for uf_bruta in ufs_declaradas:
        if not isinstance(uf_bruta, str) or not PADRAO_UF.fullmatch(uf_bruta):
            raise ErroIngestao(f"UF invalida: {uf_bruta!r}")
        uf = uf_bruta.upper()
        if uf in ufs:
            raise ErroIngestao(f"UF duplicada: {uf}")
        ufs.append(uf)

    municipios_expandidos: list[dict[str, str]] = []
    if ufs:
        catalogo = carregar_catalogo_municipios_ibge(caminho_municipios_ibge)
        por_uf: dict[str, list[dict[str, str]]] = {}
        for municipio in catalogo:
            por_uf.setdefault(municipio["uf"], []).append(municipio)
        inexistentes = sorted(set(ufs) - por_uf.keys())
        if inexistentes:
            raise ErroIngestao("UF inexistente no catalogo IBGE: " + ", ".join(inexistentes))
        for uf in sorted(ufs):
            municipios_expandidos.extend(por_uf[uf])

    selecionados_por_codigo = {
        municipio["codigoIbge"]: municipio for municipio in municipios_manuais
    }
    chaves_por_codigo = {
        municipio["codigoIbge"]: (
            municipio["uf"],
            normalizar_texto(municipio["nome"]),
        )
        for municipio in municipios_manuais
    }
    codigos_por_chave = {
        chave: codigo for codigo, chave in chaves_por_codigo.items()
    }
    for municipio in municipios_expandidos:
        codigo = municipio["codigoIbge"]
        chave = (municipio["uf"], normalizar_texto(municipio["nome"]))
        existente_por_codigo = chaves_por_codigo.get(codigo)
        existente_por_chave = codigos_por_chave.get(chave)
        if existente_por_codigo == chave and existente_por_chave == codigo:
            continue
        if existente_por_codigo is not None or existente_por_chave is not None:
            raise ErroIngestao(
                "Conflito entre municipio manual e catalogo IBGE: "
                f"{codigo} {municipio['nome']}/{municipio['uf']}"
            )
        selecionados_por_codigo[codigo] = municipio
        chaves_por_codigo[codigo] = chave
        codigos_por_chave[chave] = codigo

    manifesto["ufs"] = sorted(ufs)
    manifesto["municipiosInteresse"] = sorted(
        selecionados_por_codigo.values(),
        key=lambda item: item["codigoIbge"],
    )


def carregar_manifesto(
    caminho: Path,
    caminho_municipios_ibge: Path = MUNICIPIOS_IBGE_PADRAO,
) -> dict[str, Any]:
    try:
        manifesto = json.loads(caminho.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as erro:
        raise ErroIngestao(f"Manifesto invalido: {caminho}") from erro

    if not isinstance(manifesto, dict):
        raise ErroIngestao("O manifesto deve ser um objeto JSON")
    try:
        date.fromisoformat(manifesto["dataBase"])
    except (KeyError, TypeError, ValueError) as erro:
        raise ErroIngestao("dataBase deve estar no formato AAAA-MM-DD") from erro

    fontes = manifesto.get("fontes")
    if not isinstance(fontes, list) or not 3 <= len(fontes) <= MAXIMO_FONTES:
        raise ErroIngestao("O manifesto deve declarar de 3 a 32 fontes")
    tipos = set()
    arquivos = set()
    for fonte in fontes:
        if not isinstance(fonte, dict):
            raise ErroIngestao("Fonte invalida no manifesto")
        tipo = fonte.get("tipo")
        arquivo = fonte.get("arquivo")
        url = fonte.get("url")
        checksum = fonte.get("sha256")
        if tipo not in TIPOS_FONTE:
            raise ErroIngestao(f"Tipo de fonte desconhecido: {tipo!r}")
        if (
            not isinstance(arquivo, str)
            or Path(arquivo).name != arquivo
            or not re.fullmatch(r"[A-Za-z0-9._-]+", arquivo)
            or arquivo in arquivos
        ):
            raise ErroIngestao(f"Nome de arquivo inseguro ou duplicado: {arquivo!r}")
        if (
            not isinstance(url, str)
            or not url.startswith("https://")
            or any(ord(caractere) < 32 for caractere in url)
        ):
            raise ErroIngestao(f"URL HTTPS obrigatoria para {arquivo}")
        host = urllib.parse.urlparse(url).hostname
        if host not in HOSTS_PERMITIDOS:
            raise ErroIngestao(f"Host nao permitido para {arquivo}: {host}")
        if not isinstance(checksum, str) or not re.fullmatch(r"[0-9a-f]{64}", checksum):
            raise ErroIngestao(f"SHA-256 invalido para {arquivo}")
        tipos.add(tipo)
        arquivos.add(arquivo)
    if tipos != TIPOS_FONTE:
        raise ErroIngestao("O manifesto precisa de Empresas, Estabelecimentos e Municipios")

    _expandir_municipios_manifesto(manifesto, caminho_municipios_ibge)
    return manifesto


def baixar_fonte(fonte: dict[str, str], destino: Path) -> None:
    requisicao = urllib.request.Request(
        fonte["url"],
        headers={"User-Agent": "LeadsHunter-CNPJ-00/1.0"},
    )
    contexto_ssl = ssl.create_default_context()
    try:
        with urllib.request.urlopen(
            requisicao,
            timeout=120,
            context=contexto_ssl,
        ) as resposta:
            host_final = urllib.parse.urlparse(resposta.geturl()).hostname
            if host_final not in HOSTS_PERMITIDOS:
                raise ErroIngestao(
                    f"Redirecionamento para host nao permitido: {host_final}"
                )
            tamanho = resposta.headers.get("Content-Length")
            if tamanho and int(tamanho) > MAXIMO_COMPACTADO:
                raise ErroIngestao(f"Fonte excede o limite: {fonte['arquivo']}")
            total = 0
            with destino.open("wb") as arquivo:
                while bloco := resposta.read(1024 * 1024):
                    total += len(bloco)
                    if total > MAXIMO_COMPACTADO:
                        raise ErroIngestao(f"Fonte excede o limite: {fonte['arquivo']}")
                    arquivo.write(bloco)
    except OSError as erro:
        raise ErroIngestao(f"Falha ao baixar {fonte['arquivo']}") from erro


def resolver_fontes(
    manifesto: dict[str, Any],
    diretorio_fontes: Path | None,
    temporario: Path,
) -> list[tuple[dict[str, str], Path]]:
    resolvidas = []
    for fonte in manifesto["fontes"]:
        caminho = (
            diretorio_fontes / fonte["arquivo"]
            if diretorio_fontes is not None
            else temporario / fonte["arquivo"]
        )
        if diretorio_fontes is None:
            baixar_fonte(fonte, caminho)
        elif not caminho.is_file():
            raise ErroIngestao(f"Fonte local nao encontrada: {caminho}")
        if caminho.stat().st_size > MAXIMO_COMPACTADO:
            raise ErroIngestao(f"Fonte excede o limite: {fonte['arquivo']}")
        obtido = sha256_arquivo(caminho)
        if obtido != fonte["sha256"]:
            raise ErroIngestao(
                f"Checksum inesperado para {fonte['arquivo']}: "
                f"esperado {fonte['sha256']}, obtido {obtido}"
            )
        resolvidas.append((fonte, caminho))
    return resolvidas


@contextmanager
def abrir_csvs(caminho: Path) -> Iterator[list[TextIO]]:
    recursos: list[Any] = []
    textos: list[TextIO] = []
    try:
        if caminho.suffix.lower() == ".zip":
            arquivo_zip = zipfile.ZipFile(caminho)
            recursos.append(arquivo_zip)
            membros = [item for item in arquivo_zip.infolist() if not item.is_dir()]
            if not 1 <= len(membros) <= MAXIMO_MEMBROS_ZIP:
                raise ErroIngestao(f"Quantidade insegura de membros em {caminho.name}")
            for membro in membros:
                if (
                    Path(membro.filename).name != membro.filename
                    or membro.file_size > MAXIMO_DESCOMPACTADO_POR_MEMBRO
                ):
                    raise ErroIngestao(f"Membro ZIP inseguro: {membro.filename}")
                binario = arquivo_zip.open(membro)
                texto = io.TextIOWrapper(binario, encoding="latin-1", newline="")
                recursos.extend([texto, binario])
                textos.append(texto)
        else:
            texto = caminho.open("r", encoding="latin-1", newline="")
            recursos.append(texto)
            textos.append(texto)
        yield textos
    except (OSError, zipfile.BadZipFile, UnicodeError) as erro:
        raise ErroIngestao(f"Fonte CSV/ZIP invalida: {caminho.name}") from erro
    finally:
        for recurso in reversed(recursos):
            recurso.close()


def iterar_registros(caminho: Path) -> Iterator[list[str]]:
    with abrir_csvs(caminho) as textos:
        for texto in textos:
            leitor = csv.reader(texto, delimiter=";", quotechar='"')
            linha = 0
            try:
                for linha, registro in enumerate(leitor, start=1):
                    if not registro or all(not campo for campo in registro):
                        continue
                    yield registro
            except csv.Error as erro:
                raise ErroIngestao(
                    f"CSV invalido em {caminho.name}, linha {linha + 1}"
                ) from erro


def indexar_municipios(fontes: list[Path]) -> dict[str, str]:
    indice: dict[str, str] = {}
    for caminho in fontes:
        for registro in iterar_registros(caminho):
            if len(registro) < 2:
                raise ErroIngestao(f"Registro de Municipio incompleto em {caminho.name}")
            codigo = registro[0].strip()
            nome = normalizar_texto(registro[1])
            if not codigo or not nome:
                raise ErroIngestao(f"Registro de Municipio invalido em {caminho.name}")
            anterior = indice.setdefault(codigo, nome)
            if anterior != nome:
                raise ErroIngestao(f"Codigo municipal duplicado com nomes distintos: {codigo}")
    return indice


def validar_correspondencia_municipios(
    municipios_receita: dict[str, str],
    alvos: dict[tuple[str, str], dict[str, str]],
) -> None:
    nomes_receita = set(municipios_receita.values())
    divergentes = sorted(
        (
            f"{alvo['codigoIbge']} {alvo['nome']}/{alvo['uf']}"
            for (_, nome), alvo in alvos.items()
            if nome not in nomes_receita
        )
    )
    if divergentes:
        raise ErroIngestao(
            "Municipios IBGE sem correspondencia nominal na Receita: "
            + ", ".join(divergentes)
        )


def _extrair_estabelecimentos_arquivo(
    argumentos: tuple[
        Path,
        dict[str, str],
        dict[tuple[str, str], dict[str, str]],
        str,
    ],
) -> tuple[dict[str, dict[str, str | None]], set[str]]:
    caminho, municipios_receita, alvos, data_base = argumentos
    estabelecimentos: dict[str, dict[str, str | None]] = {}
    bases: set[str] = set()
    for registro in iterar_registros(caminho):
        if len(registro) < 21:
            raise ErroIngestao(
                f"Registro de Estabelecimento incompleto em {caminho.name}"
            )
        if registro[5].strip() != SITUACAO_ATIVA:
            continue
        uf = registro[19].strip().upper()
        nome_municipio = municipios_receita.get(registro[20].strip())
        alvo = alvos.get((uf, nome_municipio or ""))
        if alvo is None:
            continue

        cnpj_base = somente_digitos(registro[0])
        cnpj = cnpj_base + somente_digitos(registro[1]) + somente_digitos(registro[2])
        if not cnpj_valido(cnpj):
            raise ErroIngestao(f"CNPJ invalido na fonte {caminho.name}: {cnpj!r}")
        if cnpj in estabelecimentos:
            raise ErroIngestao(f"CNPJ duplicado na fonte {caminho.name}: {cnpj}")

        fantasia = registro[4].strip() or None
        logradouro = " ".join(
            parte for parte in (registro[13].strip(), registro[14].strip()) if parte
        ) or None
        bairro = registro[17].strip() or None
        cep = somente_digitos(registro[18]) or None
        if cep is not None and len(cep) != 8:
            raise ErroIngestao(f"CEP invalido para {cnpj}: {cep!r}")
        estabelecimentos[cnpj] = {
            "cnpj": cnpj,
            "cnpjBase": cnpj_base,
            "nomeFantasia": fantasia,
            "nomeFantasiaNormalizado": normalizar_texto(fantasia),
            "logradouro": logradouro,
            "logradouroNormalizado": normalizar_texto(logradouro),
            "numero": registro[15].strip() or None,
            "bairro": bairro,
            "bairroNormalizado": normalizar_texto(bairro),
            "cep": cep,
            "municipioCodigoIbge": alvo["codigoIbge"],
            "uf": uf,
            "situacaoCadastral": SITUACAO_ATIVA,
            "dataBase": data_base,
        }
        bases.add(cnpj_base)
    return estabelecimentos, bases


def extrair_estabelecimentos(
    fontes: list[Path],
    municipios_receita: dict[str, str],
    alvos: dict[tuple[str, str], dict[str, str]],
    data_base: str,
    trabalhadores: int = 1,
) -> tuple[list[dict[str, str | None]], set[str]]:
    estabelecimentos: dict[str, dict[str, str | None]] = {}
    bases: set[str] = set()
    tarefas = [
        (caminho, municipios_receita, alvos, data_base)
        for caminho in fontes
    ]

    def incorporar(
        resultado: tuple[dict[str, dict[str, str | None]], set[str]],
    ) -> None:
        encontrados, bases_encontradas = resultado
        duplicados = estabelecimentos.keys() & encontrados.keys()
        if duplicados:
            raise ErroIngestao(
                "CNPJ duplicado nas fontes: " + ", ".join(sorted(duplicados))
            )
        estabelecimentos.update(encontrados)
        bases.update(bases_encontradas)

    if trabalhadores > 1 and len(tarefas) > 1:
        with ProcessPoolExecutor(
            max_workers=min(trabalhadores, len(tarefas))
        ) as executor:
            for resultado in executor.map(_extrair_estabelecimentos_arquivo, tarefas):
                incorporar(resultado)
    else:
        for tarefa in tarefas:
            incorporar(_extrair_estabelecimentos_arquivo(tarefa))
    return [estabelecimentos[cnpj] for cnpj in sorted(estabelecimentos)], bases


def _extrair_empresas_arquivo(
    argumentos: tuple[Path, frozenset[str], str],
) -> dict[str, dict[str, str]]:
    caminho, bases_necessarias, data_base = argumentos
    empresas: dict[str, dict[str, str]] = {}
    for registro in iterar_registros(caminho):
        if len(registro) < 2:
            raise ErroIngestao(f"Registro de Empresa incompleto em {caminho.name}")
        cnpj_base = somente_digitos(registro[0])
        if cnpj_base not in bases_necessarias:
            continue
        razao_social = registro[1].strip()
        if not razao_social:
            raise ErroIngestao(f"Razao social vazia para {cnpj_base}")
        empresa = {
            "cnpjBase": cnpj_base,
            "razaoSocial": razao_social,
            "razaoSocialNormalizada": normalizar_texto(razao_social),
            "dataBase": data_base,
        }
        anterior = empresas.setdefault(cnpj_base, empresa)
        if anterior != empresa:
            raise ErroIngestao(f"Empresa duplicada com dados distintos: {cnpj_base}")
    return empresas


def extrair_empresas(
    fontes: list[Path],
    bases_necessarias: set[str],
    data_base: str,
    trabalhadores: int = 1,
) -> list[dict[str, str]]:
    empresas: dict[str, dict[str, str]] = {}
    bases_compartilhadas = frozenset(bases_necessarias)
    tarefas = [
        (caminho, bases_compartilhadas, data_base)
        for caminho in fontes
    ]

    def incorporar(encontradas: dict[str, dict[str, str]]) -> None:
        for cnpj_base, empresa in encontradas.items():
            anterior = empresas.setdefault(cnpj_base, empresa)
            if anterior != empresa:
                raise ErroIngestao(
                    f"Empresa duplicada com dados distintos: {cnpj_base}"
                )

    if trabalhadores > 1 and len(tarefas) > 1:
        with ProcessPoolExecutor(
            max_workers=min(trabalhadores, len(tarefas))
        ) as executor:
            for encontradas in executor.map(_extrair_empresas_arquivo, tarefas):
                incorporar(encontradas)
    else:
        for tarefa in tarefas:
            incorporar(_extrair_empresas_arquivo(tarefa))

    ausentes = sorted(bases_necessarias - empresas.keys())
    if ausentes:
        raise ErroIngestao(f"Empresas ausentes para bases selecionadas: {', '.join(ausentes)}")
    return [empresas[cnpj_base] for cnpj_base in sorted(empresas)]


def resolver_trabalhadores(solicitados: int, quantidade_arquivos: int) -> int:
    if solicitados < 0 or solicitados > MAXIMO_TRABALHADORES:
        raise ErroIngestao(
            f"workers deve estar entre 0 e {MAXIMO_TRABALHADORES}"
        )
    if quantidade_arquivos < 1:
        return 1
    if solicitados > 0:
        return min(solicitados, quantidade_arquivos)
    process_cpu_count = getattr(os, "process_cpu_count", os.cpu_count)
    cpus_disponiveis = process_cpu_count() or 1
    return max(
        1,
        min(
            cpus_disponiveis,
            MAXIMO_TRABALHADORES_AUTOMATICOS,
            quantidade_arquivos,
        ),
    )


def gerar_dataset(
    manifesto: dict[str, Any],
    fontes: list[tuple[dict[str, str], Path]],
    trabalhadores: int = 1,
) -> dict[str, Any]:
    por_tipo: dict[str, list[Path]] = {tipo: [] for tipo in TIPOS_FONTE}
    for fonte, caminho in fontes:
        por_tipo[fonte["tipo"]].append(caminho)

    municipios_receita = indexar_municipios(por_tipo["municipios"])
    alvos = {
        (municipio["uf"].upper(), normalizar_texto(municipio["nome"])): {
            "codigoIbge": municipio["codigoIbge"],
            "nome": municipio["nome"],
            "uf": municipio["uf"].upper(),
        }
        for municipio in manifesto["municipiosInteresse"]
    }
    validar_correspondencia_municipios(municipios_receita, alvos)
    estabelecimentos, bases = extrair_estabelecimentos(
        por_tipo["estabelecimentos"],
        municipios_receita,
        alvos,
        manifesto["dataBase"],
        trabalhadores,
    )
    codigos_encontrados = {
        estabelecimento["municipioCodigoIbge"] for estabelecimento in estabelecimentos
    }
    codigos_esperados = {municipio["codigoIbge"] for municipio in alvos.values()}
    ausentes = codigos_esperados - codigos_encontrados
    if ausentes:
        raise ErroIngestao(
            "Nenhum estabelecimento ativo encontrado para: " + ", ".join(sorted(ausentes))
        )
    empresas = extrair_empresas(
        por_tipo["empresas"],
        bases,
        manifesto["dataBase"],
        trabalhadores,
    )

    fontes_metadata = [
        {
            "arquivo": fonte["arquivo"],
            "sha256": fonte["sha256"],
            "tipo": fonte["tipo"],
            "url": fonte["url"],
        }
        for fonte in sorted(manifesto["fontes"], key=lambda item: item["arquivo"])
    ]
    municipios_metadata = sorted(
        (
            {
                "codigoIbge": municipio["codigoIbge"],
                "nome": municipio["nome"],
                "uf": municipio["uf"].upper(),
            }
            for municipio in manifesto["municipiosInteresse"]
        ),
        key=lambda item: item["codigoIbge"],
    )
    return {
        "metadata": {
            "dataBase": manifesto["dataBase"],
            "empresas": len(empresas),
            "estabelecimentos": len(estabelecimentos),
            "fontes": fontes_metadata,
            "municipios": municipios_metadata,
            "ufs": manifesto.get("ufs", []),
        },
        "empresas": empresas,
        "estabelecimentos": estabelecimentos,
    }


def escrever_atomicamente(
    destino: Path,
    escrever: Callable[[TextIO], None],
) -> None:
    destino.parent.mkdir(parents=True, exist_ok=True)
    caminho_temporario: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            "w",
            encoding="utf-8",
            newline="",
            dir=destino.parent,
            delete=False,
        ) as temporario:
            caminho_temporario = Path(temporario.name)
            escrever(temporario)
        caminho_temporario.replace(destino)
    except BaseException:
        if caminho_temporario is not None:
            caminho_temporario.unlink(missing_ok=True)
        raise


def escrever_dataset(dataset: dict[str, Any], destino: Path) -> None:
    codificador = json.JSONEncoder(
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )

    def escrever(arquivo: TextIO) -> None:
        for trecho in codificador.iterencode(dataset):
            arquivo.write(trecho)
        arquivo.write("\n")

    escrever_atomicamente(destino, escrever)


def literal_sql(valor: str | None) -> str:
    if valor is None:
        return "NULL"
    if any(caractere in valor for caractere in ("\\", "\0", "\n", "\r", "\x1a")):
        return f"CONVERT(X'{valor.encode('utf-8').hex()}' USING utf8mb4)"
    return "'" + valor.replace("'", "''") + "'"


def iterar_comandos_insert(
    tabela: str,
    colunas: list[str],
    itens: Sequence[T],
    para_registro: Callable[[T], Sequence[str | None]],
    atualizacoes: list[str],
) -> Iterator[str]:
    for inicio in range(0, len(itens), 500):
        lote = itens[inicio: inicio + 500]
        valores = ",\n".join(
            "("
            + ",".join(literal_sql(valor) for valor in para_registro(item))
            + ")"
            for item in lote
        )
        yield (
            f"INSERT INTO {tabela} ({','.join(colunas)}) VALUES\n{valores}\n"
            "ON DUPLICATE KEY UPDATE\n    "
            + ",\n    ".join(
                f"{coluna} = VALUES({coluna})" for coluna in atualizacoes
            )
            + ";"
        )


def escrever_migration_sql(dataset: dict[str, Any], destino: Path) -> None:
    municipios = [
        municipio["codigoIbge"] for municipio in dataset["metadata"]["municipios"]
    ]
    comentarios_fontes = [
        "-- Fonte: "
        + fonte["tipo"]
        + " | "
        + fonte["arquivo"]
        + " | sha256="
        + fonte["sha256"]
        + " | "
        + fonte["url"]
        for fonte in dataset["metadata"]["fontes"]
    ]
    comandos_iniciais = [
        "-- Gerado por tools/cnpj/gerar_dataset.py; nao editar manualmente.",
        f"-- Competencia da base RFB: {dataset['metadata']['dataBase']}.",
        "-- UFs expandidas: "
        + (", ".join(dataset["metadata"].get("ufs", [])) or "nenhuma"),
        *comentarios_fontes,
        "DELETE FROM cnpj_estabelecimento WHERE municipio_codigo_ibge IN ("
        + ",".join(literal_sql(codigo) for codigo in municipios)
        + ");",
        "DELETE FROM cnpj_empresa WHERE NOT EXISTS ("
        "SELECT 1 FROM cnpj_estabelecimento "
        "WHERE cnpj_estabelecimento.cnpj_base = cnpj_empresa.cnpj_base"
        ");",
    ]
    comandos_empresas = iterar_comandos_insert(
        "cnpj_empresa",
        ["cnpj_base", "razao_social", "razao_social_normalizada", "data_base"],
        dataset["empresas"],
        lambda empresa: [
            empresa["cnpjBase"],
            empresa["razaoSocial"],
            empresa["razaoSocialNormalizada"],
            empresa["dataBase"],
        ],
        ["razao_social", "razao_social_normalizada", "data_base"],
    )
    comandos_estabelecimentos = iterar_comandos_insert(
        "cnpj_estabelecimento",
        [
            "cnpj", "cnpj_base", "nome_fantasia", "nome_fantasia_normalizado",
            "logradouro", "logradouro_normalizado", "numero", "bairro",
            "bairro_normalizado", "cep", "municipio_codigo_ibge", "uf",
            "situacao_cadastral", "data_base",
        ],
        dataset["estabelecimentos"],
        lambda item: [
            item["cnpj"],
            item["cnpjBase"],
            item["nomeFantasia"],
            item["nomeFantasiaNormalizado"],
            item["logradouro"],
            item["logradouroNormalizado"],
            item["numero"],
            item["bairro"],
            item["bairroNormalizado"],
            item["cep"],
            item["municipioCodigoIbge"],
            item["uf"],
            item["situacaoCadastral"],
            item["dataBase"],
        ],
        [
            "cnpj_base", "nome_fantasia", "nome_fantasia_normalizado",
            "logradouro", "logradouro_normalizado", "numero", "bairro",
            "bairro_normalizado", "cep", "municipio_codigo_ibge", "uf",
            "situacao_cadastral", "data_base",
        ],
    )

    def escrever(arquivo: TextIO) -> None:
        primeiro = True
        for grupo in (
            comandos_iniciais,
            comandos_empresas,
            comandos_estabelecimentos,
        ):
            for comando in grupo:
                if not primeiro:
                    arquivo.write("\n\n")
                arquivo.write(comando)
                primeiro = False
        arquivo.write("\n")

    escrever_atomicamente(destino, escrever)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--source-dir", type=Path)
    parser.add_argument("--output", type=Path, default=SAIDA_PADRAO)
    parser.add_argument("--output-sql", type=Path)
    parser.add_argument(
        "--municipios-ibge",
        type=Path,
        default=MUNICIPIOS_IBGE_PADRAO,
        help="Catalogo CSV versionado usado para expandir as UFs",
    )
    parser.add_argument(
        "--workers",
        type=int,
        default=0,
        help="Processos de parsing (0=automatico, 1=sequencial, maximo 16)",
    )
    parser.add_argument(
        "--no-json",
        action="store_true",
        help="Gera somente o SQL e evita materializar o artefato JSON",
    )
    argumentos = parser.parse_args()
    if argumentos.no_json and argumentos.output_sql is None:
        parser.error("--no-json exige --output-sql")

    manifesto = carregar_manifesto(argumentos.manifest, argumentos.municipios_ibge)
    quantidade_lotes = max(
        sum(1 for fonte in manifesto["fontes"] if fonte["tipo"] == tipo)
        for tipo in ("empresas", "estabelecimentos")
    )
    trabalhadores = resolver_trabalhadores(argumentos.workers, quantidade_lotes)
    with tempfile.TemporaryDirectory(prefix="leads-hunter-cnpj-") as diretorio:
        temporario = Path(diretorio)
        fontes = resolver_fontes(manifesto, argumentos.source_dir, temporario)
        dataset = gerar_dataset(manifesto, fontes, trabalhadores)
        if not argumentos.no_json:
            escrever_dataset(dataset, argumentos.output)
        if argumentos.output_sql is not None:
            escrever_migration_sql(dataset, argumentos.output_sql)

    print(
        f"Ingestao concluida com {trabalhadores} worker(s): "
        f"{len(dataset['metadata']['municipios'])} municipios, "
        f"{dataset['metadata']['empresas']} empresas e "
        f"{dataset['metadata']['estabelecimentos']} estabelecimentos."
    )
    if not argumentos.no_json:
        print(
            f"JSON gerado em {argumentos.output}; "
            f"SHA-256 {sha256_arquivo(argumentos.output)}"
        )
    if argumentos.output_sql is not None:
        print(
            f"SQL gerado em {argumentos.output_sql}; "
            f"SHA-256 {sha256_arquivo(argumentos.output_sql)}"
        )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ErroIngestao as erro:
        raise SystemExit(f"Erro: {erro}") from erro
