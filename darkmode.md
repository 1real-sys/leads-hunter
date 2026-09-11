# Sprint DM-01 — Dark mode do frontend

**Status:** EM IMPLEMENTAÇÃO — DM-01.1 A DM-01.5 CONCLUÍDAS

**Escopo:** somente frontend Angular 22

**Objetivo:** adicionar temas claro, escuro e automático a todas as telas do Leads Hunter, preservando a identidade visual, a densidade operacional, a acessibilidade e todos os comportamentos existentes.

## Resultado esperado

Ao concluir esta sprint, o usuário poderá escolher entre:

- **Sistema:** acompanha a preferência `prefers-color-scheme` do sistema operacional;
- **Claro:** mantém explicitamente o tema claro atual;
- **Escuro:** aplica explicitamente o novo tema escuro.

A escolha será preservada no navegador e aplicada antes da primeira renderização útil, evitando uma troca visual perceptível ao recarregar a página. Todas as rotas, estados, controles e elementos do mapa deverão permanecer legíveis e funcionais nos três modos.

## Contexto técnico confirmado

- Angular `22.1.4`, componentes standalone e TypeScript estrito.
- Estado local implementado prioritariamente com Signals.
- Serviços singleton novos podem usar o decorator `@Service()` disponível no Angular 22 instalado.
- SCSS global e encapsulamento padrão por componente.
- A maior parte da interface já consome tokens CSS definidos em `frontend/src/styles.scss`.
- O mecanismo global de tema usa Signals, `prefers-color-scheme`, persistência local e um seletor no shell.
- O shell principal está em `app.ts`, `app.html` e `app.scss`.
- O Leaflet cria parte do DOM fora dos templates Angular e possui cores próprias em SCSS e em `mapa-busca.ts`.
- O frontend não possui SSR; o tema será aplicado no navegador.

## Progresso desta entrega

- **Concluído:** DM-01.1, DM-01.2, DM-01.3, DM-01.4 e DM-01.5.
- **Pendente:** DM-01.6, com o tratamento interno dos tiles, controles, popup, legenda, círculo e polígonos do Leaflet.
- **Validação executada:** 211 testes frontend, build de produção sem warnings e auditoria visual/Axe em 24 combinações de rota, tema e viewport, sem overflow horizontal ou violações WCAG A/AA.
- **Decisão de escopo:** a paleta geográfica de IDHM e os estilos internos do Leaflet foram preservados para a DM-01.6, sem implementação antecipada.

## Princípios visuais obrigatórios

O dark mode deve ser uma adaptação do sistema visual atual, não um segundo redesign.

- Preservar a identidade verde-petróleo do Leads Hunter.
- Não usar preto absoluto como fundo principal.
- Não introduzir roxo neon, glow, gradientes decorativos ou glassmorphism.
- Não aumentar radius, sombras ou quantidade de cards para “parecer moderno”.
- Não adicionar ícones, ilustrações ou animações sem função.
- Preservar a hierarquia construída por posição, espaçamento, peso e contraste.
- Manter a densidade de ferramenta operacional/CRM leve.
- Manter a mesma arquitetura de sidebar e área de trabalho.
- Usar cor para reforçar estados, nunca como único meio de identificação.
- Preservar os rótulos textuais de temperatura, status, sucesso, alerta e erro.
- Respeitar `prefers-reduced-motion` já tratado globalmente.
- Interromper o polimento quando a interface estiver clara, consistente, acessível e funcional.

## Decisões de produto

### Controle de tema

Será usado um controle nativo compacto com o rótulo visível **Tema** e as opções `Sistema`, `Claro` e `Escuro`.

O controle ficará no rodapé da sidebar, abaixo da navegação. Em telas menores, acompanhará a organização responsiva do shell sem criar uma nova barra, modal ou drawer.

Motivos da escolha:

- as três opções ficam explícitas;
- o usuário consegue voltar ao comportamento automático;
- um `select` nativo possui semântica e navegação por teclado previsíveis;
- dispensa biblioteca de ícones;
- evita um toggle ambíguo sobre ação versus estado atual.

### Preferência e persistência

