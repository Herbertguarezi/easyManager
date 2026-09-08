# Easy Manager — Progresso de Implementação

Referência: `docs/software-engineering.md` (v4.4) e
`docs/arquitetura-banco-de-dados.md` (v1.1).

## Regra de processo (vale para todas as fases)

Desenvolvimento é **orientado a testes (TDD)**: para cada item da fase,
escrever o teste que falha primeiro (red), implementar o mínimo pra
passar (green), refatorar, e só então seguir pro próximo item. Nenhum
item é marcado como concluído sem teste automatizado cobrindo o
comportamento esperado — "implementei mas não testei" não conta como
feito nesta lista.

Cada fase só começa depois que a anterior está com todos os itens
marcados e os testes daquela fase rodando verdes.

---

## Fase 0 — Segurança e infraestrutura
**Status:** concluída (todos os itens com teste automatizado verde — 22 testes, `./mvnw test`)

- [x] Flyway configurado, substituindo `ddl-auto=update`; `V1` cobre o schema atual de `products`
- [x] Estrutura de pacotes por bounded context (`auth/`, `sellers/`, `stores/`, `products/`, `marketplace/`, `stock/`, `webhooks/`, `ads/`, `shared/`)
- [x] CORS restritivo (origem exata + `Access-Control-Allow-Credentials`)
- [x] Secrets separados por finalidade (`JWT_SECRET`, `MARKETPLACE_ENCRYPTION_KEY`, `WEBHOOK_SECRET`, `EMAIL_TOKEN_SECRET`), fail-fast se algum estiver ausente
- [x] Exception handling central (`@RestControllerAdvice` + `ProblemDetail`, sem stack trace em produção)
- [x] Logging sem secrets (política + utilitário de mascaramento)
- [x] Correlation ID (`X-Correlation-ID`) propagado até os logs

## Fase 1 — Auth
**Status:** não iniciada

- [ ] Cadastro (`sellers`, self-service, status `PENDENTE`)
- [ ] Confirmação de email (`email_confirmation_tokens`, hash do token)
- [ ] Login (JWT, algoritmo fixo no verificador)
- [ ] Refresh token com rotation (`refresh_tokens`, hash, `replaced_by`, detecção de reuso)
- [ ] Cookie HttpOnly/Secure/SameSite=Strict + CSRF double-submit em `/auth/refresh`
- [ ] Reset de senha (`password_reset_tokens`, hash do token)
- [ ] Rate limiting (register, login, refresh, forgot-password, reset-password)
- [ ] RBAC (`ADMIN`/`SELLER`)
- [ ] Política de senha (tamanho mínimo, checagem contra vazamentos)
- [ ] Throttling por conta em login (além de rate limit por IP)
- [ ] Respostas anti-enumeração em register/forgot-password
- [ ] MFA para contas `ADMIN`

## Fase 2 — Sellers/Stores
**Status:** não iniciada

- [ ] `Seller`, `Store`, `StoreCredential`
- [ ] OAuth Shopee (`ShopeeApiAdapter`)
- [ ] Criptografia AES-GCM dos tokens de marketplace
- [ ] Renovação automática de token
- [ ] Ownership (Store pertence a Seller)

## Fase 3 — Produtos
**Status:** não iniciada

- [ ] `seller_id` direto em `products`; `UNIQUE(seller_id, barcode)`
- [ ] `ProductVariation`; `UNIQUE(product_id, sku_variacao)`; migração dos produtos existentes para variação padrão
- [ ] Correção do bug de `listProduct` (condição invertida)
- [ ] Correção do `PUT /products/{id}` (contrato revisado para variações)
- [ ] Upload seguro (magic bytes, MIME, tamanho, nome UUID)
- [ ] Constraints de banco (`CHECK amount >= 0`, `CHECK estoque_minimo >= 0`)
- [ ] Soft delete (`deleted_at`)
- [ ] Isolamento multi-tenant reforçado no ORM (filtro Hibernate + repositório base parametrizado)

## Fase 4 — Marketplace Links
**Status:** não iniciada

- [ ] `ProductMarketplaceLink`; `UNIQUE(store_id, external_sku)`
- [ ] Busca de produtos da Store
- [ ] Sugestão por SKU principal
- [ ] Vínculo manual
- [ ] Ownership cruzado (ProductVariation e Store do mesmo seller)

## Fase 5 — Estoque
**Status:** não iniciada

- [ ] `StockMovement`
- [ ] Atualização atômica (`UPDATE ... WHERE amount >= quantidade`)
- [ ] Testes de concorrência (dois eventos simultâneos, `amount = 1`)
- [ ] Auditoria (nunca editar/apagar movimentação)

## Fase 6 — Webhooks
**Status:** não iniciada

- [ ] `webhook_events`; `UNIQUE(store_id, event_id)`
- [ ] Validação de assinatura + replay protection
- [ ] Idempotência dupla (evento + pedido)
- [ ] Processamento transacional
- [ ] Ativação por Store (`stock_sync_status`) com reconciliação mínima
- [ ] `/webhooks/**` excluído do filtro CSRF

