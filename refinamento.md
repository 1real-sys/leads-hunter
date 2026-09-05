# Refinamento — WhatsApp visível no card do Kanban

## Objetivo

Expor o canal WhatsApp diretamente no card de cada lead na tela do Kanban, junto ao telefone, sem obrigar o usuário a abrir o drawer de detalhes para descobrir se o lead possui link manual de WhatsApp.

O card deve mostrar o rótulo "WhatsApp" e um link clicável "Abrir WhatsApp" ao lado direito do telefone quando o lead possuir `whatsappUrl`. Leads sem `whatsappUrl` continuam exibindo apenas o telefone; nenhum texto de indisponibilidade é acrescentado ao card.

## Situação atual

Hoje o Kanban exibe no card o telefone do lead, mas o link manual de WhatsApp só aparece no drawer lateral que abre ao clicar no nome do lead (`lead-detalhe.html`). Para saber se um lead tem WhatsApp, o usuário precisa abrir card por card. O `whatsappUrl`, porém, já chega completo no `LeadResponse` de cada card (o mesmo `GET /api/leads/pagina` que alimenta o Kanban); ele apenas não é renderizado no template do card.

O escopo anterior deste documento (validação de presença do número no WhatsApp via provedor oficial, com estados `PENDENTE`/`DISPONIVEL`/`INDISPONIVEL`/`ERRO`) foi **descartado por decisão de produto**: não existe checagem pública, gratuita e segura dessa informação, e o custo e a burocracia de uma integração oficial não se justificam para um produto que apenas abre links manuais e nunca envia mensagens.

## Decisões

- O link continua sendo gerado **somente pelo backend** (`WhatsAppLinkGenerator` → `https://wa.me/55...`) para telefone brasileiro normalizado válido. O frontend nunca monta a URL.
- O bloco de WhatsApp é renderizado **somente quando `whatsappUrl` não for nulo**.
- Quando ausente, o card mostra apenas o telefone, sem indicador de "indisponível"; o drawer mantém a mensagem explicativa atual.
- O funcionamento existente não muda: drawer, busca, histórico e exportações permanecem como estão.
- Nenhuma mensagem é enviada; a abertura é manual em nova aba (`target="_blank"` + `rel="noopener noreferrer"`).
- O texto do link e o `aria-label` seguem os padrões já usados nas telas de Busca e Histórico.

## Escopo da implementação

Frontend puro, restrito ao componente do card do Kanban:

- `lead-card.html`: novo bloco condicionado a `whatsappUrl`, com rótulo e âncora, na mesma linha do telefone e posicionado ao lado direito;
- `lead-card.scss`: estilos do bloco e do link compacto no padrão outline do card, em linha com o telefone;
- `lead-card.spec.ts`: testes do novo bloco.

Sem alterações no backend, nos contratos HTTP, no drawer ou nas demais telas.

## Critérios de aceite

- Card com `whatsappUrl` exibe o rótulo "WhatsApp" e o link "Abrir WhatsApp" clicável ao lado direito do telefone, na mesma linha.
- O link abre `https://wa.me/55...` em nova aba, com `aria-label` descritivo contendo o nome do lead.
- Card com telefone mas sem `whatsappUrl` (ex.: fixo) não exibe o bloco de WhatsApp.
- Comportamento do drawer, das demais telas e dos endpoints permanece inalterado.
- Suíte de testes e build do frontend passam.

## Fora do escopo

- Validação de presença do número no WhatsApp (descartada por decisão de produto).
- WhatsApp Business Platform, Cloud API ou BSP autorizado.
- Baileys, whatsapp-web.js ou qualquer automação não oficial.
- Envio automático ou em massa de mensagens.
- Alterações no backend ou nos contratos HTTP.
