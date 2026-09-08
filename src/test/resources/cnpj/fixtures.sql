-- Fixtures exclusivas de teste; nao representam o subset mensal da Receita Federal.
-- CNPJs conferidos nas paginas oficiais das unidades Coco Bambu em 08/09/2026.

INSERT INTO cnpj_empresa (
    cnpj_base,
    razao_social,
    razao_social_normalizada,
    data_base
) VALUES
    ('23502037', 'CB CURITIBA COMERCIO DE ALIMENTOS LTDA', 'cb curitiba comercio de alimentos ltda', '2026-09-08'),
    ('23681920', 'CB VILA VELHA COMERCIO DE ALIMENTOS LTDA', 'cb vila velha comercio de alimentos ltda', '2026-09-08'),
    ('43869215', 'CB VITORIA COMERCIO DE ALIMENTOS LTDA', 'cb vitoria comercio de alimentos ltda', '2026-09-08')
ON DUPLICATE KEY UPDATE
    razao_social = VALUES(razao_social),
    razao_social_normalizada = VALUES(razao_social_normalizada),
    data_base = VALUES(data_base);

INSERT INTO cnpj_estabelecimento (
    cnpj,
    cnpj_base,
    nome_fantasia,
    nome_fantasia_normalizado,
    logradouro,
    logradouro_normalizado,
    numero,
    bairro,
    bairro_normalizado,
    cep,
    municipio_codigo_ibge,
    uf,
    situacao_cadastral,
    data_base
) VALUES
    (
        '23502037000113', '23502037', NULL, '',
        'RUA COMENDADOR ARAUJO', 'rua comendador araujo', '731',
        'BATEL', 'batel', '80420063', '4106902', 'PR', '02', '2026-09-08'
    ),
    (
        '23681920000118', '23681920', 'COCO BAMBU VILA VELHA',
        'coco bambu vila velha', 'AVENIDA DOUTOR OLIVIO LIRA',
        'avenida doutor olivio lira', '353', 'PRAIA DA COSTA',
        'praia da costa', '29101950', '3205200', 'ES', '02', '2026-09-08'
    ),
    (
        '43869215000156', '43869215', NULL, '',
        'RUA JOAO DA CRUZ', 'rua joao da cruz', '10',
        'PRAIA DO CANTO', 'praia do canto', '29055620',
        '3205309', 'ES', '02', '2026-09-08'
    )
ON DUPLICATE KEY UPDATE
    cnpj_base = VALUES(cnpj_base),
    nome_fantasia = VALUES(nome_fantasia),
    nome_fantasia_normalizado = VALUES(nome_fantasia_normalizado),
    logradouro = VALUES(logradouro),
    logradouro_normalizado = VALUES(logradouro_normalizado),
    numero = VALUES(numero),
    bairro = VALUES(bairro),
    bairro_normalizado = VALUES(bairro_normalizado),
    cep = VALUES(cep),
    municipio_codigo_ibge = VALUES(municipio_codigo_ibge),
    uf = VALUES(uf),
    situacao_cadastral = VALUES(situacao_cadastral),
    data_base = VALUES(data_base);
