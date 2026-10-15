# Easy Manager — Arquitetura de Banco de Dados

**Versão:** 2.0 (redesenha `plans`, remove `plan_modules` e
`sellers.plan_id` — sincronização com o modelo comercial de módulos,
planos por Stores, combo e tokens; ver `easy-manager-plano-execucao.md`
Etapa C)
**Banco:** MariaDB 11
**Migração:** Flyway (substitui `ddl-auto=update`)

---

## 1. Convenções gerais

- **Chave primária:** `UUID`, gerado na aplicação (`UUID.randomUUID()`), armazenado como `CHAR(36)` — mesma convenção já usada em `products` hoje, mantida por consistência em todas as tabelas novas.
- **Timestamps:** `criado_em` (`DATETIME`, `NOT NULL`, default `CURRENT_TIMESTAMP`) em toda tabela; `atualizado_em` nas tabelas que sofrem update (não em tabelas somente-append, como `stock_movements`, `webhook_events`, `token_usage`, `token_packages`, `payment_attempts`, `risk_signals`, `api_pricing_history` e `exchange_rate_snapshots`).
- **Soft delete:** `deleted_at` (`DATETIME`, nullable) em `products`, `stores`, `product_variations` — nunca `DELETE` físico nessas três, por causa do histórico associado (`stock_movements`, `product_marketplace_links`).
- **Enums:** representados como `VARCHAR` com `CHECK` constraint (mais portável entre versões do MariaDB e mais simples de evoluir via Flyway do que `ENUM` nativo).
- **Segredos em coluna:** colunas `*_cripto` armazenam o valor já criptografado pela aplicação (AES-GCM) — o banco nunca vê o token em claro.
- **Nunca `CASCADE DELETE`** em tabelas de histórico (`stock_movements`, `webhook_events`, `ad_snapshots`, `ad_reports`, `sku_metrics`, `token_usage`, `payment_attempts`, `risk_signals`, `api_pricing_history`, `exchange_rate_snapshots`). Uso de `ON DELETE RESTRICT` (ou `SET NULL` quando o campo já é nullable, como `product_variation_id` em `stock_movements` e `analisado_por` em `trial_contestations`).
- **`subscriptions` nunca é hard-deletada nem tem `deleted_at`:** 1 linha por seller×módulo, pra sempre — reativação (mesmo anos depois) reaproveita a mesma linha, nunca cria outra (ver §3, nota de reativação).

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
| `cnpj` | `CHAR(14)` | nullable, `UNIQUE` — novo, Etapa C8, suporta RN-ABUSE-001 |
| `cnpj_verificado_em` | `DATETIME` | nullable — novo, Etapa C8; `NULL` = ainda não verificado contra a Receita Federal |
| `criado_em` | `DATETIME` | `NOT NULL` |
| `atualizado_em` | `DATETIME` | `NOT NULL` |

Índices: `UNIQUE(email)` (já cobre lookup de login), `UNIQUE(cnpj)`
(um CNPJ não pode logar como dois sellers diferentes — primeira linha
de defesa anti-abuso, antes mesmo do score de risco).

**Removido nesta versão:** `plan_id` (v4.5). Um seller pode ter 0, 1
ou 2 assinaturas de módulo simultâneas (PRODUCTS e/ou ADS_ANALYZER),
cada uma com seu próprio plano, ciclo e estado — não cabe mais como FK
única em `sellers`. Essa relação passa a viver em `subscriptions`
(próximo sub-passo do plano de execução, Etapa C3), não em `sellers`.

### `plans` (redesenhado — v2.0, substitui a versão v4.5)
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `module` | `VARCHAR(30)` | `NOT NULL`, `CHECK (module IN ('PRODUCTS','ADS_ANALYZER'))` |
| `nome` | `VARCHAR(50)` | `NOT NULL` (`'Básico'`, `'Intermediário'`, `'Completo'`) |
| `stores_limit` | `INT` | `NOT NULL` (1, 2 ou 5 — RN-PLAN-002/003) |
| `preco_mensal` | `DECIMAL(10,2)` | `NOT NULL` |
| `margem_alvo` | `DECIMAL(5,4)` | nullable — só ADS_ANALYZER; `NULL` em planos PRODUCTS, que não têm custo variável de IA (RN-PLAN-001) |
| `cota_tokens_mes_atual` | `BIGINT` | nullable — só ADS_ANALYZER; recalculada pelo job diário de câmbio (RN-TOKEN-001/002), `NULL` em planos PRODUCTS |
| `criado_em` | `DATETIME` | `NOT NULL` |
| `atualizado_em` | `DATETIME` | `NOT NULL` — toca toda vez que o job diário recalcula `cota_tokens_mes_atual` |

