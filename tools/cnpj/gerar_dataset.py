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
import re
import ssl
import tempfile
import unicodedata
import urllib.parse
import urllib.request
import zipfile
from contextlib import contextmanager
from datetime import date
from pathlib import Path
from typing import Any, Iterator, TextIO


RAIZ_PROJETO = Path(__file__).resolve().parents[2]
SAIDA_PADRAO = RAIZ_PROJETO / "src/main/resources/cnpj/cnpj-subset.json"
HOSTS_PERMITIDOS = {
    "arquivos.receitafederal.gov.br",
    "dadosabertos.rfb.gov.br",
}
TIPOS_FONTE = {"empresas", "estabelecimentos", "municipios"}
SITUACAO_ATIVA = "02"
MAXIMO_FONTES = 32
MAXIMO_MEMBROS_ZIP = 8
MAXIMO_COMPACTADO = 2 * 1024 * 1024 * 1024
MAXIMO_DESCOMPACTADO_POR_MEMBRO = 12 * 1024 * 1024 * 1024


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
    return " ".join(re.sub(r"[^0-9a-z]+", " ", sem_acentos.lower()).split())


def somente_digitos(valor: str | None) -> str:
    return re.sub(r"\D", "", valor or "")


def cnpj_valido(cnpj: str) -> bool:
    if not re.fullmatch(r"\d{14}", cnpj) or len(set(cnpj)) == 1:
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


def carregar_manifesto(caminho: Path) -> dict[str, Any]:
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

    municipios = manifesto.get("municipiosInteresse")
    if not isinstance(municipios, list) or not municipios:
        raise ErroIngestao("O manifesto deve declarar municipiosInteresse")
    chaves = set()
    codigos = set()
    for municipio in municipios:
        if not isinstance(municipio, dict):
            raise ErroIngestao("Municipio de interesse invalido")
        codigo = municipio.get("codigoIbge")
        nome = municipio.get("nome")
        uf = municipio.get("uf")
        if not isinstance(codigo, str) or not re.fullmatch(r"\d{7}", codigo):
            raise ErroIngestao(f"Codigo IBGE invalido: {codigo!r}")
        if not isinstance(nome, str) or not normalizar_texto(nome):
            raise ErroIngestao(f"Nome municipal invalido: {nome!r}")
        if not isinstance(uf, str) or not re.fullmatch(r"[A-Za-z]{2}", uf):
            raise ErroIngestao(f"UF invalida: {uf!r}")
        chave = (uf.upper(), normalizar_texto(nome))
        if chave in chaves or codigo in codigos:
            raise ErroIngestao(f"Municipio de interesse duplicado: {nome}/{uf}")
        chaves.add(chave)
        codigos.add(codigo)
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
        if zipfile.is_zipfile(caminho):
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


def extrair_estabelecimentos(
    fontes: list[Path],
    municipios_receita: dict[str, str],
    alvos: dict[tuple[str, str], dict[str, str]],
    data_base: str,
) -> tuple[list[dict[str, str | None]], set[str]]:
    estabelecimentos: dict[str, dict[str, str | None]] = {}
    bases = set()
    for caminho in fontes:
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
                raise ErroIngestao(f"CNPJ duplicado nas fontes: {cnpj}")

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
    return [estabelecimentos[cnpj] for cnpj in sorted(estabelecimentos)], bases


def extrair_empresas(
    fontes: list[Path],
    bases_necessarias: set[str],
    data_base: str,
) -> list[dict[str, str]]:
    empresas: dict[str, dict[str, str]] = {}
    for caminho in fontes:
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
    ausentes = sorted(bases_necessarias - empresas.keys())
    if ausentes:
        raise ErroIngestao(f"Empresas ausentes para bases selecionadas: {', '.join(ausentes)}")
    return [empresas[cnpj_base] for cnpj_base in sorted(empresas)]


def gerar_dataset(manifesto: dict[str, Any], fontes: list[tuple[dict[str, str], Path]]) -> dict[str, Any]:
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
    estabelecimentos, bases = extrair_estabelecimentos(
        por_tipo["estabelecimentos"],
        municipios_receita,
        alvos,
        manifesto["dataBase"],
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
    empresas = extrair_empresas(por_tipo["empresas"], bases, manifesto["dataBase"])

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
        },
        "empresas": empresas,
        "estabelecimentos": estabelecimentos,
    }


def escrever_dataset(dataset: dict[str, Any], destino: Path) -> None:
    destino.parent.mkdir(parents=True, exist_ok=True)
    conteudo = json.dumps(
        dataset,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ) + "\n"
    with tempfile.NamedTemporaryFile(
        "w",
        encoding="utf-8",
        dir=destino.parent,
        delete=False,
    ) as temporario:
        temporario.write(conteudo)
        caminho_temporario = Path(temporario.name)
    caminho_temporario.replace(destino)


