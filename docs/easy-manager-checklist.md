# Easy Manager — Checklist de Próximos Passos (progresso)

**Última atualização:** Setembro/2026
**Como usar:** `[x]` = concluído, com referência de onde está registrado.
`[ ]` = ainda não feito. Itens parcialmente resolvidos ficam `[ ]` com
uma nota — só marco `[x]` quando está de fato fechado, para o
checklist continuar confiável.

Documentos de referência até agora:
- `easy-manager-precificacao.md` (v2.4) — regras de negócio em prosa
- `easy-manager-regras-negocio.md` (v1.1) — regras extraídas em RN-XXX + invariantes + máquina de estados
- `easy-manager-arquitetura-banco-de-dados.md` (v2.0) — schema completo, migrations `V2`–`V19`
- `easy-manager-software-engineering.md` (v5.0) — Entitlements (§4.6), demais bounded contexts
- `easy-manager-diagramas-uml.md` (v1.0) — classes, casos de uso, sequência, estados, componentes
- `easy-manager-plano-execucao.md` — roteiro das Etapas A–G, com o histórico de todas as revisões cruzadas
- `PROGRESS.md` — roadmap de implementação pro Claude Code (Fase 0 concluída, Fase 1.5 nova)

---

## 1. Especificação de negócio

- [x] Extrair todas as regras do documento e transformar em RN-XXX
      → `easy-manager-regras-negocio.md`, 73 regras (cresceu de 51 ao
      longo das revisões — RN-PAY-004, RN-SUB-009/010, RN-TOKEN-006–010
      e outras adicionadas depois da extração inicial)
- [x] Separar regras por domínio
  - [x] Plans
  - [x] Subscriptions
  - [x] Stores
  - [x] PRODUCTS
  - [x] ADS_ANALYZER
  - [x] Trial
  - [x] Tokens
  - [x] Combo
  - [x] Pagamentos
  - [x] Inadimplência
  - [x] Retenção
  - [x] Anti-abuso
- [x] Definir as invariantes do sistema
      → seção "Invariantes do sistema" em `easy-manager-regras-negocio.md`, 12 invariantes
- [x] Definir claramente o que o sistema nunca pode permitir
      → mesma seção acima
- [x] Criar máquina de estados da assinatura
      → seção 13 de `easy-manager-regras-negocio.md` (por módulo,
      9 estados, diagrama Mermaid)
- [x] Documentar transições entre estados
      → tabela de transições §13.3 (origem/gatilho/destino/condição/RN)
- [x] Documentar regras de upgrade/downgrade
      → transições `ATIVO → ATIVO` via upgrade/downgrade, §13.3
- [x] Documentar regras de cancelamento
      → transições `ATIVO → CANCELAMENTO_SOLICITADO → RETENÇÃO`, §13.3
- [x] Documentar regras de renovação
      → transição `ATIVO → ATIVO` via renovação de ciclo, §13.3

## 2. Fechar o modelo de tokens

- [x] Definir cálculo de custo de input
      → RN-TOKEN-006 (média ponderada, peso ~2:1 entrada:saída,
      recalibrado automaticamente após 100 execuções reais)
- [x] Definir cálculo de custo de output
      → mesmo RN-TOKEN-006
- [x] Definir como input + output viram custo real
      → mesmo RN-TOKEN-006 (custo_por_token_atual = média ponderada)
- [x] Definir cálculo da cota
      → RN-TOKEN-001 (`cota_tokens_mes = custo_maximo_R$ / custo_por_token_atual`)
- [x] Definir arredondamentos
      → RN-TOKEN-007 (sempre para baixo/piso)
- [x] Definir comportamento quando a execução ultrapassaria a cota
      → RN-ADS-003 (planos pagos) e RN-TRIAL-005 (trial — teto de tokens sempre prevalece)
- [x] Definir como o câmbio influencia a cota
      → RN-TOKEN-002 (job diário de câmbio)
- [x] Definir como atualizar o preço da API da Anthropic
      → RN-TOKEN-002 (atualização manual, sem automação)
- [x] Definir histórico das alterações de preço da API
      → RN-TOKEN-008 (versionado: data, valor anterior, valor novo)
- [x] Definir se o sistema controla internamente tokens, R$, ou ambos
      → RN-TOKEN-009 (tokens é a fonte de verdade; R$ é só snapshot histórico)