Constraints: `UNIQUE(module, nome)`.

Seed inicial: **6 linhas fixas**, uma por combinação módulo×plano —
PRODUCTS (Básico/1 Store/R$15, Intermediário/2/R$25, Completo/5/R$45)
e ADS_ANALYZER (Básico/1/R$39/margem 73%, Intermediário/2/R$69/margem
65%, Completo/5/R$149/margem 54%) — RN-PLAN-002/003. Diferente da
v4.5 (um único plano "Full" atribuído a todo seller no cadastro,
RF-AU19 — **obsoleto**, não existe mais atribuição automática de
plano: o seller escolhe módulo(s) e plano no checkout, ou entra em
trial).

**Sobre a cota de tokens ser uma coluna cacheada:** `cota_tokens_mes_atual`
não é calculada em tempo real a cada checagem de entitlement — seria
uma chamada de câmbio por requisição, latência desnecessária
(RN-TOKEN-002). O job diário recalcula e grava essa coluna; toda
checagem de saldo/entitlement lê o valor já cacheado aqui.

### `plan_modules` — **removida** (v4.5 tinha essa tabela; eliminada nesta revisão)

Na v4.5, `plan_modules` era a ponte N:N entre `plans` e módulo, porque
um plano podia cobrir vários módulos (o plano "Full" cobria os dois).
Com o redesenho de `plans` (módulo já é uma coluna direta, um plano =
um módulo), essa ponte deixa de fazer sentido — não existe mais "um
plano com vários módulos".

**O combo (RN-COMBO-001/002) não vira uma tabela de junção de planos.**
Ele é modelado como uma coluna em `subscriptions` (Etapa C3):
`combo_group_id` (nullable) — quando um seller assina os dois módulos
juntos, no mesmo ato, as duas linhas de `subscriptions` resultantes
compartilham o mesmo `combo_group_id`, e isso é o que dispara o
desconto de 15% e a regra de "perde o desconto na renovação se um dos
dois for cancelado" (RN-COMBO-004). Sem `combo_group_id` compartilhado
= sem desconto de combo, mesmo que o seller tenha os dois módulos
ativos (assinados em momentos diferentes, RN-COMBO-002).

### `api_pricing_history` (novo — suporta RN-TOKEN-008)
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `preco_input_usd_milhao` | `DECIMAL(10,4)` | `NOT NULL` |
| `preco_output_usd_milhao` | `DECIMAL(10,4)` | `NOT NULL` |
| `vigente_desde` | `DATETIME` | `NOT NULL` |
| `atualizado_por` | `CHAR(36)` | nullable, FK → `sellers.id` (admin que fez a atualização manual) |
| `criado_em` | `DATETIME` | `NOT NULL` |

Só-append (nunca `UPDATE`/`DELETE`) — cada mudança manual de preço
insere uma linha nova; o preço vigente é sempre o de `vigente_desde`
mais recente. Usado só pra auditoria/consulta (RN-TOKEN-008), nunca
lido no caminho de checagem de saldo em tempo real (esse usa
`plans.cota_tokens_mes_atual`, já cacheada).

### `exchange_rate_snapshots` (novo — suporta o job diário, RN-TOKEN-002)
| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `usd_brl` | `DECIMAL(10,4)` | `NOT NULL` |
| `data_referencia` | `DATE` | `NOT NULL`, `UNIQUE` (1 snapshot por dia) |
| `criado_em` | `DATETIME` | `NOT NULL` |

Gravada pelo job diário antes de recalcular `plans.cota_tokens_mes_atual`
— dá rastreabilidade de qual câmbio gerou qual cota em cada dia,
sem precisar recalcular retroativamente pra auditar.

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

## 3. Bounded Context: Assinaturas & Billing (novo — Etapa C3)

### `subscriptions`