def literal_sql(valor: str | None) -> str:
    if valor is None:
        return "NULL"
    return "'" + valor.replace("'", "''") + "'"


def comandos_insert(
    tabela: str,
    colunas: list[str],
    registros: list[list[str | None]],
    atualizacoes: list[str],
) -> list[str]:
    comandos = []
    for inicio in range(0, len(registros), 500):
        lote = registros[inicio: inicio + 500]
        valores = ",\n".join(
            "(" + ",".join(literal_sql(valor) for valor in registro) + ")"
            for registro in lote
        )
        comandos.append(
            f"INSERT INTO {tabela} ({','.join(colunas)}) VALUES\n{valores}\n"
            "ON DUPLICATE KEY UPDATE\n    "
            + ",\n    ".join(
                f"{coluna} = VALUES({coluna})" for coluna in atualizacoes
            )
            + ";"
        )
    return comandos


def escrever_migration_sql(dataset: dict[str, Any], destino: Path) -> None:
    municipios = [
        municipio["codigoIbge"] for municipio in dataset["metadata"]["municipios"]
    ]
    empresas = [
        [
            empresa["cnpjBase"],
            empresa["razaoSocial"],
            empresa["razaoSocialNormalizada"],
            empresa["dataBase"],
        ]
        for empresa in dataset["empresas"]
    ]
    estabelecimentos = [
        [
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
        ]
        for item in dataset["estabelecimentos"]
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
    comandos = [
        "-- Gerado por tools/cnpj/gerar_dataset.py; nao editar manualmente.",
        f"-- Competencia da base RFB: {dataset['metadata']['dataBase']}.",
        *comentarios_fontes,
        "DELETE FROM cnpj_estabelecimento WHERE municipio_codigo_ibge IN ("
        + ",".join(literal_sql(codigo) for codigo in municipios)
        + ");",
        "DELETE FROM cnpj_empresa WHERE NOT EXISTS ("
        "SELECT 1 FROM cnpj_estabelecimento "
        "WHERE cnpj_estabelecimento.cnpj_base = cnpj_empresa.cnpj_base"
        ");",
    ]
    comandos.extend(comandos_insert(
        "cnpj_empresa",
        ["cnpj_base", "razao_social", "razao_social_normalizada", "data_base"],
        empresas,
        ["razao_social", "razao_social_normalizada", "data_base"],
    ))
    comandos.extend(comandos_insert(
        "cnpj_estabelecimento",
        [
            "cnpj", "cnpj_base", "nome_fantasia", "nome_fantasia_normalizado",
            "logradouro", "logradouro_normalizado", "numero", "bairro",
            "bairro_normalizado", "cep", "municipio_codigo_ibge", "uf",
            "situacao_cadastral", "data_base",
        ],
        estabelecimentos,
        [
            "cnpj_base", "nome_fantasia", "nome_fantasia_normalizado",
            "logradouro", "logradouro_normalizado", "numero", "bairro",
            "bairro_normalizado", "cep", "municipio_codigo_ibge", "uf",
            "situacao_cadastral", "data_base",
        ],
    ))
    destino.parent.mkdir(parents=True, exist_ok=True)
    conteudo = "\n\n".join(comandos) + "\n"
    with tempfile.NamedTemporaryFile(
        "w", encoding="utf-8", dir=destino.parent, delete=False
    ) as temporario:
        temporario.write(conteudo)
        caminho_temporario = Path(temporario.name)
    caminho_temporario.replace(destino)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--source-dir", type=Path)
    parser.add_argument("--output", type=Path, default=SAIDA_PADRAO)
    parser.add_argument("--output-sql", type=Path)
    argumentos = parser.parse_args()

    manifesto = carregar_manifesto(argumentos.manifest)
    with tempfile.TemporaryDirectory(prefix="leads-hunter-cnpj-") as diretorio:
        temporario = Path(diretorio)
        fontes = resolver_fontes(manifesto, argumentos.source_dir, temporario)
        dataset = gerar_dataset(manifesto, fontes)
        escrever_dataset(dataset, argumentos.output)
        if argumentos.output_sql is not None:
            escrever_migration_sql(dataset, argumentos.output_sql)

    print(
        f"Dataset gerado em {argumentos.output}: "
        f"{dataset['metadata']['estabelecimentos']} estabelecimentos, "
        f"SHA-256 {sha256_arquivo(argumentos.output)}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ErroIngestao as erro:
        raise SystemExit(f"Erro: {erro}") from erro
