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
`store_credentials`, `reconciliation_alerts`, `plans`, `subscriptions`,
`payment_attempts`, `refunds`, `trial_contestations`, `risk_signals`,
`token_usage`, `token_package_plans`, `token_packages`,
`api_pricing_history`, `exchange_rate_snapshots`) permanecem como
estão — só as colunas e valores de enum abaixo mudam.

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
| `cnpj_verificado_em` | `cnpj_verified_at` |
| status `PENDENTE` | `PENDING` |
| status `ATIVO` | `ACTIVE` |
| status `INATIVO` | `INACTIVE` |

(`cnpj` é sigla própria — não traduzir.)

## `plans` (v2.0 — Fase 1.5, redesenhada; substitui a entrada v4.5 abaixo)

| PT | EN |
|---|---|
| `nome` | `name` |
| `preco_mensal` | `monthly_price` |
| `margem_alvo` | `target_margin` |
| `cota_tokens_mes_atual` | `current_month_token_quota` |

(`module` e seus valores `PRODUCTS`/`ADS_ANALYZER`, `stores_limit` já em
inglês, não traduzir. `plan_modules` e `sellers.plan_id` da v4.5 foram
removidos no redesenho v2.0 — ver `docs/arquitetura-banco-de-dados.md`
§2 — não existem mais no schema, entrada mantida abaixo só como
histórico.)

## `plans` / `plan_modules` (v4.5 — obsoleto, não existe mais no schema)

| PT | EN |
|---|---|
| `name` | `name` (já em inglês) |
| `module` | `module` (já em inglês) |
| valores do módulo: `PRODUCTS`, `ADS_ANALYZER` | já em inglês, não traduzir |
| `plan_id` (em `sellers`) | `plan_id` (já em inglês) |

## `api_pricing_history`

| PT | EN |
|---|---|
| `preco_input_usd_milhao` | `input_price_usd_per_million` |
| `preco_output_usd_milhao` | `output_price_usd_per_million` |
| `vigente_desde` | `effective_from` |
| `atualizado_por` | `updated_by` |

## `exchange_rate_snapshots`

| PT | EN |
|---|---|
| `usd_brl` | `usd_brl_rate` |
| `data_referencia` | `reference_date` |

## `subscriptions`

| PT | EN |
|---|---|
| `ciclo` | `billing_cycle` |
| `data_aniversario` | `anniversary_date` |
| `data_inicio_ciclo_atual` | `current_cycle_start_date` |
| `data_fim_ciclo_atual` | `current_cycle_end_date` |
| `data_inicio_periodo_tokens_atual` | `current_token_period_start_date` |
| `data_fim_periodo_tokens_atual` | `current_token_period_end_date` |
| `data_cancelamento_solicitado` | `cancellation_requested_at` |
| `data_bloqueio` | `blocked_at` |
| `data_exclusao_prevista` | `scheduled_deletion_date` |
| `data_expiracao_trial` | `trial_expires_at` |
| status `TRIAL_ATIVO` | `TRIAL_ACTIVE` |
| status `TRIAL_RESTRITO` | `TRIAL_RESTRICTED` |
| status `TRIAL_ESGOTADO` | `TRIAL_EXHAUSTED` |
| status `ATIVO` | `ACTIVE` |
| status `INADIMPLENTE` | `PAST_DUE` |
| status `CANCELAMENTO_SOLICITADO` | `CANCELLATION_REQUESTED` |
| status `RETENCAO` | `RETENTION` |
| status `EXCLUIDO` | `DELETED` |
| status `ARREPENDIDO` (9º estado — achado na revisão do Block B; ver PROGRESS.md) | `WITHDRAWN` |
| ciclo `MENSAL` | `MONTHLY` |
| ciclo `TRIMESTRAL` | `QUARTERLY` |
| ciclo `SEMESTRAL` | `SEMIANNUAL` |
| ciclo `ANUAL` | `ANNUAL` |

(`module`, `plan_id`, `combo_group_id`, `seller_id` já em inglês.)

## `payment_attempts`