Uma linha por par **seller × módulo**, existindo desde o primeiro
trial ou primeira assinatura paga daquele módulo, e persistindo pela
vida inteira do seller no módulo — passa pela máquina de estados
inteira (`easy-manager-regras-negocio.md` §13) potencialmente **várias
vezes** ao longo do tempo (ex: assina, cancela, assina de novo meses
depois — mesma linha, não uma nova). `SEM_ASSINATURA` não é um valor
de `status` armazenado: é a **ausência** de linha pra aquele
seller×módulo (antes do primeiro trial/assinatura).

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `seller_id` | `CHAR(36)` | `NOT NULL`, FK → `sellers.id`, `ON DELETE RESTRICT` |
| `module` | `VARCHAR(30)` | `NOT NULL`, `CHECK (module IN ('PRODUCTS','ADS_ANALYZER'))` |
| `plan_id` | `CHAR(36)` | nullable, FK → `plans.id`, `ON DELETE RESTRICT` — `NULL` enquanto em qualquer estado de trial |
| `status` | `VARCHAR(30)` | `NOT NULL`, `CHECK (status IN ('TRIAL_ATIVO','TRIAL_RESTRITO','TRIAL_ESGOTADO','ATIVO','INADIMPLENTE','CANCELAMENTO_SOLICITADO','RETENCAO','EXCLUIDO','ARREPENDIDO'))` — 9º valor (`ARREPENDIDO`) entra na `V11` (Fase 1.5, Block B), depois da `V6` original |
| `ciclo` | `VARCHAR(20)` | nullable, `CHECK (ciclo IN ('MENSAL','TRIMESTRAL','SEMESTRAL','ANUAL'))` — `NULL` enquanto em trial |
| `data_aniversario` | `DATE` | nullable — dia-base do ciclo (RN-SUB-002) |
| `data_inicio_ciclo_atual` | `DATE` | nullable |
| `data_fim_ciclo_atual` | `DATE` | nullable — usado pra travar troca de ciclo antecipado (RN-SUB-004) e pra saber quando começa a retenção em cancelamento voluntário (RN-RET-002) |
| `data_inicio_periodo_tokens_atual` | `DATE` | nullable — **novo, achado no C11**. Só ADS_ANALYZER. Período mensal de reset da cota de tokens — **independente** de `data_inicio_ciclo_atual`, que é o ciclo de *cobrança* (pode durar até 12 meses num plano anual). RN-TOKEN-005: a cota reseta todo mês mesmo dentro de um ciclo de cobrança mais longo |
| `data_fim_periodo_tokens_atual` | `DATE` | nullable — fim do período mensal acima; sempre `data_inicio_periodo_tokens_atual` + 1 mês, independente de quando o ciclo de cobrança termina |
| `combo_group_id` | `CHAR(36)` | nullable — compartilhado entre as 2 linhas (PRODUCTS + ADS_ANALYZER) quando assinadas juntas, no mesmo ato (RN-COMBO-001/002) |
| `data_cancelamento_solicitado` | `DATETIME` | nullable — marcado ao entrar em `CANCELAMENTO_SOLICITADO` (RN-SUB-007) |
| `data_bloqueio` | `DATETIME` | nullable — marcado ao entrar em `RETENCAO` vindo de `INADIMPLENTE` (dia 6, RN-INAD-002/003) |
| `data_exclusao_prevista` | `DATE` | nullable — calculada ao entrar em `RETENCAO` (por qualquer caminho): `data_bloqueio` ou `data_fim_ciclo_atual` + 7 ou 30 dias conforme o módulo (RN-RET-001) |
| `data_expiracao_trial` | `DATE` | nullable — novo, Etapa C8. Só ADS_ANALYZER: 30 dias após entrar em trial (RN-TRIAL-006). `NULL` em PRODUCTS (trial sem prazo, RN-PROD-002) e `NULL` fora de estados de trial |
| `criado_em` | `DATETIME` | `NOT NULL` |
| `atualizado_em` | `DATETIME` | `NOT NULL` |

Constraints: `UNIQUE(seller_id, module)` — só existe uma linha por
par, para sempre (ver nota abaixo sobre reativação).

Índices: `INDEX(status)` (jobs de inadimplência/retenção varrem por
status), `INDEX(combo_group_id)`, `INDEX(data_exclusao_prevista)`
(job de retenção, §10.pendente).

**`CHECK (status IN ('TRIAL_ATIVO','TRIAL_RESTRITO','TRIAL_ESGOTADO','EXCLUIDO','ARREPENDIDO') OR plan_id IS NOT NULL)`**
— nenhum estado pago existe sem plano definido. `EXCLUIDO` entra na
lista de exceção porque é alcançável por dois caminhos com
`plan_id` diferente: `RETENCAO → EXCLUIDO` (sempre com plano, RN-RET-001)
e `TRIAL_* → EXCLUIDO` (nunca teve plano — expiração de 30 dias do
trial de ADS_ANALYZER, RN-TRIAL-006, seller que nunca converteu). A
primeira versão desta constraint não incluía `EXCLUIDO` na exceção e
rejeitaria essa segunda transição (achado na revisão do Block A da
Fase 1.5 — ver `PROGRESS.md`). `ARREPENDIDO` entra pelo mesmo motivo
de `TRIAL_*`: `ATIVO → ARREPENDIDO` (arrependimento, RN-PAY-002/004)
zera o plano — achado na revisão do Block B, junto da criação do
estado em si (`regras-negocio.md` §13.4, gap 4).

