# Easy Manager — Progresso de Implementação

Referência: `docs/software-engineering.md` (v5.0) e
`docs/arquitetura-banco-de-dados.md` (v2.0). A partir desta revisão,
também: `docs/regras-negocio.md` (RN-XXX + máquina de estados da
assinatura, §13), `docs/precificacao.md` (regras de negócio em
prosa) e `docs/diagramas-uml.md` (classes, sequência, estados,
componentes).

**Nota de sincronização (Etapa F do `plano-execucao.md`):** a Fase 1
abaixo, como estava escrita antes desta revisão, assumia um único
plano "Full" atribuído automaticamente no cadastro — modelo obsoleto,
substituído pelo sistema de módulos/planos/combo/trial/tokens da
precificação. Corrigida abaixo, com uma **Fase 1.5** nova pra cobrir o
que o modelo novo exige (assinaturas, planos, entitlements) antes de
Sellers/Stores.

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
**Status:** concluída (todos os itens com teste automatizado verde — 105 testes, `./mvnw test`)

- [x] Cadastro (`sellers`, self-service, status `PENDING`)
- [x] Política de senha (tamanho mínimo, checagem contra vazamentos)
- [x] Respostas anti-enumeração em register/forgot-password (register feito; forgot-password aplica o mesmo princípio quando o endpoint for construído no item "Reset de senha")
- [x] Confirmação de email (`email_confirmation_tokens`, hash do token)
- [x] Login (JWT, algoritmo fixo no verificador)
- [x] Throttling por conta em login (além de rate limit por IP)
- [x] Refresh token com rotation (`refresh_tokens`, hash, `replaced_by`, detecção de reuso)
- [x] Cookie HttpOnly/Secure/SameSite=Strict + CSRF double-submit em `/auth/refresh`
- [x] Reset de senha (`password_reset_tokens`, hash do token), revogando refresh tokens antigos
- [x] Rate limiting (register, login, refresh, forgot-password, reset-password)
- [x] MFA para contas `ADMIN`
- [x] Campos `cnpj`/`cnpj_verificado_em` em `sellers` (RN-ABUSE-001) —
      verificação em si (chamada à Receita Federal) é opcional nesta
      fase; o campo e a `UNIQUE` constraint não são (feito na `V2`,
      junto da criação da tabela — ver item "Cadastro")

**Removido desta fase (não é mais Auth, virou Fase 1.5):** `plans`,
seed do plano "Full", e a anotação `@RequiresModule`/interceptor AOP —
dependem do `EntitlementService`, que só existe depois da Fase 1.5.
Cadastro de seller **não** atribui plano nem assinatura (RF-AU19) —
nasce sem `subscriptions`, ponto.

## Fase 1.5 — Assinaturas, Planos & Entitlements (nova)
**Status:** não iniciada

Pré-requisito de tudo que vem depois: as Fases 2–7 vão todas checar
entitlement em algum ponto (`canUseModule`, `canCreateStore`,
`canExecuteAnalysis` etc.), e nenhuma delas pode ser testada de
verdade sem isso existir. Documentos de referência: `regras-negocio.md`
(RN-PLAN, RN-SUB, RN-STORE, RN-TOKEN, RN-COMBO, RN-PAY, RN-INAD,
RN-RET, RN-ABUSE) e `arquitetura-banco-de-dados.md` §2–§3 (Etapa C1–C11).

**Pré-requisito (achado da Fase 1) — identidade da requisição:**
- [x] Filtro de autenticação JWT: valida o access token (`AccessTokenIssuer.verify`) e expõe `sellerId`/`role` ao restante da requisição; RBAC `ADMIN`/`SELLER`. **Não protege `/products/**` ainda** — isso fica para a Fase 3, quando `products.seller_id` existir (proteger antes quebra o frontend atual sem dar isolamento de verdade)
- [x] Handlers específicos no `GlobalExceptionHandler`: 403 para módulo não contratado (senão o handler genérico devolve 500) e correção do 404 mascarado como 500 (`NoHandlerFoundException`, achado da Fase 1)

**Schema (migrations novas, a partir da próxima versão livre — `V5` em diante):**
> A numeração `V2`–`V19` do §11 de `arquitetura-banco-de-dados.md` é só
> ordem relativa. Na implementação, `V2`–`V4` já foram usadas por Auth
> (tabelas de Auth + `cnpj`, throttling, MFA). Use sempre o próximo
> número livre; nunca edite migration já aplicada.

- [x] `plans` (redesenhado — 6 linhas fixas, seed manual) + `api_pricing_history` + `exchange_rate_snapshots`
- [x] `subscriptions` (máquina de 9 estados, `regras-negocio.md` §13 — `ARREPENDIDO` adicionado na revisão do Block B, migration `V11`)
- [x] **Corrigir `chk_subscriptions_plan_required`** (achado na revisão do
      Block A): a versão criada em `V6` não inclui `EXCLUIDO` na lista de
      estados isentos de `plan_id`, mas `TRIAL_* → EXCLUIDO` (expiração de
      30 dias do trial ADS_ANALYZER, RN-TRIAL-006) nunca teve plano — essa
      transição legítima quebraria a constraint. `V6` já está aplicada,
      não editar: migration nova (`DROP`/`ADD CONSTRAINT`) corrigindo para
      `status IN ('TRIAL_ATIVO','TRIAL_RESTRITO','TRIAL_ESGOTADO','EXCLUIDO') OR plan_id IS NOT NULL`
      (doc de arquitetura já atualizado). Teste novo:
      `subscriptionsAllowsAnExcludedStatusWithoutAPlan`.
- [x] `token_usage` + `token_package_plans` + `token_packages` — `token_usage.analysis_run_id` nasce como coluna `CHAR(36) UNIQUE NOT NULL` **sem FK** (`analysis_runs` só existe na Fase 7; a FK entra lá)
- [x] `payment_attempts` + `refunds`
- [x] `trial_contestations` + `risk_signals`
- *(adiado para a Fase 2: `store_module_activation` — depende de `stores`)*

**`SubscriptionStateMachine`:**
- [x] Valida toda transição de `subscriptions.status` contra a tabela
      de `regras-negocio.md` §13.3 — nenhum `UPDATE status` fora daqui (RF-AU24)
- [x] Transições de trial (início, restrição por 2+ sinais de risco,
      esgotamento, contestação, expiração de 30 dias no ADS_ANALYZER)
- [x] Transições de assinatura paga (1ª cobrança, renovação, upgrade/
      downgrade — só ciclo mensal, RN-SUB-004 —, cancelamento
      irreversível, arrependimento com corte imediato)
- [ ] **Correção pós-revisão do Block B:** adicionar `ARREPENDIDO →
      ATIVO` (assina de novo, sem reabrir trial — `regras-negocio.md`
      §13.3) ao mapa de transições; hoje `ARREPENDIDO` está modelado
      como terminal (default conservador do Claude Code, sem
      confirmação de negócio — decisão agora tomada e registrada no
      doc). Teste novo cobrindo essa transição; o teste existente
      `withdrawnIsTerminal...` (se esse nome foi usado) precisa virar
      o contrário do que testava.
