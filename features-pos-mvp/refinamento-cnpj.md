# CNPJ por estabelecimento

**Estado:** implementado; depende de uma base local importada pelo usuário.

O projeto não consulta a Receita Federal durante a busca. O pipeline em [tools/cnpj](../tools/cnpj/README.md) prepara uma competência da base pública e gera a carga usada pelo MySQL. Sem essa importação, Busca e Kanban continuam operando, mas a correspondência local de CNPJ fica indisponível.

Na captura, `CnpjService` tenta associar a unidade ativa do mesmo município ao lead por endereço e nome. Candidatos ambíguos ou com evidência insuficiente são recusados. CNPJ, razão social, competência, confiança e origem ficam no lead; CNPJ e razão social aparecem na API, no detalhe e nas exportações. A ação **Buscar CNPJ** no Histórico tenta novamente apenas os leads sem CNPJ vinculados à busca selecionada.

Quando uma nova competência é carregada, um CNPJ existente só é revalidado se a competência ativa do município mudou. Se o mesmo CNPJ for confirmado, seus metadados são atualizados; se não for confirmado, o enriquecimento antigo é limpo sem trocar silenciosamente a identidade do estabelecimento. Dados comerciais, score e snapshots de `BuscaLead` são preservados.

A regra adicional de endereço exato está implementada, mas desativada por padrão e pendente de revisão manual antes de ser promovida. Veja [correspondência por endereço exato](refinamento-cnpj-match.md).

**Referências:** [guia da carga](../tools/cnpj/README.md), [fluxo interno](../fluxo.md), [API](../API.md).