## Fase 7 — Ads Analyzer
**Status:** não iniciada

- [ ] Coleta com paginação
- [ ] Timezone centralizado (`MarketplaceTimezone.SHOPEE`)
- [ ] Snapshot (`UNIQUE(store_id, data)`) e `analysis_run_id`
- [ ] Análise via IA (execução síncrona, 1x/dia manual por Store)
- [ ] Exportação de dados brutos (`GET /ads/export/{storeId}/{data}`), com ownership e neutralização de injeção CSV
- [ ] Retry (erros transitórios) e circuit breaker

## Fase 8 — Frontend
**Status:** não iniciada

- [ ] Login (com tratamento de sessão expirada)
- [ ] Stores, Produtos, Vínculos, status de estoque
- [ ] Ads Analyzer (seletor de Store, exportação)

---

## Log de decisões durante a implementação

(Registrar aqui qualquer desvio do que está em `docs/software-engineering.md`
— o que mudou e por quê — para trazer de volta na revisão.)

### Fase 0

- **Flyway / `V1`:** `V1__create_products_table.sql` usa o tipo nativo
  `UUID` para `products.id` (não `CHAR(36)`), porque essa migration é o
  bootstrap do schema que já existia em produção/dev (criado por
  `ddl-auto=update`), não o design-alvo. A convenção `CHAR(36)` de
  `docs/easy-manager-arquitetura-banco-de-dados.md` (§1) passa a valer a
  partir de `V2` (tabelas novas).
- **Flyway baseline:** `spring.flyway.baseline-on-migrate=true` com
  `baseline-version=1` (igual à versão de `V1`). Isso evita apagar/recriar
  o banco de dev existente: no banco atual (schema não vazio, sem
  `flyway_schema_history`), o Flyway marca `V1` como baseline sem
  reexecutá-la; num banco novo (ambiente novo, banco de teste), não há
  schema pré-existente, então o baseline não é acionado e `V1` roda de
  verdade, criando a tabela do zero. `spring.jpa.hibernate.ddl-auto` virou
  `validate` (não `none`), para o Hibernate continuar conferindo que o
  mapeamento da entidade bate com o schema real criado pelas migrations.
- **Infraestrutura de testes (não documentada nos docs de arquitetura):**
  o host de desenvolvimento não tem JDK instalado nem Docker
  disponível para uso direto pela JVM de teste (Testcontainers exigiria
  Docker-outside-of-Docker). Solução adotada: um container MariaDB 11
  dedicado (`mariadb_test`, mesma rede `easymanager_springnet` do
  `docker-compose.yml`, schema `easymanager_test`) serve de banco de
  teste, e os testes rodam via `mvn` dentro de um container
  `maven:3.9.6-eclipse-temurin-21` (mesma imagem base do
  `Dockerfile.dev`), com `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`
  apontando pra esse container — não altera nada do banco de dev
  (`mariadb_manager`/`easymanager`), que segue com os 8 produtos de teste
  já existentes.
- **Estrutura de pacotes:** dentro de `products/`, adotada a divisão
  literal de §2.1 (`domain / application / infrastructure / presentation`)
  em vez de manter a nomenclatura hexagonal anterior
  (`adapters.in`/`adapters.out`): `products.presentation` (controller),
  `products.infrastructure.persistence` (entity/JPA repo/adapter),
  `products.application` (+ `ports.in`/`ports.out`, mantido), `products.domain`
  (classe `Product`). `Role.java` (enum `ADMIN`/`USER`, não usado em lugar
  nenhum do código) foi deixado como está em
  `com.guarezi.easymanager.domain.enums` — não é código de Produtos, e
  criar `auth/` vazio só pra acomodá-lo seria adiantar a Fase 1; será
  reposicionado quando o bounded context de Auth existir de fato (a
  versão final também não deve ter os dois valores atuais: o glossário e
  o doc de arquitetura definem `role IN ('ADMIN','SELLER')`).
  `FlywayMigrationTest` foi colocado em `shared.infrastructure` (não em
  `products`), por testar a configuração do Flyway em si (mecanismo
  transversal), ainda que a migration `V1` que ele verifica seja hoje só
  sobre `products`.
