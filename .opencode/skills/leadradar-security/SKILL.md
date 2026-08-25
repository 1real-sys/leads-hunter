# SECURITY.md — Leads Hunter

Este documento define os requisitos e diretrizes de segurança do projeto **Leads Hunter**.

O objetivo é orientar futuras implementações de segurança no backend, frontend, infraestrutura e banco de dados, sem introduzir complexidade desnecessária antes do momento adequado no roadmap.

> Este documento é uma referência de segurança. A implementação deve respeitar também o `AGENTS.md`, as skills do projeto e o estado atual definido em `fluxo.md`.

---

# 1. Princípios gerais

Toda implementação deve seguir estes princípios:

- aplicar **menor privilégio**;
- negar acesso por padrão;
- validar toda entrada externa;
- nunca confiar em dados vindos do frontend;
- não expor detalhes internos em erros;
- não armazenar segredos no código;
- não depender apenas de validações client-side;
- registrar eventos relevantes de segurança;
- manter dependências atualizadas;
- limitar superfície de ataque;
- separar autenticação de autorização;
- proteger dados sensíveis em trânsito e em repouso;
- aplicar defesa em profundidade;
- evitar mecanismos de segurança próprios quando existirem soluções consolidadas.

---

# 2. Proteção de rotas

As rotas da aplicação devem ser protegidas de acordo com sua finalidade.

Quando autenticação estiver implementada:

- toda rota deve ser considerada protegida por padrão;
- liberar explicitamente somente endpoints públicos;
- utilizar Spring Security;
- aplicar autorização no backend;
- nunca confiar apenas no frontend para esconder funcionalidades;
- validar permissões também na camada de serviço quando houver risco de acesso indevido.

Exemplos de rotas potencialmente públicas:

```text
POST /api/auth/login
POST /api/auth/refresh
```

Exemplos de rotas protegidas:

```text
POST   /api/buscas
GET    /api/buscas
GET    /api/buscas/{id}
GET    /api/leads
GET    /api/leads/{id}
PATCH  /api/leads/{id}/status
PATCH  /api/leads/{id}/observacoes
PATCH  /api/leads/{id}/contato
GET    /api/exportacao/leads.csv
GET    /api/exportacao/leads.xlsx
```

Se futuramente existirem perfis diferentes, utilizar autorização baseada em roles/permissões, evitando regras espalhadas pelo código.

---

# 3. Proteção contra XSS

O backend não deve confiar em textos enviados pelo usuário.

Medidas:

- validar tamanho e formato dos campos;
- não retornar HTML construído a partir de entrada do usuário;
- retornar JSON sempre que possível;
- no Angular, manter o escaping padrão;
- evitar `innerHTML`;
- evitar `bypassSecurityTrustHtml`, `bypassSecurityTrustUrl` e APIs equivalentes sem necessidade comprovada;
- nunca construir scripts dinamicamente com conteúdo vindo do usuário;
- sanitizar HTML somente quando a funcionalidade realmente permitir HTML;
- configurar Content Security Policy no ambiente de produção.

Headers recomendados:

```text
Content-Security-Policy
X-Content-Type-Options: nosniff
Referrer-Policy
Permissions-Policy
```

Não usar `X-XSS-Protection` como mecanismo principal de defesa.

---

# 4. Proteção contra SQL Injection

Usar exclusivamente mecanismos parametrizados.

Permitido:

- Spring Data JPA;
- JPQL parametrizado;
- Criteria API;
- prepared statements.

Evitar:

- concatenação de entrada do usuário em SQL;
- construção manual de cláusulas SQL com strings não confiáveis;
- native queries montadas dinamicamente.

Nunca fazer:

```text
"SELECT * FROM lead WHERE nome = '" + entradaUsuario + "'"
```

Filtros dinâmicos devem utilizar parâmetros, Specifications, Criteria API ou solução equivalente.

---

# 5. Proteção contra IDOR / BOLA

Nunca considerar que um usuário pode acessar um recurso apenas porque conhece seu ID.

Exemplo perigoso:

```text
GET /api/leads/123
```

A existência do ID `123` não prova que o usuário autenticado pode acessar esse lead.

Quando houver contas/tenants:

