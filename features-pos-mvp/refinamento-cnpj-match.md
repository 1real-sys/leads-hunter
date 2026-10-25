# CNPJ por endereço exato

**Estado:** código e diagnóstico implementados; a regra nova fica desativada por padrão. A revisão manual de todos os candidatos aprováveis e a promoção ainda não ocorreram. Nenhum backfill em massa foi executado.

## Por que existe

O caminho legado de `CnpjService` exige semelhança de nome e endereço. Ele pode recusar a unidade correta quando o nome comercial difere da razão social e a base não traz nome fantasia. O caso investigado foi o lead 653, Farmácia São Miguel, cujo município, CEP, rua e número coincidem com um estabelecimento ativo, apesar de o nome divergir.

## Regra implementada

- O endereço exato exige mesmo município, estabelecimento ativo, CEP válido de oito dígitos, número canônico presente e igual e logradouro igual após normalização.
- Uma consulta truncada é recusada. Se restar um único estabelecimento nesse endereço, o caminho exato pode aprová-lo mesmo com nome divergente. Com vários, somente um candidato com semelhança nominal de pelo menos 0,55 pode desempatar; se nenhum ou mais de um atingir o limiar, a correspondência é recusada.
- Fora do caminho exato, a regra legada de nome e endereço continua. A origem registrada é `ENDERECO_EXATO` ou `NOME_ENDERECO`; CNPJs antigos podem ter origem nula.
- A normalização de números compara, por exemplo, `048` com `48`, mas rejeita números ausentes ou desconhecidos. Os limites de candidatos impedem que uma consulta parcial seja tomada como prova de unicidade.
- Uma nova competência que não confirme o mesmo CNPJ limpa o enriquecimento anterior sem substituí-lo silenciosamente por outro. Status, observações, último contato, score e snapshots históricos não participam da correspondência.

## Controle de ativação

`CnpjMatchPolicy` exige `cnpj.match.endereco-exato.habilitado=true` e uma allowlist válida de municípios ou UFs. Variáveis equivalentes: `CNPJ_MATCH_ENDERECO_EXATO_HABILITADO`, `CNPJ_MATCH_MUNICIPIOS_PERMITIDOS` e `CNPJ_MATCH_UFS_PERMITIDAS`. A lista de municípios, quando explicitamente configurada, prevalece sobre a de UFs; vazia bloqueia o caminho novo. Sem habilitação ou allowlist, só o caminho legado opera.

## Pendência para promoção

1. Gerar o diagnóstico somente leitura na base local configurada e revisar a comparação entre regra legada e regra nova. A última execução registrada no histórico ocorreu em 22/09/2026; os números da base daquela data não são um retrato permanente.
2. Revisar manualmente **100% dos candidatos que poderiam ser aprovados** na allowlist. Registrar `CERTO`, `ERRADO` ou `INCONCLUSIVO`, com evidência do vínculo físico entre estabelecimento e lead. Nome parecido não basta. Qualquer `ERRADO` veta a rodada; `INCONCLUSIVO` não autoriza sua linha.
3. Só depois configurar a política aprovada e considerar a aplicação. O runner é opt-in (`leadhunter.cnpj-match-runner=true`), inicia em modo relatório e exige revisão vinculada ao relatório para escrever. Na aplicação, revalida estado, competência, CNPJ e avaliação atual antes de cada gravação. O comando com `--aplicar` altera a base e não faz parte de uma validação somente leitura.
4. Medir antes e depois, incluindo ganho de cobertura, erros e preservação dos dados comerciais. Não declarar a regra promovida apenas porque testes automatizados passaram.

**Referências:** [pipeline da base CNPJ](../tools/cnpj/README.md), [fluxo interno](../fluxo.md), [histórico de implementações](../HISTORICO_IMPLEMENTACOES.md).