- [x] Transições de inadimplência/retenção (régua de 5 tentativas,
      bloqueio no dia 6, reativação — mesma linha, sem novo checkout
      só quando vem de inadimplência, RN-SUB-010 vs RN-RET-003)

**`EntitlementService` (contrato completo em `software-engineering.md` §4.6):**
- [ ] `canUseModule`, `getStoreLimit`
- [ ] `getTokenBalance`, `canBuyExtraPackage`
- [ ] `canExecuteAnalysis` — nesta fase: módulo + saldo de tokens + e-mail do seller confirmado (RN-ABUSE-005: seller `PENDING` nunca executa análise; o login de `PENDING` segue permitido). A checagem "Store ativa" entra na Fase 7
- *(adiados para a Fase 2, dependem de `stores`: `canCreateStore`, `canActivateStore`, `getActiveStores`)*
- [ ] `getSubscriptionStatus`, `isInTrial`, `isPastDue`, `isInRetention`
- [ ] `canCreateProduct` (teto de 15 produtos do trial PRODUCTS, RN-PROD-003)
- [ ] Anotação `@RequiresModule` + interceptor AOP (403, não 404 — RF-AU22), agora resolvendo contra o `EntitlementService` real
- [ ] Checagem composta em endpoints de execução (`canExecuteAnalysis`, não só `canUseModule` — RF-AU23)

**Jobs automáticos desta fase:**
- [ ] Job diário de câmbio + recálculo de `cota_tokens_mes_atual` (RN-TOKEN-002)
- [ ] Job de cobrança/régua de inadimplência (5 tentativas, bloqueio dia 6)
- [ ] Job de retenção/exclusão (7 dias ADS_ANALYZER, 30 dias PRODUCTS) — nesta fase só a transição de estado + uma porta de purga (`DataPurgePort`) com implementação vazia; a exclusão real de dados entra junto das tabelas de cada módulo (Fases 3 e 7)
- [ ] Job de expiração de trial (30 dias, só ADS_ANALYZER) — purga dos snapshots pela mesma porta, implementada na Fase 7

## Fase 2 — Sellers/Stores
**Status:** não iniciada

- [ ] `Seller`, `Store`, `StoreCredential`
- [ ] OAuth Shopee (`ShopeeApiAdapter`)
- [ ] Criptografia AES-GCM dos tokens de marketplace
- [ ] Renovação automática de token
- [ ] Ownership (Store pertence a Seller)
- [ ] Conexão de Store nunca gateada por módulo (RF-AU21)
- [ ] `store_module_activation` (migration própria; depende de `stores`)
- [ ] `EntitlementService`: `canCreateStore`, `canActivateStore`, `getActiveStores` (adiados da Fase 1.5)
- [ ] FK `token_usage.store_id → stores.id` (migration própria; coluna já existe sem FK desde o Block A da Fase 1.5 — achado do Block A, mesmo padrão do `analysis_run_id` na Fase 7)

## Fase 3 — Produtos
**Status:** não iniciada

- [ ] `seller_id` direto em `products`; `UNIQUE(seller_id, barcode)`
- [ ] `ProductVariation`; `UNIQUE(product_id, sku_variacao)`; migração dos produtos existentes para variação padrão
- [ ] Correção do bug de `listProduct` (condição invertida)
- [ ] Correção do `PUT /products/{id}` (contrato revisado para variações)
- [ ] Upload seguro (magic bytes, MIME, tamanho, nome UUID)
- [ ] Constraints de banco (`CHECK amount >= 0`, `CHECK estoque_minimo >= 0`)
- [ ] Soft delete (`deleted_at`)
- [ ] Isolamento multi-tenant reforçado no ORM (filtro Hibernate + repositório base parametrizado) — recurso de outro seller responde `404`, **não** `403` (§3.4). Não reaproveitar a `AuthorizationException` do Block 0/C da Fase 1.5 aqui: aquela é para falha de RBAC/entitlement (403, "existe mas não está liberado"); isso aqui é ownership de um recurso específico (404, "não confirma nem que existe")
- [ ] `ProductService.criarProduto` chama `EntitlementService.canCreateProduct` antes de persistir — teto de 15 produtos do trial (RN-PROD-002/003, depende da Fase 1.5)

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
- [ ] Checagem de cota antes de rodar (`EntitlementService.canExecuteAnalysis` — módulo + Store ativa + saldo, RF-AU23, depende da Fase 1.5)
- [ ] Registro em `token_usage` após cada execução que chamou a Anthropic (tokens de entrada/saída, preço/câmbio vigentes, `ciclo_referencia` = período mensal de tokens, não ciclo de cobrança — RN-TOKEN-005)
- [ ] Isolamento do pool de tokens do trial (60.000 total, corta antes de furar — RN-TRIAL-005) do pool de plano pago
- [ ] FK `token_usage.analysis_run_id → analysis_runs` (migration própria; coluna e UNIQUE já existem desde a Fase 1.5)
- [ ] `canExecuteAnalysis` passa a exigir também Store ativa no módulo (RN-ADS-002: 1 execução por Store por dia — confirmar que a checagem existe na aplicação, o índice de `analysis_runs` não é `UNIQUE`)
- [ ] Retry (erros transitórios) e circuit breaker

## Fase 8 — Frontend
**Status:** não iniciada

- [ ] Login (com tratamento de sessão expirada)
- [ ] Stores, Produtos, Vínculos, status de estoque
- [ ] Ads Analyzer (seletor de Store)
- [ ] Ambiente de dev com HTTPS (proxy com TLS): os cookies `refresh_token`/`csrf_token` são `Secure` e não voltam em `http://localhost:5173` (achado da Fase 1)

---

## Log de decisões durante a implementação

(Registrar aqui qualquer desvio do que está em `docs/software-engineering.md`
— o que mudou e por quê — para trazer de volta na revisão.)

### Fase 1.5 — Block A (schema: migrations + seed dos 6 planos)

- **Migrations `V5`–`V9`** (próximo número livre confirmado com o usuário
  antes de codar): `V5` (`plans` + `api_pricing_history` +
  `exchange_rate_snapshots`, com o seed dos 6 planos), `V6`
  (`subscriptions`), `V7` (`token_usage` + `token_package_plans` +
  `token_packages`, com seed dos 3 pacotes), `V8` (`payment_attempts` +
  `refunds`), `V9` (`trial_contestations` + `risk_signals`) — uma
  migration por item do checklist, na ordem em que os itens aparecem
  acima.