- vincular os recursos ao proprietário;
- buscar recurso considerando ID + proprietário;
- verificar autorização antes de retornar ou alterar dados;
- evitar IDs sequenciais como único mecanismo de proteção;
- considerar UUID quando fizer sentido, sem tratá-lo como substituto de autorização.

A autorização deve ocorrer no servidor.

---

# 6. Proteção da chave da Google Places API

Nunca armazenar a chave no código-fonte.

Proibido:

```java
private static final String API_KEY = "AIza...";
```

Utilizar variável de ambiente, por exemplo:

```text
GOOGLE_PLACES_API_KEY
```

E referenciar através de configuração Spring.

Regras:

- nunca enviar a chave da Places API para o frontend;
- chamadas devem passar pelo backend;
- restringir a chave no Google Cloud;
- habilitar somente APIs necessárias;
- aplicar limites de cota;
- monitorar consumo;
- rotacionar imediatamente em caso de vazamento;
- impedir commit de `.env` e arquivos de segredo;
- utilizar secret manager no ambiente de produção quando disponível.

Adicionar secret scanning no repositório quando possível.

---

# 7. Proteção contra DoS e DDoS

A aplicação isoladamente não consegue impedir um DDoS volumétrico.

A proteção deve existir em camadas.

Aplicação:

- rate limiting;
- limite de tamanho de payload;
- timeouts;
- limites de paginação;
- limites de exportação;
- limites de busca;
- cache;
- circuit breaker quando necessário;
- evitar operações de custo ilimitado;
- restringir chamadas externas.

Infraestrutura:

- reverse proxy;
- CDN/WAF quando disponível;
- proteção do provedor de nuvem;
- limites de conexão;
- autoscaling quando aplicável;
- métricas e alertas.

Nunca depender apenas do Bucket4j para proteção contra DDoS volumétrico.

---

# 8. Proteção das rotas POST, PATCH, PUT e DELETE

Rotas que modificam estado devem receber proteção adicional.

Aplicar:

- autenticação;
- autorização;
- Bean Validation;
- rate limiting;
- limite de payload;
- Content-Type permitido;
- tratamento consistente de erros;
- logs de eventos importantes;
- validação de propriedade do recurso;
- proteção contra requisições repetidas quando relevante.

Quando autenticação for baseada em cookies, implementar proteção contra CSRF.

Quando a API utilizar token Bearer enviado explicitamente no header e não depender de cookies automáticos para autenticação, analisar o modelo antes de adicionar CSRF desnecessariamente.

Para operações críticas, considerar idempotency keys quando houver risco de duplicação.

---

# 9. Proteção contra web fuzzing e enumeração

Não existe mecanismo confiável para impedir completamente fuzzing.

O objetivo é reduzir a informação e o impacto disponíveis ao atacante.

Aplicar:

- rate limiting;
- respostas de erro uniformes;
- não expor stack traces;
- não expor nomes internos de classes;
- não expor paths físicos;
- remover endpoints desnecessários;
- proteger Actuator;
- desabilitar endpoints de debug em produção;
- evitar arquivos de backup acessíveis;
- bloquear directory listing;
- não publicar documentação administrativa sem necessidade;
- monitorar padrões anormais de requisições.

Evitar diferenças desnecessárias de resposta que permitam enumeração de usuários ou recursos.

---

# 10. Proteção de subdomínios

Para infraestrutura futura:

- manter inventário de subdomínios;
- remover DNS de serviços desativados;
- evitar registros apontando para recursos não provisionados;
- revisar CNAMEs antigos;
- impedir subdomain takeover;
- não expor ambientes internos desnecessariamente;
- separar desenvolvimento, staging e produção;
- usar TLS em todos os subdomínios públicos;
- aplicar políticas de segurança no reverse proxy;
- restringir painéis administrativos por VPN, IP ou autenticação forte quando possível.

Não utilizar nomes previsíveis como mecanismo de segurança.

---

# 11. Proteção contra dump e vazamento do banco de dados

O banco nunca deve ficar diretamente exposto à Internet.

Aplicar:

