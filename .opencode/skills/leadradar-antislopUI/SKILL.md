---
name: anti-ai-slop-ui
description: Use esta skill ao criar, revisar ou polir interfaces visuais do frontend. Evita padrões genéricos e repetitivos associados a interfaces geradas automaticamente por IA, priorizando hierarquia, clareza, identidade, densidade adequada, consistência e decisões visuais justificadas pela função real do produto.
---

# Anti AI Slop UI

## Objetivo

Esta skill existe para impedir que interfaces funcionais adquiram aparência genérica, excessivamente decorativa ou reconhecivelmente produzida por padrões automáticos de IA.

Ela não define funcionalidades do produto e não substitui skills técnicas do framework.

Seu papel é orientar:

- composição visual;
- hierarquia;
- tipografia;
- espaçamento;
- densidade;
- superfícies;
- ícones;
- cores;
- feedback visual;
- microinterações;
- consistência;
- personalidade visual;
- revisão crítica da interface.

A prioridade é criar interfaces que pareçam projetadas deliberadamente para o produto em questão.

## Relação com outras skills

Quando utilizada junto de skills específicas do projeto e do framework:

1. requisitos funcionais e contratos do produto têm prioridade;
2. acessibilidade, semântica e práticas técnicas do framework têm prioridade;
3. esta skill orienta as decisões visuais dentro desses limites.

Esta skill nunca deve:

- alterar contratos de API por motivo estético;
- remover requisito funcional;
- prejudicar acessibilidade;
- substituir HTML semântico por soluções visuais frágeis;
- adicionar dependências apenas para obter determinado estilo;
- transformar uma aplicação operacional em landing page de marketing.

No LeadRadar:

- `leadradar-frontend` define O QUE a aplicação deve fazer;
- `angular-developer` define COMO implementar Angular corretamente;
- `anti-ai-slop-ui` orienta COMO a interface deve evitar aparência genérica e artificial.

## Princípio central

Não adicionar elementos apenas para fazer a interface parecer mais:

- moderna;
- premium;
- sofisticada;
- tecnológica;
- dinâmica;
- "SaaS".

Todo elemento visual deve justificar sua existência por pelo menos uma destas funções:

- indicar hierarquia;
- facilitar leitura;
- agrupar informação;
- separar contextos;
- comunicar estado;
- tornar uma ação identificável;
- melhorar navegação;
- fornecer feedback;
- reforçar identidade visual;
- reduzir esforço cognitivo.

Se o elemento não cumprir nenhuma dessas funções, considere removê-lo.

## Design orientado à tarefa

Antes de projetar uma tela, identificar:

1. qual tarefa o usuário está tentando realizar;
2. qual informação precisa enxergar primeiro;
3. qual decisão precisa tomar;
4. qual é a principal ação da tela;
5. quais informações podem permanecer secundárias;
6. quais estados excepcionais precisam ficar claros.

A composição deve surgir dessas respostas.

Não escolher primeiro um padrão visual e depois encaixar a funcionalidade dentro dele.

Evitar raciocínio como:

> "Vou usar um bento porque ficará moderno."

Preferir:

> "Estas informações precisam ser comparadas simultaneamente, então uma grade é adequada."

## Não desenhar por aleatoriedade

Nunca escolher deliberadamente:

- layout;
- fonte;
- posição;
- quantidade de cards;
- cores;
- animações;
- arquitetura de componentes

por randomização apenas para produzir variedade.

Variedade não é objetivo.

Adequação ao produto é objetivo.

Layouts diferentes devem existir porque tarefas diferentes exigem estruturas diferentes.

## Anti-template

Não assumir automaticamente que toda aplicação precisa de:

- hero;
- slogan;
- subtítulo promocional;
- CTA gigante;
- seção de benefícios;
- três cards lado a lado;
- bento grid;
- testimonials;
- pricing;
- FAQ;
- estatísticas gigantes;
- marquee;
- logos de parceiros;
- glassmorphism;
- gradientes decorativos;
- ilustrações abstratas.

Esses elementos só devem existir quando fizerem sentido para o produto e para a tela atual.

Aplicações operacionais devem parecer aplicações operacionais.

Landing pages devem parecer landing pages.

Não misturar automaticamente os dois paradigmas.

## Hierarquia visual

Toda tela deve possuir hierarquia perceptível sem depender de efeitos extravagantes.

Priorizar nesta ordem:

1. posição;
2. tamanho;
3. espaçamento;
4. peso tipográfico;
5. contraste;
6. cor;
7. efeitos.