- Tipo público: `TemaPreferido = 'system' | 'light' | 'dark'`.
- Valor inicial: preferência salva; na ausência dela, `system`.
- Chave sugerida no `localStorage`: `leads-hunter-theme`.
- Valores desconhecidos ou indisponibilidade do storage devem cair com segurança para `system`.
- Em `system`, mudanças do sistema operacional durante a sessão devem atualizar o tema resolvido.
- Em `light` ou `dark`, mudanças do sistema não devem sobrescrever a escolha explícita.
- Não haverá sincronização entre abas nesta sprint; o comportamento local por recarregamento é suficiente para o produto single-user atual.

### Aplicação no documento

- O elemento `<html>` receberá `data-theme="light"` ou `data-theme="dark"` conforme o tema resolvido.
- `color-scheme` será atualizado para que inputs, selects, textareas, scrollbars e controles nativos usem o esquema correto.
- Um bootstrap mínimo de tema será executado antes da aplicação Angular para evitar flash claro quando a preferência persistida for escura.
- A lógica de leitura e resolução deve ficar pequena, determinística e sem interpolar conteúdo externo no DOM.
- O estado Angular continuará sendo a fonte de verdade durante a sessão.

## Direção da paleta escura

A paleta abaixo é uma base técnica para implementação. Os valores ainda deverão ser conferidos no navegador, nos estados reais e por contraste automatizado antes de serem considerados finais.

| Token | Tema escuro proposto | Função |
| --- | --- | --- |
| `--surface-page` | `#101b19` | Fundo geral da aplicação |
| `--surface` | `#172522` | Sidebar, painéis, cards e tabelas |
| `--surface-subtle` | `#1d302c` | Hover, cabeçalhos e agrupamentos secundários |
| `--ink` | `#e8f2f0` | Texto principal |
| `--muted` | `#afc3bf` | Texto secundário |
| `--border` | `#334a46` | Divisórias e limites de controles |
| `--accent` | `#58b8a9` | Ações primárias e estado selecionado |
| `--accent-strong` | `#8bd7ca` | Links e accent usado como texto |
| `--accent-soft` | `#153a35` | Fundo de seleção e destaque discreto |
| `--on-accent` | `#071b18` | Texto sobre o accent principal |
| `--focus-ring` | `#c4b5fd` | Foco visível com alto contraste |
| `--selection` | `#245c54` | Seleção de texto |
| `--success-surface` | `#153029` | Fundo de sucesso |
| `--success-ink` | `#8ed9bd` | Texto de sucesso |
| `--success-border` | `#356a5b` | Borda de sucesso |
| `--danger-surface` | `#351d1c` | Fundo de erro |
| `--danger-ink` | `#f4aaa3` | Texto de erro |
| `--danger-border` | `#7a4a48` | Borda de erro |
| `--warning-surface` | `#332c18` | Fundo de alerta |
| `--warning-ink` | `#f1d27a` | Texto de alerta |
| `--warning-border` | `#756431` | Borda de alerta |
| `--hot-surface` | `#351f1c` | Fundo de temperatura quente |
| `--hot-ink` | `#ffaaa0` | Texto de temperatura quente |
| `--hot-border` | `#7a4740` | Borda de temperatura quente |
| `--warm-surface` | `#332c18` | Fundo de temperatura morna |
| `--warm-ink` | `#f1d27a` | Texto de temperatura morna |
| `--warm-border` | `#756431` | Borda de temperatura morna |
| `--cold-surface` | `#192d38` | Fundo de temperatura fria |
| `--cold-ink` | `#a8d7ef` | Texto de temperatura fria |
| `--cold-border` | `#3f647a` | Borda de temperatura fria |

Os pares propostos de texto/fundo possuem contraste preliminar acima de 7:1. Isso não substitui a auditoria dos componentes renderizados, porque opacidade, hover, sobreposição, tamanho da fonte e estilos do Leaflet podem alterar o resultado final.

## Arquitetura proposta

```text
Preferência Sistema/Claro/Escuro
             |
             v
TemaStore (@Service + signal privado)
             |
             +--> localStorage
             +--> matchMedia(prefers-color-scheme)
             |
             v
tema resolvido: light | dark
             |
             v
<html data-theme="..."> + color-scheme
             |
             +--> tokens globais
             +--> componentes existentes
             +--> controles nativos
             +--> Leaflet e overlays do mapa
```

### Responsabilidades

`TemaStore` deverá:

- carregar e validar a preferência;
- expor a preferência como Signal somente leitura;
- expor o tema resolvido como `computed()`;
- alterar a preferência por um método explícito;
- persistir a alteração;
- observar `matchMedia` somente para resolver a opção `system`;
- aplicar `data-theme` e `color-scheme` ao documento;
- remover listeners quando o serviço for destruído;
- tratar falha de storage sem impedir o bootstrap.

O componente `App` deverá apenas:

- injetar o store;
- expor o Signal ao template;
- encaminhar a mudança do seletor;
- manter intactos navegação, foco após troca de rota e estrutura do shell.

## Plano de implementação

### DM-01.1 — Consolidar os tokens de tema — CONCLUÍDA

**Objetivo:** separar valores cromáticos de tokens estruturais e permitir a troca global de paleta.

Tarefas:

- manter tipografia, espaçamento, radius, alturas e breakpoints fora da variação de tema;
- manter os tokens claros atuais como tema padrão;
- adicionar a paleta escura sob o seletor de tema do `<html>`;
- definir `color-scheme: light` e `color-scheme: dark` nos contextos correspondentes;
- criar somente tokens adicionais que possuam uso concreto, como backdrop, sombra elevada e superfícies translúcidas do mapa;
- substituir valores cromáticos literais de componentes por tokens semânticos;
- não criar uma cópia completa de cada arquivo SCSS para o tema escuro;
- não usar `::ng-deep`; estilos de DOM gerado pelo Leaflet devem ficar no stylesheet global.

Valores literais já identificados para migração:

- erro do formulário em `busca-form.scss`;
- erro da exportação em `exportacao-leads.scss`;
- borda do card, sombra de drag e backdrop/drawer do Kanban;
- fundos translúcidos, bordas e sombras da legenda e feedback do mapa;
- cores do círculo e das bordas municipais em `mapa-busca.ts`.

### DM-01.2 — Criar o estado global de tema — CONCLUÍDA

**Objetivo:** controlar preferência, resolução e persistência sem gerenciador de estado externo.

Arquivos previstos:

- `frontend/src/app/core/theme/tema.model.ts`;
- `frontend/src/app/core/theme/tema-store.ts`;
- `frontend/src/app/core/theme/tema-store.spec.ts`.

Tarefas:

- usar `@Service()` e `inject()` conforme o padrão Angular 22 já adotado pelo projeto;
- armazenar estado mutável em Signal privado e expor versão somente leitura;
- usar `computed()` para derivar `light` ou `dark` da preferência e do sistema;
- validar a string recuperada do storage sem `any`;
- evitar `effect()` para propagação de estado quando um método explícito ou `computed()` resolver;
- limitar o acesso direto a `document`, `matchMedia` e `localStorage` ao núcleo de tema;
- aplicar a escolha no elemento raiz sem recarregar rota ou componentes;
- garantir fallback seguro quando APIs do navegador lançarem erro.

### DM-01.3 — Evitar flash de tema no bootstrap — CONCLUÍDA

**Objetivo:** aplicar a preferência resolvida antes da primeira renderização útil.

Arquivos previstos:

- `frontend/src/index.html`;
- possivelmente um utilitário pequeno e testável em `frontend/src/app/core/theme/`, caso evite duplicação de resolução.

Tarefas:

- declarar suporte a esquemas claro e escuro nos metadados do documento;
- ler apenas a chave conhecida do storage;
- aceitar somente `system`, `light` e `dark`;
- resolver `system` com `matchMedia('(prefers-color-scheme: dark)')`;
- definir `data-theme` antes do bootstrap Angular;
- manter fallback claro quando nenhuma API estiver disponível;
- não incluir dependência, request externo ou conteúdo dinâmico não confiável.

Critério específico: recarregar diretamente `/busca`, `/kanban`, `/historico`, `/historico/:id` ou `/bloqueios` com preferência escura não deve exibir um frame claro perceptível antes do tema correto.

### DM-01.4 — Adicionar o seletor ao shell — CONCLUÍDA

**Objetivo:** tornar a preferência descoberta e operável sem alterar a navegação principal.

Arquivos previstos:

- `frontend/src/app/app.ts`;
- `frontend/src/app/app.html`;
- `frontend/src/app/app.scss`;
- `frontend/src/app/app.spec.ts`.

Tarefas:

- adicionar a região de preferências ao fim da sidebar;
- usar `label` visível associado ao `select` nativo;
- manter a ordem `Sistema`, `Claro`, `Escuro`;
- refletir imediatamente a opção selecionada;
- preservar o espaço e a prioridade da navegação;
- adaptar o controle aos breakpoints atuais de 64 rem e 40 rem;
- manter foco visível, área de toque adequada e contraste de todos os estados;
- não adicionar ícones decorativos, tooltip, dropdown customizado ou animação temática.

### DM-01.5 — Adaptar superfícies e estados de todas as telas — CONCLUÍDA

**Objetivo:** garantir cobertura completa sem alterar layout ou comportamento.

#### Shell e rota não encontrada

- página, sidebar, navegação ativa, links, skip link e foco do conteúdo;
- placeholder e ação da página não encontrada.

#### Busca

- sidebar da busca e área principal;
- Signal Forms: input, select, checkbox, slider, disabled, invalid e focus;
- botão principal e estados de hover/disabled/loading;
- mensagens de sucesso, vazio, alerta e erro;
- resumo e cards de resultados;
- badges de temperatura e link manual de WhatsApp.

#### Kanban

- filtros e exportação;
- fundo do board e cinco colunas;
- cabeçalhos, contadores, cards e metadados;
- estados de coluna vazia, carregamento, erro e paginação;
- hover, foco, card em movimento, preview e placeholder do Angular CDK;
- status, temperatura e badge de IDHM sem depender somente de cor;
- drawer, backdrop, campos editáveis, mensagens e ações.

#### Histórico

- lista, tabela, cabeçalhos, linhas em hover e rolagem horizontal;
- detalhe da busca, resumo, snapshot, contatos e ação de CNPJ;
- estados carregando, vazio, erro e sucesso;
- manter a distinção textual entre dado atual do lead e score histórico.

#### Bloqueios

- formulário, lista, tabela e ação de remoção;
- estados vazio, carregando, sucesso e erro;
- controles disabled, invalid, hover e focus.

#### Controles nativos

- inputs, textareas, selects, checkboxes e range;
- autofill do navegador, quando aplicável;
- placeholder e texto desabilitado;
- scrollbar onde o navegador respeitar `color-scheme`;
- seleção de texto.

### DM-01.6 — Adaptar Leaflet e a camada IDHM — PENDENTE

**Objetivo:** evitar um mapa excessivamente claro dentro da interface escura sem perder leitura cartográfica.

Tarefas:

- manter OpenStreetMap, URLs e atribuição existentes;
- não adicionar provedor de tiles nem dependência nova;
- aplicar tratamento tonal somente ao pane de tiles no tema escuro, sem filtrar marcador, círculo, polígonos, legenda ou controles;
- começar com redução moderada de brilho/saturação e ajuste de contraste, validando nomes de vias e limites; não usar filtro agressivo apenas para “forçar” um mapa preto;
- preservar a paleta geográfica do IDHM porque ela codifica faixas de dados e é compartilhada com badges e testes;
- criar tokens para borda municipal normal, borda destacada, círculo da busca, overlays e sombras;
- substituir as cores literais de `mapa-busca.ts` por propriedades CSS/tokens compatíveis com SVG do Leaflet;
- se o navegador alvo não resolver custom properties em presentation attributes SVG, ler os tokens computados ao aplicar/reaplicar o estilo, sem duplicar a paleta completa no TypeScript;
- tematizar zoom, atribuição, popup, tip, legenda e feedback no stylesheet global;
- confirmar que troca de tema não recria a instância do mapa nem dispara chamadas geográficas;
- confirmar que círculo, marker, polígonos e popups continuam acima dos tiles e operáveis por teclado.

### DM-01.7 — Testes automatizados

#### Testes do núcleo de tema

- preferência ausente resulta em `system`;
- preferência `light` e `dark` é restaurada;
- valor inválido cai para `system`;
- escolha do sistema resolve claro e escuro corretamente;
- mudança do sistema atualiza apenas a preferência `system`;
- escolha explícita não é sobrescrita por mudança do sistema;
- alteração é persistida e atualiza `data-theme`/`color-scheme`;
- falha de leitura ou escrita do storage não derruba a aplicação;
- listener de `matchMedia` é removido no teardown.

#### Testes do shell

- seletor possui label acessível e as três opções;
- valor exibido acompanha o Signal;
- mudança no `select` atualiza o store;
- navegação, `aria-current`, skip link e foco após mudança de rota permanecem intactos;
- layout não ganha header, footer ou navegação redundante.

