#!/usr/bin/env python3
"""Validação estrutural e geográfica do artefato produzido na IDHM-00."""

from __future__ import annotations

import hashlib
import json
import unittest
from pathlib import Path
from typing import Any


RAIZ_PROJETO = Path(__file__).resolve().parents[2]
DATASET = RAIZ_PROJETO / "src/main/resources/geo/municipios-idhm.json"
ARTEFATO_SHA256 = "8c9ce54dff5eec54e7401ba2392e4305145edc4acb02c21388425393c6b56286"


def ponto_no_segmento(
    longitude: float,
    latitude: float,
    inicio: list[float],
    fim: list[float],
) -> bool:
    produto = (longitude - inicio[0]) * (fim[1] - inicio[1]) - (
        latitude - inicio[1]
    ) * (fim[0] - inicio[0])
    if abs(produto) > 1e-10:
        return False
    return (
        min(inicio[0], fim[0]) - 1e-10 <= longitude <= max(inicio[0], fim[0]) + 1e-10
        and min(inicio[1], fim[1]) - 1e-10
        <= latitude
        <= max(inicio[1], fim[1]) + 1e-10
    )


def ponto_no_anel(longitude: float, latitude: float, anel: list[list[float]]) -> bool:
    dentro = False
    anterior = anel[-1]
    for atual in anel:
        if ponto_no_segmento(longitude, latitude, anterior, atual):
            return True
        cruza = (atual[1] > latitude) != (anterior[1] > latitude)
        if cruza:
            longitude_intersecao = (anterior[0] - atual[0]) * (
                latitude - atual[1]
            ) / (anterior[1] - atual[1]) + atual[0]
            if longitude < longitude_intersecao:
                dentro = not dentro
        anterior = atual
    return dentro


def ponto_no_poligono(
    longitude: float,
    latitude: float,
    aneis: list[list[list[float]]],
) -> bool:
    return bool(aneis) and ponto_no_anel(longitude, latitude, aneis[0]) and not any(
        ponto_no_anel(longitude, latitude, buraco) for buraco in aneis[1:]
    )


def ponto_na_geometria(longitude: float, latitude: float, geometria: dict[str, Any]) -> bool:
    if geometria["type"] == "Polygon":
        return ponto_no_poligono(longitude, latitude, geometria["coordinates"])
    if geometria["type"] == "MultiPolygon":
        return any(
            ponto_no_poligono(longitude, latitude, poligono)
            for poligono in geometria["coordinates"]
        )
    return False


class DatasetIdhmTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        conteudo = DATASET.read_bytes()
        cls.sha256 = hashlib.sha256(conteudo).hexdigest()
        cls.dataset = json.loads(conteudo)
        cls.municipios = cls.dataset["municipios"]
        cls.por_codigo = {municipio["codigoIbge"]: municipio for municipio in cls.municipios}

    def test_deve_conter_malha_completa_e_codigos_unicos(self) -> None:
        self.assertEqual(ARTEFATO_SHA256, self.sha256)
        self.assertEqual(5_570, len(self.municipios))
        self.assertEqual(len(self.municipios), len(self.por_codigo))
        self.assertEqual(5_570, self.dataset["metadata"]["municipios"])
        self.assertEqual(2010, self.dataset["metadata"]["idhmReferencia"])
        self.assertEqual(["5101837"], self.dataset["metadata"]["municipiosSemGeometria"])

    def test_todos_os_municipios_devem_ter_campos_e_geometria_validos(self) -> None:
        for municipio in self.municipios:
            with self.subTest(codigo=municipio["codigoIbge"]):
                self.assertRegex(municipio["codigoIbge"], r"^\d{7}$")
                self.assertTrue(municipio["nome"])
                self.assertRegex(municipio["uf"], r"^[A-Z]{2}$")
                self.assertEqual(2010, municipio["idhmReferencia"])
                if municipio["idhm"] is not None:
                    self.assertGreaterEqual(municipio["idhm"], 0)
                    self.assertLessEqual(municipio["idhm"], 1)
                self.assertIn(municipio["geometry"]["type"], {"Polygon", "MultiPolygon"})
                self.assertEqual(4, len(municipio["bbox"]))
                self.assertLessEqual(municipio["bbox"][0], municipio["bbox"][2])
                self.assertLessEqual(municipio["bbox"][1], municipio["bbox"][3])

    def test_vitoria_deve_conter_coordenada_e_idhm_esperados(self) -> None:
        vitoria = self.por_codigo["3205309"]
        self.assertEqual("Vitória", vitoria["nome"])
        self.assertEqual("ES", vitoria["uf"])
        self.assertEqual(0.845, vitoria["idhm"])
        self.assertTrue(ponto_na_geometria(-40.3128, -20.3155, vitoria["geometry"]))

    def test_curitiba_deve_conter_coordenada_e_idhm_esperados(self) -> None:
        curitiba = self.por_codigo["4106902"]
        self.assertEqual("Curitiba", curitiba["nome"])
        self.assertEqual("PR", curitiba["uf"])
        self.assertEqual(0.823, curitiba["idhm"])
        self.assertTrue(ponto_na_geometria(-49.2733, -25.4284, curitiba["geometry"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
