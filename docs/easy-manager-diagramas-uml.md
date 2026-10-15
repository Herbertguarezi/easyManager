# Easy Manager — Diagramas UML

**Versão:** 1.0
**Data:** Setembro/2026
**Fonte:** `easy-manager-arquitetura-banco-de-dados.md` v2.0,
`easy-manager-software-engineering.md` v5.0,
`easy-manager-regras-negocio.md` (RN-XXX + máquina de estados §13)
**Etapa:** D do `easy-manager-plano-execucao.md` (checklist item 16)

Notação Mermaid em todos os diagramas — renderiza direto no GitHub,
GitLab, e na maioria dos editores markdown modernos.

---

## 1. Diagrama de classes — Assinaturas & Billing

Escopo: o bounded context novo desta revisão (Etapa C). `Seller`,
`Store` e `Product` aparecem só com os campos relevantes pra esse
contexto — o detalhe completo deles está nos bounded contexts próprios
(`arquitetura-banco-de-dados.md` §4–§6).

```mermaid
classDiagram
    class Seller {
        +UUID id
        +String email
        +String cnpj
        +DateTime cnpjVerificadoEm
    }

    class Plan {
        +UUID id
        +Module module
        +String nome
        +int storesLimit
        +BigDecimal precoMensal
        +BigDecimal margemAlvo
        +long cotaTokensMesAtual
    }

    class Subscription {
        +UUID id
        +UUID sellerId
        +Module module
        +UUID planId
        +SubscriptionStatus status
        +CicloCobranca ciclo
        +Date dataAniversario
        +Date dataInicioCicloAtual
        +Date dataFimCicloAtual
        +Date dataInicioPeriodoTokensAtual
        +Date dataFimPeriodoTokensAtual
        +UUID comboGroupId
        +Date dataExpiracaoTrial
        +DateTime dataCancelamentoSolicitado
        +DateTime dataBloqueio
        +Date dataExclusaoPrevista
    }

    class StoreModuleActivation {
        +UUID id
        +UUID storeId
        +Module module
        +boolean ativo
        +DateTime ativadoEm
        +DateTime desativadoEm
    }

    class TokenUsage {
        +UUID id
        +UUID analysisRunId
        +UUID subscriptionId
        +UUID storeId
        +Origem origem
        +int tokensEntrada
        +int tokensSaida
        +int tokensTotal
        +BigDecimal custoBrlSnapshot
        +Date cicloReferencia
    }

    class TokenPackagePlan {
        +UUID id
        +Tamanho tamanho
        +int tokens
        +BigDecimal precoBrl
    }

    class TokenPackage {
        +UUID id
        +UUID subscriptionId
        +UUID packagePlanId
        +int tokensComprados
        +BigDecimal precoPagoBrl
        +Date cicloReferencia
        +Date expiraEm
    }

    class PaymentAttempt {
        +UUID id
        +UUID subscriptionId
        +TipoTentativa tipo
        +int tentativaNumero
        +DateTime dataTentativa
        +ResultadoTentativa resultado
    }

    class Refund {
        +UUID id
        +UUID subscriptionId
        +TipoReembolso tipo
        +BigDecimal valorBrl
        +DateTime solicitadoEm
        +DateTime processadoEm
    }

    class TrialContestation {
        +UUID id
        +UUID subscriptionId
        +String justificativa
        +StatusContestacao status
        +UUID analisadoPor
    }

    class RiskSignal {
        +UUID id
        +UUID subscriptionId
        +TipoSinal tipoSinal
        +String detalhe
        +DateTime detectadoEm
    }

    class Store {
        +UUID id
        +UUID sellerId
        +boolean ativo
    }

    Seller "1" -- "0..2" Subscription : possui
    Plan "1" -- "0..*" Subscription : vigente em
    Subscription "1" -- "0..*" StoreModuleActivation : autoriza
    Store "1" -- "0..2" StoreModuleActivation : ativada em
    Subscription "1" -- "0..*" TokenUsage : consome
    Subscription "1" -- "0..*" TokenPackage : compra
    TokenPackagePlan "1" -- "0..*" TokenPackage : catálogo de
    Subscription "1" -- "0..*" PaymentAttempt : tenta cobrar
    Subscription "1" -- "0..*" Refund : reembolsa
    Subscription "1" -- "0..*" TrialContestation : contesta
    Subscription "1" -- "0..*" RiskSignal : detecta sinal em
```