## 3. Modelo de dados

*(O desenho completo de todas as tabelas novas/alteradas está em
`easy-manager-arquitetura-banco-de-dados.md` v2.0 — migrations `V2`
a `V19` documentadas. Os itens "Definir X" abaixo estão marcados
porque isso é exatamente o que foi feito; os itens "Criar/Revisar X"
continuam `[ ]` porque são entidades/migrations de código (`.java`/
Flyway) ainda não escritas — a spec pronta não substitui o arquivo.)*

- [ ] Criar ERD atualizado
- [ ] Revisar entidade Seller
- [ ] Revisar entidade Store
- [ ] Criar/revisar Module
- [ ] Criar/revisar Plan
- [ ] Criar/revisar PlanModule
- [ ] Criar Subscription
- [x] Definir ciclo da assinatura
- [x] Definir ativação de Store por módulo
- [x] Definir controle de consumo de tokens
- [x] Definir histórico de uso da IA
- [x] Definir pacotes extras de tokens
- [x] Definir pagamentos
- [x] Definir tentativas de pagamento
- [x] Definir dados de trial
- [x] Definir score de risco
- [x] Definir retenção/exclusão de dados
- [ ] Criar índices necessários
- [ ] Criar constraints necessárias
- [ ] Criar migrations

## 4. Entitlements

*(Design/especificação de todo este item já está pronto em
`easy-manager-software-engineering.md` §4.6 v5.0 — contrato do
`EntitlementService`, método a método, com a lógica e a RN-XXX de
cada resposta. Os itens abaixo continuam `[ ]` porque isso é código
ainda não escrito, não spec — a spec por si não conta como "criado".)*

- [ ] Seller pode utilizar PRODUCTS?
- [ ] Seller pode utilizar ADS_ANALYZER?
- [ ] Quantas Stores pode cadastrar?
- [ ] Quantas Stores pode ativar?
- [ ] Quais Stores estão ativas?
- [ ] Quantos tokens possui?
- [ ] Ainda pode executar análise?
- [ ] Pode comprar pacote extra?
- [ ] Está dentro do período de trial?
- [ ] Está inadimplente?
- [ ] Está dentro do período de retenção?
- [ ] Criar `EntitlementService` (canUseModule, canCreateStore,
      canActivateStore, canExecuteAnalysis, getStoreLimit,
      getTokenBalance, getSubscriptionStatus, canCreateProduct)

## 5. Subscription/Billing

- [ ] Criar criação de assinatura
- [ ] Criar ativação após pagamento aprovado
- [ ] Criar renovação
- [ ] Criar upgrade
- [ ] Criar downgrade
- [ ] Criar cancelamento
- [ ] Criar reativação
- [ ] Criar troca de ciclo
- [ ] Implementar mensal
- [ ] Implementar trimestral
- [ ] Implementar semestral
- [ ] Implementar anual
- [ ] Implementar descontos
- [ ] Implementar combo
- [ ] Implementar separação dos módulos
- [ ] Implementar regra dos 7 dias
- [ ] Implementar comportamento após cancelamento

## 6. Stores

- [ ] Criar Store
- [ ] Excluir Store
- [ ] Ativar Store para PRODUCTS
- [ ] Desativar Store para PRODUCTS
- [ ] Ativar Store para ADS_ANALYZER
- [ ] Desativar Store para ADS_ANALYZER
- [ ] Validar limite de cadastro
- [ ] Validar limite de Stores ativas
- [ ] Implementar downgrade automático
- [ ] Permitir escolha manual das Stores
- [ ] Preservar histórico de Store desvinculada
- [ ] Excluir histórico quando o seller excluir definitivamente a Store
- [ ] Garantir que `stock_sync_status` permaneça separado da ativação do módulo

## 7. Trial

**PRODUCTS**
- [ ] Criar trial sem cartão
- [ ] Limitar a 1 Store
- [ ] Limitar a 15 produtos
- [ ] Bloquear 16º produto
- [ ] Bloquear 2ª Store
- [ ] Direcionar para upgrade
- [ ] Aplicar verificação anti-abuso