- acesso somente por rede privada;
- firewall;
- usuário de aplicação com menor privilégio;
- usuário administrativo separado;
- senhas fortes;
- credenciais fora do código;
- TLS quando aplicável;
- backups criptografados;
- controle de acesso aos backups;
- rotação de credenciais;
- logs de acesso;
- monitoramento de consultas anormais;
- limitar permissões `FILE`, `SUPER`, `GRANT` e equivalentes;
- não utilizar `root` como usuário da aplicação.

A aplicação não deve possuir permissões de administração do servidor de banco quando não forem necessárias.

Evitar retornar grandes volumes de dados sem paginação.

Exports devem possuir limites apropriados.

---

# 12. Proteção de File Upload

Se upload de arquivos for implementado futuramente:

- utilizar allowlist de extensões;
- validar MIME real;
- validar magic bytes quando aplicável;
- limitar tamanho;
- gerar nome interno aleatório;
- não confiar no nome enviado pelo usuário;
- impedir path traversal;
- armazenar fora do web root;
- não executar arquivos enviados;
- remover metadados quando necessário;
- analisar arquivos com antivírus/malware scanning quando o risco justificar;
- limitar quantidade de uploads;
- aplicar autenticação e autorização;
- impedir sobrescrita arbitrária;
- bloquear arquivos executáveis e formatos desnecessários.

Nunca salvar diretamente algo como:

```text
/uploads/{nomeEnviadoPeloUsuario}
```

sem normalização e controle.

---

# 13. Proteção contra Broken Authentication

A autenticação deve evitar:

- senhas armazenadas em texto;
- hashes rápidos como MD5 ou SHA-256 puro;
- tokens eternos;
- sessões previsíveis;
- enumeração de usuários;
- reset de senha inseguro;
- tokens em URLs;
- credenciais em logs;
- ausência de rate limiting no login;
- refresh tokens sem controle;
- logout que não invalida sessões quando invalidação for necessária.

Aplicar:

- hash forte de senha;
- rate limiting de login;
- mensagens de erro genéricas;
- MFA quando o risco/produto justificar;
- sessões/token com expiração;
- rotação de refresh token;
- revogação de sessão;
- proteção contra credential stuffing;
- auditoria de logins suspeitos.

Exemplo de mensagem correta:

```text
Usuário ou senha inválidos.
```

Evitar:

```text
Este e-mail existe, mas a senha está errada.
```

---

# 14. Proteção contra ataques comuns em APIs

Considerar especialmente os riscos da OWASP API Security Top 10.

Proteger contra:

- Broken Object Level Authorization;
- Broken Authentication;
- Broken Object Property Level Authorization;
- consumo irrestrito de recursos;
- Broken Function Level Authorization;
- acesso irrestrito a fluxos sensíveis;
- Server-Side Request Forgery;
- configuração incorreta;
- inventário inadequado de APIs;
- consumo inseguro de APIs externas.

Regras gerais:

- validar request DTOs;
- aplicar allowlist de campos alteráveis;
- não fazer bind direto de payload para entidade JPA;
- limitar paginação;
- limitar filtros;
- limitar exports;
- limitar requests por usuário/IP quando apropriado;
- controlar timeouts de integrações;
- validar respostas externas;
- não confiar automaticamente na Google Places API ou outra API externa;
- versionar API quando necessário;
- remover endpoints antigos;
- documentar endpoints existentes.

---

# 15. Mass Assignment / Overposting

Nunca permitir que o cliente controle diretamente todos os campos da entidade.

Não receber entidades JPA como request body.

Utilizar DTO específico para cada operação.

Exemplo:

```text
AtualizarStatusRequest
AtualizarObservacoesRequest
AtualizarContatoRequest
```

Assim, uma requisição de alteração de observação não poderá alterar silenciosamente campos como proprietário, score ou identificadores internos.

---

# 16. SSRF

Qualquer funcionalidade que faça requests para URLs fornecidas pelo usuário deve ser tratada como risco de SSRF.

Se isso existir futuramente:

- usar allowlist de hosts;
- bloquear localhost;
- bloquear ranges privados;
- bloquear metadata endpoints de provedores de nuvem;
- validar redirects;
- limitar protocolos;
- permitir somente `https` quando aplicável;
- configurar timeout;
- limitar tamanho da resposta.

Nunca aceitar uma URL arbitrária e fazer request diretamente sem validação.

