# Easy Manager — Documento de Engenharia de Software

**Versão:** 4.4 (revisão de segurança: correção de IDOR na exportação, tokens de auth hasheados, JWT/CORS/CSRF endurecidos, reforço estrutural de multi-tenancy)
**Data:** Setembro/2026
**Status:** Documento de arquitetura-alvo consolidado. Nenhum código dos bounded contexts novos foi escrito ainda.

**Nota sobre esta versão:** revisão de segurança de ponta a ponta sobre as versões v4.0–v4.3. Encontrou e corrigiu uma falha de IDOR introduzida pela própria v4.3 (endpoint de exportação sem checagem de ownership), armazenamento em texto puro de tokens de confirmação de email/reset de senha, ausência de defesa contra confusão de algoritmo JWT, configuração incompleta de CORS+cookie, e dependência exclusiva de convenção de código (sem reforço no ORM) para o isolamento multi-tenant. Também adiciona política de senha, proteção contra enumeração de conta, MFA para ADMIN e neutralização de injeção de fórmula CSV no documento exportado. Nenhuma decisão funcional anterior foi revertida — esta versão fecha brechas de segurança em cima do que já estava definido.

---

## 1. Visão Geral

Sem alteração em relação à v4.0: SaaS multi-seller, cada seller conecta
várias lojas em vários marketplaces (Shopee no MVP; Shein, Mercado Livre e
TikTok Shop em fase 2), com Ads Analyzer e estoque sincronizado por
webhook. O que muda nesta versão é o rigor com que isolamento de dados,
segurança de sessão e consistência de estoque são tratados — pontos que
determinam se o sistema é seguro o suficiente para operar com dados reais
de terceiros.

### 1.1 Bounded contexts (atualizado)

| Bounded context | Responsabilidade | Status |
|---|---|---|
| **Auth** | Cadastro/login, JWT + refresh token rotation, RBAC | Novo, não implementado |
| **Sellers & Stores** | Cadastro de lojas por marketplace, credenciais OAuth por loja | Novo, não implementado |
| **Produtos** | Catálogo, variações, estoque, movimentações, soft delete | Existente, sendo remodelado |
| **Vínculo Produto↔Marketplace** | Ligação entre `ProductVariation` e SKU externo, com ownership cruzado validado | Novo, não implementado |
| **Ads Analyzer** | Coleta diária, análise via IA, relatório, execução como job assíncrono | Novo, não implementado |
| **Webhooks** | Recepção de eventos, `webhook_events`, replay protection, processamento transacional | Novo, não implementado |

---

## 2. Arquitetura Consolidada

```
                         ┌─────────────────────────────┐
                         │     Frontend (React SPA)      │
                         │  Access Token → memória        │
                         │  Refresh Token → cookie         │
                         │  HttpOnly/Secure/SameSite       │
                         └───────────────┬────────────────┘
                                          │ Authorization: Bearer + Cookie
                                          │ X-Correlation-ID
                    ┌─────────────────────▼─────────────────────┐
                    │              easymanager_api                 │
                    │              Java / Spring Boot               │
                    │ ┌─────┐┌──────────┐┌─────────┐┌────────────┐│
                    │ │auth ││sellers/  ││products ││marketplace ││
                    │ │     ││stores    ││+stock   ││            ││
                    │ └─────┘└──────────┘└─────────┘└────────────┘│
                    │        ┌──────────┐┌────────────┐            │
                    │        │webhooks   ││ads          │            │
                    │        └──────────┘└────────────┘            │
                    │        shared: security/exceptions/           │
                    │                observability/infra            │
                    └───────────────────┬─────────────────────────┘
                                         │
                              ┌──────────▼──────────┐
                              │ MariaDB (Flyway)      │
                              │ constraints + índices │
                              └───────────────────────┘
                                         │
                              ┌──────────▼──────────┐
                              │ Secret Manager/KMS    │
                              │ (produção)            │
                              │ .env (desenvolvimento)│
                              └───────────────────────┘
```

### 2.1 Estrutura de pacotes do backend

```
easymanager_api
│
├── auth            (domain / application / infrastructure / presentation)
├── sellers
├── stores
├── products
├── marketplace
├── stock
├── webhooks
├── ads
└── shared
    ├── security
    ├── exceptions
    ├── observability
    └── infrastructure
```

`shared` deve permanecer pequeno — apenas o que é genuinamente
transversal (autenticação/autorização, exceptions base, logging,
correlação). Não deve virar um depósito de utilitários usados por todos os
módulos indiscriminadamente; isso corrói as fronteiras dos bounded
contexts que o resto deste documento se esforça para manter.

---

## 3. Multi-tenancy e Isolamento de Dados (novo, seção transversal)

### 3.1 Regra fundamental

Todo dado de negócio pertence, direta ou indiretamente, a um `Seller`.
Nenhum seller pode acessar dado de outro seller — mesmo conhecendo o UUID
do recurso.

```
Seller
 ├── Store
 │    ├── StoreCredential
 │    ├── ProductMarketplaceLink
 │    ├── AdSnapshot / AdReport
 │    └── StockMovement (via ProductVariation)
 │
 └── Product   (agora com seller_id direto — ver §6.1)
      └── ProductVariation
```

### 3.2 Ownership obrigatório em toda operação autenticada

Toda consulta a um recurso autenticado deve filtrar por `seller_id`, nunca
buscar só por ID:

```java
// Evitar
productRepository.findById(productId);

// Preferir
productRepository.findByIdAndSellerId(productId, sellerId);
```