#### Regressão por componentes

- ajustar somente testes que dependam legitimamente de valores cromáticos migrados para tokens;
- manter todos os testes funcionais existentes de Busca, Kanban, Histórico, Bloqueios, mapa e exportação;
- incluir teste de que a troca de tema não recria o mapa nem faz novo request de GeoJSON;
- evitar testes frágeis que comparem toda a folha de estilos ou snapshots extensos.

### DM-01.8 — Validação visual, responsiva e acessível

Executar a matriz abaixo em **tema claro e escuro**:

| Área | Desktop | Mobile | Estados mínimos |
| --- | --- | --- | --- |
| Busca | 1440 × 1000 | 390 × 844 | inicial, mapa, loading, resultado, vazio e erro |
| Kanban | 1440 × 1000 | 390 × 844 | cards, coluna vazia, drag, paginação, drawer e erro |
| Histórico | 1440 × 1000 | 390 × 844 | lista, vazio e erro |
| Detalhe histórico | 1440 × 1000 | 390 × 844 | tabela, CNPJ, loading, sucesso e erro |
| Bloqueios | 1440 × 1000 | 390 × 844 | lista, vazio, sucesso, erro e remoção |
| Rota inexistente | 1440 × 1000 | 390 × 844 | conteúdo e ação de retorno |

Validações obrigatórias:

- Axe sem violações WCAG A/AA nas rotas principais nos dois temas;
- contraste WCAG AA de textos, links, controles, foco e estados;
- foco visível e ordem de tabulação preservados;
- não depender somente da cor para temperatura, status, erro ou seleção;
- nenhuma rolagem da página causada pelo novo seletor;
- nenhuma regressão no scroll independente das colunas do Kanban;
- nenhuma regressão de overflow em tabelas, drawer ou sidebar móvel;
- inspeção visual de hover, focus, active, disabled, loading, empty, success, warning e error;
- persistência confirmada após reload;
- preferência `system` confirmada ao alternar o esquema do navegador;
- screenshots comparáveis de claro e escuro para todas as rotas;
- revisão anti-slop final usando o checklist da skill.

## Arquivos previstos

### Criados

- `frontend/src/app/core/theme/tema.model.ts`
- `frontend/src/app/core/theme/tema-store.ts`
- `frontend/src/app/core/theme/tema-store.spec.ts`

### Modificados diretamente

- `frontend/src/index.html`
- `frontend/src/styles.scss`
- `frontend/src/app/app.ts`
- `frontend/src/app/app.html`
- `frontend/src/app/app.scss`
- `frontend/src/app/app.spec.ts`
- `frontend/src/app/features/busca/mapa-busca.ts`
- `frontend/src/app/features/busca/mapa-busca.scss`
- `frontend/src/app/features/busca/mapa-busca.spec.ts`
- arquivos SCSS de Busca, Kanban, Histórico e Bloqueios que ainda possuam cor literal ou estado sem token semântico.

### Modificados após a implementação

- `fluxo.md`, registrando o estado real e as validações executadas;
- `HISTORICO_IMPLEMENTACOES.md`, com uma única entrada da feature;
- este `darkmode.md`, mudando o status apenas quando todos os critérios aplicáveis estiverem atendidos.

A lista é uma previsão. O relatório final da futura implementação deverá registrar somente os arquivos realmente alterados.

## Critérios de aceite

- [x] O seletor `Sistema/Claro/Escuro` está disponível e acessível em todas as rotas pelo shell.
- [x] A preferência escolhida persiste após reload e navegação direta.
- [x] `Sistema` acompanha `prefers-color-scheme` durante a sessão.
- [x] O tema correto é aplicado antes da primeira renderização útil.
- [ ] Todas as superfícies e textos usam tokens semânticos, sem duplicação completa de SCSS por tema.
- [x] Inputs, selects, textareas, checkboxes e sliders permanecem legíveis e operáveis.
- [x] Busca, Kanban, Histórico, detalhe, Bloqueios e página inexistente têm suas superfícies da aplicação adaptadas ao tema escuro.
- [x] Estados loading, empty, success, warning, error, hover, focus, active e disabled estão cobertos pelos tokens semânticos.
- [x] Drawer, backdrop, drag-and-drop e paginação do Kanban não sofreram regressão na suíte frontend.
- [ ] Leaflet, controles, popup, legenda, círculo e camada IDHM estão coerentes com o tema.
- [ ] A troca de tema não recria o mapa nem provoca request HTTP.
- [x] A paleta IDHM continua semanticamente igual e identificada também por texto.
- [x] Nenhuma dependência foi adicionada.
- [x] Nenhum endpoint, DTO ou código backend foi alterado.
- [x] A suíte frontend passa integralmente.
- [x] O build de produção termina sem erro e respeita os budgets existentes.
- [ ] O smoke E2E passa sem regressão do fluxo do MVP.
- [x] Axe não encontra violações WCAG A/AA nas seis rotas auditadas, nos dois temas e viewports.
- [x] A revisão visual das rotas auditadas não encontra overflow ou contraste insuficiente.
- [x] A revisão anti-slop não encontra decoração gratuita, excesso de superfícies, glow, neon ou hierarquia artificial.