**Invariante de aplicação (não expressável só em constraint de banco):**
`plans.module` referenciado por `plan_id` deve ser igual a
`subscriptions.module` — checado na camada de serviço antes do
insert/update, mesmo padrão já usado em `product_marketplace_links`
(v4.1, ver §6 abaixo).

**Reativação reaproveita a mesma linha, nunca cria uma nova (decisão
de design que resolve RN-SUB-010 e RN-RET-003 no schema) — só a
partir de `RETENCAO`, nunca de `EXCLUIDO`:** como `UNIQUE(seller_id,
module)` é permanente, tanto a reativação por regularização de
pagamento (`RETENCAO` → `ATIVO`, RN-RET-003) quanto a reassinatura via
checkout completo (`RETENCAO` → `ATIVO` via novo checkout, RN-SUB-010)
**atualizam a mesma linha** — nunca inserem uma segunda. A diferença
entre os dois casos é só o que a camada de aplicação exige antes de
fazer o `UPDATE` (regularizar uma cobrança pendente vs. rodar o fluxo
de checkout do zero); o schema não distingue os dois caminhos, só
guarda o resultado.

`EXCLUIDO` é terminal de verdade, sem exceção (confirmado na revisão
do Block B da máquina de estados, Fase 1.5 — `regras-negocio.md` §13
nunca teve nenhuma seta saindo dele; esta nota dizia o contrário antes
dessa revisão e estava errada). A linha não é hard-deletada só por
motivo de auditoria/histórico — não porque o seller possa reativar a
partir dali. Reativar depois de `EXCLUIDO` exige uma nova assinatura,
que é um conceito diferente de "reaproveitar a linha" (fora do escopo
decidido até agora — ver a nota sobre `WITHDRAWN` alguns parágrafos
abaixo para o caso análogo de arrependimento).

### `payment_attempts` (novo — Etapa C7)

Registra tanto a **primeira cobrança** (ato da assinatura — sem régua
de tolerância, RN-PAY-001) quanto as tentativas de **renovação**
(régua de 5 tentativas/5 dias com acesso liberado, RN-INAD-001) —
diferenciadas pela coluna `tipo`, já que o comportamento de cada uma é
diferente (a diferença é decidida na aplicação, não no schema: a
`tipo = 'PRIMEIRA_COBRANCA'` que falha não libera nenhum acesso,
independente de `tentativa_numero`).

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `subscription_id` | `CHAR(36)` | `NOT NULL`, FK → `subscriptions.id`, `ON DELETE RESTRICT` |
| `tipo` | `VARCHAR(20)` | `NOT NULL`, `CHECK (tipo IN ('PRIMEIRA_COBRANCA','RENOVACAO'))` |
| `tentativa_numero` | `INT` | `NOT NULL` — sempre `1` para `PRIMEIRA_COBRANCA`; `1` a `5` para `RENOVACAO` (RN-INAD-001) |
| `data_tentativa` | `DATETIME` | `NOT NULL` |
| `resultado` | `VARCHAR(20)` | `NOT NULL`, `CHECK (resultado IN ('APROVADO','RECUSADO','PENDENTE'))` |
| `motivo_recusa` | `VARCHAR(200)` | nullable — retorno bruto do gateway de pagamento |
| `gateway_transaction_id` | `VARCHAR(100)` | nullable — referência externa, pra conciliação |
| `criado_em` | `DATETIME` | `NOT NULL` |

Índices: `INDEX(subscription_id, tipo, tentativa_numero)`.

**Próxima tentativa não é uma coluna aqui** — o job de cobrança
(checklist item 10) calcula "última tentativa + 1 dia" consultando a
linha mais recente por `subscription_id`, e decide a transição de
estado (`INADIMPLENTE → RETENCAO` no dia 6, RN-INAD-002/003) com base
na contagem de tentativas `RECUSADO` consecutivas, sem depender de um
campo de agendamento persistido.

### `refunds` (novo — achado no C11, cobre RN-PAY-002/003)