**ADS_ANALYZER**
- [ ] Criar 3 execuções gratuitas
- [ ] Criar limite de 60.000 tokens
- [ ] Criar prompt específico do trial
- [ ] Criar relatório parcial
- [ ] Bloquear seções premium
- [ ] Criar CTA de conversão
- [ ] Criar barra de consumo
- [ ] Bloquear execução antes de ultrapassar 60.000 tokens
- [ ] Persistir snapshots
- [ ] Criar expiração de snapshots após 30 dias

## 8. Anti-abuso

- [ ] Verificar CNPJ
- [ ] Criar identificação de dispositivo
- [ ] Registrar e-mail utilizado
- [ ] Registrar rede/IP como sinal auxiliar
- [ ] Criar score de risco
- [ ] Definir regra de 2+ sinais
- [ ] Bloquear trial automaticamente quando necessário
- [ ] Enviar e-mail de notificação
- [ ] Criar tela de bloqueio
- [ ] Criar "Contestar bloqueio"
- [ ] Criar fluxo de revisão manual
- [ ] Implementar rate limit
- [ ] Implementar confirmação de e-mail
- [ ] Bloquear e-mails descartáveis
- [ ] Implementar limite por dispositivo
- [ ] Criar métricas de trial → pago

## 9. Token Usage

- [ ] Registrar cada execução
- [ ] Registrar tokens de entrada
- [ ] Registrar tokens de saída
- [ ] Registrar custo da execução
- [ ] Associar execução à Store
- [ ] Associar execução à assinatura
- [ ] Associar execução ao ciclo
- [ ] Calcular saldo
- [ ] Implementar alerta de 80%
- [ ] Bloquear ao atingir limite
- [ ] Implementar pacote Extra P
- [ ] Implementar Extra M
- [ ] Implementar Extra G
- [ ] Implementar expiração dos pacotes
- [ ] Implementar upgrade com transporte do consumo
- [ ] Implementar downgrade com possibilidade de saldo negativo

## 10. Jobs automáticos

- [ ] Job diário de câmbio
- [ ] Job de atualização da cota
- [ ] Job de renovação
- [ ] Job de cobrança
- [ ] Job de retry
- [ ] Job de bloqueio por inadimplência
- [ ] Job de retenção de PRODUCTS
- [ ] Job de retenção de ADS_ANALYZER
- [ ] Job de limpeza de trial
- [ ] Job de limpeza de snapshots
- [ ] Criar logs de execução dos jobs
- [ ] Criar mecanismo para evitar execução duplicada

## 11. API

**Plans**
- [ ] GET /plans
- [ ] GET /plans/{id}

**Subscription**
- [ ] Criar assinatura
- [ ] Consultar assinatura
- [ ] Upgrade
- [ ] Downgrade
- [ ] Cancelar
- [ ] Reativar
- [ ] Alterar ciclo

**Stores**
- [ ] Criar
- [ ] Listar
- [ ] Excluir
- [ ] Ativar módulo
- [ ] Desativar módulo

**ADS_ANALYZER**
- [ ] Executar análise
- [ ] Consultar consumo
- [ ] Consultar histórico
- [ ] Consultar saldo
- [ ] Comprar tokens extras

**Trial**
- [ ] Criar trial
- [ ] Consultar status
- [ ] Consultar consumo
- [ ] Contestar bloqueio

**Billing**
- [ ] Webhook de pagamento aprovado
- [ ] Webhook de pagamento recusado
- [ ] Webhook de renovação
- [ ] Webhook de cancelamento

## 12. Testes

**Unitários**
- [ ] Cálculo de preço
- [ ] Cálculo de desconto
- [ ] Cálculo de cota
- [ ] Cálculo de consumo
- [ ] Limite de Stores
- [ ] Limite de tokens
- [ ] Upgrade
- [ ] Downgrade
- [ ] Combo
- [ ] Trial
- [ ] Retenção
- [ ] Inadimplência

**Integração**
- [ ] Banco
- [ ] Pagamento
- [ ] Webhooks
- [ ] Anthropic
- [ ] Jobs
- [ ] Sistema de Stores