Evitar resolver toda hierarquia usando:

- sombras;
- cores diferentes;
- bordas coloridas;
- badges;
- gradientes;
- glow.

Se remover sombras, gradientes e ícones e a hierarquia desaparecer completamente, a composição provavelmente está fraca.

## Tipografia

### Não usar tipografia como decoração automática

Escolher uma família tipográfica pela:

- legibilidade;
- cobertura de caracteres;
- disponibilidade;
- coerência com o produto;
- desempenho;
- contraste entre pesos.

Não escolher fonte apenas porque é frequente em sites modernos.

Não proibir automaticamente:

- Inter;
- Geist;
- Space Grotesk;
- outras fontes populares.

O problema não é a fonte isoladamente.

O problema é reproduzir sem intenção todo o conjunto visual associado a templates genéricos.

### Hierarquia tipográfica

Usar uma escala pequena e consistente.

Evitar quantidade excessiva de:

- tamanhos;
- pesos;
- estilos;
- letras espaçadas;
- uppercase.

Não transformar todo pequeno texto auxiliar em:

```text
TRACKING-WIDE UPPERCASE
```

Não criar labels artificiais como:

```text
SECTION 01
FEATURE 03
MODULE 02
QUESTION 05
```

quando esses rótulos não possuem significado real para o usuário.

### Headlines

Títulos devem ser proporcionais à interface.

Em aplicações operacionais:

- não usar headings cinematográficos sem necessidade;
- evitar títulos enormes ocupando metade da viewport;
- evitar quebra artificial de frases;
- evitar largura estreita que transforme frases simples em muitas linhas.

O tamanho deve respeitar:

- importância;
- espaço disponível;
- densidade esperada da aplicação.

## Espaçamento

Usar uma escala consistente de espaçamento.

Não inserir enormes áreas vazias apenas para produzir aparência editorial.

Não compactar elementos relacionados até prejudicar leitura.

A distância deve comunicar relacionamento:

- elementos relacionados → mais próximos;
- grupos diferentes → mais afastados;
- seções diferentes → separação perceptível.

Evitar usar espaço vertical gigante como solução universal.

Aplicações densas podem ser visualmente boas quando a densidade é organizada.

## Cards

### Não transformar tudo em card

Antes de criar um card, perguntar:

> Este conteúdo realmente precisa ser percebido como uma unidade independente?

Não envolver automaticamente:

- títulos;
- filtros;
- grupos de botões;
- pequenos textos;
- navegação;
- estatísticas simples

em containers arredondados separados.

Cards demais fragmentam a interface.

### Cards funcionais

Quando cards forem adequados:

- usar hierarquia interna clara;
- evitar múltiplas bordas e sombras;
- não inserir decoração sem significado;
- manter densidade compatível com o uso;
- permitir leitura rápida.

Em listas operacionais, um card compacto normalmente é melhor que um card promocional.

## Bento grids

Bento não é proibido.

Também não é padrão.

Utilizar apenas quando diferentes blocos de informação realmente se beneficiem de:

- proporções diferentes;
- leitura simultânea;
- agrupamentos visuais independentes.

Não usar bento simplesmente para preencher uma homepage.

Se uma lista, tabela, coluna ou layout simples comunicar melhor, utilizar a solução simples.

## Cantos arredondados

Não aplicar `border-radius` grande em todos os elementos automaticamente.

Definir uma escala pequena e coerente.

Exemplo conceitual:

```text
controle pequeno -> radius pequeno
container interativo -> radius moderado
modal/painel -> radius moderado
```

Evitar o padrão:

```text
tudo = rounded-2xl
```

Radius deve reforçar consistência, não servir como decoração principal.

## Sombras

Não aplicar `box-shadow` a todo container.

Preferir criar separação através de:

- fundo;
- contraste;
- borda;
- espaçamento;
- alinhamento.

Usar sombras quando houver necessidade real de comunicar:

- elevação;
- sobreposição;
- elemento flutuante;
- drag;
- popover;
- modal.

Evitar sombras grandes e difusas sem função.

## Gradientes

Gradientes não são proibidos.

Não utilizá-los automaticamente para tornar uma interface "moderna".

Evitar:

- gradiente roxo/azul gratuito;
- texto com gradiente sem significado;
- múltiplos gradientes competindo;
- fundos neon atrás de conteúdo operacional.

Usar gradiente somente quando houver intenção visual clara.

## Glassmorphism e liquid glass

Não utilizar glassmorphism por padrão.

