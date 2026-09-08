# Glossário de Tradução PT → EN (código)

Os documentos `software-engineering.md` e `arquitetura-banco-de-dados.md`
usam termos em português (linguagem de negócio/decisão). O código —
classes, métodos, tabelas, colunas, enums — sai em **inglês**, seguindo
este mapeamento. Use-o em toda fase, para manter nomenclatura consistente
do início ao fim. Se um termo novo aparecer numa fase futura e não
estiver aqui, adicione a tradução escolhida a este arquivo antes de
codar, não decida ad-hoc.

Nomes de tabela já em inglês nos documentos (`sellers`, `stores`,
`products`, `stock_movements`, `webhook_events`, `ad_snapshots`,
`sku_metrics`, `ad_reports`, `analysis_runs`, `refresh_tokens`,
`email_confirmation_tokens`, `password_reset_tokens`,
`admin_audit_log`, `product_variations`, `product_marketplace_links`,
`store_credentials`, `reconciliation_alerts`) permanecem como estão —
só as colunas e valores de enum abaixo mudam.

## Colunas comuns (todas as tabelas)

| PT | EN |
|---|---|
| `criado_em` | `created_at` |
| `atualizado_em` | `updated_at` |
| `deleted_at` | `deleted_at` (já em inglês) |

## `sellers`

| PT | EN |
|---|---|
| `senha_hash` | `password_hash` |
| `email_confirmado_em` | `email_confirmed_at` |
| status `PENDENTE` | `PENDING` |
| status `ATIVO` | `ACTIVE` |
| status `INATIVO` | `INACTIVE` |

## `refresh_tokens` / `email_confirmation_tokens` / `password_reset_tokens`

| PT | EN |
|---|---|
| `expira_em` | `expires_at` |
| `usado_em` | `used_at` |

(`token_hash`, `revoked_at`, `replaced_by`, `seller_id` já em inglês.)

## `stores`

| PT | EN |
|---|---|
| `nome_da_loja` | `store_name` |
| `ativo` | `active` |
| status `NAO_CONFIGURADO` | `NOT_CONFIGURED` |
| status `EM_REVISAO` | `IN_REVIEW` |
| status `ATIVO` | `ACTIVE` |
| status `PAUSADO` | `PAUSED` |

(`marketplace` e seus valores `SHOPEE`/`SHEIN`/`MERCADO_LIVRE`/
`TIKTOK_SHOP` são nomes próprios — não traduzir.)

## `store_credentials`

| PT | EN |
|---|---|
| `access_token_cripto` | `access_token_encrypted` |
| `refresh_token_cripto` | `refresh_token_encrypted` |
| `expira_em` | `expires_at` |

## `products`

| PT | EN |
|---|---|
| `sku_principal` | `main_sku` |
| `nome` | `name` |

(`barcode`, `photo_url`, `seller_id` já em inglês.)

## `product_variations`

| PT | EN |
|---|---|
| `sku_variacao` | `variation_sku` |
| `atributo` | `attribute` |
| `estoque_minimo` | `minimum_stock` |

(`amount`, `product_id` já em inglês.)

## `product_marketplace_links`

| PT | EN |
|---|---|
| `matched_by` valor `SKU_PRINCIPAL_SUGERIDO` | `SUGGESTED_BY_MAIN_SKU` |

(`external_sku`, `matched_by` valor `MANUAL`, `product_variation_id`,
`store_id` já em inglês.)

## `stock_movements`

| PT | EN |
|---|---|
| `tipo` | `type` |
| `origem` | `source` |
| `quantidade` | `quantity` |
| tipo `VENDA` | `SALE` |
| tipo `AJUSTE_MANUAL` | `MANUAL_ADJUSTMENT` |
| tipo `ENTRADA` | `STOCK_IN` |
| status `SEM_VINCULO` | `UNLINKED` |
| status `AGUARDANDO_ATIVACAO` | `PENDING_ACTIVATION` |
| status `ESTOQUE_INSUFICIENTE` | `INSUFFICIENT_STOCK` |
| status `ERRO` | `ERROR` |

(status `OK`, origem `WEBHOOK`/`MANUAL`/`BATCH`, `external_order_id`,
`external_event_id` já em inglês.)

## `reconciliation_alerts`

| PT | EN |
|---|---|
| `data` | `date` |
| `pedidos_marketplace` | `marketplace_orders_count` |
| `divergencia` | `discrepancy` |
| `resolvido_em` | `resolved_at` |

(`webhook_events_count` já em inglês.)

## `ad_snapshots`

| PT | EN |
|---|---|
| `data` | `date` |
| `total_pedidos` | `total_orders` |

## `sku_metrics`

| PT | EN |
|---|---|
| `nome` | `name` |
| `vendas` | `sales` |
| `unidades` | `units` |
| `gasto_ads` | `ad_spend` |
| `cliques` | `clicks` |
| `impressoes` | `impressions` |

(`sku`, `acos` — termo de mercado — permanecem.)

## `ad_reports`

| PT | EN |
|---|---|
| `data` | `date` |
| `conteudo_markdown` | `markdown_content` |

## `analysis_runs`

| PT | EN |
|---|---|
| `data_referencia` | `reference_date` |

(`status`, `started_at`, `finished_at`, `error` já em inglês.)

## `admin_audit_log`

| PT | EN |
|---|---|
| `acao` | `action` |
| `resultado` | `result` |

(`admin_id`, `seller_id`, `ip` já em inglês.)

## Conceitos gerais / nomes de classe

| PT (documento) | EN (código) |
|---|---|
| Vínculo Produto ↔ Marketplace | `ProductMarketplaceLink` (já usado nos docs) |
| Movimentação de estoque | `StockMovement` |
| Execução do Ads Analyzer | `AnalysisRun` |
| Reconciliação | `Reconciliation` |