**Casos extremos**
- [ ] Consumo exatamente igual à cota
- [ ] Consumo 1 token acima da cota
- [ ] Upgrade após 99% de consumo
- [ ] Downgrade com consumo superior à nova cota
- [ ] Exclusão da Store no limite
- [ ] Downgrade com Stores excedentes
- [ ] Falha na primeira cobrança
- [ ] Falha na renovação
- [ ] Cancelamento no último dia
- [ ] Cancelamento dentro dos 7 dias
- [ ] Combo com cancelamento parcial
- [ ] Trial bloqueado por score
- [ ] Falso positivo contestado

## 13. Frontend

- [ ] Página de preços
- [ ] Comparação dos planos
- [ ] Seleção de módulo
- [ ] Seleção de combo
- [ ] Seleção de ciclo
- [ ] Checkout
- [ ] Tela da assinatura
- [ ] Tela de Stores
- [ ] Seleção de Store ativa por módulo
- [ ] Barra de tokens
- [ ] Aviso de 80%
- [ ] Tela de cota esgotada
- [ ] Tela de upgrade
- [ ] Tela de downgrade
- [ ] Tela de inadimplência
- [ ] Tela de retenção de dados
- [ ] Tela de trial
- [ ] Tela de bloqueio anti-abuso
- [ ] Tela de contestação

## 14. Observabilidade

- [ ] Log de alterações de plano
- [ ] Log de consumo de IA
- [ ] Log de pagamentos
- [ ] Log de bloqueios
- [ ] Log de score de risco
- [ ] Log de execução dos jobs
- [ ] Métrica de custo de IA
- [ ] Métrica de margem real
- [ ] Métrica de trial → pago
- [ ] Métrica de churn
- [ ] Métrica de inadimplência
- [ ] Métrica de uso por Store
- [ ] Alertas de custo anormal da IA

## 15. Antes de colocar em produção

- [ ] Testar fluxo completo de trial
- [ ] Testar primeira assinatura
- [ ] Testar pagamento aprovado
- [ ] Testar pagamento recusado
- [ ] Testar renovação
- [ ] Testar inadimplência
- [ ] Testar cancelamento
- [ ] Testar reativação
- [ ] Testar upgrade
- [ ] Testar downgrade
- [ ] Testar combo
- [ ] Testar troca de ciclo
- [ ] Testar consumo máximo de IA
- [ ] Testar pacote adicional
- [ ] Testar exclusão de Store
- [ ] Testar retenção
- [ ] Testar anti-abuso
- [ ] Testar recuperação de falhas
- [ ] Fazer teste de carga
- [ ] Fazer backup/restore
- [ ] Revisar segurança
- [ ] Revisar LGPD
- [ ] Revisar termos de uso
- [ ] Revisar política de privacidade
- [ ] Revisar política de cancelamento/reembolso

## 16. Diagramas UML

*(Todos em `easy-manager-diagramas-uml.md`, notação Mermaid.)*

- [x] Diagrama de classes (Seller, Store, Module, Plan, PlanModule,
      Subscription, Token Usage, Payment Attempt, Trial)
      → §1 (escopo atualizado: `PlanModule` foi removida no C2, o
      diagrama reflete `Plan`→`Subscription` direto + `combo_group_id`)
- [x] Diagrama de casos de uso (seller, sistema, jobs automáticos) → §2
- [x] Diagrama de sequência — assinatura de plano (checkout → pagamento
      aprovado → ativação) → §3.1
- [x] Diagrama de sequência — execução de análise ADS_ANALYZER (coleta
      → checagem de cota → chamada IA → snapshot → relatório) → §3.2
- [x] Diagrama de sequência — upgrade/downgrade de plano → §3.3
- [x] Diagrama de sequência — régua de inadimplência (tentativas →
      bloqueio → retenção → exclusão) → §3.4
- [x] Diagrama de estados — assinatura (trial/ativo/inadimplente/
      bloqueado/cancelado/retenção/excluído) — mesmo diagrama da máquina
      de estados do item 1, em notação UML formal
      → já existia em `regras-negocio.md` §13.2 (Mermaid `stateDiagram-v2`
      **é** notação UML formal); §4.1 do novo documento aponta pra lá
      como fonte única, sem duplicar
- [x] Diagrama de estados — Store (cadastrada/ativa por módulo/
      desvinculada/excluída) → §4.2 (novo, não existia antes)
- [x] Diagrama de componentes/arquitetura (API, banco, jobs, integração
      Anthropic, painel web)