Transparência e blur devem ter motivo funcional ou visual específico.

Evitar:

- vários painéis translúcidos sobre fundos complexos;
- blur reduzindo contraste;
- bordas brilhantes;
- reflexos artificiais;
- "liquid glass" improvisado.

Legibilidade tem prioridade sobre efeito.

## Orbs, glows e decoração abstrata

Não adicionar automaticamente:

- círculos desfocados;
- blobs;
- orbs radiais;
- glow neon;
- manchas de gradiente;
- grades de pontinhos;
- linhas decorativas;
- ruído;
- estrelas;
- sparkles.

Um fundo simples não é falha de design.

Quando decoração existir, deve reforçar a identidade da aplicação e nunca competir com o conteúdo.

## Cores

Definir uma paleta limitada.

Uma aplicação normalmente precisa de:

- superfícies;
- texto primário;
- texto secundário;
- bordas;
- cor de ação;
- estados semânticos.

Evitar quantidade excessiva de cores decorativas.

Estados devem usar cores semanticamente:

- sucesso;
- alerta;
- erro;
- informação.

Não usar cor como único mecanismo para transmitir significado.

## Dark mode

Não escolher automaticamente:

```text
preto + roxo neon
```

apenas porque é uma combinação comum em interfaces de IA.

Tema escuro deve existir porque:

- faz sentido para o produto;
- foi solicitado;
- integra o sistema visual.

Não utilizar fundo preto absoluto sem avaliar contraste e conforto visual.

## Fundo branco

Fundo branco puro também não é obrigatório.

Escolher superfícies de acordo com contraste e hierarquia.

Não adicionar cinza apenas porque branco "parece simples demais".

Simplicidade não precisa ser disfarçada.

## Ícones

Ícones devem:

- melhorar reconhecimento;
- economizar espaço quando necessário;
- acompanhar ações conhecidas;
- manter linguagem visual consistente.

Não adicionar ícones a cada label.

Não usar ícone decorativo ao lado de todo heading.

Não substituir texto importante por ícone ambíguo.

### Biblioteca de ícones

Lucide, Material Symbols, Phosphor ou outra biblioteca não são proibidos.

Não escolher uma biblioteca apenas porque é o default habitual da IA.

Se uma biblioteca já existir no projeto e for adequada, reutilizá-la.

Evitar misturar várias famílias.

### Sparkles e estrelas

Não usar automaticamente ícones como:

- sparkle;
- star;
- magic wand;
- rocket;
- lightning;
- brain

para sinalizar funcionalidades comuns.

Especialmente evitar associar estrelas arbitrariamente a:

- IA;
- automação;
- destaque;
- premium;
- novidade.

## Emojis

Não utilizar emojis como substitutos de iconografia ou identidade visual em interfaces profissionais, salvo quando o contexto do produto exigir.

Evitar:

```text
Buscar leads 🚀
Exportar 📊
Lead quente 🔥
```

quando texto, ícone semântico ou linguagem visual existente comunicarem melhor.

## Badges e pills

Não transformar toda informação em pill.

Badges são apropriados para valores curtos como:

- estado;
- categoria;
- classificação;
- filtro ativo.

Evitar usar pills para:

- frases;
- subtítulos;
- decoração;
- slogans;
- chamadas promocionais sem função.

## Faixas coloridas

Evitar automaticamente barras coloridas na lateral de cards apenas para diferenciar visualmente conteúdo.

Se utilizada, a cor deve possuir significado estável e acessível.

Nunca depender exclusivamente dessa faixa para identificar estado.

## Checks

Não transformar toda lista informativa em:

```text
✓ recurso
✓ recurso
✓ recurso
✓ recurso
```

Use checkmark apenas quando estiver comunicando efetivamente:

- conclusão;
- presença;
- validação;
- comparação.

## Ícones + título + texto repetidos

Evitar grids compostos por vários blocos idênticos:

```text
[ícone]
Título
Descrição

[ícone]
Título
Descrição

[ícone]
Título
Descrição
```

quando o conteúdo não exige essa estrutura.

Variar arquitetura somente quando houver diferença real entre informações.

Não variar por decoração.

## Três cards em fila

Não utilizar automaticamente três cards porque é uma composição estatisticamente comum.

A quantidade de colunas deve depender de:

- quantidade de conteúdo;
- importância;
- largura;
- leitura;
- comportamento responsivo.

Pode haver:

- 1;
- 2;
- 3;
- 4;
- lista;
- tabela;
- painel;
- coluna única.