- **Achado, confirmado com o usuário antes de codar — `token_usage.store_id`
  tem o mesmo problema do `analysis_run_id`:** o doc de arquitetura declara
  `store_id CHAR(36) NOT NULL FK → stores.id`, mas `stores` só existe na
  Fase 2, não na 1.5 (mesma categoria do `analysis_run_id` → `analysis_runs`,
  que o próprio checklist já cobria). O checklist não mencionava esse caso
  explicitamente. Resolvido com o mesmo tratamento: `store_id` nasce como
  `CHAR(36) NOT NULL`, sem FK; a FK real entra numa migration própria da
  Fase 2, quando `stores` existir — junto de `store_module_activation`,
  que já estava adiada pro mesmo motivo.
- **Glossário estendido antes de codar** (`docs/glossario-traducao.md`):
  todo o vocabulário novo de planos/assinatura/billing/tokens traduzido e
  registrado antes de aparecer em qualquer migration — `plans` (v2.0,
  substitui a entrada v4.5 obsoleta), `api_pricing_history`,
  `exchange_rate_snapshots`, `subscriptions` (incluindo os 8 valores de
  `status` e os 4 de `billing_cycle`), `payment_attempts`, `refunds`,
  `trial_contestations`, `risk_signals`, `token_usage`,
  `token_package_plans`, `token_packages`. Duas traduções não óbvias,
  registradas lá com a justificativa: (1) `INADIMPLENTE` → `PAST_DUE` (não
  `DELINQUENT`/`DEFAULTED` — termo padrão de billing em inglês pra "não
  pago, dentro do prazo de tolerância", que é exatamente RN-INAD-001); (2)
  tamanho de pacote `P`/`M`/`G` → `S`/`M`/`L` (P=pequeno→Small, G=grande→
  Large — sigla de tamanho, não abreviação de palavra portuguesa a manter).
- **`plans` — seed dos 6 planos direto na migration `V5`, não numa
  migration separada:** "seed manual" (RN-PLAN-002/003) values são fixos e
  parte do schema-alvo, não dado de runtime — mesma lógica de
  `token_package_plans` (`V7`, seed dos 3 pacotes). `current_month_token_quota`
  nasce `NULL` nos 3 planos ADS_ANALYZER — só o job diário de câmbio
  (RN-TOKEN-001/002, ainda não implementado, item de jobs desta mesma
  fase) preenche; não foi calculado/estimado aqui.
- **Nenhuma entidade JPA nova nesta etapa:** Block A é só schema — os
  testes (`BillingSchemaMigrationTest`, em `billing.infrastructure`, novo
  bounded context/pacote) verificam direto via `JdbcTemplate`
  (colunas via `DatabaseMetaData`, seed via `SELECT`, constraints via
  `INSERT` que deve falhar), sem `@Entity`/repositório — mesmo padrão já
  usado por `FlywayMigrationTest` (Fase 0) pra `products`. As entidades
  JPA entram no Block B/C, quando o `SubscriptionStateMachine` e o
  `EntitlementService` precisarem ler/escrever essas tabelas de verdade.
- **`CHECK` de banco pro invariante "nenhum estado pago sem plano"**
  (`chk_subscriptions_plan_required`, doc §3) foi testado de propósito
  (`subscriptionsRejectsAPaidStatusWithoutAPlan`/
  `subscriptionsAllowsATrialStatusWithoutAPlan`) — é o único `CHECK`
  composto (não-enum) desta etapa, e é justamente o tipo de constraint
  que costuma ter erro de sintaxe/lógica silencioso se não for exercitado
  com um insert real.

### Fase 1.5 — Block B (`SubscriptionStateMachine`)

Antes de codar, reli a §13 inteira de `regras-negocio.md` (tabela +
diagrama) prestando atenção especial em toda aresta "com plano" ↔ "sem
plano", como pedido. Achei duas contradições reais entre
`regras-negocio.md` e `arquitetura-banco-de-dados.md` — **parei e
perguntei antes de codar**, não decidi sozinho:

- **`EXCLUIDO`/`DELETED` é terminal de verdade (confirmado).**
  `regras-negocio.md` §13.1/§13.2/§13.3 não tem nenhuma seta saindo de
  `EXCLUIDO`; `arquitetura-banco-de-dados.md` (nota "Reativação
  reaproveita a mesma linha", §3) dizia o contrário
  (`"RETENCAO/EXCLUIDO → ATIVO via novo checkout... pode voltar a ATIVO
  anos depois"`). Confirmado: **terminal**, sem exceção.
  `SubscriptionStateMachine.ALLOWED_TRANSITIONS` não tem entrada pra
  `DELETED` (nenhuma transição sai dele), testado
  (`deletedIsTerminalNoOutgoingTransitionIsAllowed`).
  **`arquitetura-banco-de-dados.md` ainda contém a nota errada — não a
  editei eu mesmo** (mesmo padrão desta sessão: correções de documento
  de negócio/arquitetura são feitas pelo usuário, eu só sinalizo). Precisa
  tirar `"RETENCAO/EXCLUIDO → ATIVO via novo checkout"` e
  `"EXCLUIDO... pode voltar a ATIVO anos depois"` do §3, mantendo só
  `RETENCAO → ATIVO`.
- **Novo 9º status, `WITHDRAWN`/`ARREPENDIDO` (confirmado).**
  `ATIVO → SEM_ASSINATURA` (arrependimento, RN-PAY-002/004) não tinha
  nenhum valor de `status` real definido — `SEM_ASSINATURA` não é
  armazenável, e `subscriptions` nunca é hard-deletada. Decisão: **novo
  valor de status próprio** (não reaproveita `DELETED`, que carrega
  semântica de "exclusão após retenção esgotada" — arrependimento não
  tem período de retenção nenhum, é imediato). Migration `V11` adiciona
  `WITHDRAWN` ao `CHECK` de `status` e ao `CHECK` de `plan_required`
  (isento de plano, igual `DELETED`/`TRIAL_*`). Glossário atualizado
  (`ARREPENDIDO` → `WITHDRAWN`).
  **`regras-negocio.md` ainda descreve só 8 estados (§13.1) — não editei
  eu mesmo.** Precisa: adicionar `WITHDRAWN`/`ARREPENDIDO` à tabela de
  estados (§13.1), ao diagrama (§13.2, `ATIVO --> ARREPENDIDO` no lugar
  de `ATIVO --> SEM_ASSINATURA`) e à tabela de transições (§13.3).
- **Achado extra, não perguntado ao usuário — decisão conservadora
  registrada aqui pra revisão:** o destino de `WITHDRAWN` em si (pode uma
  assinatura arrependida reiniciar depois, do jeito que `SEM_ASSINATURA`
  conceitualmente permite?) não está definido em nenhum documento.
  Modelado como **também terminal por enquanto** (sem nenhuma transição
  saindo de `WITHDRAWN` na tabela) — é o default mais seguro (state
  machine restritiva demais é mais fácil de corrigir depois do que uma
  permissiva demais), mas **não é uma decisão de negócio confirmada**,
  só um valor conservador; revisitar se o produto quiser permitir reinício
  depois de um arrependimento.

Decisões de design (não são desvio de RN, são detalhe de implementação):

- **`RETENCAO → ATIVO` é uma entrada só na tabela, cobre RN-RET-003
  (regularização simples) e RN-SUB-010 (checkout completo do zero)
  igual** — o próprio `arquitetura-banco-de-dados.md` §3 diz que "o
  schema não distingue os dois caminhos, só guarda o resultado". A
  diferença de precondição (regularizar cobrança pendente vs. rodar
  checkout do zero) é responsabilidade de quem chamar
  `regularizePayment` numa fase futura (nenhum caso de uso de
  checkout/cobrança existe ainda nesta fase) — não da tabela de
  transição em si.
- **`SEM_ASSINATURA` nunca é uma entrada do mapa de transições** (nem
  chave nem valor) — não é um `SubscriptionStatus` armazenável. Criar uma
  linha nova (trial ou pagamento direto) é uma operação diferente
  (`startTrial`/`startPaidSubscriptionDirectly`, que checam a ausência de
  linha via `SubscriptionRepository`), nunca uma chamada a
  `transitionTo`.
- **Upgrade/downgrade (`ATIVO → ATIVO`, self-loop) não passa por
  `transitionTo`:** `RF-AU24` fala especificamente de `status` — como
  esse self-loop não muda `status` (só `plan_id`), `changePlan` faz sua
  própria validação (`status == ATIVO`, `billing_cycle == MONTHLY` —
  RN-SUB-004) e persiste direto. Transporte de saldo de tokens entre
  planos (RN-SUB-005/006) fica fora de escopo — é conta do
  `EntitlementService`/Fase 7, não desta classe.
- **`withdraw()` usa `createdAt` como âncora dos 7 dias corridos
  (RN-PAY-002)** — `Subscription` não tem um campo separado de "data da
  1ª cobrança"; `createdAt` é a data de criação da linha, que só existe
  a partir do primeiro pagamento aprovado (`startPaidSubscriptionDirectly`/
  `convertTrialToPaidSubscription`), então coincide.
- **Nenhuma entidade JPA foi só CRUD:** `Subscription`
  (`billing.domain`), `SubscriptionEntity` +
  `SubscriptionJpaRepository` + `SubscriptionRepositoryPersistenceAdapter`
  (`billing.infrastructure.persistence`) seguem o mesmo padrão hexagonal
  já usado em Auth (`@JdbcTypeCode(SqlTypes.CHAR)` pra UUID,
  `@Enumerated(EnumType.STRING)` pros enums). `Module`, `SubscriptionStatus`,
  `BillingCycle` (`billing.domain`) são os 3 enums novos — `Module` já
  carrega `retentionDays()` (RN-RET-001) e `hasTrialExpiry()`
  (RN-TRIAL-006/RN-PROD-002) como métodos, reusados por várias transições
  em vez de duplicar o `if (module == ADS_ANALYZER)` em cada uma.
- **Guards que dependem de OUTRAS tabelas não foram implementados
  aqui** (contagem de `risk_signals` pra restrição automática — RN-ABUSE-002 —,
  contagem de `payment_attempts` pra saber que é a 5ª tentativa —
  RN-INAD-001/002): `restrictForRiskSignals`/`blockForNonPayment` fazem a
  transição de estado assumindo que o chamador já decidiu que a condição
  foi atingida — a decisão de "quando chamar" (o job de risco, o job de
  cobrança) é da Fase 1.5 Block D (jobs automáticos), que ainda não
  existe. Consistente com o próprio RF-AU24: esta classe só valida/executa
  a transição de `status`, não recalcula toda RN de novo.

### Fase 1.5 — Correção pós-Block A (achado do usuário)

- **`chk_subscriptions_plan_required` não isentava `DELETED`
  (`EXCLUIDO`):** bug real no doc de arquitetura (RN não estava mal
  interpretada — o próprio `CHECK` que o doc pedia estava incompleto),
  não erro de implementação — o `CHECK` da `V6` foi escrito exatamente
  como o doc especificava na hora. `TRIAL_* → DELETED` (expiração de 30
  dias do trial ADS_ANALYZER, RN-TRIAL-006, seller que nunca converteu)
  é uma transição legítima sem plano, e a constraint original rejeitava
  esse insert. `V6` já estava aplicada — corrigido via migration nova
  (`V10`, `DROP CONSTRAINT` + `ADD CONSTRAINT`), não editando `V6`. Teste
  novo (`subscriptionsAllowsAnExcludedStatusWithoutAPlan`) confirmado RED
  contra o schema antigo antes de aplicar `V10`, GREEN depois. Fica como
  lição para o Block B: qualquer transição que cruze "com plano" ↔ "sem
  plano" merece atenção redobrada — é exatamente esse tipo de aresta que
  causou o bug.
- **`token_usage.store_id → stores.id` (FK pendente) virou item explícito
  da Fase 2**, junto de `store_module_activation` — já estava identificado
  no Block A, mas só citado no log de decisões, sem entrada própria no
  checklist de nenhuma fase futura; o usuário corrigiu isso no
  `PROGRESS.md`.

### Fase 1.5 — Block 0 (pré-requisito: identidade da requisição)

- **`AccessTokenIssuer.verify` promovido a método da porta, e passa a
  devolver `RequestIdentity` (novo tipo), não mais `Claims` do JJWT:** a
  Fase 1 tinha deixado `verify` só na classe de infra
  (`JwtAccessTokenIssuer`), sem porta, "porque nenhum caso de uso da
  camada de aplicação o consome" — agora consome (`JwtAuthenticationFilter`,
  que é infra, mas depende da porta, não da classe concreta, por hexagonal).
  `RequestIdentity(UUID sellerId, Role role)` vive em
  `auth.application.ports.out`, ao lado de `AccessTokenIssuer` — devolver
  `Claims` (tipo do JJWT) pela porta vazaria um detalhe de implementação
  pros chamadores. `JwtAccessTokenIssuerTest` foi reescrito para as novas
  asserções (mesmo comportamento coberto, tipo de retorno diferente).
- **`JwtAuthenticationFilter` é deliberadamente permissivo, nunca um
  gate:** roda em toda requisição (mesmo padrão de bean de
  `RateLimitingFilter` — `@Component extends OncePerRequestFilter`, Spring
  Boot registra automaticamente, sem precisar de
  `spring-boot-starter-security`), mas token ausente/malformado/expirado
  nunca vira erro — só deixa a identidade não resolvida, exatamente como
  requisição sem header nenhum. É isso que permite a infraestrutura existir
  já, sem proteger `/products/**` (nada ali lê a identidade ainda) —
  resposta à pergunta 2 feita ao usuário antes de começar este bloco,
  confirmada antes de codar.
- **Identidade fica em `HttpServletRequest` attribute
  (`RequestIdentityContext`), não `ThreadLocal`:** o par request/response já
  é o escopo certo por requisição; não há risco de vazar identidade pra
  próxima requisição de uma thread reaproveitada em pool, que existiria com
  `ThreadLocal` se algum caminho esquecesse de limpar no `finally`.
- **RBAC (`@RequiresRole` + `RequiresRoleInterceptor` +
  `WebMvcSecurityConfig`) é opt-in por endpoint/controller, e nenhum
  endpoint usa a anotação ainda:** a Fase 1.5 (conforme o checklist abaixo)
  não introduz nenhum endpoint HTTP novo — só schema, máquina de estados,
  `EntitlementService` e jobs. A infraestrutura de RBAC fica pronta para a
  própria Fase 1.5 (se algum endpoint de checkout/assinatura surgir depois)
  ou para a Fase 2/3 usarem depois, sem exigir revisitar este bloco.
- **`shared.exceptions.AuthorizationException` (403, "Acesso negado.")
  serve tanto para RBAC (agora) quanto para "módulo não contratado" (Block
  C, mais adiante):** o item do checklist original falava especificamente
  em "403 para módulo não contratado", mas antes do `EntitlementService`
  existir não há nada concreto de módulo pra modelar — como as duas
  falhas são a mesma forma (403, mensagem genérica fixa, nunca ecoa detalhe
  interno, mesmo raciocínio já usado em `AuthenticationException`), uma
  classe só resolve as duas; Block C decide se cabe uma subclasse quando o
  `EntitlementService` existir de verdade, sem reabrir este bloco. **Nota
  da revisão do usuário (ver item da Fase 3 acima): não vale para ownership
  cruzado de recurso — aquele caso é `404` (§3.4), não `403`, e não deve
  reaproveitar esta classe.**
- **Correção do achado da Fase 1 (404 mascarado como 500):**
  `spring.mvc.throw-exception-if-no-handler-found=true` +
  `@ExceptionHandler(NoHandlerFoundException.class)` novo, registrado antes
  do catch-all de `Exception.class`. Não precisou de nada além disso —
  `spring.mvc.static-path-pattern` já tinha sido restringido a `/images/**`
  numa correção anterior (fora do escopo desta fase), então rotas
  desconhecidas fora desse prefixo já não passavam pelo resolvedor de
  recurso estático.
- **Achado de teste (corrigido antes de aplicar):** `JwtAuthenticationFilter`
  é um bean de filtro global — `@WebMvcTest` sempre o inclui no contexto,
  mesmo quando a lista `controllers = {...}` não tem nada a ver com auth
  (mesmo mecanismo, mesma categoria do achado já registrado pra
  `RateLimitingFilter` na Fase 1, mas agora com uma dependência de bean
  real). Sem um `AccessTokenIssuer` disponível nesses contextos fatiados, o
  Spring falha a subir o contexto (`UnsatisfiedDependencyException`).
  Corrigido adicionando `@MockitoBean private AccessTokenIssuer
  accessTokenIssuer;` em `ProductControllerTest` e
  `GlobalExceptionHandlerTest` — qualquer `@WebMvcTest` novo precisará do
  mesmo mock daqui pra frente, independente do que o teste em si exercita.

### Fase 1

Decisões validadas com o usuário antes de começar a codar (Lacunas
Conhecidas #5/#6 do doc de engenharia, mais duas ambiguidades que os
documentos não cobriam):

- **Algoritmo JWT:** `HS256` com `JWT_SECRET` (já existe desde a Fase
  0). Resolve a Lacuna Conhecida #5. Verificador fixa o algoritmo no
  código (nunca confia no header `alg` do token, §4.2.1).
- **Senha vazada (RF-AU14):** checagem via API pública "Pwned
  Passwords", k-anonymity (só os 5 primeiros caracteres do hash SHA-1
  da senha, nunca a senha em claro). Resolve a Lacuna Conhecida #6.
