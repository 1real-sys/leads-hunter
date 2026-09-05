# Refinamento de leads — validação do WhatsApp

## Objetivo

Alterar o fluxo atual para que um link de WhatsApp só seja exibido quando o número capturado for confirmado como pertencente ao WhatsApp.

O telefone capturado deve continuar visível normalmente mesmo quando:

- o número não estiver no WhatsApp;
- a validação ainda estiver pendente;
- o serviço externo estiver indisponível;
- a validação não puder ser concluída.

Regra principal: **sem confirmação positiva, não gerar nem exibir `whatsappUrl`.**

## Situação atual

Hoje o fluxo é:

```text
Google Places
  -> telefone capturado
  -> telefone normalizado
  -> WhatsAppLinkGenerator
  -> https://wa.me/...
```

O sistema considera que um telefone brasileiro normalizado é suficiente para gerar o link. Isso apenas monta uma URL; não confirma se o número possui uma conta WhatsApp.

## Viabilidade

É tecnicamente possível, mas não por meio do link `wa.me` e não existe uma validação pública, gratuita e confiável que deva ser usada como atalho.

A opção segura é integrar um mecanismo oficial da WhatsApp Business Platform, caso o endpoint de consulta de contatos esteja disponível para a conta e para a versão vigente da API. Outra opção é utilizar um BSP autorizado, como provedor intermediário. Essa decisão precisa ser confirmada na documentação e na conta escolhida antes da implementação, porque exige credenciais próprias do WhatsApp, normalmente token de acesso, identificação do número comercial e configuração de negócio.

Não usar:

- WhatsApp Web automatizado;
- Baileys ou bibliotecas não oficiais;
- tentativa de abrir `wa.me` para inferir existência;
- disparo de mensagem para testar o número;
- consulta em massa sem controle de custo, consentimento e limites.

A verificação deve ser uma consulta de presença/validade permitida pelo provedor, sem envio automático de mensagem.

## Fluxo desejado

```text
Lead capturado pela Google Places
  |
  +--> telefone ausente ou inválido
  |      -> telefone permanece ausente/inválido
  |      -> whatsappUrl = null
  |
  +--> telefone válido
         -> persistir telefone e status PENDENTE
         -> validar no provedor oficial
              |
              +--> confirmado
              |      -> status DISPONIVEL
              |      -> gerar whatsappUrl
              |
              +--> não encontrado
              |      -> status INDISPONIVEL
              |      -> whatsappUrl = null
              |      -> exibir somente telefone
              |
              +--> erro, timeout ou limite
                     -> status ERRO ou PENDENTE
                     -> whatsappUrl = null
                     -> exibir telefone e aviso neutro
```

## Decisão de produto

O telefone e a situação do WhatsApp devem ser tratados como informações diferentes:

- `telefone`: dado capturado e exibido ao usuário;
- `telefoneNormalizado`: valor usado para validação;
- `whatsappStatus`: resultado da validação;
- `whatsappValidadoEm`: momento da última resposta conclusiva;
- `whatsappUrl`: derivado somente quando `whatsappStatus == DISPONIVEL`.

Sugestão de estados persistidos:

- `PENDENTE` — telefone válido ainda não consultado ou aguardando nova tentativa;
- `DISPONIVEL` — provedor confirmou que o número está no WhatsApp;
- `INDISPONIVEL` — provedor confirmou que o número não está no WhatsApp;
- `ERRO` — não foi possível concluir por falha temporária, limite ou indisponibilidade.

O estado `ERRO` nunca deve gerar link. A interface deve continuar mostrando o telefone sem transformar erro técnico em “não possui WhatsApp”.

## Plano de implementação

### Fase 0 — Spike da integração

- Confirmar qual produto oficial será utilizado: WhatsApp Business Platform ou BSP autorizado.
- Confirmar se a conta possui o recurso de consulta de contatos/números.
- Confirmar limites, preço, retenção, região, termos de uso e formato da resposta.
- Criar credenciais de teste separadas da produção.
- Testar números controlados sem enviar mensagens.
- Documentar o contrato do provedor antes de escrever o adapter.

Esta fase exige credenciais do WhatsApp, não a chave da Google Places. A chave nunca deve ser colocada no frontend, no Git ou em `refinamento.md`.

### Fase 1 — Modelo e persistência

- Criar migration Flyway para o estado da validação e a data da última validação.
- Adicionar enum Java para os estados definidos.
- Definir valores iniciais para leads existentes, provavelmente `PENDENTE` quando houver telefone normalizado.
- Preservar o resultado quando o mesmo telefone reaparecer em outra busca.
- Resetar para `PENDENTE` quando o telefone normalizado mudar.
- Manter a deduplicação atual por `googlePlaceId`.
- Não armazenar token, resposta bruta do provedor ou dados desnecessários.

### Fase 2 — Adapter e serviço de validação

