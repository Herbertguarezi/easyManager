# Easy Manager — Arquitetura de Banco de Dados

**Versão:** 1.1 (corrige armazenamento em texto puro de tokens de auth, conforme revisão de segurança v4.4 do documento de engenharia)
**Banco:** MariaDB 11
**Migração:** Flyway (substitui `ddl-auto=update`)

---

## 1. Convenções gerais

- **Chave primária:** `UUID`, gerado na aplicação (`UUID.randomUUID()`), armazenado como `CHAR(36)` — mesma convenção já usada em `products` hoje, mantida por consistência em todas as tabelas novas.
- **Timestamps:** `criado_em` (`DATETIME`, `NOT NULL`, default `CURRENT_TIMESTAMP`) em toda tabela; `atualizado_em` nas tabelas que sofrem update (não em tabelas somente-append, como `stock_movements` e `webhook_events`).
- **Soft delete:** `deleted_at` (`DATETIME`, nullable) em `products`, `stores`, `product_variations` — nunca `DELETE` físico nessas três, por causa do histórico associado (`stock_movements`, `product_marketplace_links`).
- **Enums:** representados como `VARCHAR` com `CHECK` constraint (mais portável entre versões do MariaDB e mais simples de evoluir via Flyway do que `ENUM` nativo).
- **Segredos em coluna:** colunas `*_cripto` armazenam o valor já criptografado pela aplicação (AES-GCM) — o banco nunca vê o token em claro.
- **Nunca `CASCADE DELETE`** em tabelas de histórico (`stock_movements`, `webhook_events`, `ad_snapshots`, `ad_reports`, `sku_metrics`). Uso de `ON DELETE RESTRICT` (ou `SET NULL` quando o campo já é nullable, como `product_variation_id` em `stock_movements`).

---

## 2. Bounded Context: Auth

### `sellers`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `email` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` |
| `senha_hash` | `VARCHAR(255)` | `NOT NULL` |
| `role` | `VARCHAR(20)` | `NOT NULL`, `CHECK (role IN ('ADMIN','SELLER'))` |
| `status` | `VARCHAR(20)` | `NOT NULL`, `CHECK (status IN ('PENDENTE','ATIVO','INATIVO'))` |
| `email_confirmado_em` | `DATETIME` | nullable |
| `criado_em` | `DATETIME` | `NOT NULL` |
| `atualizado_em` | `DATETIME` | `NOT NULL` |

Índices: `UNIQUE(email)` (já cobre lookup de login).

### `refresh_tokens`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `seller_id` | `CHAR(36)` | `NOT NULL`, FK → `sellers.id` |
| `token_hash` | `VARCHAR(255)` | `NOT NULL` |
| `expira_em` | `DATETIME` | `NOT NULL` |
| `criado_em` | `DATETIME` | `NOT NULL` |
| `revoked_at` | `DATETIME` | nullable |
| `replaced_by` | `CHAR(36)` | nullable, FK → `refresh_tokens.id` |

Índices: `INDEX(seller_id)`, `INDEX(token_hash)`.

### `email_confirmation_tokens` / `password_reset_tokens`
Mesmo formato para as duas:

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `seller_id` | `CHAR(36)` | `NOT NULL`, FK → `sellers.id` |
| `token_hash` | `VARCHAR(255)` | `NOT NULL`, `UNIQUE` — **hash** do token, nunca o valor em texto puro (corrigido na revisão de segurança v4.4; o valor original só existe no link enviado por email, nunca persistido) |
| `expira_em` | `DATETIME` | `NOT NULL` |
| `usado_em` | `DATETIME` | nullable |

---

## 3. Bounded Context: Sellers & Stores