- **Envio de email (confirmação de cadastro, reset de senha):** não
  especificado nos documentos. Decisão: nesta fase, o link/token é só
  **logado no servidor** (nunca em claro se marcado `@Sensitive`), sem
  envio real — destrava o fluxo ponta a ponta sem depender de um
  provedor de email. Integração real de envio fica para decisão futura,
  fora do escopo desta fase.
- **MFA para ADMIN:** não especificado nos documentos. Decisão: TOTP
  (RFC 6238, padrão Google Authenticator/Authy) — não depende de
  provedor externo (SMS/email).
- **Escopo da migration `V2` (desvio do §11 de `arquitetura-banco-de-dados.md`):**
  o documento agrupa `sellers`, `refresh_tokens`,
  `email_confirmation_tokens`, `password_reset_tokens` **e também**
  `plans`, `api_pricing_history`, `exchange_rate_snapshots` na mesma
  `V2`. Isso contradiz `PROGRESS.md`, que — na revisão "Etapa F" que
  criou a Fase 1.5 — colocou `plans`/`api_pricing_history`/
  `exchange_rate_snapshots` explicitamente no checklist da **Fase 1.5**,
  não da Fase 1. Segui o `PROGRESS.md` (mais recente e explícito sobre
  os limites de cada fase) em vez do agrupamento antigo do doc de
  arquitetura: `V2` desta fase cobre só as 4 tabelas de Auth. `plans` e
  as demais tabelas de billing ganham sua própria migration quando a
  Fase 1.5 começar, com o próximo número de versão livre naquele
  momento. Efeito colateral: os campos `cnpj`/`cnpj_verificado_em` de
  `sellers`, que o doc separa numa `V16` tardia (alter table), entram
  direto na `V2` — a tabela ainda não existe em nenhum ambiente, não há
  razão para criar e depois alterar na mesma fase.
