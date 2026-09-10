#!/usr/bin/env python3
"""Executa o ingestor e mede tempo e pico agregado de RSS no Linux."""

from __future__ import annotations

import argparse
import subprocess
import sys
import time
from pathlib import Path


GERADOR = Path(__file__).with_name("gerar_dataset.py")


def filhos_de(pid: int) -> list[int]:
    caminho = Path(f"/proc/{pid}/task/{pid}/children")
    try:
        return [int(valor) for valor in caminho.read_text().split()]
    except (FileNotFoundError, PermissionError, ProcessLookupError):
        return []


def arvore_de_processos(raiz: int) -> set[int]:
    encontrados: set[int] = set()
    pendentes = [raiz]
    while pendentes:
        pid = pendentes.pop()
        if pid in encontrados:
            continue
        encontrados.add(pid)
        pendentes.extend(filhos_de(pid))
    return encontrados


def rss_kib(pid: int) -> int:
    try:
        with Path(f"/proc/{pid}/status").open(encoding="ascii") as arquivo:
            for linha in arquivo:
                if linha.startswith("VmRSS:"):
                    return int(linha.split()[1])
    except (FileNotFoundError, PermissionError, ProcessLookupError, ValueError):
        return 0
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--intervalo",
        type=float,
        default=0.1,
        help="Intervalo entre amostras de RSS, em segundos",
    )
    parser.add_argument("argumentos", nargs=argparse.REMAINDER)
    opcoes = parser.parse_args()
    argumentos = opcoes.argumentos
    if argumentos[:1] == ["--"]:
        argumentos = argumentos[1:]
    if not argumentos:
        parser.error("informe os argumentos do gerar_dataset.py depois de --")
    if not 0.02 <= opcoes.intervalo <= 5:
        parser.error("--intervalo deve estar entre 0.02 e 5 segundos")

    comando = [sys.executable, str(GERADOR), *argumentos]
    inicio = time.perf_counter()
    processo = subprocess.Popen(comando)
    pico_rss_kib = 0
    amostras = 0
    try:
        while processo.poll() is None:
            rss_atual = sum(rss_kib(pid) for pid in arvore_de_processos(processo.pid))
            pico_rss_kib = max(pico_rss_kib, rss_atual)
            amostras += 1
            time.sleep(opcoes.intervalo)
    except KeyboardInterrupt:
        processo.terminate()
        processo.wait()
        raise

    duracao = time.perf_counter() - inicio
    print(f"Tempo decorrido: {duracao:.2f} s")
    print(f"Pico RSS agregado: {pico_rss_kib / 1024:.2f} MiB ({amostras} amostras)")
    return processo.returncode


if __name__ == "__main__":
    raise SystemExit(main())