Escolher a estrutura correta, não a mais familiar.

## Dashboards

Não criar dashboard porque o sistema possui dados.

Dashboards só devem existir se o usuário precisar monitorar múltiplos indicadores simultaneamente.

Não inventar:

- gráficos;
- KPIs;
- cards numéricos;
- percentuais;
- tendências

quando esses dados não fazem parte da tarefa.

## Dados falsos

Não criar elementos como:

- testimonials falsos;
- usuários fictícios;
- logos de empresas fictícias;
- números inventados;
- métricas falsas;
- atividades recentes fictícias

para preencher espaço visual.

Durante desenvolvimento, fixtures devem estar claramente identificadas como dados de teste.

## Terminal fake

Não criar automaticamente uma janela de terminal decorativa para representar tecnologia.

Terminal só deve existir se:

- o produto realmente utiliza terminal;
- a demonstração precisa mostrar comandos reais.

Não usar código fake como decoração.

## Conteúdo

Evitar copy genérica típica de landing pages produzidas automaticamente.

Exemplos a evitar:

```text
Transforme a maneira como você trabalha.
Leve sua produtividade para o próximo nível.
Tudo o que você precisa em um só lugar.
Não é apenas X — é Y.
```

Preferir texto direto e específico ao produto.

## Travessões e estrutura textual

Não utilizar em dash ou construções estilísticas repetitivas apenas para dar tom editorial.

Pontuação deve seguir a linguagem natural do conteúdo.

Evitar padrões recorrentes artificiais como:

```text
Rápido — sem complicação.
Simples — mas poderoso.
Automação — do jeito certo.
```

## Microinterações

Interfaces não precisam estar constantemente em movimento.

Usar animação para:

- feedback de ação;
- mudança de estado;
- drag-and-drop;
- abertura e fechamento;
- progressão;
- transição que ajude orientação espacial.

Evitar animação apenas para demonstrar sofisticação.

### Hover

Não animar todo elemento ao passar o mouse.

Em elementos clicáveis, hover deve indicar interatividade de forma discreta.

Preferir mudanças como:

- fundo;
- borda;
- texto;
- elevação mínima.

Evitar automaticamente:

- scale exagerado;
- tilt;
- glow;
- deslocamentos grandes;
- animações longas.

### Duração

Para microinterações comuns, preferir durações curtas.

Evitar transições lentas que atrasem uma ferramenta operacional.

Respeitar `prefers-reduced-motion`.

## Skeleton loaders

Skeleton não é obrigatório.

Escolher entre:

- spinner;
- skeleton;
- placeholder;
- estado textual

de acordo com o contexto.

Usar skeleton quando ajudar a preservar estrutura e reduzir mudança visual durante carregamento previsível.

Não adicionar skeleton apenas porque é considerado moderno.

## Estados vazios

Não preencher estados vazios com ilustrações genéricas automaticamente.

Um bom estado vazio deve explicar:

1. o que aconteceu;
2. se é esperado;
3. qual ação pode ser tomada.

Exemplo:

```text
Nenhum lead encontrado neste raio.

Tente aumentar o raio ou selecionar outras categorias.
```

Isso normalmente é melhor que uma ilustração decorativa gigante.

## Loading e feedback

A interface deve distinguir claramente:

- carregando;
- sucesso;
- vazio;
- erro;
- ação indisponível.

Não criar feedback puramente estético.

O usuário deve entender o estado atual da aplicação.

## Erros

Mensagens de erro devem ser:

- claras;
- curtas;
- acionáveis quando possível.

Evitar:

```text
Oops!
Algo incrível deu errado.
```

Preferir:

```text
Não foi possível carregar os leads.
Tentar novamente
```

## Responsividade

Não transformar automaticamente a versão mobile em todos os componentes empilhados.

Reavaliar:

- prioridade;
- largura;
- ordem;
- controles;
- navegação;
- densidade.

Em aplicações desktop-first, preservar eficiência operacional sempre que possível.

## Densidade

Não confundir espaço vazio com qualidade.

Interfaces de produtividade podem possuir densidade moderada ou alta.

A densidade deve permitir:

- escaneabilidade;
- comparação;
- ação rápida.

Evitar tanto:

- telas excessivamente vazias;
- quanto blocos esmagados sem respiro.

## Navegação

Evitar navegação profunda quando poucas áreas principais resolvem o produto.

Não criar sidebar automaticamente.

Não criar topbar + sidebar + breadcrumbs simultaneamente sem necessidade.

