lead de nome Petz Vila Velha
ao usar buscar informações, a pesquisa inteligente alega nao encontrar o instagram ou site proprio.
quando eu vou pesquisar manualmente encontro:
https://www.google.com/search?q=Petz+Vila+Velha&ie=UTF-8
site proprio como primeira opção de resultado: https://www.petz.com.br/loja/petz-vila-velha
dentro do site, tem a seguinte informação disponivel
Endereço
Rodovia do Sol, 256
Itapuã - Vila Velha/ ES - CEP: 29102-320
é o endereço que temos do nosso lead no banco de dados.

como  então nossa função alega nao ter encontrado nada usando o brave search?

um mero detalhe é que o nosso lead tem escrito Rod. do Sol
e o site Rodovia do Sol
mas isso NAO deve ser motivo para ignorar esse site ou descarta-lo, nossa função também tem que pensar e assimilar.

dito isso, faça funcionar

Não crie o código mais fácil, use que tem a maior qualidade e maior precisao
lembre-se que está em java 25, tem muita coisa nova ao seu dispor

use esse .md para documentar suas alterações no algoritmo de buscar informaçoes
documente as mudanças[places](../src/main/java/dev/jlm/leadshunter/integracao/places)
o motivo de suas escolhas
o que funcionou melhor
tentativa de acerto e erro
nao apague nada dessa linha pra cima.

## Refinamento implementado em 13/09/2026 — identificação de filiais

### Diagnóstico reproduzido

O problema foi reproduzido com o lead **Petz Vila Velha** do banco, em uma consulta somente de leitura. Antes da alteração, as três chamadas previstas no fluxo terminaram com Instagram e site ausentes. Porém, a API do Brave retornou `https://www.petz.com.br/loja/petz-vila-velha` como primeiro resultado da consulta de site, com título, endereço e telefone suficientes para confirmar a unidade.

