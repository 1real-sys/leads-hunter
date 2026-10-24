# AGENTS.md — Leads Hunter

Instruções para agentes que alteram este repositório. O pedido atual do usuário prevalece sobre este arquivo; em seguida, prevalecem o código e os contratos existentes, a skill especializada, `fluxo.md` e `HISTORICO_IMPLEMENTACOES.md`.

## Escopo e regras do projeto

- Pacote Java: `dev.jlm.leadshunter`; nunca usar `com.leadradar`.
- Mantenha o escopo pedido. Preserve o comportamento existente e evite dependências, refatorações amplas ou mudanças arquiteturais sem necessidade concreta. Não implemente frontend se a tarefa não pedir.
- O MVP é local e individual: não acrescente autenticação, deploy, Redis, RabbitMQ ou Kafka por antecipação.
- Modele `Busca N:N Lead` por `BuscaLead`, nunca por `lead.busca_id`. Deduplique `Lead` por `googlePlaceId`. Em novas buscas, preserve `status`, `observacoes` e `ultimoContatoEm` do lead existente.
- Use Flyway para schema e `ddl-auto=validate`; nunca `ddl-auto=update`. Leia chaves e credenciais de variáveis de ambiente, sem valores hardcoded.
- WhatsApp é apenas link manual `https://wa.me/55...`; não implemente envio automático ou em massa.
- A pesquisa inteligente pode abrir a URL candidata, site próprio ou perfil público do Instagram, somente para confirmar telefone, endereço ou CNPJ do lead. Limite a três consultas ao Brave e três páginas por lead. Não faça scraping de motores de busca, não siga redirecionamentos nem acesse destinos privados.
- A ação explícita **Buscar e-mails** pode ler até duas páginas do mesmo host do site oficial (`websiteUri`), após confirmar a identidade do estabelecimento, e gravar somente e-mails do domínio próprio. Ela não amplia a permissão de navegação da pesquisa inteligente.

## Onde buscar contexto

- Antes de uma implementação relevante, leia `fluxo.md` para estado, decisões e pendências. Consulte `HISTORICO_IMPLEMENTACOES.md` somente quando precisar entender a evolução. Código e Git prevalecem sobre documentação desatualizada. Não é necessário ler `v1.md`.
- Leia apenas as skills pertinentes à tarefa, em `.opencode/skills/`:

| Assunto | Skill |
| --- | --- |
| Entidades, migrations, índices e persistência | `leadradar-database` |
| Serviços, controllers, DTOs, regras de negócio, Places, cache, rate limit e exportação | `leadradar-backend` |
| Organização, responsabilidades e decisões estruturais | `leadradar-architecture` |
| Angular, UI e integração frontend-backend | `leadradar-frontend` e `angular-developer` |
| Criação ou polimento visual de UI | `leadradar-antislopUI` como complemento |
| Priorização e planejamento | `leadradar-roadmap` |
| Tarefas amplas que cruzam domínios | `leadradar-overview`, se necessário |

- **Segurança:** leia `.opencode/skills/leadradar-security/SKILL.md` antes de alterar entradas ou rotas HTTP, autorização, dados sensíveis, banco ou queries, integrações externas, segredos, exportação, cache/rate limit, logs/erros, dependências de segurança ou infraestrutura. Leia também quando criar qualquer nova superfície de entrada, acesso a dados ou operação abusável. Aplique somente os requisitos relacionados ao escopo atual e ao estágio de `fluxo.md`; não antecipe todas as proteções futuras. Se houver conflito com uma regra de segurança, explique-o e preserve a alternativa segura, salvo instrução explícita e justificada em nível superior.

## Como trabalhar

1. Entenda o pedido e inspecione os arquivos e o fluxo envolvidos antes de editar. Identifique comportamentos a preservar e validações necessárias.
2. Implemente somente o escopo pedido, seguindo os padrões existentes. Informe resumidamente o usuário sobre análise, mudança e validação.
3. Execute testes e build adequados. Para backend, use `./mvnw test` e, quando apropriado, `./mvnw verify` ou `./mvnw package`; para frontend, use os scripts reais do projeto. Investigue e corrija falhas causadas pela mudança. Distinga falhas preexistentes.
4. Atualize `fluxo.md` após mudanças relevantes, registrando estado real, pendências e próximo passo. Após implementação historicamente relevante, acrescente a próxima entrada numerada em `HISTORICO_IMPLEMENTACOES.md`, resumindo finalidade e arquivos criados, modificados e removidos. Mudanças triviais não exigem entrada histórica.
5. Entregue o relatório abaixo quando alterar o projeto. Para análise sem alterações, basta informar o resultado.

Não declare concluído o que não foi implementado e validado. Nunca afirme que leu arquivos, executou comandos ou obteve resultados sem evidência. Código, testes, documentação e relatório devem descrever o mesmo estado. Informe problemas fora do escopo somente quando relevantes.

## Relatório final para alterações

Use estas seções:

### Implementado

Explique o comportamento alterado e onde ocorreu, não apenas os nomes dos arquivos.

### Validação

Liste os comandos realmente executados e seus resultados. Declare testes não executados ou falhas sem ocultá-los.

### Documentação atualizada

Informe o que mudou em `fluxo.md` e `HISTORICO_IMPLEMENTACOES.md`, quando aplicável.

### Arquivos criados

Liste os arquivos criados ou escreva `Nenhum.`.

### Arquivos modificados

Liste os arquivos modificados ou escreva `Nenhum.`.

### Arquivos removidos

Inclua esta seção somente se houver remoções.

### Observações

Inclua esta seção somente para limitações, decisões importantes ou falhas relevantes.