---

## 2. Diagrama de casos de uso

Mermaid não tem notação nativa de caso de uso — representado como
grafo ator↔ação, mesma informação.

```mermaid
flowchart LR
    Seller((Seller))
    Admin((Admin))
    Sistema((Sistema/Jobs))

    Seller --> UC1[Iniciar trial]
    Seller --> UC2[Assinar plano]
    Seller --> UC3[Fazer upgrade/downgrade]
    Seller --> UC4[Cancelar assinatura]
    Seller --> UC5[Exercer arrependimento 7 dias]
    Seller --> UC6[Contestar bloqueio de trial]
    Seller --> UC7[Ativar/desativar Store por módulo]
    Seller --> UC8[Executar análise de anúncios]
    Seller --> UC9[Comprar pacote avulso de tokens]
    Seller --> UC10[Excluir Store]

    Admin --> UC11[Revisar contestação]
    Admin --> UC12[Atualizar preço da API/câmbio manual]

    Sistema --> UC13[Job diário de câmbio + recalcular cota]
    Sistema --> UC14[Régua de cobrança/inadimplência]
    Sistema --> UC15[Job de retenção/exclusão]
    Sistema --> UC16[Job de expiração de trial]

    UC2 -.include.-> UC17[Validar combo]
    UC3 -.include.-> UC18[Checar ciclo mensal - RN-SUB-004]
    UC8 -.include.-> UC19[Checar entitlement + saldo]
```

---

## 3. Diagramas de sequência

### 3.1 Assinatura de plano (checkout → 1ª cobrança → ativação)

```mermaid
sequenceDiagram
    actor Seller
    participant API
    participant SubscriptionService
    participant Gateway as Gateway de Pagamento
    participant DB

    Seller->>API: POST /subscriptions (module, plan, ciclo)
    API->>SubscriptionService: criarAssinatura(...)
    SubscriptionService->>DB: valida combo (se aplicável, RN-COMBO-002)
    SubscriptionService->>Gateway: cobrar 1ª fatura
    alt pagamento aprovado
        Gateway-->>SubscriptionService: aprovado
        SubscriptionService->>DB: INSERT/UPDATE subscriptions (status=ATIVO)
        SubscriptionService->>DB: INSERT payment_attempts (tipo=PRIMEIRA_COBRANCA, resultado=APROVADO)
        SubscriptionService-->>API: 201 Created
        API-->>Seller: assinatura ativa
    else pagamento recusado
        Gateway-->>SubscriptionService: recusado
        SubscriptionService->>DB: INSERT payment_attempts (tipo=PRIMEIRA_COBRANCA, resultado=RECUSADO)
        Note over SubscriptionService,DB: RN-PAY-001: nenhum acesso liberado,<br/>subscriptions permanece sem linha ATIVO
        SubscriptionService-->>API: 402 Payment Required
        API-->>Seller: pagamento recusado, tente novamente
    end
```

### 3.2 Execução de análise (ADS_ANALYZER)

```mermaid
sequenceDiagram
    actor Seller
    participant API
    participant EntitlementService
    participant AdAnalysisService
    participant Shopee as Shopee API
    participant Anthropic
    participant DB

    Seller->>API: POST /ads/analysis/{storeId}
    API->>EntitlementService: canExecuteAnalysis(sellerId, storeId)
    EntitlementService->>DB: status da subscription + saldo (token_usage/token_packages)
    alt sem saldo ou módulo/Store não liberados
        EntitlementService-->>API: false
        API-->>Seller: 403 (cota esgotada / Store inativa / módulo não liberado)
    else liberado
        EntitlementService-->>API: true
        API->>AdAnalysisService: executar(storeId)
        AdAnalysisService->>DB: INSERT analysis_runs (status=RUNNING)
        AdAnalysisService->>Shopee: coletar pedidos + ads do dia
        Shopee-->>AdAnalysisService: dados
        AdAnalysisService->>DB: INSERT ad_snapshots, sku_metrics
        AdAnalysisService->>Anthropic: mensagem com prompt determinístico
        Anthropic-->>AdAnalysisService: relatório + uso de tokens
        AdAnalysisService->>DB: INSERT token_usage (tokens_entrada, tokens_saida, ciclo_referencia)
        AdAnalysisService->>DB: INSERT ad_reports
        AdAnalysisService->>DB: UPDATE analysis_runs (status=SUCCESS)
        AdAnalysisService-->>API: relatório pronto
        API-->>Seller: 200 OK
    end
```