Aplica-se a: `Products`, `ProductVariations`, `Stores`,
`StoreCredentials`, `ProductMarketplaceLinks`, `StockMovements`,
`AdSnapshots`, `AdReports`.

### 3.3 Ownership indireto

Quando o recurso não tem `seller_id` direto, a checagem percorre a
relação (`ProductVariation → Product → Seller`,
`ProductMarketplaceLink → Store → Seller`). Ter o papel `SELLER` não
autoriza acesso a qualquer recurso desse tipo — a autorização real é a
combinação **Autenticação + Role + Tenant (Seller) + Ownership do
recurso específico**.

### 3.4 404 em vez de 403 para recurso de outro tenant

Para um recurso pertencente a outro seller, a API responde `404 Not
Found`, não `403 Forbidden` — isso evita confirmar a um seller que um
determinado UUID existe no sistema, mesmo que não seja dele.

### 3.5 Autorização cruzada no vínculo de marketplace

Ao criar um `ProductMarketplaceLink`, validar simultaneamente que a
`ProductVariation` e a `Store` pertencem ao **mesmo** seller. Sem essa
checagem dupla, seria possível um seller A vincular um produto seu a uma
Store do seller B (ou vice-versa) — vínculo cruzado inaceitável.

### 3.6 Reforço estrutural, não só disciplina de código (novo)

A regra de §3.2 ("toda consulta filtra por `seller_id`") é uma convenção
de código — MariaDB não tem Row-Level Security nativo como o Postgres,
então nada impede, em tese, que um endpoint novo escrito às pressas
esqueça o filtro e vaze dado entre tenants, sem gerar nenhum erro visível.
Duas camadas reduzem esse risco, e devem ser usadas juntas, não como
alternativas:

- **Repositório base parametrizado por tenant**: todo repositório de
  recurso com `seller_id` (direto ou indireto) estende uma interface
  base que **exige** o `sellerId` como parâmetro em toda assinatura de
  busca — torna a versão "insegura" (`findById` sem tenant) inexistente
  no código, em vez de apenas desencorajada por convenção.
- **Filtro do Hibernate (`@FilterDef`/`@Filter`) ativado por sessão**,
  aplicando `WHERE seller_id = :sellerId` automaticamente em toda query
  daquela entidade dentro do contexto da requisição autenticada — camada
  de segurança que funciona mesmo se um desenvolvedor esquecer de
  aplicar o filtro manualmente numa query nova.

Nenhuma das duas substitui os testes de isolamento multi-tenant (§12.1);
elas reduzem a chance de a lacuna existir antes mesmo de o teste rodar.

---

## 4. Bounded Context: Auth (revisado)

### 4.1 Sessão: Refresh Token Rotation

Mantém-se access token de vida curta (15–30 min). Adiciona-se **rotação
do refresh token a cada uso**:

```
Login → Access Token A + Refresh Token A
Refresh (usa A) → revoga A → emite Access Token B + Refresh Token B
```

O refresh token antigo nunca continua válido após uso — cada refresh
consome o token corrente e emite um novo.

**Detecção de reuso:** se um refresh token já revogado for apresentado
novamente, isso é tratado como indício de comprometimento de sessão; a
cadeia de tokens daquele seller/dispositivo é revogada por completo.

### 4.2 Armazenamento no frontend

- **Access token → memória da aplicação React** (nunca `localStorage`/
  `sessionStorage`, que ficam expostos a XSS).
- **Refresh token → cookie `HttpOnly`, `Secure`, `SameSite=Strict`**
  (fixado, não "quando o fluxo permitir" — indefinição aqui é a mesma
  categoria de erro que motivou fixar UTC+8 em vez de deixar a cargo do
  host). Como cookie é usado para o refresh token, `POST /auth/refresh`
  **exige** proteção CSRF explícita (double-submit token: um valor
  gerado no login, devolvido tanto no cookie quanto em resposta lida
  pelo JS, reenviado em header customizado a cada refresh e validado no
  servidor). `SameSite=Strict` sozinho reduz o risco mas não substitui o
  double-submit — navegadores antigos e alguns fluxos de navegação
  (link direto de outro site) ainda permitem o envio do cookie.
- **CORS deve ser configurado com origem exata E
  `Access-Control-Allow-Credentials: true` juntos.** Os dois precisam
  aparecer na mesma configuração — `Access-Control-Allow-Origin: *` é
  proibido pelo navegador quando `credentials: true`, então a origem
  exata (`https://app.easymanager.com`, `http://localhost:5173` em dev)
  é obrigatória, não opcional, a partir do momento em que o refresh
  token depende de cookie.
- **`/webhooks/**` fica explicitamente fora do filtro CSRF** — os
  marketplaces fazem POST servidor-a-servidor e não têm como fornecer um
  token CSRF; a autenticidade desses endpoints vem da validação de
  assinatura (§9.2), não de CSRF.

### 4.2.1 Algoritmo de assinatura do JWT (novo)

O JWT deve ser assinado com um algoritmo forte e fixo do lado do
servidor (ex: `HS256` com `JWT_SECRET` de alta entropia, ou `RS256`/
`ES256` se houver necessidade de verificação por múltiplos serviços). O
verificador **nunca deve confiar no campo `alg` presente no próprio
token** para decidir como validá-lo — essa é a vulnerabilidade clássica
de "algorithm confusion": um token forjado anunciando `alg: none`, ou
anunciando HMAC enquanto o servidor espera RSA (usando a chave pública,
que é conhecida, como segredo HMAC), passa despercebido se a biblioteca
JWT for configurada para aceitar "o que o token disser". A configuração
correta fixa o algoritmo esperado no código do verificador,
independente do que o header do token alega.

