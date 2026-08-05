---
name: leadradar-database
description: Use esta skill ao criar ou alterar modelagem MySQL, entidades JPA, migrations Flyway, índices e relacionamentos do LeadRadar Cartão.
---

# Banco de Dados — LeadRadar Cartão

## Motor

Use MySQL local.

O banco pode rodar via Docker Compose ou instalação direta.

## Versionamento de schema

Use Flyway desde o início.

Não use `spring.jpa.hibernate.ddl-auto=update` como solução contínua.

Valor aceitável em desenvolvimento:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

As mudanças de schema devem ser feitas por migrations versionadas.

## Modelo relacional recomendado

Use três tabelas principais:

```text
busca
lead
busca_lead
```

Motivo: um mesmo estabelecimento pode aparecer em várias buscas. Portanto, `lead.google_place_id` deve ser único, e o histórico de aparições deve ficar em tabela associativa.

Não use apenas `lead.busca_id` se quiser rastrear múltiplas buscas do mesmo lead.

## Tabela `busca`

Representa uma execução de busca feita pelo usuário.

Campos:

| Campo | Tipo | Observação |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | identificador |
| endereco_base | VARCHAR(255) | endereço textual informado ou selecionado |
| latitude | DECIMAL(10, 7) | ponto central da busca |
| longitude | DECIMAL(10, 7) | ponto central da busca |
| raio_km | INT | raio usado na busca |
| categorias_buscadas | VARCHAR(500) | categorias serializadas, ex: `PADARIA,MERCADO` |
| total_encontrados | INT | total bruto retornado/processado |
| criada_em | DATETIME | criação |

Índices:

```sql
CREATE INDEX idx_busca_criada_em ON busca (criada_em);
```

## Tabela `lead`

Representa um estabelecimento comercial único.

Campos:

| Campo | Tipo | Observação |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | identificador |
| google_place_id | VARCHAR(255) | UNIQUE, chave de deduplicação |
| nome | VARCHAR(255) | nome do estabelecimento |
| categoria | VARCHAR(50) | enum de domínio |
| endereco_formatado | VARCHAR(255) | endereço retornado/mapeado |
| telefone | VARCHAR(30) | nullable, telefone original |
| telefone_normalizado | VARCHAR(20) | nullable, somente dígitos com DDI/DDD quando possível |
| latitude | DECIMAL(10, 7) | localização |
| longitude | DECIMAL(10, 7) | localização |
| rating_google | DECIMAL(3, 2) | nullable |
| total_reviews | INT | nullable |
| score | INT | 0 a 100 |
| temperatura | VARCHAR(10) | QUENTE, MORNO, FRIO |
| status | VARCHAR(20) | NOVO, QUALIFICADO, CONTATADO, GANHO, PERDIDO |
| observacoes | TEXT | nullable, anotações comerciais |
| ultimo_contato_em | DATETIME | nullable |
| criado_em | DATETIME | criação |
| atualizado_em | DATETIME | atualização |

Índices:

```sql
CREATE UNIQUE INDEX uk_lead_google_place_id ON lead (google_place_id);
CREATE INDEX idx_lead_status ON lead (status);
CREATE INDEX idx_lead_categoria ON lead (categoria);
CREATE INDEX idx_lead_temperatura ON lead (temperatura);
CREATE INDEX idx_lead_score ON lead (score);
```

## Tabela `busca_lead`

Representa a relação N:N entre buscas e leads.

Campos:

| Campo | Tipo | Observação |
|---|---|---|
| busca_id | BIGINT FK | referencia `busca.id` |
| lead_id | BIGINT FK | referencia `lead.id` |
| score_na_busca | INT | score calculado naquela busca |
| temperatura_na_busca | VARCHAR(10) | temperatura naquela busca |
| encontrado_em | DATETIME | data/hora em que apareceu nessa busca |

Chave primária sugerida:

```sql
PRIMARY KEY (busca_id, lead_id)
```

Índices:

```sql
CREATE INDEX idx_busca_lead_lead_id ON busca_lead (lead_id);
CREATE INDEX idx_busca_lead_encontrado_em ON busca_lead (encontrado_em);
```

## Relacionamentos

```text
busca 1 — N busca_lead
lead  1 — N busca_lead
```

Na prática:

- uma busca encontra vários leads;
- um lead pode aparecer em várias buscas;
- `google_place_id` impede duplicação do estabelecimento;
- `busca_lead` preserva histórico.

## Regras de deduplicação

Ao ingerir resultado da Google Places API:

1. verificar se já existe lead com o mesmo `google_place_id`;
2. se não existir, criar lead novo com status `NOVO`;
3. se existir, atualizar apenas dados externos úteis, como telefone, rating, total de reviews e endereço;
4. não sobrescrever `status`, `observacoes` e `ultimo_contato_em` de forma automática;
5. criar ou manter a relação em `busca_lead`.

## Regras para dados comerciais

Campos comerciais devem ser preservados:

- `status`;
- `observacoes`;
- `ultimo_contato_em`.

Esses campos representam interação humana e não devem ser apagados por nova busca.

## Enums

`StatusFunil`:

```text
NOVO
QUALIFICADO
CONTATADO
GANHO
PERDIDO
```

`Temperatura`:

```text
QUENTE
MORNO
FRIO
```

`CategoriaNegocio` inicial:

```text
MERCADO
PADARIA
DOCERIA
RESTAURANTE
DISTRIBUIDORA
ACOUGUE
FARMACIA
OUTROS
```

## Observação sobre volume

Uso previsto:

- local;
- single-user;
- buscas esporádicas;
- cerca de 100 a 150 leads por busca.

Não implementar sharding, particionamento ou otimizações de escala no MVP.

MySQL com índices básicos é suficiente.