### `stores`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `seller_id` | `CHAR(36)` | `NOT NULL`, FK → `sellers.id` |
| `marketplace` | `VARCHAR(30)` | `NOT NULL`, `CHECK (marketplace IN ('SHOPEE','SHEIN','MERCADO_LIVRE','TIKTOK_SHOP'))` |
| `nome_da_loja` | `VARCHAR(255)` | `NOT NULL` |
| `ativo` | `BOOLEAN` | `NOT NULL DEFAULT TRUE` |
| `stock_sync_status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'NAO_CONFIGURADO'`, `CHECK (... IN ('NAO_CONFIGURADO','EM_REVISAO','ATIVO','PAUSADO'))` |
| `deleted_at` | `DATETIME` | nullable |
| `criado_em` / `atualizado_em` | `DATETIME` | `NOT NULL` |

Índices: `INDEX(seller_id)`.

### `store_credentials`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `store_id` | `CHAR(36)` | `NOT NULL`, `UNIQUE`, FK → `stores.id` |
| `access_token_cripto` | `TEXT` | `NOT NULL` |
| `refresh_token_cripto` | `TEXT` | `NOT NULL` |
| `expira_em` | `DATETIME` | `NOT NULL` |
| `criado_em` / `atualizado_em` | `DATETIME` | `NOT NULL` |

**Nunca aparecem em log/resposta HTTP/exception** — regra de aplicação, não de schema, mas registrada aqui porque é a tabela mais sensível do banco.

**Nota (v4.3):** a exportação de dados brutos para análise em IA externa
(documento de texto gerado sob demanda, ver documento de engenharia
§8.6–8.7) **não introduz tabela nova** — é gerada a partir de
`ad_snapshots`/`sku_metrics`, já existentes, e não é persistida. Não há
armazenamento de credencial de terceiro nem chave de criptografia
adicional para essa funcionalidade.

---

## 4. Bounded Context: Produtos

### `products`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `seller_id` | `CHAR(36)` | `NOT NULL`, FK → `sellers.id` |
| `sku_principal` | `VARCHAR(100)` | `NOT NULL` |
| `nome` | `VARCHAR(255)` | `NOT NULL` |
| `barcode` | `VARCHAR(100)` | `NOT NULL` |
| `photo_url` | `VARCHAR(500)` | nullable (upload pode acontecer depois da criação) |
| `deleted_at` | `DATETIME` | nullable |
| `criado_em` / `atualizado_em` | `DATETIME` | `NOT NULL` |

Constraints: `UNIQUE(seller_id, barcode)`.
Índices: `INDEX(seller_id)`.

### `product_variations`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `product_id` | `CHAR(36)` | `NOT NULL`, FK → `products.id` |
| `sku_variacao` | `VARCHAR(100)` | `NOT NULL` |
| `atributo` | `VARCHAR(100)` | `NOT NULL` (ex: "Tamanho 40") |
| `amount` | `INT` | `NOT NULL DEFAULT 0`, `CHECK (amount >= 0)` |
| `estoque_minimo` | `INT` | `NOT NULL DEFAULT 0`, `CHECK (estoque_minimo >= 0)` |
| `deleted_at` | `DATETIME` | nullable |
| `criado_em` / `atualizado_em` | `DATETIME` | `NOT NULL` |

Constraints: `UNIQUE(product_id, sku_variacao)`.
Índices: `INDEX(product_id)`.

---

## 5. Bounded Context: Vínculo Produto ↔ Marketplace

### `product_marketplace_links`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `product_variation_id` | `CHAR(36)` | `NOT NULL`, FK → `product_variations.id` |
| `store_id` | `CHAR(36)` | `NOT NULL`, FK → `stores.id` |
| `external_sku` | `VARCHAR(100)` | `NOT NULL` |
| `matched_by` | `VARCHAR(30)` | `NOT NULL`, `CHECK (... IN ('MANUAL','SKU_PRINCIPAL_SUGERIDO'))` |
| `criado_em` | `DATETIME` | `NOT NULL` |

