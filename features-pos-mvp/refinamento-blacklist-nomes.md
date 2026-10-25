# Bloqueio de nomes na busca

**Estado:** implementado.

É possível cadastrar, listar e remover termos na tela Bloqueios e pela API. O serviço normaliza acentos e caixa, exige de 3 a 120 caracteres e impede duplicatas normalizadas. As rotas são GET, POST e DELETE em `/api/bloqueios`.

Em cada nova busca, os termos são carregados e comparados por substring normalizada com os nomes devolvidos pelo Google Places. O filtro atua depois da resposta externa e antes da persistência; um resultado bloqueado não cria nem atualiza `Lead` ou `BuscaLead`. A resposta da busca informa `totalBloqueados`.

**Limites:** o bloqueio não economiza consultas à Google, não apaga leads já salvos e não altera buscas antigas. Termos genéricos podem eliminar resultados legítimos; a escolha dos termos cabe a quem usa a aplicação.

**Referências:** [API](../API.md), [fluxo interno](../fluxo.md) e [histórico de implementações](../HISTORICO_IMPLEMENTACOES.md).