### 3.3 Upgrade / downgrade de plano

```mermaid
sequenceDiagram
    actor Seller
    participant API
    participant SubscriptionService
    participant DB

    Seller->>API: PATCH /subscriptions/{id} (novoPlanId)
    API->>SubscriptionService: trocarPlano(...)
    SubscriptionService->>DB: subscriptions.ciclo == MENSAL?
    alt ciclo antecipado, fora da janela de vencimento
        SubscriptionService-->>API: 422 (RN-SUB-004: troca bloqueada em ciclo antecipado)
        API-->>Seller: erro — só perto do vencimento
    else ciclo mensal (ou perto do vencimento)
        SubscriptionService->>DB: SUM(token_usage) do período atual
        SubscriptionService->>DB: UPDATE subscriptions SET plan_id = novoPlanId
        Note over SubscriptionService,DB: saldo = cota_do_plano_novo − consumido<br/>(RN-SUB-005/006, RN-TOKEN-004)
        alt saldo negativo (downgrade)
            SubscriptionService-->>API: 200 (execuções bloqueadas até próximo reset mensal)
        else saldo positivo
            SubscriptionService-->>API: 200 (troca concluída)
        end
        API-->>Seller: plano atualizado
    end
```

### 3.4 Régua de inadimplência (job diário)

```mermaid
sequenceDiagram
    participant Job as Job de Cobrança (diário)
    participant DB
    participant Gateway as Gateway de Pagamento
    participant Email as Serviço de E-mail

    Job->>DB: SELECT subscriptions WHERE status IN (ATIVO, INADIMPLENTE) AND vencimento <= hoje
    loop cada assinatura vencida
        Job->>DB: COUNT(payment_attempts WHERE tipo=RENOVACAO, tentativa consecutiva)
        alt tentativa 1 (1º dia de atraso)
            Job->>DB: UPDATE subscriptions SET status = INADIMPLENTE
        end
        Job->>Gateway: tentar cobrar
        alt aprovado
            Gateway-->>Job: aprovado
            Job->>DB: INSERT payment_attempts (resultado=APROVADO)
            Job->>DB: UPDATE subscriptions SET status = ATIVO
        else recusado, tentativa < 5
            Gateway-->>Job: recusado
            Job->>DB: INSERT payment_attempts (resultado=RECUSADO)
            Note over Job,DB: acesso continua liberado (RN-INAD-001)
        else recusado, tentativa = 5 (dia 6)
            Gateway-->>Job: recusado
            Job->>DB: INSERT payment_attempts (resultado=RECUSADO)
            Job->>DB: UPDATE subscriptions SET status = RETENCAO,<br/>data_bloqueio = agora,<br/>data_exclusao_prevista = agora + 7/30 dias
            Job->>Email: notificar seller (bloqueio + prazo de retenção)
        end
    end
```

---

## 4. Diagramas de estados

### 4.1 Assinatura (já formalizado em `regras-negocio.md` §13 — reproduzido aqui por completude)

Ver `easy-manager-regras-negocio.md` §13.2 para o diagrama completo
(9 estados, todas as transições com RN-XXX — inclui `ARREPENDIDO`,
achado depois deste diagrama ter sido publicado; ver `regras-negocio.md`
§13.4). Não duplicado aqui pra
evitar as duas versões divergirem — **a fonte de verdade é §13 do
regras-negocio.md**, não este documento.