Constraints: `UNIQUE(store_id, external_sku)` — **não** globalmente único, pois marketplaces diferentes podem usar o mesmo SKU.
Índices: `INDEX(store_id)`, `INDEX(product_variation_id)`.

**Validação de aplicação (não expressável só em constraint de banco):** `product_variation_id` e `store_id` devem pertencer ao mesmo `seller_id` — checagem feita na camada de serviço antes do insert (ver §3.5 do documento de engenharia, v4.1).

---

## 6. Bounded Context: Estoque

### `stock_movements`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `product_variation_id` | `CHAR(36)` | nullable, FK → `product_variations.id` (`ON DELETE SET NULL`) |
| `store_id` | `CHAR(36)` | nullable, FK → `stores.id` |
| `external_order_id` | `VARCHAR(100)` | nullable |
| `external_event_id` | `VARCHAR(100)` | nullable |
| `tipo` | `VARCHAR(20)` | `NOT NULL`, `CHECK (tipo IN ('VENDA','AJUSTE_MANUAL','ENTRADA'))` |
| `origem` | `VARCHAR(20)` | `NOT NULL`, `CHECK (origem IN ('WEBHOOK','MANUAL','BATCH'))` |
| `quantidade` | `INT` | `NOT NULL` |
| `status` | `VARCHAR(30)` | `NOT NULL`, `CHECK (status IN ('OK','SEM_VINCULO','AGUARDANDO_ATIVACAO','ESTOQUE_INSUFICIENTE','ERRO'))` |
| `criado_em` | `DATETIME` | `NOT NULL` |

Constraints: `UNIQUE(store_id, external_order_id)` — idempotência de pedido; aplicada apenas quando ambos não são `NULL` (constraint parcial, ou verificação a nível de aplicação se o MariaDB usado não suportar índice único parcial nativamente).
Índices: `INDEX(product_variation_id)`, `INDEX(store_id)`.

**Nunca editado ou apagado** — correções geram nova linha (ver v4.1, §9.6).

---

## 7. Bounded Context: Webhooks

### `webhook_events`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `store_id` | `CHAR(36)` | `NOT NULL`, FK → `stores.id` |
| `event_id` | `VARCHAR(100)` | nullable (nem todo marketplace fornece) |
| `event_type` | `VARCHAR(50)` | `NOT NULL` |
| `payload_hash` | `VARCHAR(64)` | `NOT NULL` (SHA-256 do payload — nunca o payload sensível completo) |
| `received_at` | `DATETIME` | `NOT NULL` |
| `processed_at` | `DATETIME` | nullable |
| `status` | `VARCHAR(20)` | `NOT NULL`, `CHECK (status IN ('RECEIVED','PROCESSING','PROCESSED','FAILED','IGNORED'))` |
| `error_message` | `VARCHAR(500)` | nullable |

Constraints: `UNIQUE(store_id, event_id)` quando `event_id IS NOT NULL`.
Índices: `INDEX(store_id, received_at)` (para o job de reconciliação diária).

### `reconciliation_alerts` (versão mínima, RF-W09)
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `store_id` | `CHAR(36)` | `NOT NULL`, FK → `stores.id` |
| `data` | `DATE` | `NOT NULL` |
| `pedidos_marketplace` | `INT` | `NOT NULL` |
| `webhook_events_count` | `INT` | `NOT NULL` |
| `divergencia` | `INT` | `NOT NULL` (diferença entre as duas contagens) |
| `criado_em` | `DATETIME` | `NOT NULL` |
| `resolvido_em` | `DATETIME` | nullable (seller marca como investigado) |

Constraints: `UNIQUE(store_id, data)`.

---

## 8. Bounded Context: Ads Analyzer

### `ad_snapshots`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `store_id` | `CHAR(36)` | `NOT NULL`, FK → `stores.id` |
| `data` | `DATE` | `NOT NULL` |
| `total_pedidos` | `INT` | `NOT NULL` |
| `criado_em` | `DATETIME` | `NOT NULL` |

