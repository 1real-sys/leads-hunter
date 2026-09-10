#!/usr/bin/env python3
"""Gera o catalogo municipal minimo usado pela expansao de UFs do CNPJ."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import tempfile
from pathlib import Path
from typing import Any


RAIZ_PROJETO = Path(__file__).resolve().parents[2]
FONTE_PADRAO = RAIZ_PROJETO / "src/main/resources/geo/municipios-idhm.json"
SAIDA_PADRAO = Path(__file__).with_name("municipios-ibge.csv")
TOTAL_MUNICIPIOS = 5_570


class ErroCatalogo(RuntimeError):
    """Indica que a fonte nao pode gerar um catalogo municipal confiavel."""


def sha256_arquivo(caminho: Path) -> str:
    digest = hashlib.sha256()
    with caminho.open("rb") as arquivo:
        for bloco in iter(lambda: arquivo.read(1024 * 1024), b""):
            digest.update(bloco)
    return digest.hexdigest()


def carregar_municipios(caminho: Path) -> list[dict[str, str]]:
    try:
        documento: Any = json.loads(caminho.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as erro:
        raise ErroCatalogo(f"Fonte municipal invalida: {caminho}") from erro
    if not isinstance(documento, dict) or not isinstance(documento.get("municipios"), list):
        raise ErroCatalogo("A fonte deve conter uma lista municipios")

    municipios: list[dict[str, str]] = []
    codigos: set[str] = set()
    chaves: set[tuple[str, str]] = set()
    for item in documento["municipios"]:
        if not isinstance(item, dict):
            raise ErroCatalogo("Registro municipal invalido")
        codigo = item.get("codigoIbge")
        nome = item.get("nome")
        uf = item.get("uf")
        if (
            not isinstance(codigo, str)
            or len(codigo) != 7
            or not codigo.isdecimal()
            or not isinstance(nome, str)
            or not nome.strip()
            or not isinstance(uf, str)
            or len(uf) != 2
            or not uf.isalpha()
        ):
            raise ErroCatalogo(f"Registro municipal incompleto: {item!r}")
        chave = (uf.upper(), nome.casefold())
        if codigo in codigos or chave in chaves:
            raise ErroCatalogo(f"Municipio duplicado: {codigo} {nome}/{uf}")
        codigos.add(codigo)
        chaves.add(chave)
        municipios.append({
            "codigo_ibge": codigo,
            "nome": nome.strip(),
            "uf": uf.upper(),
        })

    if len(municipios) != TOTAL_MUNICIPIOS:
        raise ErroCatalogo(
            f"Esperados {TOTAL_MUNICIPIOS} municipios; encontrados {len(municipios)}"
        )
    if len({municipio["uf"] for municipio in municipios}) != 27:
        raise ErroCatalogo("O catalogo deve conter as 27 UFs")
    return sorted(municipios, key=lambda item: item["codigo_ibge"])


def escrever_catalogo(municipios: list[dict[str, str]], destino: Path) -> None:
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
            escritor = csv.DictWriter(
                temporario,
                fieldnames=["codigo_ibge", "nome", "uf"],
                lineterminator="\n",
            )
            escritor.writeheader()
            escritor.writerows(municipios)
        caminho_temporario.replace(destino)
    except BaseException:
        if caminho_temporario is not None:
            caminho_temporario.unlink(missing_ok=True)
        raise


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=FONTE_PADRAO)
    parser.add_argument("--output", type=Path, default=SAIDA_PADRAO)
    argumentos = parser.parse_args()

    municipios = carregar_municipios(argumentos.source)
    escrever_catalogo(municipios, argumentos.output)
    print(
        f"Catalogo gerado em {argumentos.output}: {len(municipios)} municipios, "
        f"SHA-256 {sha256_arquivo(argumentos.output)}"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ErroCatalogo as erro:
        raise SystemExit(f"Erro: {erro}") from erro