### 4.3 Tabela `refresh_tokens` (revisada)

| Coluna | Descrição |
|---|---|
| `id` | PK |
| `seller_id` | FK |
| `token_hash` | **hash** do refresh token — nunca o token em texto puro |
| `expira_em` | |
| `criado_em` | |
| `revoked_at` | nullable |
| `replaced_by` | referência ao token que o substituiu na rotação |

### 4.4 Secrets de Auth

Chaves separadas por finalidade, nunca reaproveitadas:
`JWT_SECRET`, `MARKETPLACE_ENCRYPTION_KEY`, `WEBHOOK_SECRET`,
`EMAIL_TOKEN_SECRET`. Em desenvolvimento, variáveis de ambiente bastam;
em produção, preferir um Secret Manager/KMS.

### 4.5 Requisitos funcionais (atualizado)

Mantidos RF-AU01–RF-AU09 da v4.0, acrescidos de:

| ID | Descrição |
|---|---|
| RF-AU10 | Refresh token é rotacionado a cada uso; o token anterior é revogado |
| RF-AU11 | Reuso de refresh token revogado dispara revogação de toda a cadeia daquele seller |
| RF-AU12 | Refresh token é armazenado no backend apenas como hash |
| RF-AU13 | Frontend nunca persiste o refresh token em `localStorage`/`sessionStorage` |
| RF-AU14 | Senha exige tamanho mínimo (ex: 10 caracteres) e não pode estar em lista de senhas vazadas conhecidas (ex: verificação contra Have I Been Pwned via k-anonymity) |
| RF-AU15 | Login com senha incorreta aplica throttling progressivo por conta (não só por IP), além do rate limiting já previsto |
| RF-AU16 | `POST /auth/register` e `POST /auth/forgot-password` respondem de forma idêntica independente de o email já existir ou não, evitando enumeração de contas |
| RF-AU17 | Contas `ADMIN` exigem MFA (segundo fator) além de senha, dado o escopo de poder do papel (§3.2 da v4.0) |
| RF-AU18 | `email_confirmation_tokens.token` e `password_reset_tokens.token` são armazenados como hash, nunca em texto puro — mesma regra já aplicada a `refresh_tokens.token_hash` |

---

## 5. Bounded Context: Sellers & Stores (revisado)

Modelo de dados mantido da v4.0 (`Seller → Store → StoreCredential`),
com os seguintes ajustes:

- `store_credentials` ganha `criado_em`/`atualizado_em`.
- **Os tokens de `store_credentials` nunca devem aparecer** em logs,
  respostas HTTP, exceptions, mensagens de erro, relatórios ou dumps da
  aplicação — regra explícita de logging (ver §9.5).
- Chave de criptografia dos tokens de marketplace
  (`MARKETPLACE_ENCRYPTION_KEY`) é distinta de `JWT_SECRET` e das demais
  (reforça a decisão já tomada na v4.0 de chaves separadas, agora
  nomeada explicitamente).

---

## 6. Bounded Context: Produtos (revisado)

### 6.1 `seller_id` direto em `products`

Mesmo sendo possível descobrir o seller por relação indireta, `products`
passa a ter `seller_id` diretamente — simplifica autorização, consultas,
índices e auditoria:

```
products
----------------
id
seller_id   ← novo
sku_principal
nome
barcode
photo_url
```

### 6.2 Escopo de unicidade de SKU (decisão explícita)

- `sku_variacao` **não é globalmente único**. Escopo:
  `UNIQUE(product_id, sku_variacao)`.
- `barcode`: **`UNIQUE(seller_id, barcode)`**, não global. `barcode`
  representa uso físico interno (leitor de código de barras no depósito),
  não um identificador de mercado — cada seller tem seu próprio estoque
  físico, sua própria etiqueta, seu próprio depósito. Não há razão de
  negócio para impedir que dois sellers usem a mesma sequência de
  dígitos; a decisão segue o mesmo princípio de isolamento por tenant já
  adotado em todo o resto do modelo (§3).

### 6.3 Upload de imagem — validação e armazenamento

O requisito de upload/substituição de foto (RF-P04) ganha critérios
concretos:

- Validar: tamanho máximo, MIME type, extensão, **magic bytes**
  (não confiar apenas na extensão informada pelo cliente), dimensões,
  formato permitido.
- Nome do arquivo persistido é um identificador gerado (`UUID.webp`), não
  o nome original enviado pelo cliente — reduz colisão e problemas de
  segurança.
- Preferir Object Storage (`React → API → Object Storage → URL`) em vez
  de depender exclusivamente do filesystem do container — resolve de
  quebra a limitação já conhecida de `uploads/` sendo commitado ao git.

### 6.4 Soft delete

Para `Product`, `Store` e `ProductVariation`, avaliar `deleted_at` (ou
`ativo`) em vez de exclusão física — especialmente relevante para
produtos que já possuem `stock_movements` ou `product_marketplace_links`
associados. Exclusão física desses registros destruiria histórico de
auditoria. Foreign keys para tabelas de histórico não devem usar `CASCADE
DELETE` indiscriminado; o comportamento de cada relacionamento precisa
ser definido explicitamente.

### 6.5 Requisitos funcionais adicionais