| PT | EN |
|---|---|
| `tipo` | `type` |
| `tentativa_numero` | `attempt_number` |
| `data_tentativa` | `attempted_at` |
| `resultado` | `result` |
| `motivo_recusa` | `decline_reason` |
| tipo `PRIMEIRA_COBRANCA` | `INITIAL_CHARGE` |
| tipo `RENOVACAO` | `RENEWAL` |
| resultado `APROVADO` | `APPROVED` |
| resultado `RECUSADO` | `DECLINED` |
| resultado `PENDENTE` | `PENDING` |

(`subscription_id`, `gateway_transaction_id` já em inglês.)

## `refunds`

| PT | EN |
|---|---|
| `valor_brl` | `amount_brl` |
| `solicitado_em` | `requested_at` |
| `processado_em` | `processed_at` |
| tipo `ARREPENDIMENTO_TOTAL` | `FULL_WITHDRAWAL` |
| tipo `ARREPENDIMENTO_COMBO_PARCIAL` | `PARTIAL_COMBO_WITHDRAWAL` |

("Arrependimento" aqui é o direito de arrependimento do art. 49 do CDC —
"withdrawal", não "regret"; `subscription_id`, `gateway_transaction_id`
já em inglês.)

## `trial_contestations`

| PT | EN |
|---|---|
| `justificativa` | `justification` |
| `analisado_por` | `reviewed_by` |
| `analisado_em` | `reviewed_at` |
| status `PENDENTE` | `PENDING` |
| status `APROVADA` | `APPROVED` |
| status `NEGADA` | `DENIED` |

(`subscription_id`, `status` já em inglês.)

## `risk_signals`

| PT | EN |
|---|---|
| `tipo_sinal` | `signal_type` |
| `detalhe` | `detail` |
| `detectado_em` | `detected_at` |
| sinal `EMAIL_REPETIDO` | `DUPLICATE_EMAIL` |
| sinal `CELULAR_REPETIDO` | `DUPLICATE_PHONE` |
| sinal `IP_REDE` | `IP_NETWORK` |
| sinal `LIMITE_DISPOSITIVO` | `DEVICE_LIMIT` |

(`subscription_id`, sinal `DEVICE_FINGERPRINT` já em inglês.)

## `token_usage`

| PT | EN |
|---|---|
| `origem` | `source` |
| `tokens_entrada` | `input_tokens` |
| `tokens_saida` | `output_tokens` |
| `tokens_total` | `total_tokens` |
| `custo_brl_snapshot` | `cost_brl_snapshot` |
| `ciclo_referencia` | `reference_cycle` |
| origem `PLANO_PAGO` | `PAID_PLAN` |

(`analysis_run_id`, `subscription_id`, `store_id`, `api_pricing_id`,
`exchange_rate_id`, origem `TRIAL` já em inglês.)

## `token_package_plans`

| PT | EN |
|---|---|
| `tamanho` | `size` |
| `preco_brl` | `price_brl` |
| tamanho `P` | `S` |
| tamanho `M` | `M` (já em inglês) |
| tamanho `G` | `L` |

(`tokens` já em inglês.)

## `token_packages`

| PT | EN |
|---|---|
| `tokens_comprados` | `tokens_purchased` |
| `preco_pago_brl` | `price_paid_brl` |
| `ciclo_referencia` | `reference_cycle` |
| `expira_em` | `expires_at` |

(`subscription_id`, `package_plan_id` já em inglês.)

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
| Assinatura | `Subscription` |
| Plano | `Plan` |
| Módulo (enum fixo, não tabela — `software-engineering.md` §4.6) | `Module` |
| Direitos de uso / entitlement | `Entitlement`/`EntitlementService` (já em inglês nos docs) |
| Máquina de estados da assinatura | `SubscriptionStateMachine` |
| Status da assinatura (enum) | `SubscriptionStatus` |
| Tentativa de pagamento | `PaymentAttempt` |
| Reembolso | `Refund` |
| Contestação de trial | `TrialContestation` |
| Sinal de risco | `RiskSignal` |
| Uso de tokens | `TokenUsage` |
| Pacote de tokens (catálogo) | `TokenPackagePlan` |
| Pacote de tokens (compra) | `TokenPackage` |
| Histórico de preço da API | `ApiPricingHistory` |
| Snapshot de câmbio | `ExchangeRateSnapshot` |