A arquitetura de navegação deve refletir a quantidade real de áreas.

## Modais

Não usar modal para toda ação secundária.

Preferir, conforme contexto:

- expansão inline;
- painel lateral;
- nova rota;
- popover;
- edição no próprio conteúdo.

Modal é apropriado para tarefas que realmente interrompem o fluxo atual.

## Painéis laterais

Não criar drawer apenas porque parece moderno.

Usar quando for importante:

- manter contexto anterior visível;
- visualizar detalhe;
- editar entidade sem abandonar lista.

## Tabelas, listas e grids

Não evitar tabelas porque parecem "menos modernas".

Se o usuário precisa comparar várias entidades em múltiplos campos, tabela pode ser a melhor interface.

Escolher:

- tabela para comparação;
- lista para escaneamento sequencial;
- card para entidade independente;
- Kanban para mudança de estado;
- mapa para contexto espacial.

A estrutura deve refletir o modelo mental da tarefa.

## Identidade do produto

Uma interface não precisa ser extravagante para possuir personalidade.

Construir identidade principalmente através de:

- proporções;
- tipografia;
- ritmo;
- densidade;
- paleta;
- iconografia;
- linguagem;
- comportamento;
- consistência.

Não depender de efeitos especiais para produzir personalidade.

## Consistência

Reutilizar padrões depois que forem definidos.

Ações equivalentes devem:

- parecer equivalentes;
- possuir labels consistentes;
- ocupar posições previsíveis;
- reagir de maneira semelhante.

Não introduzir uma nova variação visual em cada tela apenas para evitar repetição.

Consistência intencional não é AI slop.

## Acessibilidade não é opcional

Design anti-slop nunca justifica:

- contraste insuficiente;
- texto pequeno demais;
- foco invisível;
- interação apenas por hover;
- botão sem nome acessível;
- depender somente de cor;
- animação impossível de reduzir.

Quando estética e acessibilidade conflitarem, acessibilidade vence.

## Revisão anti-slop obrigatória

Ao criar ou revisar uma tela visualmente relevante, verificar:

### Estrutura

- A estrutura nasceu da tarefa ou de um template conhecido?
- Existem containers que poderiam ser removidos?
- Há cards demais?
- Existe informação repetida?
- A principal ação é evidente?
- A tela possui densidade adequada?

### Tipografia

- Existem tamanhos demais?
- Headings são grandes sem necessidade?
- Há uppercase decorativo?
- Existem meta-labels artificiais?
- A leitura é rápida?

### Cores

- Há cores decorativas demais?
- O accent possui função?
- Estados usam semântica consistente?
- Informação depende apenas da cor?

### Superfícies

- Tudo possui radius?
- Tudo possui shadow?
- Existem glass panels gratuitos?
- Existem bordas demais?

### Iconografia

- Há ícones sem função?
- Há sparkles/estrelas decorativas?
- Ícones substituem texto importante?
- A família iconográfica é consistente?

### Movimento

- Animações ajudam compreensão?
- Existe hover exagerado?
- A interface funciona sem animações?
- `prefers-reduced-motion` é respeitado?

### Conteúdo

- Existe copy genérica?
- Existem dados fictícios?
- Há testimonials ou métricas inventados?
- O texto descreve o produto real?

### Produto

- Alguma decoração atrapalha a tarefa?
- Alguma decisão foi tomada apenas porque "fica moderno"?
- Algum padrão foi adicionado porque é comum em sites gerados por IA?

Se sim, revisar.

## Aplicações operacionais

Para aplicações internas, CRMs, ferramentas administrativas, sistemas de busca e produtividade:

Priorizar:

- velocidade de leitura;
- contexto;
- densidade moderada;
- ações previsíveis;
- feedback claro;
- comparação fácil;
- navegação curta;
- persistência de contexto.

Evitar transformar essas aplicações em showcase visual.

O usuário deve perceber primeiro a ferramenta, não o design.

## Regra de parada

Quando a interface estiver:

- clara;
- consistente;
- acessível;
- funcional;
- com hierarquia adequada;
- visualmente coerente;

não continuar adicionando detalhes apenas para "melhorar o design".

Saber parar faz parte do design.

## Regra final

Não tente fazer a interface parecer não gerada por IA através de extravagância.

Faça parecer projetada por uma pessoa através de decisões justificáveis.

A pergunta principal nunca é:

> "Isso parece premium?"

A pergunta principal é:

> "Esta é a melhor forma de apresentar esta função para este produto e este usuário?"