| ID | Descrição |
|---|---|
| RF-P12 | `products.seller_id` obrigatório; toda consulta de produto filtra por ele |
| RF-P13 | Unicidade de `sku_variacao` escopada a `product_id` |
| RF-P14 | Upload de imagem validado por MIME type, extensão, magic bytes e tamanho; nome de arquivo gerado (UUID) |
| RF-P15 | Exclusão de Product/Store/ProductVariation é soft delete quando houver histórico associado |

---

## 7. Bounded Context: Vínculo Produto ↔ Marketplace (revisado)

- Mantida a Opção C (vínculo por SKU, não barcode) e o princípio de que
  o SKU principal só sugere, nunca vincula sozinho.
- **Autorização cruzada obrigatória** (ver §3.5): criar um vínculo exige
  que `ProductVariation` e `Store` pertençam ao mesmo seller.
- **Escopo de unicidade do `external_sku`:** `UNIQUE(store_id,
  external_sku)` — diferentes Stores/marketplaces podem usar o mesmo
  SKU sem conflito.

| ID | Descrição |
|---|---|
| RF-VM07 | Criar vínculo valida que `ProductVariation` e `Store` pertencem ao mesmo seller; rejeita vínculo cruzado |
| RF-VM08 | `external_sku` é único por Store (`UNIQUE(store_id, external_sku)`), não globalmente |

---

## 8. Bounded Context: Ads Analyzer (revisado)

### 8.1 Execução síncrona, disparada pelo seller (decisão fechada)

**Decisão:** o Ads Analyzer permanece **síncrono** — o seller solicita a
análise e espera a resposta HTTP com o relatório pronto. Não há fila/job
assíncrono nesta fase. O motivo de a assincronia não ser necessária agora
é o próprio limite de uso: a análise manual é **limitada a 1x por dia por
Store** (§8.4), então não existe cenário de alta concorrência de
execuções longas competindo por recursos — o único "custo" de manter
síncrono é o tempo de resposta daquela chamada isolada, aceitável dentro
do limite diário.

Se no futuro o tempo de resposta se tornar um problema prático (catálogo
muito grande, Shopee lenta em dias de pico), a migração para
`POST /ads/manual → cria Job → 202 Accepted → Worker processa` fica como
evolução natural, não como requisito do MVP.

### 8.2 Estado da execução

Introduzir conceito de execução rastreável, com `analysis_run_id`:
`store_id`, período analisado, `status` (`PENDING`/`RUNNING`/`SUCCESS`/
`FAILED`), `started_at`, `finished_at`, `error`. Isso complementa o
`UNIQUE(store_id, data)` já existente em `ad_snapshots` (mantido), dando
visibilidade de *quando* e *como* cada execução ocorreu, não só o
resultado final.

### 8.3 Timezone centralizado

A regra de UTC+8 para a Shopee não deve ser espalhada como
`ZoneOffset.of("+8")` pelo código. Centralizar em algo como
`MarketplaceTimezone.SHOPEE`, facilitando a chegada de Mercado Livre,
Shein e TikTok Shop com fusos/regras próprios.

### 8.4 Limitação de execução manual (decisão fechada)

Execução manual sob demanda é útil, mas gera custo direto (chamadas à
Shopee e à Anthropic). **Decisão: limite fixo de 1 execução manual por
Store por dia** (`MAX_MANUAL_RUNS_PER_STORE_PER_DAY = 1`), sem
necessidade de cooldown adicional — o limite diário já é a proteção,
tanto de custo quanto de sobrecarga das APIs externas. A execução
agendada (automática, diária) conta separadamente e não consome esse
limite.

### 8.5 Requisitos funcionais (§8.1–8.4)

| ID | Descrição |
|---|---|
| RF-A16 | Execução do Ads Analyzer é rastreada por `analysis_run_id`, com status e timestamps |
| RF-A17 | Timezone por marketplace é centralizado em um único ponto de configuração |
| RF-A18 | Execução manual é limitada a 1x por dia por Store, independente da execução agendada |
| RF-A19 | `POST /ads/manual` está sob rate limiting |

### 8.6 Exportação de dados brutos para análise externa, por Store (revisado)

**Correção de escopo em relação ao rascunho anterior desta versão:** não
se trata de integração automática com outra IA (nada de chamada HTTP a
um provedor externo, nem armazenamento de chave de terceiro). É mais
simples e não introduz nenhuma superfície de segurança nova: o **Easy
Manager gera um documento de texto** com os dados já coletados, o seller
baixa esse documento e leva manualmente para a IA de sua preferência,
fora do sistema.

**Fluxo:** `GET /ads/export/{storeId}/{data}` → **valida que `storeId`
pertence ao seller autenticado (§3.2, mesma regra de ownership de todo o
resto do sistema — nenhuma exceção para este endpoint)** → sistema busca
o `AdSnapshot`/`SkuMetric` já salvo daquela Store/data (o mesmo dado que
já existe da coleta diária, sem precisar de nova chamada à Shopee) →
monta um documento de texto simples (Markdown ou `.txt`) com os dados
brutos consolidados por SKU (vendas, unidades, gasto de anúncio, cliques,
impressões, ACOS) → retorna para download.

**Vulnerabilidade corrigida nesta revisão:** o desenho original desta
seção (v4.3) não declarava a checagem de ownership sobre `storeId`, o
que teria permitido a qualquer seller autenticado trocar o UUID na URL e
baixar dados de vendas/anúncio de uma Store de outro seller (IDOR). A
regra de ownership de §3.2 se aplica aqui como a qualquer outro endpoint
— não há exceção para leitura "só de exportação".