Constraints: `UNIQUE(store_id, data)`.

### `sku_metrics`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `snapshot_id` | `CHAR(36)` | `NOT NULL`, FK → `ad_snapshots.id` |
| `sku` | `VARCHAR(100)` | `NOT NULL` |
| `nome` | `VARCHAR(255)` | nullable |
| `vendas` | `DECIMAL(12,2)` | `NOT NULL DEFAULT 0` |
| `unidades` | `INT` | `NOT NULL DEFAULT 0` |
| `gasto_ads` | `DECIMAL(12,2)` | `NOT NULL DEFAULT 0` |
| `cliques` | `INT` | `NOT NULL DEFAULT 0` |
| `impressoes` | `INT` | `NOT NULL DEFAULT 0` |
| `acos` | `DECIMAL(6,2)` | nullable (indefinido quando `vendas = 0`) |

Índices: `INDEX(snapshot_id)`.

### `ad_reports`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `store_id` | `CHAR(36)` | `NOT NULL`, FK → `stores.id` |
| `data` | `DATE` | `NOT NULL` |
| `conteudo_markdown` | `LONGTEXT` | `NOT NULL` |
| `criado_em` | `DATETIME` | `NOT NULL` |

Constraints: `UNIQUE(store_id, data)`.

### `analysis_runs`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `store_id` | `CHAR(36)` | `NOT NULL`, FK → `stores.id` |
| `data_referencia` | `DATE` | `NOT NULL` |
| `status` | `VARCHAR(20)` | `NOT NULL`, `CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED'))` |
| `started_at` | `DATETIME` | `NOT NULL` |
| `finished_at` | `DATETIME` | nullable |
| `error` | `VARCHAR(500)` | nullable |

Índices: `INDEX(store_id, data_referencia)`.

---

## 9. Auditoria administrativa

### `admin_audit_log`
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `admin_id` | `CHAR(36)` | `NOT NULL`, FK → `sellers.id` |
| `seller_id` | `CHAR(36)` | `NOT NULL`, FK → `sellers.id` |
| `acao` | `VARCHAR(50)` | `NOT NULL` (ex: `ATIVAR_SELLER`, `DESATIVAR_SELLER`) |
| `ip` | `VARCHAR(45)` | `NOT NULL` |
| `resultado` | `VARCHAR(20)` | `NOT NULL` |
| `criado_em` | `DATETIME` | `NOT NULL` |

---

## 10. Ordem de migração Flyway

| Versão | Conteúdo |
|---|---|
| `V1` | Estrutura atual (`products` — bootstrap a partir do schema existente, antes de `ddl-auto=update` ser desligado) |
| `V2` | Auth: `sellers`, `refresh_tokens`, `email_confirmation_tokens`, `password_reset_tokens` |
| `V3` | Sellers & Stores: `stores`, `store_credentials` |
| `V4` | Produtos: `seller_id` em `products`, `product_variations`, migração dos produtos existentes para variação "padrão" |
| `V5` | Marketplace Links: `product_marketplace_links` |
| `V6` | Estoque: `stock_movements`, constraints `CHECK` em `product_variations.amount`/`estoque_minimo` |
| `V7` | Webhooks: `webhook_events`, `reconciliation_alerts` |
| `V8` | Ads: `ad_snapshots`, `sku_metrics`, `ad_reports`, `analysis_runs` |
| `V9` | Auditoria: `admin_audit_log` |

Cada migration é aditiva e reversível apenas para frente (sem `down` — Flyway padrão); qualquer correção de schema depois de aplicado entra como nova versão (`V10`, `V11`...), nunca editando uma migration já rodada em produção.

**Nota (v4.3):** a exportação de dados brutos para IA externa (documento
de texto sob demanda) não requer migration — é implementada como um
endpoint de leitura sobre `ad_snapshots`/`sku_metrics`, sem novo schema.