- **`Role` (domain enum):** o `Role.java` deixado como estava na Fase 0
  (`com.guarezi.easymanager.domain.enums`, valores `ADMIN`/`USER`, não
  usado) foi movido para `auth.domain.Role` e corrigido para
  `ADMIN`/`SELLER`, conforme o glossário e o doc de arquitetura —
  reposicionado agora que o bounded context de Auth existe de fato,
  como já estava anotado no log da Fase 0.
- **`Cadastro` — mapeamento `UUID` em tabelas novas:** `SellerEntity.id`
  precisou de `@JdbcTypeCode(SqlTypes.CHAR)` explícito, e `cnpj` de
  `columnDefinition = "CHAR(14)"` — sem isso, o Hibernate assume tipo
  nativo `UUID`/`VARCHAR(255)` por padrão pra `java.util.UUID`/`String`
  e o `ddl-auto=validate` (Fase 0) derruba o contexto reclamando que não
  bate com o `CHAR(36)`/`CHAR(14)` real da migration. Vale como padrão
  pra toda entidade nova a partir daqui (V2+): PK sempre precisa desse
  `@JdbcTypeCode`, e qualquer coluna `CHAR(n)` de tamanho fixo (como
  `cnpj`) precisa do `columnDefinition` explícito.
- **Achado, não corrigido nesta fase — `GlobalExceptionHandler` mascara
  404 como 500:** ao escrever o teste RED do cadastro, uma rota
  inexistente (`/auth/register` antes do controller existir) voltou
  `500 Internal Server Error` (`ProblemDetail` genérico), não `404` —
  porque `@ExceptionHandler(Exception.class)` (Fase 0) também captura
  `NoHandlerFoundException`. É um defeito real (uma URL errada devia
  virar `404`, não `500`), mas está fora do escopo dos itens da Fase 1
  no `PROGRESS.md`; registrado aqui para virar item de uma fase
  seguinte (ou correção pontual, a combinar) — não corrigido
  silenciosamente agora pra não misturar escopo.