**Penalidade mantida, agora pelo motivo certo:** o documento exportado
contém apenas os **dados brutos do dia solicitado** — sem a comparação
com o snapshot do dia anterior (RF-A06) e sem o vínculo produto/estoque
(RF-A11). Essas duas informações vivem dentro do `AdAnalysisService` e do
prompt determinístico da Anthropic; gerar um documento textual simples
para uso manual fora do sistema não tenta reproduzir esse enriquecimento.

**O que isso NÃO precisa:**
- Nenhuma tabela de credencial de terceiro (`external_ai_configs` foi
  removida do desenho).
- Nenhuma chave de criptografia nova (`EXTERNAL_AI_ENCRYPTION_KEY` não é
  necessária).
- Nenhum adapter novo de IA (`AiAnalysisClient`/`AnthropicApiAdapter`
  seguem intocados — a exportação não passa por eles).
- Nenhuma alteração em `ad_reports` (o relatório gerado pela Anthropic
  continua sendo uma coisa; o documento exportado é outra, gerada sob
  demanda, não persistida).

**Custo e limites:** como a exportação só lê dado já coletado (não
dispara nova chamada à Shopee nem à Anthropic), **não está sujeita ao
limite de 1 execução manual por dia** (RF-A18) — esse limite protege
custo de coleta/análise, que a exportação não consome.

### 8.7 Requisitos funcionais da exportação

| ID | Descrição |
|---|---|
| RF-A20 | Seller pode exportar, sob demanda, um documento de texto com os dados brutos consolidados (por SKU: vendas, unidades, gasto de anúncio, cliques, impressões, ACOS) de uma Store em uma data específica |
| RF-A21 | O documento exportado não inclui comparação com snapshot anterior nem vínculo produto/estoque |
| RF-A22 | A exportação é gerada a partir do snapshot já salvo — não dispara nova coleta na Shopee nem chamada à Anthropic |
| RF-A23 | A exportação não é limitada pelo RF-A18 (não consome custo de coleta/análise) |
| RF-A24 | `storeId` do endpoint de exportação é validado contra o seller autenticado antes de qualquer leitura (ownership obrigatório, sem exceção) |
| RF-A25 | Campos de origem externa (nome de produto, SKU) que comecem com `=`, `+`, `-` ou `@` são neutralizados (prefixados com aspas simples ou caractere de escape) antes de entrar no documento exportado, prevenindo injeção de fórmula caso o seller abra o arquivo como CSV no Excel |

---

## 9. Bounded Context: Webhooks (revisão significativa)

### 9.1 Camada de eventos — nova tabela `webhook_events`

Além de `stock_movements`, uma tabela dedicada a **todo evento recebido**,
independente de gerar ou não uma movimentação de estoque:

| Coluna | Descrição |
|---|---|
| `id` | PK |
| `store_id` | FK |
| `event_id` | identificador do evento fornecido pelo marketplace, quando disponível |
| `event_type` | |
| `payload_hash` | hash do payload recebido (não o payload sensível completo em log) |
| `received_at` | |
| `processed_at` | nullable |
| `status` | `RECEIVED` / `PROCESSING` / `PROCESSED` / `FAILED` / `IGNORED` |
| `error_message` | nullable |

`UNIQUE(store_id, event_id)` quando o marketplace fornecer um `event_id`
confiável — camada de idempotência **do evento**, separada da
idempotência **do pedido** (`UNIQUE(store_id, external_order_id)` em
`stock_movements`, mantida da v4.0). As duas camadas coexistem:

```
Evento    → event_id           → idempotência do webhook (webhook_events)
Pedido    → external_order_id  → idempotência da movimentação (stock_movements)
```

### 9.2 Proteção contra replay

Não confiar apenas na URL (`POST /webhooks/shopee/orders`) para
autenticidade. Quando a API do marketplace suportar, validar em sequência:

```
Validar HTTPS → assinatura → timestamp → estrutura →
evento duplicado → processar
```

### 9.3 Processamento transacional

O processamento do webhook é uma transação única — não pode persistir
parcialmente:

```
BEGIN TRANSACTION
 1. Verificar evento duplicado
 2. Registrar evento em webhook_events
 3. Encontrar Store
 4. Verificar stock_sync_status
 5. Encontrar ProductMarketplaceLink
 6. Encontrar ProductVariation
 7. Atualizar estoque (atômico, ver §9.4)
 8. Criar StockMovement
 9. Marcar evento como PROCESSED
COMMIT / ROLLBACK em caso de erro
```

### 9.4 Atualização atômica do estoque — correção de concorrência

**Ponto crítico identificado:** carregar a entidade via JPA, decrementar
em memória e salvar (`amount = amount - quantidade`) permite uma
condição de corrida real — dois webhooks concorrentes lendo `amount = 1`
podem ambos concluir "ainda há estoque" e ambos decrementarem, resultando
em duas vendas processadas para uma única unidade.

**Correção:** update atômico direto no banco, condicionado ao estoque
disponível:

```sql
UPDATE product_variations
SET amount = amount - :quantity
WHERE id = :variationId
  AND amount >= :quantity;
```

- `rows affected = 1` → baixa realizada com sucesso.
- `rows affected = 0` → estoque insuficiente; `StockMovement` é
  registrada com `status = ESTOQUE_INSUFICIENTE`, sem decrementar nada.

Isso roda dentro da mesma transação do §9.3.

### 9.5 Regra de estoque negativo — dupla camada

Mantida a regra de domínio em Java (`amount >= 0`), **reforçada por
constraint de banco**: `CHECK (amount >= 0)` em `product_variations`, e
o mesmo para `estoque_minimo >= 0`. A validação não deve depender
exclusivamente da camada Java.