A [página oficial da filial](https://www.petz.com.br/loja/petz-vila-velha) e o trecho devolvido pelo Brave apresentam **Rodovia do Sol, 256**, Vila Velha/ES, e **(027) 3022-5308**. No cadastro, o endereço formatado contém `Rod. do Sol`, mas o componente estruturado `logradouro` já contém `Rodovia do Sol`. Portanto, a abreviação merecia correção geral, mas não era a causa principal desse caso.

As causas identificadas no código foram:

1. O classificador exigia todos os termos distintivos do nome no domínio. Para `Petz Vila Velha`, exigia `petz`, `vila` e `velha`, descartando `petz.com.br`.
2. A deduplicação reduzia todos os caminhos ao mesmo host. A página `nossas-lojas`, que cita unidades em várias cidades/UFs, contaminava a avaliação da página específica da filial com conflitos geográficos de outras unidades.
3. O canonicalizador eliminava o caminho do site. Mesmo uma página de filial aceita seria substituída pela página inicial da rede, perdendo o destino que comprovou a correspondência.
4. A comparação de endereço procurava palavras e número espalhados pelo texto. Não reconhecia equivalências como `Rod.`/`Rodovia` e exigia termos suficientes mesmo em logradouros curtos, como `Av. Brasil`.
5. O reconhecimento de telefone não tratava o zero de tronco em `(027)`, deixando de aproveitar um identificador exato disponível no resultado.

### Alterações no algoritmo e motivos

**Marca e unidade:** `ClassificadorUrlService` retira o município completo apenas dos termos exigidos no domínio do site, quando resta uma marca distintiva. O nome da filial continua sendo comparado ao conteúdo e exige confirmação independente. Não remove palavras isoladas do município nem flexibiliza domínios de nomes que ficam genéricos. Não há regra específica para a Petz ou domínio cadastrado manualmente.

**Endereço por ocorrência:** o novo `AnalisadorEnderecoPesquisa` normaliza acentos e tipos de logradouro com equivalências delimitadas: `R.`/`Rua`, `Av.`/`Avenida`, `Rod.`/`Rodovia`, `Estr.`/`Estrada`, `Tv.`/`Travessa`, `Al.`/`Alameda` e `Pç.`/`Praça`. O número precisa acompanhar o logradouro na mesma ocorrência e no mesmo trecho. Números promocionais, seguidores, fragmentos de outro trecho e números com complemento diferente não comprovam endereço. Endereços explicitamente divergentes vetam o candidato. Sem componentes estruturados, a rotina tenta extrair logradouro e número do endereço formatado.

**Evidências independentes:** município e endereço confirmado contribuem separadamente para a pontuação, favorecendo a página que contém o endereço da unidade. Telefone com `(027)` passa a corresponder ao número nacional com DDD `27`. Continuam os vetos de município/UF, CNPJ e DDD, além da margem de confiança entre sites distintos. A correção não reduz o limiar mínimo de aceitação.

**Página antes do domínio:** resultados equivalentes da mesma página são consolidados preservando qualquer conflito. Depois, entre as páginas elegíveis de um mesmo site, é escolhida a de maior pontuação. Assim, informações de outra filial ou de uma lista nacional não invalidam a página local; duplicatas contraditórias dessa página continuam impedindo sua aceitação. A ordenação possui desempate determinístico.

**URL útil:** `UrlCandidatoCanonicalizer` mantém o caminho da página confirmada, remove query/fragmento e preserva escapes do caminho. Sites continuam limitados a HTTP/HTTPS, com os bloqueios existentes de diretórios, redes sociais e destinos inseguros. O formatador passa a conservar esse caminho ao gravar e reler o bloco automático. Caminho ou subdomínio com o nome da empresa não legitima um domínio-base alheio.

Foram usadas estruturas imutáveis e um `record` para a evidência de endereço, expressões delimitadas e as bibliotecas já disponíveis no Java 25/projeto. A escolha foi por regras verificáveis de equivalência e associação de dados. Não foram adicionados modelo de IA, dependências, consultas extras, navegação em sites candidatos ou scraping de Instagram.

### Tentativas, erros e o que funcionou melhor

| Etapa executada | Resultado observado | Consequência |
| --- | --- | --- |
| Diagnóstico inicial com Brave e banco real | A página correta estava nos resultados, mas o algoritmo retornou ausência. | Corrigir a classificação e a consolidação, mantendo as consultas existentes. |
| Regressão antes da correção | Dos 31 casos inicialmente escritos, 15 falharam, incluindo a captura real da Petz. | As falhas demonstraram os comportamentos que precisavam mudar. |
| Primeira correção conjunta | Os 31 casos novos passaram; dois testes antigos ainda esperavam que `/cardapio` fosse removido. | Atualizar essas duas expectativas para o comportamento solicitado de preservar a página. |
| Revisão de precisão | Foram incluídos sufixo alfanumérico do número, endereço divergente na mesma cidade, nome parcial da filial, escapes de URL e número após pontuação de outra frase. | Limitar a equivalência ao tipo de logradouro e manter o vínculo entre rua e número, sem juntar frases/trechos. |
| Replay das respostas anteriores | Sete leads de Castelo continuaram sem confirmação; a Petz passou a retornar somente o site correto. | As correções não reintroduziram as associações anteriormente rejeitadas nessa amostra. |
| Nova consulta real após a correção | A Petz retornou `https://www.petz.com.br/loja/petz-vila-velha`; Instagram não confirmado. | Caso relatado comprovado também com nova resposta da API. |

O resultado melhor veio da combinação de marca/unidade, evidência de endereço e consolidação por página. Aumentar o número de consultas não era necessário: a informação já estava disponível na resposta inicial do Brave.

### Validação e limites

- **117 testes direcionados passaram**, incluindo 36 casos de filiais/endereço e o replay real completo pelo serviço e formatador.
- **Suíte final `./mvnw test`: 371 testes, 365 aprovados, seis opt-in ignorados, zero falhas/erros.** A execução inicial no sandbox falhou por bloqueio dos recursos de MySQL/Mockito; a execução com acesso autorizado passou. `./mvnw -DskipTests package` gerou o JAR após a suíte.
- **Seis chamadas reais ao Brave:** três na reprodução anterior e três na confirmação posterior. Não houve escrita no banco de aplicação nem chamada à Google Places.
- A captura inicial foi versionada em `src/test/resources/pesquisa/brave-petz-vila-velha.json`, sem credenciais e com `googlePlaceId` substituído por identificador de fixture. O teste automático não precisa de rede ou banco.
- O replay adicional de oito leads usou respostas temporárias já existentes; não consumiu novas chamadas. Seu resultado foi um site (Petz) e nenhum Instagram confirmado.
- O Instagram da unidade não foi comprovado pelos resultados coletados. Isso não significa que o perfil não exista.
- A precisão/cobertura geral exige uma amostra rotulada maior; o caso Petz não foi usado para declarar o aceite amplo da INFO-01.7.
- Blocos anteriores não foram reescritos em lote. Uma nova execução de **Buscar informações** reavalia leads com ausência ou resultado parcial e pode substituir a ausência pelo site confirmado, preservando o texto comercial. Leads já com os dois links válidos continuam sendo ignorados.

### Como reproduzir

Regressão offline, com a captura real versionada:

```bash
./mvnw -Dtest=PesquisaFiliaisPrecisaoTest test
```

Diagnóstico real opt-in, somente de leitura, com as credenciais locais configuradas (até três chamadas Brave):

```bash
./mvnw -Dtest=PesquisaBraveLeadsReaisLiveTest -DpesquisaBraveLeadsLive=true '-DpesquisaBraveNome=Petz Vila Velha' test
```




Função precisa de mais refinamento
lead Supermercado Michel
pesquisa inteligente alegar nao encontrar nada 
minha pesquisa manual
https://www.google.com/search?q=Supermercado+Michel&ie=UTF-8
https://www.instagram.com/centraldecomprasmichel/
neste instagram é possivel encontrar o exato numero de telefone registrado em nosso banco de dados de lead.
refine ainda mais a pesquisa inteligente, esse instagram devia ter sido encontrado.
nao trate apenas esse caso, ele serve só de exemplo o ideal é ser expansivo

## Refinamento implementado em 14/09/2026 — nomes comerciais alternativos e confirmação de perfis

### Diagnóstico do Supermercado Michel

O caso foi reproduzido com o lead de Castelo/ES, em leitura do banco sem atualizar observações. O perfil correto, `https://www.instagram.com/centraldecomprasmichel`, já aparecia na terceira consulta, com o título **Central de Compras Michel**, mas o trecho do Instagram não apresentava o telefone. Havia dois obstáculos: exigir o nome comercial completo descartava essa variação; e a resposta inicial não trazia confirmação independente suficiente. O homônimo `@supermercadomichel` continuou recusado por apresentar DDD 41, incompatível com o DDD 28 do lead.

A consulta pelo usuário descoberto e telefone, `centraldecomprasmichel 28 3542-1440`, encontrou uma referência pública na [página de lojas da Central de Compras](https://centraldecompras.com.br/lojas). O trecho indexado associa **Avenida Ministro Araripe, 288, Castelo/ES**, **(28) 3542-1440** e **Instagram: @centraldecomprasmichel** no mesmo bloco da unidade. Essa foi a evidência utilizada. Não foi necessário acessar ou extrair a biografia do Instagram.

### Alterações no algoritmo e motivos

**Nomes comerciais alternativos:** `ClassificadorUrlService` admite Instagram com nome diferente quando há marca distintiva compartilhada e telefone, CNPJ ou Place ID exato no material público. A regra não contém nomes ou domínios específicos de Michel. Também foi testada com Drogaria/Farma Aurora e Padaria/Empório Girassol. Semelhança do nome ou localização isolada não basta para essa alternativa; permanecem os 70 pontos mínimos, a margem de 15 pontos e os vetos de endereço, município/UF, CNPJ e DDD.

**Confirmação direcionada e limitada:** `PesquisaWebInternaService` mantém as duas consultas iniciais e a terceira sem município quando necessária. Persistindo a ausência de Instagram, com telefone válido, escolhe até dois perfis já descobertos, relacionados ao nome e sem conflito, para pesquisar pelo usuário e telefone. A lista é deduplicada e fixada antes das confirmações; novos perfis encontrados não provocam outra rodada de consultas. As duas respostas são avaliadas em conjunto, evitando aceitar o primeiro resultado e ocultar um concorrente igualmente plausível. O máximo passou de três para **cinco consultas por lead**; leads sem perfis a confirmar não consomem essas consultas extras.

**Consulta tipada:** o novo `ConfirmacaoPerfilInstagram` limita usuário e telefone a formatos válidos e preserva o nome original do lead no pedido interno. `BravePesquisaApiClient` e o cliente legado montam essa consulta ampla sem restringir a busca ao domínio Instagram, permitindo encontrar referências em sites comerciais. Endpoints dos provedores, limites de resposta e tratamento de falhas permanecem os existentes. Os termos enviados ao buscador nunca são usados como comprovação da identidade.

**Referências públicas com contexto:** uma resposta de site pode corroborar um perfil já descoberto quando o mesmo trecho/bloco reúne telefone exato, endereço com número e referência explícita a um único Instagram. São aceitos o rótulo `Instagram: @usuario` e links válidos de perfil. Não são unidos trechos separados por quebras de linha, reticências ou separadores de unidades. Endereço conflitante, telefone diferente, múltiplos perfis, fonte bloqueada ou simples menção a `@usuario` sem rótulo não confirmam o vínculo. Os resultados originais do perfil continuam na análise, de modo que a referência externa não apaga um conflito público anterior.

**Preservação do comportamento:** a fonte da referência não é automaticamente aceita como site próprio. Falha técnica na confirmação não vira ausência conclusiva nem atualiza observações. O formatador existente preserva o texto manual e substitui a ausência anterior quando chega um resultado confirmado. Não foram adicionados frontend, schema, dependências, regras por estabelecimento, chamadas à Google Places ou scraping de Instagram.

### Tentativas e resultados

| Etapa executada | Chamadas Brave | Resultado observado |
| --- | --- | --- |
| Reprodução com as três consultas anteriores | 3 | Perfil correto apareceu, mas faltava evidência para aceitá-lo. |
| Consulta exploratória pelo usuário e `instagram` | 1 | Predominaram trechos de login e referências sem confirmação suficiente. |
| Consulta exploratória pelo usuário e telefone | 1 | A página indexada da rede trouxe endereço, telefone e perfil no mesmo bloco. |
| Validação real do fluxo implementado, com asserção da URL esperada | 5 | Confirmou `https://www.instagram.com/centraldecomprasmichel`; nenhum site foi confirmado. |

Foram **dez chamadas reais nesta rodada**, somente de leitura, sem escrita no banco da aplicação. O melhor resultado veio de buscar evidência sobre um candidato já encontrado e relacioná-la à unidade correta. Repetir a busca apenas pelo nome ou aceitar o handle mais parecido não resolveria a falta de confirmação.

### Validação e limites

- **154 testes direcionados passaram**, incluindo 41 casos de identidade alternativa, referências públicas e planejamento das confirmações. Os testes também cobrem ambiguidade, limites, conflitos, telefone inválido, falhas técnicas e montagem das consultas nos clientes.
- **`./mvnw test`: 418 testes, 412 aprovados, seis opt-in ignorados, zero falhas/erros.**
- **`./mvnw -DskipTests package`: passou**, gerando o JAR após a suíte completa.
- O diagnóstico real passou com asserção explícita de `https://www.instagram.com/centraldecomprasmichel`, em cinco consultas. A captura final foi versionada em `src/test/resources/pesquisa/brave-michel-confirmacao.json`, sem credenciais e com `googlePlaceId` substituído por identificador de fixture.
- O replay offline demonstra que as três respostas iniciais ainda são insuficientes e que o fluxo completo confirma o perfil. Também verifica a substituição do bloco de ausência e a preservação do texto manual.
- Não houve execução de frontend nesta rodada, pois a mudança ficou no backend. O aceite amplo da INFO-01.7 continua parcial: os casos Petz e Michel não permitem estimar precisão/cobertura geral sem uma amostra rotulada maior.
- Uma nova execução de **Buscar informações** reavalia leads com ausência ou resultado parcial. Blocos já completos continuam sendo ignorados; não houve reescrita retroativa no banco.

### Como reproduzir

Regressão offline, incluindo a captura real e as regras gerais:

```bash
./mvnw -Dtest=PesquisaIdentidadeAlternativaTest test
```

Diagnóstico real opt-in, somente de leitura, com credenciais locais configuradas e até cinco chamadas para o lead:

```bash
./mvnw -Dtest=PesquisaBraveLeadsReaisLiveTest -DpesquisaBraveLeadsLive=true '-DpesquisaBraveNome=Supermercado Michel' -DpesquisaBraveInstagramEsperado=https://www.instagram.com/centraldecomprasmichel test
```

O replay pelo diagnóstico live exige uma captura completa das consultas pedidas pelo fluxo atual. Capturas antigas sem as confirmações adicionais falham explicitamente, sem acessar a rede para completá-las.


A função de enriquecimento AINDA precisa ser aprimorada.

IMPORTANTE: NÃO refaça a função nem substitua a estratégia atual. Ela já passou por vários refinamentos. Analise a implementação existente, descubra por que ela falhou neste caso e faça um aprimoramento GENERALIZÁVEL, preservando o que já funciona.

Caso real:

Lead: Hortifruti Castelo

O enriquecimento retornou que não encontrou informações.

Porém, pesquisei manualmente apenas:

Hortifruti Castelo

E o PRIMEIRO resultado do Google foi:

https://www.instagram.com/hortfrutcastelo/

Eu CONFIRMEI que é o Instagram correto, não apenas pela semelhança do nome:

o telefone do Instagram é EXATAMENTE o mesmo cadastrado no nosso banco para esse lead;
parte do endereço exibido também corresponde ao endereço que temos.

Ou seja, era um resultado fácil de encontrar e com evidências fortes de identidade, mas nosso enriquecimento não o encontrou.

Investigue o fluxo ATUAL e descubra onde esse candidato está sendo perdido: query enviada à Brave, resultados retornados, quantidade analisada, filtros, validação, reconhecimento de Instagram, comparação de nome/username, telefone, endereço, snippets etc.

Não presuma a causa antes de analisar o código. Descubra se o problema está na descoberta ou se a Brave retorna o candidato e nossa própria lógica o descarta.

Hortifruti Castelo é apenas um caso de reprodução. NÃO crie hardcode, alias, exceção ou ajuste específico para fazê-lo funcionar.

Corrija a limitação geral que causou essa falha. Pequenas diferenças como Hortifruti Castelo → hortfrutcastelo não deveriam eliminar um candidato quando existem evidências muito mais fortes, principalmente TELEFONE IDÊNTICO + ENDEREÇO COMPATÍVEL.

Faça o menor refinamento necessário sobre a implementação existente e ajuste os testes para cobrir a causa geral encontrada.

Antes de implementar, identifique brevemente qual foi a causa concreta da falha.

## Refinamento de 15/09/2026 — telefone, username e endereço parcial

### Causa concreta identificada antes da alteração

A investigação reproduziu o fluxo atual com o lead **Hortifruti Castelo**, sem escrever na base. A primeira consulta foi `Hortifruti Castelo Castelo ES instagram`: a Brave devolveu **dez resultados**, com `https://www.instagram.com/hortfrutcastelo/` na **primeira posição**. O mesmo perfil apareceu na consulta de site, na quarta posição, e na consulta de Instagram sem município, novamente em primeiro. A URL foi reconhecida como perfil válido e o título continha o nome completo do lead. Portanto, não havia falha de descoberta, limite de resultados ou rejeição da URL por ser Instagram.

O resultado já convertido apresentava `028 3542-2436` e `Av. Nossa Senhora da Penha, 559 - Castelo | ES`. O cadastro traz `(28) 99935-3480`, `Avenida Nossa Senhora da Penha`, número `557`, Castelo/ES. A avaliação teve 85 pontos, mas foi vetada pela diferença no número do imóvel. Esse veto também impedia selecionar o perfil para uma consulta adicional de confirmação. O handle abreviado exigia identificador forte; o telefone cadastrado não estava no trecho retornado.

Foi isolada uma segunda limitação no código: o reconhecedor aceitava `(028)` e `(28)`, mas não `028` sem parênteses. Mesmo substituindo o conteúdo por um cenário controlado com telefone e endereço exatos, o zero de tronco sem parênteses fazia perder o sinal de telefone. A normalização já sabia remover esse zero, mas o padrão que extraía o número não o deixava chegar até ela.

### Esclarecimento do usuário e evidência da fonte

Durante a investigação, o usuário confirmou que a página atual do Instagram mostra **`028 999353480`**, equivalente ao celular do cadastro, e a mesma avenida em Castelo/ES. Depois, uma leitura diagnóstica pontual do HTML público do próprio perfil confirmou os mesmos dados na meta description: `028 999353480` e `Av. Nossa Senhora da Penha, 559 - Castelo | ES`. Essa leitura serviu somente para verificar o caso; não foi incorporada à aplicação como scraping de Instagram.

As consultas diagnósticas `hortfrutcastelo 28 99935-3480` e `hortfrutcastelo 028 999353480` não forneceram esse celular em evidência do perfil. A segunda voltou a retornar o perfil correto, porém com o telefone fixo anterior. Uma captura adicional do **JSON bruto**, antes do parser, confirmou: HTTP 200, dez resultados, perfil correto na primeira posição, descrição com `028 3542-2436`, apenas um `extra_snippet` com texto genérico e nenhuma ocorrência do celular cadastrado nos campos desse resultado. Portanto, não foi o limite de 500 caracteres ou de cinco trechos extras que descartou o celular nessa captura. A resposta da fonte difere da página atual descrita pelo usuário.

### Refinamento implementado, preservando a estratégia

- **Telefone:** `ClassificadorUrlService` passa a extrair zero de tronco sem parênteses, preservando a comparação exata do número nacional. `028 999353480`, `028 99935-3480` e `(28) 99935-3480` correspondem ao mesmo número. Sequências maiores, dígitos espalhados pelo texto e números diferentes continuam sem comprovar identidade. DDD divergente também é reconhecido nesse formato e continua vetando o candidato.
- **Endereço:** `AnalisadorEnderecoPesquisa` separa conflito de logradouro e conflito de número. Para Instagram, diferença apenas no número deixa de ser veto definitivo. O perfil pode ser aceito por **telefone exato + mesmo logradouro + município** no mesmo trecho ou, quando a fonte está desatualizada, por nome exato, username quase idêntico, mesmo logradouro/município e número vizinho com diferença máxima de dois. Outra rua ou uma diferença maior continuam recusadas.
- **Username e localização:** uma distância de edição limitada a dois caracteres é aplicada somente ao username do Instagram e somente junto das demais evidências anteriores. Assim, `hortifruti castelo` pode corresponder a `hortfrutcastelo` sem criar alias. Município presente apenas no nome/título de outro perfil deixa de contar como localização; o resumo precisa indicar município/UF, `em município`, `centro de município` ou conter apenas o município. Isso retirou a falsa ambiguidade com perfis da Bahia e com `Castelo Maçã`.
- **Consolidação:** a confirmação posterior do mesmo perfil pode resolver a divergência de número sem apagar o resultado anterior. Outra rua, município/UF conflitante, CNPJ e DDD permanecem vetos. Trechos separados não são combinados para fabricar a confirmação; dois perfis igualmente plausíveis continuam produzindo ausência.
- **Escopo preservado:** sites próprios mantêm o veto de número divergente. Não houve alias, hardcode de negócio, nova query, novo provedor ou aumento do máximo de cinco consultas por lead. A aproximação fica restrita ao username e ao conjunto forte de evidências descrito acima. Também não houve frontend, dependência, migration, acesso automático à página do Instagram ou escrita no banco da aplicação.

### Tentativas e resultados

| Etapa | Resultado |
| --- | --- |
| Reprodução anterior, cinco chamadas Brave | Instagram ausente apesar de aparecer em primeiro; descartado por número divergente e sem telefone exato na resposta. A execução também selecionou um site em consulta de outro perfil, sem validação manual de pertencimento; isso não foi tratado como acerto. |
| Consulta exploratória pelo perfil e telefone formatado | Uma chamada; sem evidência do celular cadastrado. |
| Consulta pelo formato visual informado pelo usuário | Uma chamada; perfil correto voltou com o telefone fixo anterior. |
| Inspeção do JSON bruto | Uma chamada; confirmou ausência do celular nesse resultado antes do parser. |
| Regressão do reconhecedor antes da correção | Seis falhas reproduziram a perda do telefone e a falta de veto de DDD nesse formato. |
| Revalidação com telefone exato e mesma avenida/município | Cenários controlados passaram, inclusive com número de imóvel diferente e username abreviado. |
| Primeira tentativa de tolerância | O perfil correto passou isoladamente, mas dois concorrentes também usavam “Castelo” do nome como falsa localização; o conjunto completo continuou ambíguo. |
| Separação entre identidade e localização | Perfis concorrentes sem localização real foram recusados; a captura real passou a selecionar somente `@hortfrutcastelo`. |
| Validação real final | Duas chamadas Brave; confirmou `https://www.instagram.com/hortfrutcastelo` e nenhum site. |

Foram **quinze chamadas Brave nesta investigação**, todas de leitura: oito no diagnóstico inicial, cinco na primeira validação ainda ambígua e duas na confirmação final. Replays não consumiram rede. Não houve escrita no banco nem chamada à Google Places.

### Validação final

- **154 testes direcionados passaram**, incluindo as regressões de Petz e Michel e os novos casos de telefone, username, localização, confirmação posterior, endereço parcial, conflitos e ambiguidade.
- **`./mvnw test`: 446 testes, 440 aprovados, seis opt-in ignorados, zero falhas/erros.**
- **`./mvnw -DskipTests package`: passou** após a suíte.
- O cenário controlado com `028 999353480` valida o reconhecimento do telefone atual, mas a confirmação real não dependeu de fingir que esse celular veio da Brave. A resposta antiga foi aceita pelo conjunto verificável de nome exato, username quase idêntico, mesma avenida/município e número vizinho.
- **A confirmação automática real passou:** `BRAVE_LEAD nome=Hortifruti Castelo; instagram=https://www.instagram.com/hortfrutcastelo; site=ausente`, em duas chamadas.
- As capturas reais permanecem em temporários locais. Nenhuma resposta foi alterada para inserir o telefone atual, e os termos da consulta continuam sem valer como prova.

Regressão offline:

```bash
./mvnw -Dtest=ClassificadorUrlPrecisaoTest,PesquisaFiliaisPrecisaoTest,PesquisaIdentidadeAlternativaTest test
```

## Refinamento de 15/09/2026 — handle de ramo (marca + praça)

### Causa concreta identificada antes da alteração

O lead **Multishow Supermercados Castelo - Volta Redonda** (id 347, Castelo/ES, telefone `5528999146676`) foi reproduzido com a API real, em leitura do banco e sem escrever observações. A Brave devolveu `https://www.instagram.com/multishowcastelo/` já na **primeira consulta**, com o título `Multishow Castelo (@multishowcastelo)`. O candidato era descartado pela própria lógica, em dois pontos gerais:

1. **Categoria no plural não era genérica.** `tokensDistintivos` removia apenas as formas no singular da lista (`supermercado`), então `supermercados` permanecia como termo distintivo obrigatório. O mesmo valia para `farmacias`, `drogarias`, `acougues`, `restaurantes`, `armazens` etc.
2. **A identidade exigia o nome inteiro.** O nome gera os termos `multishow`, `supermercados`, `castelo`, `volta`, `redonda`. O handle `multishowcastelo` contém somente `multishow` + `castelo`; `volta` e `redonda` são complemento de praça e nunca aparecem no handle. A exigência de todos os termos impedia tanto a aceitação quanto a seleção do perfil para as consultas de confirmação.

O snippet do Brave não expõe a bio do perfil (o telefone aparece lá como `wa.me/5528999146676`). Portanto não havia como o fluxo confirmar o perfil por telefone a partir dos resultados; a descoberta precisava reconhecer o padrão de handle de filial.

### Alterações no algoritmo e motivos

**Termos genéricos plurais:** `ClassificadorUrlService.tokensDistintivos` passa a considerar a flexão de número de `TERMOS_GENERICOS` (`supermercados` → `supermercado`, `farmacias` → `farmacia`, `armazens` → `armazem`). É uma correção geral da normalização de categoria: o singular já era genérico e o plural não podia carregar identidade sozinho.

**Handle de ramo:** o novo `identificadorDeRamo` aceita um perfil do Instagram quando o handle contém uma **sequência contígua de pelo menos dois termos distintivos** do nome, exigindo simultaneamente um termo do município (a praça) e um termo que não venha dele (a marca). Assim, `multishow` + `castelo` confirma o ramo, enquanto handles que são apenas a rede (`multishowsupermercados`), outra praça (`multishowvitoria`) ou apenas a cidade (`mercadocastelo`) continuam recusados. Leads cujo nome é só a própria praça, como `Padaria São José` em São José, não têm marca fora do município e não são aceitos por essa via. A regra vale somente para Instagram; domínios de site continuam com a lógica anterior.

**Confirmação sem consulta extra:** quando o handle de ramo é aceito, o perfil já entra como elegível na primeira análise. Isso eliminou a terceira consulta (Instagram sem município) na reprodução real e preservou o máximo de cinco consultas por lead. Nenhuma consulta nova, provedor, dependência ou estratégia de busca foi adicionada.

**Escopo preservado:** continuam o mínimo de 70 pontos, a margem de 15 pontos entre candidatos, os vetos de endereço/logradouro, município/UF, CNPJ e DDD, e o limite de confirmações. A nova pontuação apenas registra o identificador de ramo como evidência forte.

### Tentativas e resultados

| Etapa executada | Resultado observado | Consequência |
| --- | --- | --- |
| Reprodução real, três consultas | `@multishowcastelo` na 1ª posição, sem telefone no snippet; resultado ausente | Corrigir a normalização de gênero/número e reconhecer o handle de ramo. |
| Primeira versão do `identificadorDeRamo` | Exigia dois termos contíguos quaisquer com um fora do município | Aceitava nomes genéricos como `Padaria São José` em município diferente. |
| Revisão da regra | Passou a exigir, no mesmo trecho, um termo do município e um da marca | Falsos positivos de praça pura recusados; `multishowcastelo` mantido. |
| Casos de recusa | `multishowsupermercados`, `multishowvitoria`, `castelosuper`, `mercadocastelo`, `saojose` | Todos ausentes, preservando a precisão. |
| Replay real | Aceito na primeira análise, em **duas** chamadas (antes três) | Sem consumo extra de cota. |

### Validação e limites

- **13 testes novos** em `PesquisaRamoIdentificadorTest`, incluindo o replay real da captura e os casos de recusa por rede, outra praça, praça pura, DDD divergente e site próprio.
- **`./mvnw test`: 459 testes, 453 aprovados, seis opt-in ignorados, zero falhas/erros.**
- **`./mvnw -DskipTests package`: passou**, gerando o JAR.
- **Validação real:** `BRAVE_LEAD nome=Multishow Supermercados Castelo - Volta Redonda; instagram=https://www.instagram.com/multishowcastelo; site=ausente`, em duas chamadas. A captura foi versionada em `src/test/resources/pesquisa/brave-multishow-castelo.json`, sem credenciais e com `googlePlaceId` substituído por identificador de fixture.
- O telefone da bio não é exposto pelo snippet do Brave; a confirmação não depende dele e não há acesso ao perfil. A leitura do HTML público informada pelo usuário não foi incorporada à aplicação.
- O endereço do cadastro (`R. Antônio Rangel, 110 - Niterói`) diverge parcialmente da bio (`Bairro Volta Redonda`), o que não interfere na regra, que usa a praça/município.
- O aceite amplo da INFO-01.7 continua parcial: falta uma amostra rotulada maior para medir precisão/cobertura gerais.

### Como reproduzir

```bash
./mvnw -Dtest=PesquisaRamoIdentificadorTest test
```

Diagnóstico real opt-in, somente de leitura, com até duas chamadas Brave:

```bash
./mvnw -Dtest=PesquisaBraveLeadsReaisLiveTest -DpesquisaBraveLeadsLive=true '-DpesquisaBraveNome=Multishow Supermercados Castelo - Volta Redonda' -DpesquisaBraveInstagramEsperado=https://www.instagram.com/multishowcastelo test
```

## Refinamento de 15/09/2026 — abertura da URL candidata e teto por lead

### Motivação

A validação dependia apenas dos metadados do buscador. Quando o telefone ou o endereço aparecem na página (por exemplo, na bio do Instagram) mas não no trecho indexado, o candidato correto ficava sem confirmação. Passou a ser permitido abrir a própria URL candidata — site próprio ou perfil público do Instagram — somente para verificar se telefone, endereço ou CNPJ do lead estão lá. Abrir o candidato não é scraping de motores de busca; a fonte da pesquisa continua sendo a API do Brave.

### Teto por lead

Para controlar custo e abuso, cada lead tem direito a **até 3 consultas ao Brave** (duas de descoberta e uma de fallback ou confirmação) e **até 3 aberturas de página**. As páginas são escolhidas entre candidatos já relacionados pelo nome/handle e ainda não confirmados, com prioridade para perfis de Instagram, onde a bio costuma trazer o telefone. A lista é deduplicada e não encadeia novas descobertas.

### Alterações no algoritmo e motivos

- **`LeitorPaginaCandidata`**: faz `GET` com timeout e limite de bytes, sem seguir redirecionamentos. O destino é restrito a HTTP/HTTPS público: bloqueia `localhost`, sufixos internos, portas não padrão, userinfo e endereços any/loopback/link-local/site-local/multicast. Só aceita `text/html`, `text/plain` e tipos JSON/XML correlatos. Extrai meta descrição, JSON estruturado pequeno (`application/json` e `application/ld+json`) e o texto do corpo, dentro de um teto de tamanho. Falha, timeout, bloqueio ou resposta de erro retornam vazio e **nunca** viram ausência conclusiva nem atualizam observações.
- **`PesquisaWebInternaService`**: mantém as duas consultas iniciais, limita a terceira (sem município) e permite no máximo uma confirmação direcionada se ainda sobrar orçamento de busca. Entre uma etapa e outra, abre os candidatos pendentes e reavalia o conjunto completo, de modo que a página passa a ser apenas mais uma evidência para o mesmo classificador.
- **`ClassificadorUrlService.candidatosParaValidar`**: reaproveita a avaliação existente para escolher candidatos com relação de nome, sem conflito e ainda não elegíveis, priorizando Instagram e ordenando por pontuação. Nenhuma regra de identidade, limiar ou veto foi afrouxada.
- **Escopo preservado**: sem novas dependências de runtime (usa jsoup e `HttpClient` já presentes), sem alteração de schema, frontend ou Google Places. O scraping de motores de busca continua desativado.

### Tentativas e resultados

| Etapa | Resultado observado | Consequência |
| --- | --- | --- |
| Extração apenas de meta e corpo | O `og:description` do Instagram não trazia o telefone; o dado estava no JSON embutido | Incluir JSON estruturado pequeno na extração. |
| Scripts JSON gigantes | Despejar todos os scripts estourava o teto antes do bloco útil | Ignorar scripts acima de 40 KB e limitar o texto total. |
| Ordenação inicial dos candidatos | Diretórios apareciam antes do perfil e consumiam as três aberturas | Priorizar Instagram antes de site na validação. |
| Validação real | `curl`/leitor encontraram `5528999146676` na página do `@multishowcastelo` | Feature comprovada ponta a ponta. |

### Validação e limites

- **`./mvnw test`: 480 testes, 473 aprovados, sete opt-in ignorados, zero falhas/erros.**
- **`./mvnw -DskipTests package`: passou.**
- **`LeitorPaginaCandidataLiveTest` (opt-in):** abriu o perfil real e confirmou o telefone exato na página.
- **Diagnóstico Brave real:** Multishow Castelo confirmado em duas consultas, sem site.
- Nenhum dado foi escrito no banco da aplicação durante a investigação; a leitura do Instagram é feita somente para validar o lead e não é persistida como scraping.
- Bater em muro de login/consentimento do Instagram é tratado como ausência de evidência adicional; não há tentativa de evasão.
- O aceite amplo da INFO-01.7 continua parcial.

### Como reproduzir

```bash
./mvnw -Dtest=LeitorPaginaCandidataTest,PesquisaWebInternaServiceTest test
./mvnw -Dtest=LeitorPaginaCandidataLiveTest -DpesquisaPaginaLive=true test
```