- **`Política de senha` — novo tipo base de exceção em `shared`:**
  `shared.exceptions.DomainValidationException` (→ `422`, mensagem
  ecoada ao cliente, ao contrário do handler genérico de `Exception`)
  criada para `WeakPasswordException` (`auth.domain`) não forçar
  `shared.exceptions.GlobalExceptionHandler` a depender de um tipo
  específico do bounded context Auth — mantém a regra de `shared`
  pequeno e sem depender "pra baixo" dos módulos (§2.1). Outros bounded
  contexts reusam o mesmo tipo base pra suas próprias violações de
  regra de negócio, sem precisar de handler novo cada vez.
  `BreachedPasswordChecker` é uma porta (`auth.application.ports.out`)
  com implementação real via Pwned Passwords
  (`auth.infrastructure.security.PwnedPasswordsBreachedPasswordChecker`,
  `RestClient`) — os testes que passam por `AuthService`/`AuthController`
  usam um mock dessa porta (nunca chamam a API real); só o teste
  dedicado do adapter (`PwnedPasswordsBreachedPasswordCheckerTest`) bate
  numa API mockada via `MockRestServiceServer`, nenhum teste chama a
  API pública de verdade.
- **`Confirmação de email` — utilitários genéricos foram para `shared`, não
  `auth`:** `Sha256Hasher` (hash rápido pra valores de alta entropia —
  tokens, e mais adiante `payload_hash` de `webhook_events`, Fase 6) e
  `SecureTokenGenerator` (token aleatório de 256 bits, URL-safe) viraram
  `shared.security`, não `auth.infrastructure.security`, porque não têm
  nada de específico de Auth — são criptografia genérica reutilizável por
  outro bounded context depois, o mesmo raciocínio de "transversal de
  verdade" que já justifica `shared` existir (§2.1). Hashing de senha
  continua separado (`BCryptPasswordEncoder`, lento e salgado de
  propósito) — nunca confundir os dois: senha usa hash lento, token de
  alta entropia usa hash rápido, é essa distinção que motiva ter as duas
  coisas como utilitários diferentes.
  TTL do token de confirmação (24h) não está especificado em nenhum
  documento — decisão própria, valor comum pra esse tipo de link, sem
  RN correspondente pra seguir.
  `AuthService.register()` chama `EmailConfirmationService`? Não —
  emite o token diretamente (gera, faz hash, salva, loga) em vez de
  delegar a outro service, já que "criar seller" e "emitir token de
  confirmação" acontecem atomicamente no mesmo caso de uso de registro;
  `EmailConfirmationService` existe separado só pro lado de consumir o
  token (`ConfirmEmailUseCase`), que é um fluxo HTTP totalmente
  independente (dias depois, outra requisição).
- **Correção (aplicada a `Política de senha` e `Confirmação de email`):**
  as mensagens de `WeakPasswordException` e
  `InvalidEmailConfirmationTokenException` tinham saído em inglês —
  inconsistente com a mensagem genérica de 500 da Fase 0, que é em
  português seguindo o exemplo literal do §10.5 (texto de produto
  visível ao cliente da API, categoria diferente de nome de
  classe/método/comentário). Corrigido: toda mensagem de
  `DomainValidationException` (e subclasses) passa a sair em
  português, daqui pra frente também.
- **`Login` — JWT via JJWT (`io.jsonwebtoken`), não a stack de JOSE do
  Spring Security:** dependência nova no `pom.xml`
  (`jjwt-api`/`jjwt-impl`/`jjwt-jackson` 0.12.6). Motivo: já evitei
  `spring-boot-starter-security` na Fase 0/nos itens anteriores desta
  fase (pra não ligar a auto-configuração de segurança do Spring, que
  travaria `/products` e tudo mais atrás de login antes da hora — RBAC
  de verdade e proteção de endpoint não são item explícito desta fase,
  ver abaixo). JJWT sozinho resolve emissão/verificação de JWT sem essa
  auto-configuração. O algoritmo fixo (§4.2.1) é garantido pela própria
  API tipada do JJWT: `verifyWith(SecretKey)` só aceita assinatura HMAC
  compatível com aquela chave — o token nunca pode "escolher" como é
  verificado, mesmo que o header dele diga outra coisa.
  Token de acesso: 15 min (a doc diz "15–30 min", usei o limite mais
  curto). `AccessTokenIssuer` é porta (`auth.application.ports.out`);
  `JwtAccessTokenIssuer` (`auth.infrastructure.security`) é a única
  implementação. O método de verificação (`verify`) só é testado
  diretamente na classe de infra por enquanto — não virou porta ainda
  porque nenhum caso de uso da camada de aplicação o consome (não há
  filtro de autenticação de requisição nesta fase, ver próximo ponto).
- **Achado — RBAC/proteção de endpoint por JWT não está na checklist da
  Fase 1:** `docs/easy-manager-software-engineering.md` §14 lista "RBAC"
  no texto da Fase 1, mas o checklist granular do `PROGRESS.md` não tem
  nenhum item equivalente a "filtro de autenticação" ou "proteger
  endpoints com JWT" — só "Login (JWT, algoritmo fixo no verificador)",
  que implementei literalmente (emitir + verificar token, testado
  isoladamente). Não construí filtro de autenticação nem protegi nenhum
  endpoint existente (`/products/**` continua público, como já estava) —
  seria escopo não pedido por nenhum item da lista. Flagando aqui em vez
  de decidir sozinho se RBAC de verdade entra nesta fase ou fica pra uma
  fase/item futuro.
- **Login e status `PENDING`:** os documentos não dizem se um seller com
  email ainda não confirmado (`status = PENDING`) pode fazer login.
  Decisão (não bloqueante, registrada aqui pra revisão): permitido —
  só bloqueei `INACTIVE`, que é claramente "conta desativada". Não
  decidi isso por inferência do código, é uma lacuna real do documento;
  se a regra certa for bloquear `PENDING` também, é uma mudança pequena
  e isolada em `LoginService`.

### Revisão pós-item 5 (achados corrigidos)

