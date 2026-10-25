# Pesquisa de site e Instagram no Histórico

**Estado:** fluxo implementado; precisão e cobertura amplas ainda dependem de uma amostra rotulada maior.

A ação **Buscar informações** opera sobre os leads vinculados à busca selecionada no Histórico. O POST cria ou recupera uma execução persistida e responde sem esperar a pesquisa; o GET acompanha estado e contadores. O worker processa os leads em sequência e grava cada resultado em transação curta. A ação altera apenas o bloco automático de links em `observacoes`, preservando o texto comercial fora dele.

## Como a pesquisa funciona

1. Se houver `websiteUri` próprio conhecido, o serviço tenta ler esse site e os links públicos encontrados nele. Um link de Instagram continua sendo candidato até passar pela checagem de identidade.
2. Se ainda faltar confirmação e a opção Brave Search estiver ativa, o serviço consulta a API oficial do Brave. Brave é opcional; desligá-lo não impede a verificação do site já conhecido.
3. O classificador compara os candidatos com nome, telefone, localização, endereço e CNPJ do lead. Nome ou handle parecido sozinho não basta; conflitos explícitos impedem a confirmação.
4. Para validar um candidato, o leitor pode abrir o site próprio ou um perfil público do Instagram. O limite é de **três consultas ao Brave e três páginas candidatas por lead**, incluindo o site oficial quando aberto. O fluxo não navega em páginas de resultados de buscadores.
5. O resultado confirmado ou a ausência de confirmação é registrado no bloco automático de `observacoes`. Falhas técnicas de leitura não são tratadas como prova de ausência.

O leitor aceita apenas destinos HTTP/HTTPS públicos, sem redirecionamentos, com timeout e limite de bytes. O scraper antigo de motores de busca permanece desativado por padrão. A busca não altera score, status, último contato, CNPJ nem os snapshots históricos.

## Limite de validação

Casos como Petz Vila Velha, Supermercado Michel e Hortifruti Castelo ajudaram a corrigir filiais, nomes alternativos e endereços abreviados. Esses casos e as regressões automatizadas sustentam o funcionamento implementado; não medem a precisão geral. A próxima validação útil é uma amostra rotulada maior de acertos, erros e ausências, com revisão manual dos links aceitos.

**Referências:** [refinamentos do classificador](refinamento-pesquisa.md), [site oficial](refinamento-site-oficial.md), [API](../API.md), [fluxo interno](../fluxo.md).
