---
name: leadradar-overview
description: Use esta skill para qualquer tarefa no projeto LeadRadar Cartão. Contém objetivo, escopo, stack, restrições e decisões globais do produto.
---

# LeadRadar Cartão — Overview do Projeto

## Nome

LeadRadar Cartão, nome provisório.

## Objetivo

Construir uma ferramenta local de prospecção que busca estabelecimentos comerciais físicos em uma região geográfica definida, organiza os resultados em um Kanban de funil comercial e permite abordagem manual via WhatsApp, com foco em oferecer um conciliador de cartão.

O critério de entrada não é ausência de site. O critério é categoria do negócio e aderência ao perfil de empresas que provavelmente movimentam pagamento por cartão.

## Perfil de negócios buscados

Priorizar estabelecimentos comerciais físicos, como:

- mercados;
- padarias;
- docerias;
- restaurantes;
- distribuidoras;
- açougues;
- farmácias;
- lojas locais com fluxo presencial;
- outros negócios que provavelmente recebem pagamentos por cartão.

## Escopo do MVP

Implementar apenas:

- busca de estabelecimentos por categoria, localização e raio;
- persistência dos leads encontrados;
- Kanban com colunas: Novo, Qualificado, Contatado, Ganho e Perdido;
- scoring simples: Quente, Morno e Frio;
- link manual para WhatsApp usando `wa.me/+55...`;
- exportação CSV/Excel dos leads;
- histórico de buscas anteriores;
- execução local na máquina do usuário.

## Fora de escopo no MVP

Não implementar no MVP:

- scraping de Instagram;
- integração com Instagram;
- disparo automático ou em massa de WhatsApp;
- Baileys;
- WhatsApp Business API;
- autenticação;
- multiusuário;
- deploy em VPS;
- fila/mensageria;
- Redis;
- Kafka;
- RabbitMQ.

Se o usuário pedir algo fora do escopo, explique que isso está no backlog futuro e proponha uma alternativa compatível com o MVP.

## Stack definida

Backend:

- Java 25 LTS;
- Spring Boot 4.x;
- Spring Web;
- Spring Data JPA;
- Hibernate/JPA;
- Bean Validation / Jakarta Validation;
- Bucket4j para rate limiting;
- Caffeine para cache;
- Virtual Threads para chamadas I/O-bound;
- Jackson para JSON.

Banco:

- MySQL local;
- Flyway para migrations.

Frontend:

- Angular 17+;
- Angular CDK;
- Leaflet ou ngx-leaflet;
- RxJS;
- Reactive Forms.

Fonte de dados:

- Google Places API como fonte primária;
- opcionalmente Nominatim/OSM Geocoding apenas para resolver endereço textual, se necessário.

Execução:

- local;
- Docker Compose opcional apenas para subir MySQL;
- sem necessidade de VPS no MVP.

## Regras globais para qualquer implementação

Ao gerar código para este projeto:

- respeite o escopo local e single-user;
- não adicione autenticação sem pedido explícito;
- não adicione deploy remoto sem pedido explícito;
- não use mensageria para fluxos simples;
- não use Redis no MVP;
- não coloque regras de negócio no Controller;
- não chame Google Places API direto no Controller;
- não use `ddl-auto: update` como estratégia de schema;
- use migrations versionadas com Flyway;
- preserve compatibilidade com MySQL;
- mantenha a separação por feature;
- priorize solução simples, legível e de portfólio.

## Regra de WhatsApp

WhatsApp deve ser apenas link manual de abertura de conversa.

Permitido:

```text
https://wa.me/55DDDNUMERO
```

Não permitido no MVP:

- disparo automático;
- disparo em massa;
- simulação de WhatsApp Web;
- robôs de mensagem;
- integração com APIs de envio.

Se o lead não tiver telefone, o botão de WhatsApp deve ficar desabilitado ou oculto.

## Comportamento esperado da IA

Quando o usuário pedir implementação, refatoração ou sugestão:

1. respeite as decisões acima;
2. não invente features fora do MVP;
3. prefira código simples e didático;
4. explique decisões quando houver tradeoff;
5. mantenha o projeto coerente com o roadmap;
6. use nomes em português nos conceitos de domínio quando fizer sentido;
7. use inglês apenas para nomes técnicos comuns ou APIs externas.