- Criar uma interface interna, por exemplo `WhatsAppNumberValidator`.
- Isolar o cliente HTTP do provedor em um adapter substituível.
- Implementar timeout, tratamento de autenticação, indisponibilidade, resposta inválida e limite externo.
- Aplicar fail closed: somente resposta positiva permite link.
- Normalizar e deduplicar números antes da consulta.
- Evitar nova consulta para número já validado dentro da política de validade definida.
- Criar rate limit separado para a validação do WhatsApp, sem reutilizar cegamente o limite da Google Places.
- Definir retry apenas para erros temporários, com limite pequeno e backoff; nunca repetir indefinidamente.

### Fase 3 — Integração com a captura

- Depois de persistir o telefone, executar a validação conforme a estratégia aprovada no spike.
- Preferir uma estratégia que não mantenha uma transação de banco aberta durante chamadas externas.
- Se a decisão for síncrona, limitar o lote e deixar o lead salvo mesmo quando o provedor falhar.
- Se a decisão for assíncrona, persistir `PENDENTE`, reprocessar com mecanismo durável e expor o estado ao frontend; não usar thread em memória como fila definitiva.
- Atualizar `LeadResponse`, respostas de busca, histórico e exportações para refletir o status e gerar link apenas para `DISPONIVEL`.
- Manter o telefone capturado mesmo em `INDISPONIVEL` e `ERRO`.

### Fase 4 — Frontend

- Exibir o número capturado em todos os estados em que ele existir.
- Exibir ação/link de WhatsApp apenas para `DISPONIVEL`.
- Mostrar estado neutro para `PENDENTE`, como “WhatsApp ainda não validado”.
- Mostrar “WhatsApp não encontrado” somente para `INDISPONIVEL`.
- Mostrar “Validação temporariamente indisponível” para `ERRO`, sem criar link.
- Atualizar Busca, resultados, card/detalhe do Kanban, histórico e exportações.
- Não criar botão que envie mensagem automaticamente.

### Fase 5 — Testes e observabilidade

- Testar telefone válido com WhatsApp confirmado.
- Testar telefone válido sem WhatsApp.
- Testar telefone ausente, inválido e estrangeiro.
- Testar número já validado reaparecendo em nova busca.
- Testar troca de telefone e reset para `PENDENTE`.
- Testar timeout, erro de autenticação, resposta inválida, rate limit e indisponibilidade do provedor.
- Testar que nenhum desses erros gera `wa.me`.
- Testar persistência após reload e atualização do histórico.
- Testar exportação com telefone presente e `whatsappUrl` nulo.
- Medir quantidade de validações, respostas positivas, negativas, erros e limites atingidos sem registrar tokens ou dados sensíveis.

## Limites e custos

O limite atual de 10 chamadas por 60 segundos pertence exclusivamente à Google Places. Ele não protege automaticamente uma futura API de validação do WhatsApp.

Antes da implementação, devem ser definidos:

- limite por minuto do provedor;
- eventual limite diário;
- custo por consulta;
- política de cache e validade do resultado;
- comportamento quando o limite for atingido;
- alerta e teto financeiro no provedor escolhido.

Não assumir que validar 20 telefones retornados por uma busca será gratuito ou permitido. O custo e o limite devem ser aplicados por número normalizado e contabilizados separadamente da Google Places.

## Segurança e privacidade

- Credenciais somente em variáveis de ambiente ou secret manager.
- Nunca enviar token ao frontend.
- Nunca registrar telefone completo, token ou resposta bruta em logs de erro.
- Restringir a chave/token ao recurso e ambiente necessários.
- Não usar conta pessoal nem automação de WhatsApp Web.
- Validar somente números capturados no fluxo permitido do produto.
- Revisar termos de uso, LGPD, retenção e finalidade antes de colocar a consulta em produção.

## Critérios de aceite

- Um lead com WhatsApp confirmado recebe `whatsappUrl` e ação manual.
- Um lead com telefone válido, mas sem WhatsApp, continua exibindo o telefone e recebe `whatsappUrl = null`.
- Um lead pendente ou com erro nunca recebe link.
- O sistema não envia mensagens e não usa WhatsApp Web, Baileys ou mecanismo não oficial.
- O resultado da validação permanece após nova consulta, reload e abertura do histórico.
- Repetições do mesmo número respeitam cache, validade e rate limit definidos.
- Falhas do provedor não impedem a persistência do lead nem apagam o telefone capturado.
- Busca, Kanban, Histórico e exportações exibem o mesmo estado de WhatsApp.
- Testes cobrem respostas positivas, negativas, pendentes, erros e limites.
- Build, suíte de testes e migration Flyway passam sem alterar regras não relacionadas.

## Pendências para iniciar a implementação

1. Escolher o provedor oficial.
2. Confirmar se a conta possui consulta de presença/validade de número sem envio de mensagem.
3. Definir orçamento, limite diário e validade do resultado.
4. Disponibilizar credenciais de teste do WhatsApp Business, nunca credenciais pessoais ou no repositório.
5. Decidir se a primeira versão será síncrona, com lote limitado, ou assíncrona e durável.

## Fora do escopo

- Envio automático ou em massa de mensagens.
- Respostas automáticas, chatbot ou campanhas.
- Importação de contatos pessoais.
- Automação de WhatsApp Web.
- Redis, RabbitMQ, Kafka ou fila nova sem decisão arquitetural específica.
- Alterações na integração Google Places que não sejam necessárias para este refinamento.