### 9.6 `stock_movements` — auditoria ampliada

| Coluna | Descrição |
|---|---|
| `id` | |
| `product_variation_id` | nullable |
| `store_id` | nullable |
| `external_order_id` | nullable |
| `external_event_id` | nullable *(novo)* |
| `tipo` | VENDA / AJUSTE_MANUAL / ENTRADA |
| `origem` | WEBHOOK / MANUAL / BATCH |
| `quantidade` | |
| `status` | OK / SEM_VINCULO / AGUARDANDO_ATIVACAO / **ESTOQUE_INSUFICIENTE** / **ERRO** *(dois novos)* |
| `criado_em` | |

Movimentações **nunca são editadas ou apagadas** para corrigir algo —
uma correção gera uma nova movimentação (ex: `ENTRADA +10`, depois
`AJUSTE_MANUAL -1`), preservando a trilha de auditoria completa.

### 9.7 Reconciliação — webhook não é a única fonte de verdade

Webhooks podem chegar atrasados, duplicados, ou simplesmente não chegar.
Um mecanismo de **reconciliação periódica**, complementar ao webhook,
compara o que foi recebido/processado contra a API do marketplace:

```
             ┌── Webhook ──→ estoque (tempo real)
Marketplace ─┤
             └── API ──────→ reconciliação (periódica)
```

**Decisão: versão mínima entra já na Fase 6 (Webhooks) do MVP**, não fica
adiada para depois. Justificativa: sem nenhuma reconciliação, um pedido
perdido (webhook não entregue, endpoint fora do ar por alguns minutos)
nunca é detectado — o estoque fica incorreto indefinidamente até o seller
notar uma divergência já antiga, o que é inaceitável para um sistema que
mexe com estoque de terceiros.

A versão mínima do job de reconciliação, por Store, uma vez ao dia:
compara **a quantidade de pedidos que a API do marketplace lista para o
dia anterior** contra **a quantidade de `webhook_events` registrados para
o mesmo dia e Store**. Se os números não baterem, gera um alerta para o
seller investigar — **não corrige nada automaticamente** (nem
reprocessa, nem ajusta estoque sozinho). Essa versão mínima é
significativamente mais barata de construir que a reconciliação completa
(pedido a pedido, com correção automática de divergência), que fica como
evolução pós-MVP.

### 9.8 Comportamento explícito na ativação (`AGUARDANDO_ATIVACAO`)

A transição de estados por Store permanece (`NAO_CONFIGURADO → EM_REVISAO
→ ATIVO`), mas o comportamento **no momento da ativação** passa a ser
explícito: ao ativar, o sistema não deve simplesmente processar
cegamente todos os webhooks antigos acumulados. Em vez disso:

1. Verificar eventos pendentes (`webhook_events` com status
   `RECEIVED`/`IGNORED` daquela Store).
2. Reconciliar com a API do marketplace antes de aplicar retroativamente.
3. Processar apenas os eventos que possam ser relacionados com segurança
   (vínculo existente, sem ambiguidade).
4. Registrar divergências que não puderem ser resolvidas automaticamente,
   como pendência visível ao seller.

### 9.9 Requisitos funcionais (atualizado, substituindo RF-W01–RF-W07 da v4.0)

| ID | Descrição |
|---|---|
| RF-W01 | Receber webhook por marketplace, validando assinatura, timestamp e estrutura antes de processar |
| RF-W02 | Registrar todo evento recebido em `webhook_events`, com `UNIQUE(store_id, event_id)` quando disponível |
| RF-W03 | Processar o webhook de forma transacional (evento + estoque + movimentação, tudo ou nada) |
| RF-W04 | Atualizar estoque via update atômico condicional (`WHERE amount >= quantidade`), nunca leitura-depois-escrita em memória |
| RF-W05 | Registrar `ESTOQUE_INSUFICIENTE` quando o update atômico não afetar nenhuma linha |
| RF-W06 | Registrar pedidos sem vínculo (`SEM_VINCULO`) e pedidos pré-ativação (`AGUARDANDO_ATIVACAO`), sem descartar o evento |
| RF-W07 | Seller pode ativar/pausar sincronização por Store; ativação dispara verificação/reconciliação dos eventos pendentes, não processamento cego |
| RF-W08 | Constraint de banco `CHECK (amount >= 0)` em `product_variations` |
| RF-W09 | Job diário de reconciliação (versão mínima, no MVP) compara nº de pedidos da API do marketplace vs. nº de `webhook_events` do mesmo dia/Store; diferença gera alerta ao seller, sem correção automática |

---

## 10. Infraestrutura, Segurança e Observabilidade (expandido)

### 10.1 CORS

Remover `@CrossOrigin("*")` da aplicação em produção. Configurar
origem explícita (ex: `https://app.easymanager.com`), e em
desenvolvimento `http://localhost:5173`. Torna-se ainda mais importante
ao usar cookie para o refresh token.

### 10.2 Rate limiting (expandido)

Além de `POST /auth/register` e `POST /auth/login` (já previstos),
adicionar: `POST /auth/refresh`, `POST /auth/forgot-password`,
`POST /auth/reset-password`, `POST /webhooks/**`, `POST /ads/manual`.

### 10.3 Logging — política explícita

Nunca logar: `access_token`, `refresh_token`, JWT, chave de criptografia,
senha, payload completo de webhook. Preferir logar: `requestId`,
`sellerId`, `storeId`, `eventId`, `externalOrderId`, `status`, `duration`.