Pedido do usuário de revisar o que já estava feito. Dois bugs reais
encontrados e corrigidos via TDD (teste novo falhando → fix → suíte
verde), ambos dentro de itens já marcados `[x]`:

- **`AuthService.register()` vazava status/id reais (Alto):** quando o
  email já existia, o código devolvia o `Seller` real — se a conta já
  estivesse `ACTIVE` (ou `INACTIVE`), a resposta mostrava esse status
  real em vez de `PENDING`, e o `id` real da conta, quebrando a RF-AU16
  pra qualquer conta já confirmada. Corrigido: a resposta para email já
  existente agora é sempre um `Seller` "fake" (id aleatório novo, mesmo
  email, status `PENDING`) — indistinguível de um cadastro novo de
  verdade, nunca carrega dado real da conta existente.
- **`LoginService.login()` tinha canal de tempo (Médio):** as checagens
  de "email desconhecido" e "conta `INACTIVE`" pulavam a comparação de
  senha (BCrypt, lenta de propósito), então respondiam muito mais rápido
  que o caminho de "senha errada" (que chama BCrypt) — mesmo a resposta
  HTTP sendo idêntica, o tempo de resposta revelava se a conta existe e
  está habilitada. Corrigido: a comparação de senha agora roda sempre,
  contra um hash fixo "dummy" (BCrypt de uma string qualquer) quando não
  há seller ou a senha não seria comparada — custo computacional igual
  em todo caminho de falha.
- **Achado não corrigido, registrado pra decisão futura (Baixo):**
  `PasswordPolicyValidator` depende de uma chamada de rede real à API
  pública do Pwned Passwords; se ela cair ou ficar lenta,
  `POST /auth/register` quebra por completo (sem timeout/circuit
  breaker configurado). Decisão de produto pendente: falhar aberto
  (deixar cadastrar sem checar vazamento) ou fechado (bloquear cadastro)
  quando a API estiver inacessível — não decidi isso sozinho.

### Throttling por conta (item novo)

- **RF-AU15 não especifica limiar nem duração do bloqueio** — decisão
  própria, registrada aqui: `sellers` ganha `failed_login_attempts` e
  `locked_until` (migration `V3`). Bloqueia a partir da **5ª tentativa
  seguida** de senha errada, com backoff exponencial a partir de 1
  minuto (`2^(tentativas-5)`), teto de 60 minutos. Zera o contador em
  login bem-sucedido. Enquanto bloqueada, a conta rejeita login **mesmo
  com a senha certa** (e essa tentativa também soma pro contador,
  estendendo o bloqueio — decisão deliberada: evita que o atacante só
  espere o bloqueio passar pra continuar tentando no mesmo ritmo).
  Mesmo esquema de custo computacional uniforme do `LoginService`
  (achado da revisão anterior) se aplica aqui — a checagem de bloqueio
  não pula a comparação de senha.

### Refresh token com rotation (item novo)

- **TTL do refresh token: 7 dias** — não especificado nos documentos
  (§4.1 só descreve o mecanismo de rotation, não a validade), decisão
  própria registrada aqui.
- **`login()` agora retorna `AuthTokens` (access + refresh), não só uma
  `String`:** mudou o contrato de `LoginUseCase` e o construtor de
  `LoginService` (trocou a dependência direta de `AccessTokenIssuer`
  por `RefreshTokenService`, que já sabe emitir o par completo) —
  refatoração que tocou `LoginServiceTest` inteiro. `POST /auth/login`
  devolve `refreshToken` no corpo da resposta JSON por enquanto —
  **temporário**, até o próximo item ("Cookie + CSRF") mover isso pra
  um cookie `HttpOnly`. `POST /auth/refresh` é novo, mesmo formato de
  resposta.
- **"Cadeia de tokens" na detecção de reuso (§4.1) interpretada como
  "todo refresh token ativo daquele seller"** — o schema não modela
  "dispositivo/sessão" separadamente (só `seller_id`, `replaced_by`),
  então não há como isolar reuso a um único dispositivo; ao detectar
  reuso, `RefreshTokenService` revoga **todos** os refresh tokens ainda
  ativos daquele seller (via `RefreshTokenRepository.findActiveBySellerId`),
  não só o encadeado a partir do token reutilizado — mais seguro (mata
  toda sessão em caso de suspeita de comprometimento), efeito
  equivalente na prática já que cada login/refresh mantém só uma linha
  ativa por vez no fluxo normal.

### Cookie + CSRF double-submit (item novo)

- **`POST /auth/login` muda de forma incompatível:** `refreshToken` saiu
  do corpo da resposta (agora só existe como cookie `refresh_token`,
  `HttpOnly`/`Secure`/`SameSite=Strict`, `Path=/auth/refresh`); o corpo
  passa a trazer `csrfToken` (o valor do double-submit, gerado uma vez
  no login, nunca rotacionado no refresh — a doc só descreve geração no
  login). `POST /auth/refresh` não recebe mais corpo — lê o refresh
  token do cookie e exige o header `X-CSRF-Token` batendo com o cookie
  `csrf_token` (não-`HttpOnly`, mesmos outros atributos).
- **`Secure` é incondicional, como a doc pede** — na prática isso
  significa que testar o fluxo de cookie num navegador real vai exigir
  HTTPS (ou um proxy dev com TLS) na Fase 8; `http://localhost:5173`
  puro não vai enviar esses cookies de volta. Não é um bug, é a
  consequência esperada de não relaxar `Secure` "quando o fluxo
  permitir" — a doc rejeita explicitamente essa flexibilização.
  `MockMvc` não simula essa restrição de transporte, então os testes
  automatizados não capturam esse problema — só apareceria testando
  com navegador de verdade.
- **Validação CSRF sem estado no servidor:** double-submit clássico —
  comparar o valor do cookie `csrf_token` com o header `X-CSRF-Token`
  é suficiente (nenhum valor fica salvo no banco), porque um pedido
  forjado cross-site consegue anexar o cookie automaticamente mas não
  consegue nem ler o corpo da resposta de login (bloqueado por CORS)
  nem setar um header customizado numa requisição simples cross-site.
  Exceção nova `shared.exceptions.CsrfValidationException` → `403`,
  mensagem genérica (nunca ecoa os valores comparados).

### Reset de senha (item novo)

- **TTL do token de reset: 1 hora** — não especificado nos documentos,
  decisão própria (mais curto que o de confirmação de email — 24h — por
  ser um fluxo mais sensível, acesso à conta).