1 linha por reembolso efetivamente processado — nem todo pedido de
cancelamento gera reembolso (só dentro dos 7 dias, RN-PAY-002/003), e
essa tabela só registra os que geram.

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `subscription_id` | `CHAR(36)` | `NOT NULL`, FK → `subscriptions.id`, `ON DELETE RESTRICT` |
| `tipo` | `VARCHAR(30)` | `NOT NULL`, `CHECK (tipo IN ('ARREPENDIMENTO_TOTAL','ARREPENDIMENTO_COMBO_PARCIAL'))` — o segundo é o caso do RN-COMBO-003 (cancela um módulo do combo, mantém o outro) |
| `valor_brl` | `DECIMAL(10,2)` | `NOT NULL` |
| `solicitado_em` | `DATETIME` | `NOT NULL` |
| `processado_em` | `DATETIME` | nullable — quando o gateway confirma o estorno |
| `gateway_transaction_id` | `VARCHAR(100)` | nullable |
| `criado_em` | `DATETIME` | `NOT NULL` |
| `atualizado_em` | `DATETIME` | `NOT NULL` — toca quando `processado_em` é preenchido |

Índices: `INDEX(subscription_id)`.

### `trial_contestations` (novo — Etapa C8)

1 linha por pedido de contestação de bloqueio (RN-ABUSE-004) — o
"contestar bloqueio" que o seller vê na tela de `TRIAL_RESTRITO`.

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `subscription_id` | `CHAR(36)` | `NOT NULL`, FK → `subscriptions.id`, `ON DELETE RESTRICT` |
| `justificativa` | `TEXT` | `NOT NULL` — texto que o seller escreve (ex: "sou cônjuge do titular de outra conta, CNPJ próprio") |
| `status` | `VARCHAR(20)` | `NOT NULL DEFAULT 'PENDENTE'`, `CHECK (status IN ('PENDENTE','APROVADA','NEGADA'))` |
| `analisado_por` | `CHAR(36)` | nullable, FK → `sellers.id` (`role='ADMIN'`), `ON DELETE SET NULL` |
| `analisado_em` | `DATETIME` | nullable |
| `criado_em` | `DATETIME` | `NOT NULL` |
| `atualizado_em` | `DATETIME` | `NOT NULL` — toca quando `status`/`analisado_por`/`analisado_em` mudam |

Índices: `INDEX(subscription_id)`, `INDEX(status)` (fila de revisão
manual consulta por `status = 'PENDENTE'`).

**Aprovação dispara a transição `TRIAL_RESTRITO → TRIAL_ATIVO`**
(RN-ABUSE-004) na camada de aplicação — não há trigger de banco pra
isso, é o mesmo service que já orquestra as outras transições da
máquina de estados.

### `risk_signals` (novo — Etapa C8b)

1 linha por sinal de risco detectado — nunca um score agregado numa
coluna só, pra manter os sinais individuais auditáveis (achado
registrado no plano de execução, C8b).

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `subscription_id` | `CHAR(36)` | `NOT NULL`, FK → `subscriptions.id`, `ON DELETE RESTRICT` |
| `tipo_sinal` | `VARCHAR(30)` | `NOT NULL`, `CHECK (tipo_sinal IN ('DEVICE_FINGERPRINT','EMAIL_REPETIDO','CELULAR_REPETIDO','IP_REDE','LIMITE_DISPOSITIVO'))` |
| `detalhe` | `VARCHAR(255)` | nullable — ex: hash do fingerprint, ou o `seller_id` que já usou o mesmo sinal antes |
| `detectado_em` | `DATETIME` | `NOT NULL` |

Índices: `INDEX(subscription_id)`, `INDEX(tipo_sinal)`.

**Sem coluna de score:** a regra de "2+ sinais coincidentes"
(RN-ABUSE-002) é `COUNT(*) >= 2` sobre esta tabela, filtrado por
`subscription_id`, checado no momento da criação do trial — não um
número calculado e guardado à parte, que poderia dessincronizar se um
sinal for adicionado depois.

---

## 4. Bounded Context: Sellers & Stores

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

**Nota (v5.0, era "v4.3"):** a exportação de dados brutos para IA
externa, que existia até a v4.5 do documento de engenharia, foi
**removida como funcionalidade** (§8.6 do documento de engenharia,
v5.0) — substituída pela cota gratuita de 3 execuções do trial
(RN-TRIAL-007). Nunca teve tabela própria, então essa remoção não
afeta nenhum schema aqui.