### 10.4 Correlation ID

Adicionar `X-Correlation-ID` propagado do frontend, através de
service/repository, até os logs — permite investigar "webhook falhou" ou
"Shopee retornou erro" sem expor dado sensível.

### 10.5 `ProblemDetail` sem detalhe interno em produção

Manter `ProblemDetail` + `@RestControllerAdvice`, mas nunca retornar
stack trace ou detalhe técnico ao cliente:

```json
{
  "type": "...",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "Não foi possível processar a solicitação.",
  "instance": "/products/123"
}
```

Detalhe técnico completo fica restrito aos logs internos.

### 10.6 Constraints e índices no banco

Além da validação em Java, aplicar no MariaDB: `NOT NULL`,
`FOREIGN KEY`, `UNIQUE`, `CHECK` — especialmente em `stores.seller_id`,
`products.seller_id`, `product_variations.product_id`,
`store_credentials.store_id`,
`product_marketplace_links.(store_id, product_variation_id)`.

Índices planejados para os padrões de consulta mais frequentes:
`products.seller_id`, `product_variations.product_id`,
`stores.seller_id`, `stock_movements.(store_id,
product_variation_id)`, `ad_snapshots.store_id`,
`product_marketplace_links.store_id`, e os campos usados nas constraints
de idempotência (`event_id`, `external_order_id`, `external_sku`).

### 10.7 Retry e Circuit Breaker (refinado)

Resilience4j mantido, mas retry só faz sentido para erros transitórios
(timeout, 502, 503, 429), nunca para 400/401/403 — e deve respeitar
`Retry-After` quando fornecido. Circuit breaker adicional para Shopee,
Anthropic e (fase 2) Shein/Mercado Livre/TikTok Shop, evitando bombardear
uma API já degradada.

### 10.8 Segurança dos endpoints — estrutura explícita

```
/auth/register        → público
/auth/login           → público
/auth/refresh         → público/autenticado via cookie
/auth/reset-password  → fluxo próprio
/webhooks/**          → autenticação por assinatura (não JWT)
/products/**          → SELLER (ownership)
/stores/**            → SELLER (ownership)
/marketplace-links/** → SELLER (ownership cruzado)
/ads/**               → SELLER (ownership) + rate limit
/admin/**             → ADMIN
```

### 10.9 Auditoria administrativa

Ações sensíveis do ADMIN (ativar/desativar seller) registram
`admin_id`, `seller_id`, ação, timestamp, IP e resultado.

### 10.10 Documentação (OpenAPI/Swagger)

Dado o tamanho crescente da API, documentar autenticação, roles, códigos
HTTP (`401/403/404/409/422/429/500`), payloads, paginação, semântica de
webhooks e idempotência.

---

## 11. Fase 2 (sem alteração de escopo, reafirmado)

Implementação real de `SheinApiAdapter`/`MercadoLivreApiAdapter`/
`TikTokShopApiAdapter`; login social; separação usuário/perfil de
negócio; impersonação com consentimento; estoque "disponível vs
comprometido"; alocação de estoque por canal.

---

## 12. Testes de Segurança (novo)

### 12.1 Isolamento multi-tenant

Seller A cria um produto; Seller B tenta `GET`/alterar esse produto →
esperado `404`/rejeição, nunca sucesso.

### 12.2 Concorrência de estoque

Dois eventos concorrentes decrementando uma variação com `amount = 1`:
exatamente um deve suceder, o outro deve falhar por
`ESTOQUE_INSUFICIENTE` — nunca os dois sucedendo.

### 12.3 Idempotência

Mesmo `event_id`/`external_order_id` reenviado 3 vezes → exatamente uma
`StockMovement` criada, não três.

### 12.4 Replay

Webhook válido processado uma vez; o mesmo webhook reenviado deve ser
identificado e ignorado.

### 12.5 Matriz mínima de autorização

| Recurso | SELLER dono | SELLER externo | ADMIN |
|---|---|---|---|
| Próprios produtos | ✓ | — | ✗ |
| Produtos de outro seller | ✗ | ✗ | ✗ |
| Próprias Stores | ✓ | — | status apenas |
| Store de outro seller | ✗ | ✗ | status apenas |
| Próprios relatórios | ✓ | — | ✗ |
| Relatório de outro seller | ✗ | ✗ | ✗ |
| Credenciais de Store | interno (nunca exposto) | ✗ | ✗ |

---

## 13. Lacunas Conhecidas (atualizado)

Persistentes desde a v4.0:
1. `ProductController.listProduct` com condição invertida
2. `PUT /products/{id}` descarta campos (a resolver junto da introdução
   de `ProductVariation`)
3. Sem testes automatizados além do smoke test de contexto
4. Nome do endpoint de Ads da Shopee não confirmado para todas as contas

As três pendências levantadas na v4.1 (escopo de unicidade do `barcode`,
prioridade da reconciliação, momento de assincronia do Ads Analyzer)
foram decididas na v4.2 — ver §6.2, §9.7 e §8.1/§8.4 respectivamente.

Da revisão de segurança (v4.4): os itens críticos e altos (IDOR na
exportação, tokens de auth em texto puro, algoritmo JWT, CORS+CSRF,
isolamento multi-tenant só por convenção) foram corrigidos diretamente
nas seções correspondentes desta versão. Seguem como itens de
implementação a confirmar, não como lacunas de design:
5. Escolha final entre `HS256` com segredo de alta entropia vs.
   `RS256`/`ES256` para o JWT — qualquer um atende, mas precisa ser
   decidido antes da Fase 1 do roadmap.