---

# 17. Path Traversal

Entradas do usuário nunca devem ser usadas diretamente para montar caminhos de arquivo.

Bloquear tentativas como:

```text
../../../../etc/passwd
```

Usar paths previamente definidos, nomes internos e validação de normalização.

---

# 18. CORS

Configurar CORS explicitamente.

Em produção:

- permitir somente origens conhecidas;
- limitar métodos;
- limitar headers;
- não usar `*` quando credentials estiverem habilitadas;
- separar configuração de desenvolvimento e produção.

CORS não substitui autenticação ou autorização.

---

# 19. Headers de segurança

No ambiente web, considerar:

```text
Content-Security-Policy
Strict-Transport-Security
X-Content-Type-Options: nosniff
Referrer-Policy
Permissions-Policy
```

Quando aplicável, configurar também políticas de framing através de CSP `frame-ancestors`.

---

# 20. TLS / HTTPS

Produção deve utilizar HTTPS.

Regras:

- redirecionar HTTP para HTTPS;
- utilizar TLS moderno;
- não aceitar certificados inválidos em integrações;
- nunca desativar validação TLS para "resolver" problemas;
- não enviar tokens ou senhas por HTTP.

---

# 21. Segurança de logs

Nunca registrar:

- senha;
- access token completo;
- refresh token;
- API key;
- cookie de sessão;
- dados sensíveis desnecessários.

Logs devem permitir investigação sem expor segredos.

Aplicar mascaramento quando necessário.

Registrar eventos como:

- login bem-sucedido/falho;
- rate limit excedido;
- alteração crítica;
- erro de autorização;
- comportamento anormal relevante.

Evitar log injection normalizando dados externos quando necessário.

---

# 22. Tratamento de erros

Produção não deve retornar:

- stack trace;
- SQL;
- mensagens internas do Hibernate;
- caminho físico de arquivos;
- segredo;
- configuração interna.

Utilizar `@ControllerAdvice`.

Resposta deve possuir estrutura consistente.

Exemplo:

```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Requisição inválida"
}
```

Detalhes técnicos completos devem ficar somente no log interno apropriado.

---

# 23. Dependências e supply chain

Aplicar:

- versões controladas;
- atualizações regulares;
- análise de vulnerabilidades;
- remoção de dependências não utilizadas;
- preferência por bibliotecas maduras;
- revisão antes de adicionar nova dependência;
- lockfiles no frontend;
- Dependabot/Renovate quando conveniente;
- secret scanning;
- análise SAST quando disponível.

Nunca instalar dependência somente porque um agente sugeriu sem verificar sua necessidade e procedência.

---

# 24. Segurança de serialização

Evitar:

- exposição automática de entidades JPA;
- ciclos de serialização;
- campos internos vazando em JSON;
- polymorphic deserialization insegura;
- tipos arbitrários vindos do cliente.

Utilizar DTOs explícitos.

---

# 25. Segurança de cache

Não colocar segredos ou tokens em cache sem necessidade.

Ao utilizar Caffeine:

- definir TTL adequado;
- limitar tamanho;
- evitar cache infinito;
- garantir que cache de dados por usuário não seja compartilhado incorretamente entre usuários.

A chave de cache deve incluir todo contexto de autorização relevante quando o conteúdo depender do usuário.

---

# 26. Rate limiting

Aplicar rate limiting principalmente em:

```text
POST /api/auth/login
POST /api/auth/refresh
POST /api/buscas
PATCH /api/leads/*
GET /api/exportacao/*
```

A estratégia pode considerar:

- usuário autenticado;
- IP;
- endpoint;
- combinação de usuário + endpoint;
- custo da operação.

Operações caras devem possuir limites mais restritivos.

A integração Google Places deve possuir limite próprio para evitar abuso e custo inesperado.

---

# 27. Paginação e Resource Exhaustion

Listagens devem ser paginadas.

Definir:

- tamanho padrão;
- tamanho máximo;
- limite de filtros;
- timeout;
- limite de exportação.

Nunca permitir algo como:

```text
GET /api/leads?size=999999999
```

---

# 28. Segurança do Spring Boot Actuator

Se Actuator for utilizado:

- não expor todos os endpoints;
- não publicar `/env`;
- não publicar `/beans`;
- não publicar heapdump;
- proteger endpoints administrativos;
- considerar rede interna separada;
- permitir somente endpoints necessários como health e metrics.

---

# 29. Proteção contra deserialização e payload malicioso

Configurar limites para:

- tamanho do body;
- profundidade de JSON quando relevante;
- quantidade de campos;
- tamanho de strings;
- tamanho de coleções.

DTOs devem possuir validações explícitas.

---

# 30. Segurança de exports CSV/Excel

A exportação também possui riscos.

Para CSV/Excel:

- limitar quantidade de registros;
- exigir autorização;
- evitar exposição de dados de outros usuários;
- impedir CSV/Formula Injection.

Valores iniciados por:

```text
=
+
-
@
```

devem receber tratamento quando puderem vir de fontes não confiáveis e forem exportados para planilhas.

---

# 31. Proteção contra abuso de integrações externas

Toda API externa deve possuir:

- timeout de conexão;
- timeout de leitura;
- limite de chamadas;
- tratamento de resposta inválida;
- tratamento de indisponibilidade;
- retry limitado quando adequado;
- circuit breaker se necessário;
- validação de payload externo;
- logs sem segredos.

Nunca confiar que uma API externa sempre retornará dados válidos.

---

# 32. Segurança de ambiente

Separar:

```text
dev
test
prod
```

Produção não deve utilizar:

- debug habilitado;
- credenciais de desenvolvimento;
- banco de desenvolvimento;
- chaves compartilhadas;
- CORS aberto;
- logs excessivamente detalhados;
- endpoints experimentais.

---

# 33. Backups e recuperação

Backups devem:

- ser criptografados;
- possuir acesso restrito;
- ser testados periodicamente;
- possuir política de retenção;
- não ficar disponíveis via servidor web;
- possuir cópias separadas quando o nível de criticidade justificar.

Um backup que nunca foi restaurado em teste não deve ser considerado garantia de recuperação.

---

# 34. Princípio de menor privilégio no banco

Criar usuário exclusivo para a aplicação.

Ele deve ter somente as permissões necessárias.

Evitar:

```text
GRANT ALL PRIVILEGES
```

A aplicação normalmente não precisa criar usuários, alterar permissões globais ou administrar o servidor MySQL.

---

# 35. Autenticação — arquitetura recomendada

A autenticação deve ser implementada somente quando entrar oficialmente no escopo do projeto.

A solução recomendada para uma SPA Angular + API Spring Boot é:

```text
Angular
   |
   | credenciais
   v
POST /api/auth/login
   |
   v
Spring Security
   |
   | valida senha
   v
Password Encoder
   |
   +--> Access Token de curta duração
   |
   +--> Refresh Token controlado
```

Separar:

- autenticação: quem é o usuário;
- autorização: o que ele pode fazer;
- sessão/token: como manter a identidade entre requests.

---

# 36. Cadastro de usuário

Fluxo recomendado:

1. receber DTO de cadastro;
2. validar campos;
3. normalizar e-mail;
4. verificar unicidade;
5. validar política de senha;
6. gerar hash da senha;
7. armazenar somente o hash;
8. nunca armazenar a senha original;
9. nunca logar a senha;
10. retornar DTO seguro.

Campos internos nunca devem ser definidos diretamente pelo cliente, como:

```text
role
isAdmin
enabled
permissions
```

salvo fluxos administrativos explicitamente autorizados.

---

# 37. Algoritmo de senha

Nunca utilizar criptografia reversível para senha.

Senha deve ser armazenada através de função de derivação de chave adequada para passwords.

Preferência:

**Argon2id**

Alternativa amplamente suportada:

**BCrypt**

Para Spring Security, utilizar implementações oficiais como:

```text
Argon2PasswordEncoder
```

ou:

```text
BCryptPasswordEncoder
```

Nunca implementar algoritmo próprio.

Nunca utilizar:

```text
MD5
SHA-1
SHA-256 puro
SHA-512 puro
Base64
AES para armazenar senha
```

como substituto de password hashing.

O salt deve ser gerado automaticamente pelo encoder.

Parâmetros de custo devem ser revisados conforme o hardware disponível.

---