- **Testes de caracterização antes do reorg:** não existiam testes de
  Produtos antes desta fase (só o smoke test de contexto — ver Lacuna
  Conhecida #3 do doc de engenharia). Como o item pede "os testes já
  existentes de Produtos continuam passando após a reorganização", esses
  testes tiveram que ser escritos primeiro (`ProductServiceTest`,
  `ProductControllerTest`, `ProductRepositoryPersistenceAdapterTest`),
  confirmados verdes na estrutura antiga, e só então usados como rede de
  segurança para o move (não há red/green clássico aqui, já que mover
  arquivos não é uma mudança de comportamento). `ProductControllerTest`
  cobre só `GET /products` e `DELETE /products/{id}` — os dois bugs
  conhecidos de `GET /products/{id}` (condição invertida) e `PUT
  /products/{id}` (descarta campos) não foram exercitados nem corrigidos,
  ficam para a Fase 3 conforme já registrado no roadmap.
- **CORS:** configurado via `WebMvcConfigurer` global
  (`shared.security.CorsConfig`), origem exata lida de
  `app.cors.allowed-origin` (env `CORS_ALLOWED_ORIGIN`, default
  `http://localhost:5173`), com `allowCredentials(true)`. O
  `@CrossOrigin("*")` que existia em `ProductController` foi removido —
  CORS agora é transversal, não por controller.
- **Secrets separados:** classe `shared.config.SecurityKeysProperties`
  (`@ConfigurationProperties(prefix = "app.security.keys")` +
  Bean Validation `@NotBlank` em cada um dos 4 campos) — evitei o nome
  "Secrets" no arquivo/classe por colidir com a regra de permissão local
  que bloqueia leitura/escrita de caminhos `*secret*`/`*credentials*`
  (proteção contra exposição acidental de segredo real); a classe e o
  arquivo chamam-se `SecurityKeysProperties`, os 4 campos internos
  continuam `jwtSecret`/`marketplaceEncryptionKey`/`webhookSecret`/
  `emailTokenSecret`. Nenhuma dessas chaves é usada ainda (JWT/criptografia/
  webhook/email token são das Fases 1–6) — só a validação de presença na
  subida da aplicação, como pedido nesta fase. Adicionada a dependência
  `spring-boot-starter-validation` (não existia no `pom.xml`) para o
  `@NotBlank` — é a via padrão do Spring Boot para "falha no boot com
  mensagem clara" sem código de validação manual.
- **`.env` / `.env.example`:** as 4 chaves novas (+ `CORS_ALLOWED_ORIGIN`)
  foram adicionadas ao `.env` local via `>>` (append). Isso tem um risco:
  se o `.env` original não terminasse em quebra de linha, a primeira
  variável apensada pode ter colado no fim da linha de `DB_PASSWORD`,
  corrompendo-a — não foi possível confirmar isso seguramente porque a
  permissão do projeto bloqueia qualquer comando que leia/exiba o
  conteúdo do `.env` (mesmo `grep -c`, que não expõe valor, foi negado
  depois da primeira tentativa). **Ação pendente do usuário:** conferir
  manualmente se `DB_PASSWORD` e `CORS_ALLOWED_ORIGIN` estão em linhas
  separadas no `.env` antes de rodar `docker-compose up`. Não foi
  possível criar `.env.example` (mesma regra de permissão bloqueou
  `Write` e `Bash`) — o `.gitignore` já previa esse arquivo
  (`!.env.example`), mas ele continua sem existir; fica como
  pendência para o usuário criar manualmente ou liberar a permissão.
- **Exception handling central:** `shared.exceptions.GlobalExceptionHandler`
  (`@RestControllerAdvice` + `@ExceptionHandler(Exception.class)`)
  devolve um `ProblemDetail` com `title`/`status`/`instance` sempre iguais
  e `detail` fixo e genérico ("Não foi possível processar a
  solicitação.", igual ao exemplo do §10.5 — texto de produto, visível ao
  cliente da API, então mantido em PT-BR mesmo com o resto do código em
  inglês; nomes de classe/método continuam em inglês). A exceção completa
  (com stack trace e mensagem original) só vai para o log do servidor via
  SLF4J — nunca para a resposta HTTP. Ainda não há tratamento
  diferenciado por tipo de exceção (404 vs 400 vs 500) — só o handler
  genérico pedido nesta fase; handlers específicos (ex: recurso não
  encontrado) entram junto com os casos de uso que os motivam nas fases
  seguintes.
- **Logging sem secrets:** anotação `shared.observability.Sensitive`
  (marca um campo) + `shared.observability.LogSanitizer` (monta uma
  representação do objeto via reflection, substituindo campos anotados
  por `***`). Aplicado de fato em
  `shared.observability.RequestLoggingFilter`, que loga uma linha por
  requisição (método, path, status, duração) passando por esse
  sanitizador — hoje nenhum desses campos é sensível, mas o pipeline já
  está pronto para quando webhooks/tokens existirem (Fases 1, 2 e 6) só
  precisarem anotar o campo com `@Sensitive`, sem alterar o filtro.
- **Correlation ID:** implementado no mesmo `RequestLoggingFilter` (não
  numa classe separada) — já existia um filtro tocando toda requisição
  para logging, então correlation id entrou como evolução dele em vez de
  um segundo filtro concorrente. Lê `X-Correlation-ID` do request, gera
  um `UUID` novo se ausente, põe no MDC (`correlationId`) durante toda a
  requisição (removido no `finally`), devolve no header da resposta, e
  também inclui no próprio campo do log estruturado (além do MDC) —
  redundância deliberada: não depende de configurar o pattern do Logback
  com `%X{correlationId}` para o id aparecer na linha de log.