6. Ferramenta de verificação de senha vazada (RF-AU14) a escolher — ex:
   API "Pwned Passwords" via k-anonymity, sem enviar a senha em claro.

---

## 14. Roadmap Técnico (substituído — ordem por fase de segurança primeiro)

**Fase 0 — Segurança e infraestrutura**
Flyway; estrutura multi-tenant (ownership em todas as consultas);
security baseline; CORS restritivo; separação de secrets; exception
handling (`ProblemDetail` sem stack trace); logging sem segredos;
correlation ID.

**Fase 1 — Auth**
Cadastro; confirmação de email; login; JWT; refresh token rotation;
reset de senha; rate limiting; RBAC.

**Fase 2 — Sellers/Stores**
Seller; Store; OAuth Shopee; criptografia dos tokens; renovação de
tokens; ownership.

**Fase 3 — Produtos**
`ProductVariation`; migração dos produtos existentes; correção dos bugs
conhecidos; upload seguro; validações; constraints de banco.

**Fase 4 — Marketplace Links**
`ProductMarketplaceLink`; busca de produtos da Store; sugestão por SKU
principal; vínculo manual; ownership cruzado.

**Fase 5 — Estoque**
`StockMovement`; atualização atômica; controle de concorrência;
auditoria; testes de idempotência.

**Fase 6 — Webhooks**
`webhook_events`; validação de assinatura; replay protection;
idempotência dupla; processamento transacional; ativação por Store com
reconciliação.

**Fase 7 — Ads Analyzer**
Coleta; paginação; timezone centralizado; snapshot; análise via IA; rate
limit/custo de execução manual; retry; circuit breaker; notificações.

**Fase 8 — Frontend**
Login (com tratamento de sessão expirada); Stores; Produtos; Vínculos;
status de estoque; Ads Analyzer.

---

## 15. Checklist de Segurança do MVP

```
[ ] Isolamento completo entre sellers
[ ] Ownership em todos os endpoints, incluindo os de leitura/exportação
[ ] Isolamento multi-tenant reforçado no ORM (filtro Hibernate), não só por convenção de código
[ ] ADMIN não acessa dados de negócio
[ ] ADMIN exige MFA
[ ] JWT curto
[ ] JWT com algoritmo fixo no verificador (nunca confia no `alg` do token)
[ ] Refresh token com rotation
[ ] Refresh token armazenado com segurança (cookie HttpOnly/Secure/SameSite=Strict)
[ ] Refresh token armazenado somente como hash
[ ] Tokens de confirmação de email e reset de senha armazenados como hash
[ ] CSRF (double-submit) em /auth/refresh
[ ] CORS com origem exata + Access-Control-Allow-Credentials juntos
[ ] /webhooks/** excluído do filtro CSRF
[ ] Política de senha (tamanho mínimo, checagem contra vazamentos conhecidos)
[ ] Throttling por conta em login, além de rate limit por IP
[ ] Respostas de registro/reset de senha não revelam se o email existe
[ ] Secrets fora do Git
[ ] AES-GCM para tokens das marketplaces
[ ] Chaves separadas por finalidade
[ ] Rate limiting (auth, refresh, webhooks, ads manual)
[ ] Webhook signature validation
[ ] Replay protection
[ ] Idempotência (evento + pedido)
[ ] webhook_events implementado
[ ] Transações no processamento de webhook
[ ] Atualização atômica do estoque
[ ] Proteção contra estoque negativo (Java + CHECK constraint)
[ ] Ledger de estoque (stock_movements, nunca editado)
[ ] Reconciliação de marketplace (ao menos planejada)
[ ] Upload seguro (magic bytes, UUID de arquivo)
[ ] Neutralização de injeção de fórmula CSV no documento exportado do Ads Analyzer
[ ] Logs sem secrets
[ ] Correlation ID
[ ] ProblemDetail sem stack trace em produção
[ ] Constraints no banco (NOT NULL, FK, UNIQUE, CHECK)
[ ] Índices nos padrões de consulta e idempotência
[ ] Flyway (sem ddl-auto=update em produção)
[ ] Testes de isolamento multi-tenant
[ ] Testes de concorrência de estoque
[ ] Testes de idempotência
[ ] Testes de replay
[ ] Testes de autorização (matriz completa)
[ ] Limitação de execução manual do Ads Analyzer
[ ] Retry somente para erros transitórios (respeitando Retry-After)
[ ] Circuit breaker nas integrações externas
```

---

## 16. Glossário (atualizado)

| Termo | Definição |
|---|---|
| Ownership | Verificação de que um recurso pertence ao seller autenticado antes de qualquer leitura/escrita |
| Refresh Token Rotation | Prática de invalidar o refresh token a cada uso, emitindo um novo |
| webhook_events | Tabela de registro de todo evento de webhook recebido, com idempotência por `event_id` |
| Replay attack | Reenvio de uma requisição/webhook legítimo capturado anteriormente, tentando ser processado de novo |
| Atualização atômica | Update condicional no banco (`WHERE amount >= quantidade`) que evita condição de corrida entre requisições concorrentes |
| Reconciliação | Processo periódico que compara o estado local com a API do marketplace para detectar divergências não capturadas por webhook |
| Circuit Breaker | Padrão que interrompe temporariamente chamadas a uma API externa já falhando, evitando cascata de falhas |
| Correlation ID | Identificador único propagado por uma requisição, usado para rastrear seu caminho pelos logs |