# 38. Fluxo de login

Algoritmo lógico:

```text
1. Receber identificador + senha.
2. Aplicar rate limiting.
3. Normalizar identificador.
4. Buscar usuário.
5. Comparar senha utilizando PasswordEncoder.matches().
6. Se inválido:
      retornar erro genérico.
7. Se válido:
      verificar conta habilitada.
8. Construir identidade/permissões.
9. Emitir sessão/tokens.
10. Registrar evento de login sem segredos.
11. Retornar somente os dados necessários.
```

A aplicação não deve informar se o erro ocorreu porque:

- o usuário não existe;
- a senha está incorreta.

Resposta recomendada:

```text
Credenciais inválidas.
```

---

# 39. Access Token

Se JWT for utilizado, o access token deve possuir curta duração.

Exemplo conceitual:

```text
5 a 15 minutos
```

Claims mínimos:

```text
sub
iat
exp
jti
roles/permissoes necessárias
```

Não colocar no JWT:

- senha;
- API keys;
- informações sensíveis desnecessárias;
- grandes estruturas de dados.

Para assinatura, preferir algoritmos assimétricos em ambientes onde separação de emissão/verificação faça sentido:

```text
RS256
ES256
```

Em arquiteturas simples, HMAC forte pode ser utilizado com secret adequadamente gerenciado, mas nunca hardcodado.

Nunca aceitar:

```text
alg: none
```

O servidor deve definir explicitamente os algoritmos aceitos.

---

# 40. Refresh Token

Refresh token deve possuir vida maior que access token, mas com controle adicional.

Recomendado:

- gerar valor criptograficamente aleatório;
- armazenar representação segura no servidor;
- associar ao usuário/sessão;
- armazenar data de expiração;
- permitir revogação;
- rotacionar a cada uso;
- invalidar o token anterior após rotação;
- detectar reutilização quando possível.

Exemplo conceitual:

```text
Access Token: 10 minutos
Refresh Token: 7 a 30 dias
```

Os valores exatos devem ser definidos conforme risco e experiência desejada.

---

# 41. Armazenamento de token no frontend

Evitar armazenar tokens sensíveis em `localStorage` quando houver alternativa arquitetural melhor, pois XSS pode acessá-los.

Para aplicações web, considerar:

- access token mantido em memória;
- refresh token em cookie `HttpOnly`, `Secure` e com `SameSite` apropriado;

ou

- sessão server-side usando cookie seguro.

Se cookies forem utilizados para autenticação, avaliar e implementar proteção CSRF.

A decisão final deve considerar o modelo de ameaça e a arquitetura real do projeto.

---

# 42. Cookies de autenticação

Quando utilizados:

```text
HttpOnly
Secure
SameSite=Lax ou Strict quando compatível
```

Evitar escopo excessivo de domínio e path.

Não armazenar informação sensível diretamente no conteúdo legível do cookie.

---

# 43. Logout

Logout deve invalidar o mecanismo que mantém a sessão.

Para refresh tokens:

- revogar refresh token/sessão;
- remover cookie;
- impedir reutilização futura.

Access tokens JWT já emitidos podem continuar válidos até expirar, salvo se houver mecanismo explícito de revogação.

Por isso a duração do access token deve ser curta.

---

# 44. Recuperação de senha

Fluxo recomendado:

1. usuário informa e-mail;
2. API sempre retorna resposta genérica;
3. se a conta existir, gerar token aleatório de uso único;
4. armazenar hash do token;
5. definir expiração curta;
6. enviar link por canal confiável;
7. usuário apresenta token;
8. validar token;
9. permitir nova senha;
10. invalidar token imediatamente;
11. revogar sessões anteriores quando apropriado.

Nunca enviar senha antiga ou nova por e-mail.

---

# 45. Proteção contra força bruta e credential stuffing

Login deve possuir:

- rate limiting;
- atraso progressivo quando necessário;
- monitoramento de falhas;
- bloqueio temporário cuidadosamente implementado;
- MFA opcional/futuro;
- detecção de padrões suspeitos quando o produto justificar.

Evitar bloqueios permanentes facilmente abusáveis para causar negação de serviço contra outras contas.

---

# 46. Autorização

Autenticação válida não significa acesso irrestrito.