- **`forgot-password` aplica a mesma anti-enumeração do `register`**
  (RF-AU16, prometida no item 3): `POST /auth/forgot-password` sempre
  responde `200` igual, independente do email existir; internamente só
  cria o token quando o seller existe. **Diferente do `register`, não
  implementei a mitigação de tempo de resposta aqui** (não faz `hash`
  nem operação cara no caminho "email não existe") — mesma categoria de
  achado 3 da revisão anterior (canal de tempo residual, de exploração
  bem mais difícil que o de senha, já que a diferença é só "uma consulta
  a mais", não `BCrypt`); registrado aqui como limitação conhecida, não
  corrigido agora pra não alongar mais este item.

### Rate limiting (item novo)

- **10 requisições/minuto por (IP, endpoint)** — §10.2 só lista os 5
  endpoints, não diz o número; decisão própria. Contador em memória
  (`FixedWindowRateLimiter`, janela fixa), sem dependência nova
  (evitei Bucket4j/Resilience4j pra isso — MVP de instância única não
  precisa de um limitador distribuído). Limitações conhecidas e
  registradas, não corrigidas agora: (1) reseta a cada restart da
  aplicação; (2) não coordena entre múltiplas instâncias, se a
  aplicação escalar horizontalmente; (3) o mapa de chaves cresce sem
  limite (sem expurgo de IPs antigos) — precisaria de um cache com TTL
  antes de rodar em produção por muito tempo; (4) usa
  `request.getRemoteAddr()` direto, não `X-Forwarded-For` — atrás de um
  load balancer/proxy reverso isso identificaria o proxy, não o cliente
  real (todo tráfego pareceria vir de um único IP), e mesmo se eu lesse
  esse header, confiar nele sem uma lista de proxies confiáveis permite
  spoofing — combinação de problemas que decidi não resolver nesta
  fase, já que a infraestrutura de deploy real ainda não está definida.
- **Achado de teste (corrigido antes de aplicar):** habilitar o filtro
  globalmente quebraria a suíte inteira — `MockMvc` sempre usa o mesmo
  IP simulado, e a suíte já faz bem mais de 10 chamadas/minuto pra
  `/auth/login` e `/auth/register` somando todos os testes que passam
  por esses endpoints. Resolvido com
  `app.security.rate-limit.enabled` (`@ConditionalOnProperty`,
  `matchIfMissing=true`) — vem `true` por padrão (produção/dev reais),
  o harness de teste (`mvn.sh`) desliga via `RATE_LIMIT_ENABLED=false`.
  A lógica em si continua testada (`FixedWindowRateLimiterTest`,
  `RateLimitingFilterTest`, direto na classe, sem Spring) e o wiring
  real ganhou um teste dedicado
  (`RateLimitingFilterIntegrationTest`, contexto Spring próprio que
  religa a property só pra si, sem contaminar nem ser contaminado pelo
  contexto compartilhado do resto da suíte).

### MFA para ADMIN — TOTP (item novo, último da Fase 1)

- **TOTP (RFC 6238) implementado direto no código**, não com biblioteca
  nova — o algoritmo (HOTP/RFC 4226 + Base32/RFC 4648) é pequeno e
  totalmente especificado; evitei adicionar mais uma dependência de
  terceiro para algo criptográfico compacto que dá pra verificar contra
  o vetor de teste oficial da própria RFC (`TotpGeneratorTest` confere
  o resultado publicado no Apêndice B da RFC 6238, não só um
  round-trip do próprio encode/decode — reduz bastante o risco de um
  bug sutil passar despercebido). Tolerância de ±1 passo (30s) pra
  deriva de relógio entre servidor e app autenticador.
- **Nome do arquivo `V4__add_mfa_key_to_sellers.sql`, não
  "...mfa_secret...":** mesma razão da Fase 0 — a regra de permissão
  local bloqueia caminho de arquivo contendo "secret". A coluna em si
  (dentro do SQL) chama-se `mfa_secret` normalmente, só o nome do
  arquivo evita a palavra.
- **Sem fluxo de auto-cadastro/enrollment de ADMIN nesta fase:** não
  existe endpoint de registro de admin nem de "ativar MFA" — o item
  pedido é a **exigência** de MFA no login, não a UX de configuração.
  `sellers.mfa_secret` é `NULL` até ser provisionado por fora do
  sistema (inserção manual, ou um fluxo de admin que ainda não existe).
  Decisão de design: ADMIN sem `mfa_secret` configurado falha o login
  **sempre** (fail-secure) — "MFA não provisionado" nunca significa
  "MFA opcional pra essa conta". Isso é coerente com o fato de que
  também não há nenhum jeito de CRIAR uma conta ADMIN via API ainda
  (cadastro self-service sempre cria `SELLER` — `AuthService.register`);
  contas ADMIN são provisionamento manual/fora de banda nesta fase.
- **Código TOTP soma no contador de tentativas falhas (throttling)** —
  senha certa + TOTP errado/ausente para ADMIN é tratado como uma
  tentativa de login falha igual a senha errada, incrementando
  `failed_login_attempts` e podendo bloquear a conta — mesma lógica já
  existente, sem caminho novo de exceção pra esse caso.
- **`reset-password` revoga todo refresh token ativo do seller**
  (exigido pelo próprio item) via `RefreshTokenService.revokeAllActiveTokensFor`
  (método que já existia, tornado público — reuso em vez de duplicar a
  lógica de revogação em massa). Também zera `failed_login_attempts`/
  `locked_until` — não pedido explicitamente, mas decisão própria
  razoável (quem redefine a senha com sucesso não deveria continuar
  bloqueado por tentativas antigas); registrado aqui.
- **Anti-enumeração:** o item pede register **e** forgot-password, mas
  forgot-password como endpoint só existe no item "Reset de senha" (mais
  adiante nesta mesma fase) — não faria sentido construir esse endpoint
  fora de ordem só pela resposta anti-enumeração. Resolvido agora:
  `POST /auth/register` responde `201` com o mesmo formato (id/email/
  status) tanto pra email novo quanto pra email já cadastrado, sem
  criar linha duplicada nem sobrescrever a existente; o hash da senha
  roda nos dois caminhos (mesmo quando descartado) pra não vazar a
  existência da conta por tempo de resposta. `forgot-password` aplica o
  mesmo princípio quando for implementado.

### Fase 0

- **Flyway / `V1`:** `V1__create_products_table.sql` usa o tipo nativo
  `UUID` para `products.id` (não `CHAR(36)`), porque essa migration é o
  bootstrap do schema que já existia em produção/dev (criado por
  `ddl-auto=update`), não o design-alvo. A convenção `CHAR(36)` de
  `docs/arquitetura-banco-de-dados.md` (§1) passa a valer a partir de
  `V2` (tabelas novas).
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
  corrompendo-a — **verificado manualmente pelo usuário, sem problema
  encontrado**. `.env.example` não foi criado nesta fase por bloqueio de
  permissão (`Read(./.env.*)` no `.claude/settings.json` também bloqueava
  a criação, efeito colateral não previsto da regra) — **corrigido**: a
  regra virou variantes explícitas (`.env`, `.env.local`,
  `.env.production`, `.env.development`, `.env.test`), liberando
  `.env.example`. Criar `.env.example` fica como primeiro passo da
  Fase 1, junto das variáveis novas de Auth.
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
