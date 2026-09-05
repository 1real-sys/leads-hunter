#!/usr/bin/env python3
"""Gera o dataset municipal offline usado pela feature de IDHM.

O script usa apenas a biblioteca padrão do Python. As fontes remotas e seus
checksums são fixos para que uma mudança upstream exija revisão explícita.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import math
import os
import ssl
import tempfile
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterable


RAIZ_PROJETO = Path(__file__).resolve().parents[2]
SAIDA_PADRAO = RAIZ_PROJETO / "src/main/resources/geo/municipios-idhm.json"

FONTE_IDHM = {
    "nome": "Atlas Cidade — Dados abertos dos municípios do Brasil",
    "url": "https://www.atlascidade.com.br/dados/municipios-brasil.json",
    "arquivo": "municipios-brasil.json",
    "sha256": "a19ddcab8150d68248fe14c002f58096bdba63af64b7cb00df4220e9e22e007e",
    "licenca": "CC BY 4.0",
    "atribuicao": "Atlas Cidade; IDHM 2010: PNUD, Ipea e FJP",
}

FONTE_GEOMETRIA = {
    "nome": "IBGE — API de Malhas Geográficas v3",
    "url": (
        "https://servicodados.ibge.gov.br/api/v3/malhas/paises/BR"
        "?formato=application/vnd.geo+json&qualidade=minima&intrarregiao=municipio"
    ),
    "arquivo": "ibge-municipios-min.geojson",
    "sha256": "5efffedf8772dc8654322affafac4e3e8b001839e7c2af5cab4eb1094dcd6240",
    "licenca": "Dados públicos do IBGE; condições compatíveis com CC BY 4.0",
    "atribuicao": "Instituto Brasileiro de Geografia e Estatística — IBGE",
}

HOSTS_PERMITIDOS = {"www.atlascidade.com.br", "servicodados.ibge.gov.br"}
TAMANHO_MAXIMO_FONTE = 12 * 1024 * 1024
QUANTIDADE_GEOMETRIAS_ESPERADA = 5_570
MUNICIPIOS_SEM_GEOMETRIA_ESPERADOS = {"5101837"}


class ErroDataset(RuntimeError):
    """Indica uma inconsistência de fonte ou de geração."""


def sha256(conteudo: bytes) -> str:
    return hashlib.sha256(conteudo).hexdigest()


def descompactar_gzip(conteudo: bytes, nome: str) -> bytes:
    if not conteudo.startswith(b"\x1f\x8b"):
        return conteudo
    try:
        with gzip.GzipFile(fileobj=io.BytesIO(conteudo)) as arquivo:
            descompactado = arquivo.read(TAMANHO_MAXIMO_FONTE + 1)
    except OSError as erro:
        raise ErroDataset(f"Resposta gzip inválida para {nome}") from erro
    if len(descompactado) > TAMANHO_MAXIMO_FONTE:
        raise ErroDataset(
            f"Fonte descompactada excede o limite de {TAMANHO_MAXIMO_FONTE} bytes"
        )
    return descompactado


def baixar(fonte: dict[str, str]) -> bytes:
    url = fonte["url"]
    host = urllib.parse.urlparse(url).hostname
    if host not in HOSTS_PERMITIDOS or not url.startswith("https://"):
        raise ErroDataset(f"Fonte remota não permitida: {url}")

    requisicao = urllib.request.Request(
        url,
        headers={"User-Agent": "LeadsHunter-IDHM-00/1.0"},
    )
    contexto_ssl = ssl.create_default_context()
    with urllib.request.urlopen(requisicao, timeout=60, context=contexto_ssl) as resposta:
        url_final = resposta.geturl()
        host_final = urllib.parse.urlparse(url_final).hostname
        if host_final not in HOSTS_PERMITIDOS:
            raise ErroDataset(f"Redirecionamento para host não permitido: {host_final}")

        tamanho_declarado = resposta.headers.get("Content-Length")
        if tamanho_declarado and int(tamanho_declarado) > TAMANHO_MAXIMO_FONTE:
            raise ErroDataset(f"Fonte excede o limite de {TAMANHO_MAXIMO_FONTE} bytes")

        conteudo = resposta.read(TAMANHO_MAXIMO_FONTE + 1)
        if len(conteudo) > TAMANHO_MAXIMO_FONTE:
            raise ErroDataset(f"Fonte excede o limite de {TAMANHO_MAXIMO_FONTE} bytes")
        return descompactar_gzip(conteudo, fonte["arquivo"])


def ler_fonte(fonte: dict[str, str], diretorio_fontes: Path | None) -> bytes:
    if diretorio_fontes is None:
        conteudo = baixar(fonte)
    else:
        caminho = diretorio_fontes / fonte["arquivo"]
        if not caminho.is_file():
            raise ErroDataset(f"Fonte local não encontrada: {caminho}")
        conteudo = caminho.read_bytes()

    hash_obtido = sha256(conteudo)
    hash_esperado = fonte.get("sha256")
    if hash_esperado is not None and hash_obtido != hash_esperado:
        raise ErroDataset(
            f"Checksum inesperado para {fonte['arquivo']}: "
            f"esperado {hash_esperado}, obtido {hash_obtido}"
        )
    return conteudo


def carregar_json(conteudo: bytes, nome: str) -> Any:
    try:
        return json.loads(conteudo)
    except (UnicodeDecodeError, json.JSONDecodeError) as erro:
        raise ErroDataset(f"JSON inválido em {nome}") from erro


def codigo_ibge(valor: Any) -> str:
    try:
        codigo = f"{int(valor):07d}"
    except (TypeError, ValueError) as erro:
        raise ErroDataset(f"Código IBGE inválido: {valor!r}") from erro
    if len(codigo) != 7:
        raise ErroDataset(f"Código IBGE fora do formato de 7 dígitos: {codigo}")
    return codigo


def normalizar_idhm(valor: Any, codigo: str) -> float | None:
    if valor is None or valor == "":
        return None
    try:
        idhm = float(valor)
    except (TypeError, ValueError) as erro:
        raise ErroDataset(f"IDHM inválido para {codigo}: {valor!r}") from erro
    if not math.isfinite(idhm) or not 0 <= idhm <= 1:
        raise ErroDataset(f"IDHM fora do intervalo para {codigo}: {idhm}")
    return round(idhm, 3)


def indexar_idhm(registros: Any) -> dict[str, dict[str, Any]]:
    if not isinstance(registros, list):
        raise ErroDataset("A fonte de IDHM deve ser um array JSON")

    indice: dict[str, dict[str, Any]] = {}
    for registro in registros:
        if not isinstance(registro, dict):
            raise ErroDataset("Registro não-objeto encontrado na fonte de IDHM")
        codigo = codigo_ibge(registro.get("codigo_ibge"))
        if codigo in indice:
            raise ErroDataset(f"Código IBGE duplicado na fonte de IDHM: {codigo}")

        nome = registro.get("municipio")
        uf = registro.get("uf")
        if not isinstance(nome, str) or not nome.strip():
            raise ErroDataset(f"Município sem nome para o código {codigo}")
        if not isinstance(uf, str) or len(uf) != 2 or not uf.isalpha():
            raise ErroDataset(f"UF inválida para o código {codigo}: {uf!r}")

        indice[codigo] = {
            "codigoIbge": codigo,
            "nome": nome.strip(),
            "uf": uf.upper(),
            "idhm": normalizar_idhm(registro.get("idhm_2010"), codigo),
            "idhmReferencia": 2010,
        }
    return indice


def distancia_perpendicular(
    ponto: list[float], inicio: list[float], fim: list[float]
) -> float:
    dx = fim[0] - inicio[0]
    dy = fim[1] - inicio[1]
    if dx == 0 and dy == 0:
        return math.hypot(ponto[0] - inicio[0], ponto[1] - inicio[1])
    numerador = abs(dy * ponto[0] - dx * ponto[1] + fim[0] * inicio[1] - fim[1] * inicio[0])
    return numerador / math.hypot(dx, dy)


def simplificar_linha(pontos: list[list[float]], tolerancia: float) -> list[list[float]]:
    if len(pontos) <= 2 or tolerancia <= 0:
        return pontos

    maior_distancia = 0.0
    indice = 0
    for atual in range(1, len(pontos) - 1):
        distancia = distancia_perpendicular(pontos[atual], pontos[0], pontos[-1])
        if distancia > maior_distancia:
            indice = atual
            maior_distancia = distancia

    if maior_distancia <= tolerancia:
        return [pontos[0], pontos[-1]]

    esquerda = simplificar_linha(pontos[: indice + 1], tolerancia)
    direita = simplificar_linha(pontos[indice:], tolerancia)
    return esquerda[:-1] + direita


def simplificar_anel(anel: Any, tolerancia: float) -> list[list[float]]:
    if not isinstance(anel, list):
        raise ErroDataset("Anel de geometria inválido")

    pontos: list[list[float]] = []
    for ponto in anel:
        if (
            not isinstance(ponto, list)
            or len(ponto) < 2
            or not all(isinstance(valor, (int, float)) and math.isfinite(valor) for valor in ponto[:2])
        ):
            raise ErroDataset(f"Coordenada inválida: {ponto!r}")
        coordenada = [round(float(ponto[0]), 5), round(float(ponto[1]), 5)]
        if not pontos or coordenada != pontos[-1]:
            pontos.append(coordenada)

    if len(pontos) < 4:
        raise ErroDataset("Anel com menos de quatro posições")
    if pontos[0] != pontos[-1]:
        pontos.append(pontos[0])

    abertos = pontos[:-1]
    indice_oposto = max(
        range(1, len(abertos)),
        key=lambda indice: math.dist(abertos[0], abertos[indice]),
    )
    trecho_a = simplificar_linha(abertos[: indice_oposto + 1], tolerancia)
    trecho_b = simplificar_linha(abertos[indice_oposto:] + [abertos[0]], tolerancia)
    simplificado = trecho_a + trecho_b[1:-1]

    if len({tuple(ponto) for ponto in simplificado}) < 3:
        return pontos
    simplificado.append(simplificado[0])
    return simplificado


def simplificar_geometria(geometria: Any, tolerancia: float) -> dict[str, Any]:
    if not isinstance(geometria, dict):
        raise ErroDataset("Geometria ausente ou inválida")
    tipo = geometria.get("type")
    coordenadas = geometria.get("coordinates")

    if tipo == "Polygon" and isinstance(coordenadas, list):
        processadas = [simplificar_anel(anel, tolerancia) for anel in coordenadas]
    elif tipo == "MultiPolygon" and isinstance(coordenadas, list):
        processadas = [
            [simplificar_anel(anel, tolerancia) for anel in poligono]
            for poligono in coordenadas
        ]
    else:
        raise ErroDataset(f"Tipo de geometria não suportado: {tipo!r}")

    return {"type": tipo, "coordinates": processadas}


def iterar_posicoes(valor: Any) -> Iterable[list[float]]:
    if (
        isinstance(valor, list)
        and len(valor) >= 2
        and isinstance(valor[0], (int, float))
        and isinstance(valor[1], (int, float))
    ):
        yield valor
        return
    if isinstance(valor, list):
        for item in valor:
            yield from iterar_posicoes(item)


def calcular_bbox(geometria: dict[str, Any]) -> list[float]:
    posicoes = list(iterar_posicoes(geometria["coordinates"]))
    if not posicoes:
        raise ErroDataset("Geometria sem coordenadas")
    longitudes = [ponto[0] for ponto in posicoes]
    latitudes = [ponto[1] for ponto in posicoes]
    return [min(longitudes), min(latitudes), max(longitudes), max(latitudes)]


def montar_dataset(
    dados_idhm: Any,
    malha: Any,
    tolerancia: float,
    hash_idhm: str,
    hash_geometria: str,
) -> dict[str, Any]:
    if tolerancia < 0 or not math.isfinite(tolerancia):
        raise ErroDataset("A tolerância de simplificação deve ser finita e não negativa")
    if not isinstance(malha, dict) or malha.get("type") != "FeatureCollection":
        raise ErroDataset("A malha do IBGE deve ser uma FeatureCollection")
    features = malha.get("features")
    if not isinstance(features, list):
        raise ErroDataset("A malha do IBGE não possui uma lista de features")

    indice_idhm = indexar_idhm(dados_idhm)
    municipios: list[dict[str, Any]] = []
    codigos_geometria: set[str] = set()

    for feature in features:
        if not isinstance(feature, dict):
            raise ErroDataset("Feature inválida na malha do IBGE")
        propriedades = feature.get("properties")
        if not isinstance(propriedades, dict):
            raise ErroDataset("Feature sem propriedades na malha do IBGE")
        codigo = codigo_ibge(propriedades.get("codarea"))
        if codigo in codigos_geometria:
            raise ErroDataset(f"Código IBGE duplicado na malha: {codigo}")
        codigos_geometria.add(codigo)

        informacao = indice_idhm.get(codigo)
        if informacao is None:
            raise ErroDataset(f"Município da malha sem correspondência de dados: {codigo}")
        geometria = simplificar_geometria(feature.get("geometry"), tolerancia)
        municipios.append({**informacao, "bbox": calcular_bbox(geometria), "geometry": geometria})

    sem_geometria = set(indice_idhm) - codigos_geometria
    if len(municipios) != QUANTIDADE_GEOMETRIAS_ESPERADA:
        raise ErroDataset(
            f"Quantidade de geometrias inesperada: {len(municipios)}; "
            f"esperado {QUANTIDADE_GEOMETRIAS_ESPERADA}"
        )
    if sem_geometria != MUNICIPIOS_SEM_GEOMETRIA_ESPERADOS:
        raise ErroDataset(
            "Diferença inesperada entre dados e malha: " + ", ".join(sorted(sem_geometria))
        )

    municipios.sort(key=lambda municipio: municipio["codigoIbge"])
    return {
        "metadata": {
            "schemaVersion": 1,
            "municipios": len(municipios),
            "idhmReferencia": 2010,
            "simplificacaoToleranciaGraus": tolerancia,
            "municipiosSemGeometria": sorted(sem_geometria),
            "idhm": {**FONTE_IDHM, "sha256": hash_idhm},
            "geometria": {**FONTE_GEOMETRIA, "sha256": hash_geometria},
        },
        "municipios": municipios,
    }


def gravar_atomico(destino: Path, dataset: dict[str, Any]) -> None:
    destino.parent.mkdir(parents=True, exist_ok=True)
    descritor, nome_temporario = tempfile.mkstemp(
        prefix=f".{destino.name}.",
        suffix=".tmp",
        dir=destino.parent,
    )
    try:
        with os.fdopen(descritor, "w", encoding="utf-8") as arquivo:
            json.dump(dataset, arquivo, ensure_ascii=False, separators=(",", ":"))
            arquivo.write("\n")
        os.replace(nome_temporario, destino)
    except BaseException:
        try:
            os.unlink(nome_temporario)
        except FileNotFoundError:
            pass
        raise


def argumentos() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=SAIDA_PADRAO,
        help=f"arquivo de saída (padrão: {SAIDA_PADRAO})",
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        help=(
            "diretório com municipios-brasil.json e ibge-municipios-min.geojson; "
            "se omitido, baixa as fontes fixas por HTTPS"
        ),
    )
    parser.add_argument(
        "--simplification-tolerance",
        type=float,
        default=0.001,
        help="tolerância RDP em graus aplicada sobre a malha mínima do IBGE",
    )
    return parser.parse_args()


def main() -> None:
    args = argumentos()
    conteudo_idhm = ler_fonte(FONTE_IDHM, args.source_dir)
    conteudo_malha = ler_fonte(FONTE_GEOMETRIA, args.source_dir)
    dataset = montar_dataset(
        carregar_json(conteudo_idhm, FONTE_IDHM["arquivo"]),
        carregar_json(conteudo_malha, FONTE_GEOMETRIA["arquivo"]),
        args.simplification_tolerance,
        sha256(conteudo_idhm),
        sha256(conteudo_malha),
    )
    gravar_atomico(args.output, dataset)
    tamanho = args.output.stat().st_size
    print(
        f"Dataset gerado: {args.output} | "
        f"{dataset['metadata']['municipios']} municípios | {tamanho} bytes | "
        f"sha256={sha256(args.output.read_bytes())}"
    )


if __name__ == "__main__":
    main()