### 4.2 Store (cadastro → ativação por módulo → desvinculação/exclusão)

Diferente da assinatura, esta máquina não tem uma tabela de `status`
única — é a combinação de `stores.deleted_at` (soft delete) e o
`ativo`/`desativado_em` de cada linha de `store_module_activation`
(uma por módulo). O diagrama abaixo mostra o ciclo de vida de **uma
linha de `store_module_activation`**, já que é isso que efetivamente
muda de estado.

```mermaid
stateDiagram-v2
    [*] --> INATIVA: Store cadastrada\n(RN-STORE-001,\nlinha criada p/ ambos módulos)
    INATIVA --> ATIVA: seller ativa\n(dentro do limite do plano,\nRN-STORE-002/006)
    ATIVA --> INATIVA: seller desativa\nmanualmente
    ATIVA --> DESVINCULADA: downgrade automático\n(ordem de cadastro,\nRN-STORE-004)
    DESVINCULADA --> ATIVA: upgrade recupera\na vaga (histórico retido,\nRN-STORE-005/RN-RET-004)
    INATIVA --> EXCLUIDA: seller exclui a Store\n(RN-STORE-008)
    ATIVA --> EXCLUIDA: seller exclui a Store\n(RN-STORE-008)
    DESVINCULADA --> EXCLUIDA: seller exclui a Store\n(RN-STORE-008)
    EXCLUIDA --> [*]: hard delete\n(sem retenção, RN-RET-005)
```

**Nota:** `DESVINCULADA` e `INATIVA` são estados de dados idênticos no
schema (`ativo=false`) — a diferença é só **como chegou lá** (downgrade
automático vs. desativação manual), preservada em `desativado_em` +
log de auditoria, não em um campo de estado separado. Aqui no diagrama
eles aparecem distintos só pra deixar as duas origens claras.

---

## 5. Diagrama de componentes / arquitetura

```mermaid
flowchart TB
    subgraph Frontend
        SPA[React SPA]
    end

    subgraph Backend["Spring Boot API"]
        Auth[Auth/JWT]
        Entitlement[EntitlementService]
        SubSvc[SubscriptionService +\nSubscriptionStateMachine]
        ProdSvc[ProductService]
        AdsSvc[AdAnalysisService]
        Webhooks[WebhookController]
    end

    subgraph Jobs["Jobs agendados"]
        JobCambio[Job diário de câmbio/cota]
        JobCobranca[Job de cobrança/inadimplência]
        JobRetencao[Job de retenção/exclusão]
        JobTrial[Job de expiração de trial]
        JobAnalise[Job de análise agendada]
    end

    DB[(MariaDB)]
    Anthropic[Anthropic API]
    Shopee[Shopee Open API]
    Gateway[Gateway de Pagamento]
    Email[Serviço de E-mail]

    SPA -->|REST + JWT| Auth
    Auth --> Entitlement
    Entitlement --> SubSvc
    Entitlement --> ProdSvc
    Entitlement --> AdsSvc

    SubSvc --> DB
    ProdSvc --> DB
    AdsSvc --> DB
    Webhooks --> DB

    AdsSvc --> Anthropic
    AdsSvc --> Shopee
    Webhooks -.recebe evento.- Shopee

    SubSvc --> Gateway
    JobCobranca --> Gateway
    JobCobranca --> Email
    JobRetencao --> Email

    JobCambio --> DB
    JobCobranca --> DB
    JobRetencao --> DB
    JobTrial --> DB
    JobAnalise --> AdsSvc

    style Frontend fill:#e8f4f8
    style Backend fill:#fff4e0
    style Jobs fill:#f0e8f8
```

---

## 6. Rastreabilidade

Todos os diagramas acima usam nomes de tabela/coluna idênticos aos de
`easy-manager-arquitetura-banco-de-dados.md` v2.0 e cita RN-XXX onde
relevante — qualquer divergência entre este documento e o schema é bug
de documentação, não uma segunda fonte de verdade. Em caso de conflito:
schema (`arquitetura-banco-de-dados.md`) e regras (`regras-negocio.md`
§13) prevalecem sobre os diagramas aqui.