## Validações

Executadas nesta entrega:

```bash
cd frontend
npm test -- --watch=false
npm run build
```

A auditoria em Firefox cobriu Busca, Kanban, Histórico, detalhe histórico, Bloqueios e rota inexistente em `1440 × 1000` e `390 × 844`, nos temas claro e escuro. Também foram conferidos os breakpoints de 1024, 768, 641 e 640 px, além da resolução automática de `Sistema` com preferência escura do navegador.

Permanecem para o fechamento da sprint completa:

```bash
cd frontend
npm run e2e:smoke
```

Depois da DM-01.6, repetir a inspeção visual/Axe com os estados internos do mapa, incluindo controles, popup, legenda e camada IDHM.

## Fora do escopo

- Alterações no backend, banco ou contratos HTTP.
- Tema por usuário salvo no servidor.
- Sincronização de preferência entre dispositivos.
- Sincronização em tempo real entre abas.
- Novo sistema de design ou redesign das telas.
- Troca de tipografia, layout, navegação ou densidade.
- Nova biblioteca de componentes, ícones ou temas.
- Tailwind, Angular Material ou biblioteca global de estado.
- Novo provedor de mapa ou tiles.
- Tema de alto contraste separado.
- Animação de transição entre temas.
- Novas funcionalidades de Busca, Kanban, Histórico ou Bloqueios.

## Riscos e mitigação

| Risco | Mitigação planejada |
| --- | --- |
| Flash claro antes do bootstrap | Resolver e aplicar a preferência antes da renderização Angular |
| Cores literais escaparem da troca | Inventário por `rg` e migração para tokens semânticos |
| Tema escuro virar “preto + neon” | Paleta petróleo limitada, sem glow, gradiente ou preto absoluto |
| Contraste de estados ficar insuficiente | Validar pares, componentes renderizados e Axe em ambos os temas |
| Mapa continuar excessivamente claro | Tratar somente o pane de tiles e tematizar controles/overlays |
| Filtro do mapa prejudicar nomes de vias | Ajuste moderado e inspeção real em desktop/mobile |
| IDHM perder significado | Preservar sua paleta e seus rótulos textuais |
| Troca de tema recriar o Leaflet | Atualizar tokens/estilos, não a instância do mapa |
| Persistência falhar em ambiente restrito | Capturar erro e manter `system` sem interromper a aplicação |
| Testes ficarem acoplados a hexadecimais | Testar comportamento e tokens aplicados, não screenshots de CSS |
| Controle poluir a navegação móvel | Região compacta no shell e validação nos breakpoints existentes |

## Definição de pronto

A sprint só poderá mudar de **EM IMPLEMENTAÇÃO** para **CONCLUÍDA** quando:

1. o comportamento `Sistema/Claro/Escuro` estiver implementado e persistido;
2. todas as rotas e estados aplicáveis estiverem adaptados;
3. claro e escuro preservarem a hierarquia e a densidade atuais;
4. testes frontend, build e smoke E2E tiverem sido executados e aprovados;
5. Axe e a revisão responsiva tiverem sido executados nos dois temas;
6. falhas causadas pela feature tiverem sido corrigidas e validadas novamente;
7. nenhuma dependência, backend ou funcionalidade fora do escopo tiver sido adicionada;
8. `fluxo.md`, `HISTORICO_IMPLEMENTACOES.md` e este documento refletirem o estado real;
9. o relatório final listar resultados reais, sem declarar validação não executada.
