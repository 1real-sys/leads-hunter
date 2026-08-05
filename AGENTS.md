# Instruções do projeto — Leads Hunter

Pacote raiz do projeto:

`dev.jlm.leadshunter`

Não criar `com.leadradar`.

Classe principal:

`src/main/java/dev/jlm/leadshunter/LeadsHunterApplication.java`

## Regras obrigatórias

- Projeto backend local em Java 25 + Spring Boot 4.x.
- Não implementar frontend se a tarefa não pedir.
- Não implementar autenticação, deploy, scraping de Instagram, Redis, RabbitMQ ou Kafka no MVP.
- WhatsApp deve ser apenas link manual `https://wa.me/55...`.
- Nunca implementar disparo automático ou em massa.
- Não usar `lead.busca_id`.
- O relacionamento correto é `Busca N:N Lead` via `BuscaLead`.
- Deduplicar `Lead` por `googlePlaceId`.
- Preservar `status`, `observacoes` e `ultimoContatoEm` quando um lead existente aparecer em nova busca.
- Não hardcodar chave da Google Places API.
- Usar Flyway para schema.
- Não usar `ddl-auto=update`.

## Skills adicionais

As skills completas ficam em:

- `.opencode/skills/leadradar-overview/SKILL.md`
- `.opencode/skills/leadradar-backend/SKILL.md`
- `.opencode/skills/leadradar-database/SKILL.md`
- `.opencode/skills/leadradar-architecture/SKILL.md`
- `.opencode/skills/leadradar-frontend/SKILL.md`
- `.opencode/skills/leadradar-roadmap/SKILL.md`

Leia apenas a skill relevante para a tarefa atual.

Exemplos:

- Tarefa de entidade, migration ou relacionamento: leia `leadradar-database`.
- Tarefa de service, controller, DTO ou regra de negócio: leia `leadradar-backend`.
- Tarefa de organização, fluxo ou decisão estrutural: leia `leadradar-architecture`.
- Tarefa de priorização: leia `leadradar-roadmap`.
- Tarefa de frontend: leia `leadradar-frontend`.

Não leia todas as skills se a tarefa for pequena.