Toda operação deve verificar:

```text
Usuário autenticado
        +
permissão para a ação
        +
permissão sobre o recurso
```

Para objetos pertencentes a usuários/organizações, sempre verificar ownership/tenant.

Não confiar em IDs enviados pelo frontend.

---

# 47. Sessões simultâneas

Quando autenticação for implementada, decidir explicitamente:

- múltiplas sessões permitidas?
- logout de todos os dispositivos?
- lista de sessões?
- revogação individual?
- expiração por inatividade?

Se refresh tokens forem persistidos por sessão, estas funcionalidades ficam mais fáceis de implementar com segurança.

---

# 48. Chaves criptográficas

Segredos utilizados para assinatura:

- devem ser gerados com fonte criptograficamente segura;
- nunca devem estar no Git;
- devem ser diferentes por ambiente;
- devem possuir processo de rotação;
- devem ter acesso restrito.

Em produção, utilizar secret manager quando possível.

---

# 49. Ordem recomendada para implementação futura da autenticação

Quando autenticação entrar no roadmap, implementar nesta ordem:

```text
1. Entidade de usuário e migration
2. DTOs de cadastro/login
3. PasswordEncoder
4. Cadastro seguro
5. Spring Security base
6. AuthenticationService
7. Login
8. Access Token
9. Refresh Token
10. Filtro/security chain
11. Proteção de rotas
12. Authorization/ownership
13. Logout
14. Rate limiting do login
15. Testes de autenticação
16. Recuperação de senha
17. Auditoria
18. MFA, se necessário
```

Não começar por JWT antes de definir corretamente usuário, senha, sessão e autorização.

---

# 50. Testes de segurança prioritários

Quando as funcionalidades existirem, criar testes para:

- rota protegida sem autenticação;
- rota protegida com token inválido;
- token expirado;
- usuário sem permissão;
- IDOR;
- login correto;
- senha incorreta;
- usuário inexistente com resposta equivalente;
- rate limiting;
- validação de DTO;
- mass assignment;
- SQL injection em filtros;
- XSS em campos textuais;
- refresh token expirado;
- refresh token revogado;
- reutilização de refresh token;
- CORS;
- acesso a recurso de outro usuário;
- exportação sem autorização;
- limites de paginação;
- upload inválido, caso upload exista.

---

# 51. Checklist antes de produção

Antes de considerar o sistema pronto para produção, verificar:

- [ ] HTTPS obrigatório.
- [ ] CORS restrito.
- [ ] Segredos fora do Git.
- [ ] API keys restritas.
- [ ] Banco não exposto publicamente.
- [ ] Usuário do banco com menor privilégio.
- [ ] Senhas com Argon2id ou BCrypt.
- [ ] Rate limiting ativo.
- [ ] Rotas autenticadas conforme necessidade.
- [ ] Autorização por recurso validada.
- [ ] DTOs usados em requests/responses.
- [ ] Erros sem stack trace.
- [ ] Logs sem segredos.
- [ ] Actuator protegido.
- [ ] Limites de payload definidos.
- [ ] Paginação limitada.
- [ ] Exportações limitadas.
- [ ] Dependências verificadas.
- [ ] Backups protegidos.
- [ ] Headers de segurança configurados.
- [ ] Testes de segurança executados.
- [ ] Refresh tokens revogáveis, se utilizados.
- [ ] Política de logout definida.
- [ ] Recuperação de senha segura.
- [ ] Monitoramento e alertas mínimos configurados.

---

# 52. Regra final

Segurança não deve depender de uma única camada.

O modelo esperado é:

```text
Cliente
  ↓
HTTPS
  ↓
WAF / Reverse Proxy / Rate Limit
  ↓
Spring Security
  ↓
Validação
  ↓
Autorização
  ↓
Service
  ↓
JPA parametrizado
  ↓
Banco privado com menor privilégio
```

Toda funcionalidade nova deve ser avaliada considerando:

```text
entrada
autenticação
autorização
validação
abuso
exposição de dados
consumo de recursos
logs
segredos
persistência
```

Quando houver dúvida entre uma solução própria e um mecanismo consolidado do Spring Security ou de uma biblioteca madura, utilizar a solução consolidada.