### `store_module_activation` (novo — Etapa C4)

Controla **quais Stores estão ativas em cada módulo** (RN-STORE-002),
dentro do total de Stores cadastradas pelo seller. Uma linha por par
**store × módulo**, criada para os dois módulos assim que a Store é
cadastrada (`ativo = FALSE` por padrão em ambas) — a ativação em si é
decidida depois, pelo seller ou automaticamente no primeiro plano
assinado.

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `store_id` | `CHAR(36)` | `NOT NULL`, FK → `stores.id`, `ON DELETE RESTRICT` |
| `module` | `VARCHAR(30)` | `NOT NULL`, `CHECK (module IN ('PRODUCTS','ADS_ANALYZER'))` |
| `ativo` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` |
| `ativado_em` | `DATETIME` | nullable |
| `desativado_em` | `DATETIME` | nullable — marcado ao desvincular por downgrade (RN-STORE-004) ou desativação manual; a linha **não é apagada**, só desativada (RN-STORE-005: histórico retido) |
| `criado_em` / `atualizado_em` | `DATETIME` | `NOT NULL` |

Constraints: `UNIQUE(store_id, module)`.

Índices: `INDEX(store_id)`, `INDEX(module, ativo)` (checagem de
entitlement por módulo consulta muito "quantas ativas tem esse
seller nesse módulo").

**Três flags diferentes em `stores`/`store_module_activation` — não
confundir (achado na revisão desta etapa):**
1. `stores.ativo` — flag geral pré-existente da Store (não é por
   módulo; escopo e uso definidos em v4.1, fora desta revisão).
2. `stores.stock_sync_status` — saúde da sincronização de webhooks de
   estoque, só relevante pro PRODUCTS (RN-STORE-007: propositalmente
   **não** é a mesma coisa que ativação de módulo).
3. `store_module_activation.ativo` — a nova, decide se aquela Store
   conta contra o limite de Stores ativas do plano daquele módulo
   (RN-STORE-002/006). Uma Store pode estar com `stores.ativo = TRUE`,
   `stock_sync_status = 'ATIVO'`, e ainda assim
   `store_module_activation.ativo = FALSE` pro ADS_ANALYZER — ex:
   seller nunca escolheu ativar essa Store nesse módulo, ou o plano
   não comporta mais Stores ativas no momento.

**Ordem de desvinculação em downgrade automático (RN-STORE-004):**
implementada consultando `stores.criado_em` (ordem de cadastro) — não
precisa de coluna própria aqui, já existe na tabela `stores`.

**Exclusão de Store pelo seller (RN-STORE-008):** diferente do
downgrade, aqui as linhas de `store_module_activation` daquela Store
são **apagadas de verdade** (hard delete), junto com o soft-delete de
`stores.deleted_at` — não há retenção nesse caso (RN-RET-005).

---

## 5. Bounded Context: Produtos

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

## 6. Bounded Context: Vínculo Produto ↔ Marketplace

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

## 7. Bounded Context: Estoque

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

## 8. Bounded Context: Webhooks

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

## 9. Bounded Context: Ads Analyzer

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

### `token_usage` (novo — Etapa C5)

1 linha por execução que efetivamente **chamou a API da Anthropic** —
mesmo que a geração do relatório falhe depois, se o modelo foi
chamado, os tokens foram gastos e a linha existe. Relação 1:1 com
`analysis_runs` (uma run que nunca chega a chamar a API não gera
linha aqui).

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `analysis_run_id` | `CHAR(36)` | `NOT NULL`, `UNIQUE`, FK → `analysis_runs.id`, `ON DELETE RESTRICT` |
| `subscription_id` | `CHAR(36)` | `NOT NULL`, FK → `subscriptions.id`, `ON DELETE RESTRICT` |
| `store_id` | `CHAR(36)` | `NOT NULL`, FK → `stores.id`, `ON DELETE RESTRICT` |
| `origem` | `VARCHAR(20)` | `NOT NULL`, `CHECK (origem IN ('TRIAL','PLANO_PAGO'))` — snapshot do estado da assinatura no momento da execução, não recalculado depois |
| `tokens_entrada` | `INT` | `NOT NULL` |
| `tokens_saida` | `INT` | `NOT NULL` |
| `tokens_total` | `INT` | `GENERATED ALWAYS AS (tokens_entrada + tokens_saida) STORED` |
| `api_pricing_id` | `CHAR(36)` | `NOT NULL`, FK → `api_pricing_history.id` — qual preço da API estava vigente |
| `exchange_rate_id` | `CHAR(36)` | `NOT NULL`, FK → `exchange_rate_snapshots.id` — qual câmbio estava vigente |
| `custo_brl_snapshot` | `DECIMAL(10,4)` | `NOT NULL` — R$ calculado no momento (RN-TOKEN-009: só pra relatório/auditoria, nunca usado pra decidir bloqueio) |
| `ciclo_referencia` | `DATE` | `NOT NULL` — **corrigido no C11**: `subscriptions.data_inicio_periodo_tokens_atual` no momento da execução (período mensal de tokens, não o ciclo de cobrança) — é contra essa data que a cota do período é somada, garantindo o reset mensal mesmo em plano anual (RN-TOKEN-005) |
| `criado_em` | `DATETIME` | `NOT NULL` |

Índices: `INDEX(subscription_id, ciclo_referencia)` (consulta mais
comum: "quanto essa assinatura já consumiu nesse ciclo" —
checagem de cota, RN-ADS-003/RN-TRIAL-005), `INDEX(store_id)`.

**Nunca editado ou apagado** (mesmo padrão de `stock_movements`/
`ad_snapshots`) — correção de um valor errado gera linha de ajuste
separada, não `UPDATE`.

**Saldo consumido não é uma coluna cacheada aqui nem em
`subscriptions`** — é `SUM(tokens_total)` sobre esta tabela filtrado
por `subscription_id` + `ciclo_referencia`, calculado a cada checagem
de entitlement. Decisão deliberada: evita um contador que pode
divergir da soma real por bug de concorrência. Se isso virar gargalo
de performance no futuro, cabe um contador cacheado como otimização —
não antes de medir que é necessário.

**Trial isolado do ciclo pago (RN-TRIAL-004):** enquanto
`subscriptions.status` está em qualquer estado de trial,
`ciclo_referencia` marca o início do trial (não um ciclo mensal de
cobrança, que ainda não existe) — a soma de tokens do trial nunca se
mistura com a soma de um plano pago, mesmo que seja a mesma linha de
`subscriptions` reaproveitada depois (ver nota de reativação em
`subscriptions`, Etapa C3).

### `token_package_plans` (catálogo — novo, Etapa C6)

Catálogo fixo dos 3 tamanhos de pacote avulso — mesmo padrão de
`plans`: tabela pequena, seed manual, revisão manual (RN-TOKEN-010).

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `tamanho` | `VARCHAR(10)` | `NOT NULL`, `UNIQUE`, `CHECK (tamanho IN ('P','M','G'))` |
| `tokens` | `INT` | `NOT NULL` |
| `preco_brl` | `DECIMAL(10,2)` | `NOT NULL` |
| `criado_em` / `atualizado_em` | `DATETIME` | `NOT NULL` |

Seed inicial: `P` (50.000 tokens, R$19,50), `M` (100.000, R$39,00),
`G` (150.000, R$58,50) — §5.2 da precificação. **Preço fixo**, não
recalculado pelo job diário de câmbio (RN-TOKEN-010) — só muda em
atualização manual, junto com o preço da API.

### `token_packages` (compras — novo, Etapa C6)

1 linha por compra de pacote avulso.

| Coluna | Tipo | Constraint |
|---|---|---|
| `id` | `CHAR(36)` | PK |
| `subscription_id` | `CHAR(36)` | `NOT NULL`, FK → `subscriptions.id`, `ON DELETE RESTRICT` — deve ser uma assinatura de `module = 'ADS_ANALYZER'` (invariante de aplicação, sem pacote avulso pra PRODUCTS) |
| `package_plan_id` | `CHAR(36)` | `NOT NULL`, FK → `token_package_plans.id`, `ON DELETE RESTRICT` |
| `tokens_comprados` | `INT` | `NOT NULL` — snapshot de `token_package_plans.tokens` no momento da compra |
| `preco_pago_brl` | `DECIMAL(10,2)` | `NOT NULL` — snapshot de `token_package_plans.preco_brl` no momento da compra (o catálogo pode mudar depois; a compra já feita não) |
| `ciclo_referencia` | `DATE` | `NOT NULL` — igual ao `ciclo_referencia` de `token_usage` (**corrigido no C11**: período mensal de tokens, não o ciclo de cobrança) |
| `expira_em` | `DATE` | `NOT NULL` — `data_fim_periodo_tokens_atual` no momento da compra (**corrigido no C11**, não o fim do ciclo de cobrança) — RN-TOKEN-003: expira junto com a cota normal, sem acumular |
| `criado_em` | `DATETIME` | `NOT NULL` |

Índices: `INDEX(subscription_id, ciclo_referencia)`.

**Sem coluna de "tokens restantes" no pacote:** o saldo disponível
não é rastreado pacote a pacote (não é FIFO) — é um pool único por
ciclo: `plans.cota_tokens_mes_atual` (ajustada por upgrade/downgrade,
RN-TOKEN-004) `+ SUM(token_packages.tokens_comprados` do ciclo`)
− SUM(token_usage.tokens_total` do ciclo`)`. Mais simples de auditar
e evita ter que decidir qual pacote "gastar primeiro".

---

## 10. Auditoria administrativa

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

## 11. Ordem de migração Flyway

> **Nota de sincronização (pós-Fase 1):** os números `V2`–`V19` abaixo
> indicam a **ordem relativa** das migrations, não números fixos. Na
> implementação, `V2` cobriu só as 4 tabelas de Auth (+ `cnpj`/
> `cnpj_verificado_em`, que aqui aparecem em `V16`), `V3` adicionou
> throttling de login e `V4` a chave de MFA. `plans`, `api_pricing_history`
> e `exchange_rate_snapshots` **não** estão na `V2` real — entram na
> Fase 1.5, com o próximo número livre. `store_module_activation` depende
> de `stores` (Fase 2) e a FK `token_usage → analysis_runs` só pode existir
> na Fase 7. Regra: sempre o próximo número livre, nunca editar migration
> já aplicada.

| Versão | Conteúdo |
|---|---|
| `V1` | Estrutura atual (`products` — bootstrap a partir do schema existente, antes de `ddl-auto=update` ser desligado) |
| `V2` | Auth: `sellers` (sem `plan_id`), `refresh_tokens`, `email_confirmation_tokens`, `password_reset_tokens`, `plans` (redesenhado v2.0), `api_pricing_history`, `exchange_rate_snapshots` |
| `V3` | Sellers & Stores: `stores`, `store_credentials` |
| `V4` | Produtos: `seller_id` em `products`, `product_variations`, migração dos produtos existentes para variação "padrão" |
| `V5` | Marketplace Links: `product_marketplace_links` |
| `V6` | Estoque: `stock_movements`, constraints `CHECK` em `product_variations.amount`/`estoque_minimo` |
| `V7` | Webhooks: `webhook_events`, `reconciliation_alerts` |
| `V8` | Ads: `ad_snapshots`, `sku_metrics`, `ad_reports`, `analysis_runs` |
| `V9` | Auditoria: `admin_audit_log` |
| `V10` | Assinaturas & Billing: `subscriptions` (Etapa C3) |
| `V11` | Sellers & Stores: `store_module_activation` (Etapa C4) |
| `V12` | Ads Analyzer: `token_usage` (Etapa C5) |
| `V13` | Ads Analyzer: `token_package_plans`, `token_packages` (Etapa C6) |
| `V14` | Assinaturas & Billing: `payment_attempts` (Etapa C7) |
| `V15` | Assinaturas & Billing: `trial_contestations`, `risk_signals` (Etapa C8/C8b) |
| `V16` | Auth: alter `sellers` adiciona `cnpj`, `cnpj_verificado_em` (Etapa C8) |
| `V17` | Assinaturas & Billing: alter `subscriptions` adiciona `data_expiracao_trial` (Etapa C8) |
| `V18` | Assinaturas & Billing: `refunds` (achado no C11) |
| `V19` | Assinaturas & Billing: alter `subscriptions` adiciona `data_inicio_periodo_tokens_atual`, `data_fim_periodo_tokens_atual` (correção do C11 — separa período mensal de tokens do ciclo de cobrança) |

Cada migration é aditiva e reversível apenas para frente (sem `down` — Flyway padrão); qualquer correção de schema depois de aplicado entra como nova versão (`V10`, `V11`...), nunca editando uma migration já rodada em produção.

Todas as tabelas planejadas na Etapa C (C1–C8b) do plano de execução
já têm migration definida acima — nenhuma pendência de schema restante
nessa etapa.

**Nota (v5.0, era "v4.3"):** a exportação de dados brutos foi
**removida como funcionalidade** (§8.6 do documento de engenharia,
v5.0), não apenas "não precisa de migration" — não sobrou nenhum uso
pago pra ela depois que a cota gratuita de 3 execuções virou o
mecanismo de trial (RN-TRIAL-007).
