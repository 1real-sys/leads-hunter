#!/usr/bin/env python3
"""Testes unitarios do ingestor CNPJ-00/CNPJ-06, somente com biblioteca padrao."""

from __future__ import annotations

import csv
import hashlib
import importlib.util
import io
import json
import sys
import tempfile
import unittest
import zipfile
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch


MODULO_PATH = Path(__file__).with_name("gerar_dataset.py")
SPEC = importlib.util.spec_from_file_location("gerar_dataset_cnpj", MODULO_PATH)
assert SPEC is not None and SPEC.loader is not None
cnpj = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cnpj)

CATALOGO_MODULO_PATH = Path(__file__).with_name("gerar_municipios_ibge.py")
CATALOGO_SPEC = importlib.util.spec_from_file_location(
    "gerar_municipios_ibge_cnpj",
    CATALOGO_MODULO_PATH,
)
assert CATALOGO_SPEC is not None and CATALOGO_SPEC.loader is not None
catalogo = importlib.util.module_from_spec(CATALOGO_SPEC)
CATALOGO_SPEC.loader.exec_module(catalogo)


class GerarDatasetCnpjTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporario = tempfile.TemporaryDirectory()
        self.raiz = Path(self.temporario.name)
        self.fontes = self.raiz / "fontes"
        self.fontes.mkdir()
        self.empresas = self.fontes / "Empresas0.zip"
        self.estabelecimentos = self.fontes / "Estabelecimentos0.zip"
        self.municipios = self.fontes / "Municipios.zip"

        self._gravar_zip_csv(self.municipios, [
            ["5705", "VITÓRIA"],
            ["5703", "VILA VELHA"],
            ["7535", "CURITIBA"],
            ["7107", "SÃO PAULO"],
        ])
        self._gravar_zip_csv(self.empresas, [
            ["43869215", "CB VITÓRIA COMÉRCIO DE ALIMENTOS LTDA"],
            ["23681920", "CB VILA VELHA COMÉRCIO DE ALIMENTOS LTDA"],
            ["23502037", "CB CURITIBA COMÉRCIO DE ALIMENTOS LTDA"],
            ["11222333", "EMPRESA FORA DO RECORTE LTDA"],
        ])
        self._gravar_zip_csv(self.estabelecimentos, [
            self._estabelecimento(
                "43869215", "0001", "56", "", "02", "RUA", "JOÃO DA CRUZ",
                "10", "PRAIA DO CANTO", "29055620", "ES", "5705"
            ),
            self._estabelecimento(
                "23681920", "0001", "18", "COCO BAMBU VILA VELHA", "02",
                "AVENIDA", "DOUTOR OLÍVIO LIRA", "353", "PRAIA DA COSTA",
                "29101950", "ES", "5703"
            ),
            self._estabelecimento(
                "23502037", "0001", "13", "", "02", "RUA", "COMENDADOR ARAÚJO",
                "731", "BATEL", "80420063", "PR", "7535"
            ),
            self._estabelecimento(
                "43869215", "0002", "37", "UNIDADE INATIVA", "08", "RUA",
                "JOÃO DA CRUZ", "99", "PRAIA DO CANTO", "29055620", "ES", "5705"
            ),
        ])
        self.manifesto = self.raiz / "fontes.json"
        self._gravar_manifesto()

    def tearDown(self) -> None:
        self.temporario.cleanup()

    def test_deve_normalizar_texto_e_digitos(self) -> None:
        self.assertEqual("avenida dr olivio lira", cnpj.normalizar_texto("  Avenida Dr. Olívio Lira  "))
        self.assertEqual("29101950", cnpj.somente_digitos("29.101-950"))
        self.assertEqual("'D''ÁVILA'", cnpj.literal_sql("D'ÁVILA"))
        self.assertEqual(
            "CONVERT(X'615c620a63' USING utf8mb4)",
            cnpj.literal_sql("a\\b\nc"),
        )

    def test_deve_filtrar_ativos_dos_municipios_e_gerar_saida_deterministica(self) -> None:
        manifesto = cnpj.carregar_manifesto(self.manifesto)
        with tempfile.TemporaryDirectory() as tmp:
            fontes = cnpj.resolver_fontes(manifesto, self.fontes, Path(tmp))
            dataset = cnpj.gerar_dataset(manifesto, fontes)
            dataset_repetido = cnpj.gerar_dataset(manifesto, fontes)
            primeira = self.raiz / "primeira.json"
            segunda = self.raiz / "segunda.json"
            primeira_sql = self.raiz / "primeira.sql"
            segunda_sql = self.raiz / "segunda.sql"
            cnpj.escrever_dataset(dataset, primeira)
            cnpj.escrever_dataset(dataset_repetido, segunda)
            cnpj.escrever_migration_sql(dataset, primeira_sql)
            cnpj.escrever_migration_sql(dataset_repetido, segunda_sql)

        self.assertEqual(primeira.read_bytes(), segunda.read_bytes())
        self.assertEqual(primeira_sql.read_bytes(), segunda_sql.read_bytes())
        conteudo_sql = primeira_sql.read_text()
        self.assertIn("Competencia da base RFB: 2026-08-08", conteudo_sql)
        self.assertIn("-- Fonte: empresas | Empresas0.zip | sha256=", conteudo_sql)
        self.assertIn("https://arquivos.receitafederal.gov.br/", conteudo_sql)
        self.assertIn("DELETE FROM cnpj_estabelecimento", conteudo_sql)
        self.assertIn("'43869215000156'", conteudo_sql)
        self.assertEqual(3, dataset["metadata"]["estabelecimentos"])
        self.assertEqual(3, dataset["metadata"]["empresas"])
        self.assertEqual(
            ["23502037000113", "23681920000118", "43869215000156"],
            [item["cnpj"] for item in dataset["estabelecimentos"]],
        )
        vitoria = dataset["estabelecimentos"][2]
        self.assertEqual("rua joao da cruz", vitoria["logradouroNormalizado"])
        self.assertEqual("praia do canto", vitoria["bairroNormalizado"])
        self.assertEqual("29055620", vitoria["cep"])
        self.assertEqual("3205309", vitoria["municipioCodigoIbge"])
        self.assertEqual("cb vitoria comercio de alimentos ltda", dataset["empresas"][2]["razaoSocialNormalizada"])

    def test_deve_falhar_quando_checksum_mudar(self) -> None:
        manifesto = json.loads(self.manifesto.read_text(encoding="utf-8"))
        manifesto["fontes"][0]["sha256"] = "0" * 64
        self.manifesto.write_text(json.dumps(manifesto), encoding="utf-8")

        carregado = cnpj.carregar_manifesto(self.manifesto)
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaisesRegex(cnpj.ErroIngestao, "Checksum inesperado"):
                cnpj.resolver_fontes(carregado, self.fontes, Path(tmp))

    def test_deve_falhar_quando_municipio_alvo_nao_tiver_estabelecimento(self) -> None:
        manifesto = json.loads(self.manifesto.read_text(encoding="utf-8"))
        manifesto["municipiosInteresse"].append({
            "codigoIbge": "3550308", "nome": "São Paulo", "uf": "SP"
        })
        self.manifesto.write_text(json.dumps(manifesto), encoding="utf-8")
        carregado = cnpj.carregar_manifesto(self.manifesto)
        with tempfile.TemporaryDirectory() as tmp:
            fontes = cnpj.resolver_fontes(carregado, self.fontes, Path(tmp))
            with self.assertRaisesRegex(cnpj.ErroIngestao, "3550308"):
                cnpj.gerar_dataset(carregado, fontes)

    def test_deve_expandir_es_para_os_78_municipios_do_catalogo_ibge(self) -> None:
        manifesto = json.loads(self.manifesto.read_text(encoding="utf-8"))
        manifesto.pop("municipiosInteresse")
        manifesto["ufs"] = ["es"]
        self.manifesto.write_text(json.dumps(manifesto), encoding="utf-8")

        carregado = cnpj.carregar_manifesto(self.manifesto)

        self.assertEqual(["ES"], carregado["ufs"])
        self.assertEqual(78, len(carregado["municipiosInteresse"]))
        self.assertIn(
            {"codigoIbge": "3205309", "nome": "Vitória", "uf": "ES"},
            carregado["municipiosInteresse"],
        )

    def test_deve_unir_uf_e_municipios_pontuais_sem_duplicar(self) -> None:
        manifesto = json.loads(self.manifesto.read_text(encoding="utf-8"))
        manifesto["ufs"] = ["ES"]
        self.manifesto.write_text(json.dumps(manifesto), encoding="utf-8")

        carregado = cnpj.carregar_manifesto(self.manifesto)

        self.assertEqual(79, len(carregado["municipiosInteresse"]))
        self.assertEqual(
            1,
            sum(
                municipio["codigoIbge"] == "3205309"
                for municipio in carregado["municipiosInteresse"]
            ),
        )
        self.assertIn(
            {"codigoIbge": "4106902", "nome": "Curitiba", "uf": "PR"},
            carregado["municipiosInteresse"],
        )

    def test_deve_rejeitar_uf_invalida_ou_inexistente(self) -> None:
        original = json.loads(self.manifesto.read_text(encoding="utf-8"))
        original.pop("municipiosInteresse")
        for uf, mensagem in (("E1", "UF invalida"), ("ZZ", "UF inexistente")):
            with self.subTest(uf=uf):
                manifesto = {**original, "ufs": [uf]}
                self.manifesto.write_text(json.dumps(manifesto), encoding="utf-8")
                with self.assertRaisesRegex(cnpj.ErroIngestao, mensagem):
                    cnpj.carregar_manifesto(self.manifesto)

    def test_deve_falhar_quando_nome_ibge_divergir_da_receita(self) -> None:
        catalogo_divergente = self.raiz / "municipios-divergentes.csv"
        with catalogo_divergente.open("w", encoding="utf-8", newline="") as arquivo:
            escritor = csv.DictWriter(
                arquivo,
                fieldnames=["codigo_ibge", "nome", "uf"],
                lineterminator="\n",
            )
            escritor.writeheader()
            escritor.writerow({
                "codigo_ibge": "3205309",
                "nome": "Vitória Divergente",
                "uf": "ES",
            })
        manifesto = json.loads(self.manifesto.read_text(encoding="utf-8"))
        manifesto.pop("municipiosInteresse")
        manifesto["ufs"] = ["ES"]
        self.manifesto.write_text(json.dumps(manifesto), encoding="utf-8")

        carregado = cnpj.carregar_manifesto(self.manifesto, catalogo_divergente)
        with tempfile.TemporaryDirectory() as tmp:
            fontes = cnpj.resolver_fontes(carregado, self.fontes, Path(tmp))
            with self.assertRaisesRegex(
                cnpj.ErroIngestao,
                "3205309 Vitória Divergente/ES",
            ):
                cnpj.gerar_dataset(carregado, fontes)

    def test_deve_gerar_sql_de_uf_sem_json_e_limpar_so_municipios_alvo(self) -> None:
        manifesto = json.loads(self.manifesto.read_text(encoding="utf-8"))
        manifesto.pop("municipiosInteresse")
        manifesto["ufs"] = ["ES"]
        self.manifesto.write_text(json.dumps(manifesto), encoding="utf-8")
        carregado = cnpj.carregar_manifesto(self.manifesto)
        destino_sql = self.raiz / "es.sql"
        dataset = {
            "metadata": {
                "dataBase": carregado["dataBase"],
                "empresas": 0,
                "estabelecimentos": 0,
                "fontes": [],
                "municipios": carregado["municipiosInteresse"],
                "ufs": carregado["ufs"],
            },
            "empresas": [],
            "estabelecimentos": [],
        }

        cnpj.escrever_migration_sql(dataset, destino_sql)

        sql = destino_sql.read_text(encoding="utf-8")
        comando_delete = next(
            linha for linha in sql.splitlines() if linha.startswith("DELETE FROM")
        )
        self.assertIn("-- UFs expandidas: ES", sql)
        self.assertIn("'3200102'", comando_delete)
        self.assertIn("'3205309'", comando_delete)
        self.assertEqual(78, comando_delete.count("'32"))
        self.assertNotIn("'4106902'", comando_delete)

    def test_deve_aceitar_no_json_sem_criar_artefato_intermediario(self) -> None:
        destino_json = self.raiz / "nao-deve-existir.json"
        destino_sql = self.raiz / "subset.sql"
        argumentos = [
            str(MODULO_PATH),
            "--manifest", str(self.manifesto),
            "--source-dir", str(self.fontes),
            "--output", str(destino_json),
            "--output-sql", str(destino_sql),
            "--workers", "1",
            "--no-json",
        ]

        with patch.object(sys, "argv", argumentos), redirect_stdout(io.StringIO()):
            self.assertEqual(0, cnpj.main())

        self.assertFalse(destino_json.exists())
        self.assertTrue(destino_sql.is_file())

    def test_deve_limitar_workers_e_manter_modo_sequencial_controlavel(self) -> None:
        self.assertEqual(1, cnpj.resolver_trabalhadores(1, 10))
        self.assertEqual(10, cnpj.resolver_trabalhadores(16, 10))
        automaticos = cnpj.resolver_trabalhadores(0, 10)
        self.assertGreaterEqual(automaticos, 1)
        self.assertLessEqual(automaticos, 8)
        with self.assertRaisesRegex(cnpj.ErroIngestao, "workers"):
            cnpj.resolver_trabalhadores(17, 10)

    def test_catalogo_versionado_deve_ser_reproduzivel_e_conter_27_ufs(self) -> None:
        municipios = catalogo.carregar_municipios(catalogo.FONTE_PADRAO)
        primeira = self.raiz / "municipios-1.csv"
        segunda = self.raiz / "municipios-2.csv"

        catalogo.escrever_catalogo(municipios, primeira)
        catalogo.escrever_catalogo(municipios, segunda)

        self.assertEqual(primeira.read_bytes(), segunda.read_bytes())
        self.assertEqual(
            Path(__file__).with_name("municipios-ibge.csv").read_bytes(),
            primeira.read_bytes(),
        )
        self.assertEqual(5_570, len(municipios))
        self.assertEqual(27, len({municipio["uf"] for municipio in municipios}))
        self.assertEqual(78, sum(municipio["uf"] == "ES" for municipio in municipios))

    def test_deve_rejeitar_metadado_inseguro_para_comentario_sql(self) -> None:
        manifesto = json.loads(self.manifesto.read_text(encoding="utf-8"))
        manifesto["fontes"][0]["url"] += "\nSELECT 1;"
        self.manifesto.write_text(json.dumps(manifesto), encoding="utf-8")

        with self.assertRaisesRegex(cnpj.ErroIngestao, "URL HTTPS obrigatoria"):
            cnpj.carregar_manifesto(self.manifesto)

    def _gravar_manifesto(self) -> None:
        base_url = "https://arquivos.receitafederal.gov.br/dados/cnpj/2026-08/"
        fontes = []
        for tipo, caminho in (
            ("empresas", self.empresas),
            ("estabelecimentos", self.estabelecimentos),
            ("municipios", self.municipios),
        ):
            fontes.append({
                "tipo": tipo,
                "arquivo": caminho.name,
                "url": base_url + caminho.name,
                "sha256": hashlib.sha256(caminho.read_bytes()).hexdigest(),
            })
        manifesto = {
            "dataBase": "2026-08-08",
            "fontes": fontes,
            "municipiosInteresse": [
                {"codigoIbge": "3205309", "nome": "Vitória", "uf": "ES"},
                {"codigoIbge": "3205200", "nome": "Vila Velha", "uf": "ES"},
                {"codigoIbge": "4106902", "nome": "Curitiba", "uf": "PR"},
            ],
        }
        self.manifesto.write_text(json.dumps(manifesto), encoding="utf-8")

    def _gravar_zip_csv(self, destino: Path, linhas: list[list[str]]) -> None:
        buffer = io.StringIO(newline="")
        escritor = csv.writer(buffer, delimiter=";", quotechar='"', lineterminator="\n")
        escritor.writerows(linhas)
        with zipfile.ZipFile(destino, "w", compression=zipfile.ZIP_DEFLATED) as arquivo:
            arquivo.writestr(destino.stem.upper(), buffer.getvalue().encode("latin-1"))

    def _estabelecimento(
        self,
        base: str,
        ordem: str,
        dv: str,
        fantasia: str,
        situacao: str,
        tipo_logradouro: str,
        logradouro: str,
        numero: str,
        bairro: str,
        cep: str,
        uf: str,
        municipio: str,
    ) -> list[str]:
        registro = [""] * 30
        registro[0] = base
        registro[1] = ordem
        registro[2] = dv
        registro[4] = fantasia
        registro[5] = situacao
        registro[13] = tipo_logradouro
        registro[14] = logradouro
        registro[15] = numero
        registro[17] = bairro
        registro[18] = cep
        registro[19] = uf
        registro[20] = municipio
        return registro


if __name__ == "__main__":
    unittest.main(verbosity=2)
