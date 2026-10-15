# Easy Manager — Estrutura de Precificação (v2.4)

**Data:** Setembro/2026
**Tipo:** Modelo de negócio / precificação de planos
**Substitui:** v2.3 — três rodadas de revisão crítica desde então: (1)
framework de 6 perguntas, 7 lacunas fechadas (troca de ciclo
antecipado, retenção em cancelamento voluntário, conflito de limites
do trial, reembolso parcial de combo, contestação de bloqueio,
exclusão de Store, primeira cobrança); (2) máquina de estados da
assinatura formalizada + modelo de tokens fechado (peso entrada/saída,
arredondamento, histórico de preço da API, tokens como fonte de
verdade, preço fixo dos pacotes avulsos); (3) correção terminológica
"período mensal de tokens" ≠ "ciclo de cobrança" propagada por todo o
documento (§5.0, §5.2, §5.3), incluindo a descoberta de que
upgrade/downgrade só é possível no ciclo mensal (RN-SUB-004 já
bloqueava isso em ciclo antecipado). Detalhe completo em
`easy-manager-plano-execucao.md` (Etapas A, B e revisão conjunta).
**v2.1 anterior:** trial gratuito redesenhado: relatório parcial (não
mais completo), anti-abuso reforçado por score de risco combinando
dispositivo/e-mail/rede (mantendo a verificação por CNPJ, sem exigir
CPF do titular), cota de tokens do trial reduzida e isolada da cota
dos planos pagos, prompt de IA dedicado para o trial.

---

## 1. Visão Geral

A precificação é dividida por **módulo** (alinhado ao sistema de
Módulos/Entitlements já desenhado em `docs/software-engineering.md`
§4.6): `PRODUCTS` (gerenciamento de estoque) e `ADS_ANALYZER` (análise
de anúncios via IA). Cada módulo tem sua própria régua de planos por
número de Stores, e pode ser assinado separado ou em combo com
desconto.

A lógica de cada módulo é diferente:

- **PRODUCTS**: custo de operação é baixo e fixo (banco de dados,
  armazenamento de imagens), não escala com uso de IA. Preço compete
  por praticidade, mercado comoditizado.
- **ADS_ANALYZER**: custo de operação escala com tokens de IA
  consumidos por execução (chamada à API da Anthropic). Preço reflete
  exclusividade — é uma funcionalidade de nicho, sem concorrência
  direta relevante fazendo esse tipo de análise automatizada.

---

## 2. Módulo PRODUCTS (Gerenciamento de Estoque)

Custo de operação por seller é essencialmente fixo (infraestrutura),
não varia por uso do seller dentro do módulo. A régua de preço aqui é
por número de Stores.

| Plano | Stores incluídas | Preço mensal |
|---|---|---|
| Básico | 1 | R$ 15,00 |
| Intermediário | 2 | R$ 25,00 |
| Completo | 5 | R$ 45,00 |

O número de Stores de cada plano segue a mesma regra do §6 (Stores
ativas, não cadastro rígido) — ver detalhes lá.

---

## 3. Módulo ADS_ANALYZER (Análise de Anúncios via IA)

### 3.1 Custo real de operação (referência)

Baseado em `documento-engenharia-shopee-ads-analyzer.md` §6.3
(5.000–20.000 tokens de entrada, até 6.000 de saída por execução,
modelo `claude-sonnet-5`, preços aproximados de 3 dólares por milhão de
tokens de entrada e 15 dólares por milhão de saída, dólar a R$ 5,50):

| Porte da loja | Tokens/execução | Custo/execução | Custo mensal (30 execuções) |
|---|---|---|---|
| Pequena (~50 SKUs) | ~8.000 | US$ 0,060 (≈ R$ 0,33) | ≈ R$ 9,90 |
| Média (~100 SKUs) | ~16.500 | US$ 0,1035 (≈ R$ 0,57) | ≈ R$ 17,10 |
| Grande (~150 SKUs) | ~26.000 | US$ 0,150 (≈ R$ 0,83) | ≈ R$ 24,75 |

### 3.2 Modelo de cobrança: cota de tokens mensal, calculada dinamicamente por margem

Em vez de vender execuções diárias ilimitadas (risco de custo variável
não controlado), cada plano tem uma **cota de tokens mensal**, exibida
ao seller como uma barra de progresso de consumo (mesmo padrão de UX
do limite de uso do Claude Pro/API). Isso garante que o custo de API
por seller nunca ultrapasse um teto conhecido, eliminando o risco de
prejuízo por uso excessivo.

Isso soma-se à regra já existente de 1 execução manual por Store por
dia (`docs/software-engineering.md`, decisão v4.2) — a cota de tokens é
um teto adicional sobre esse limite, não o substitui.

**Decisão de modelo (evita reajuste manual constante):** o **preço
mensal em reais** e a **margem-alvo no pior caso** de cada plano são os
valores fixos e estáveis (o que o seller vê e paga). A **cota de
tokens** não é um número fixo gravado em pedra — é **calculada a
partir desses dois valores fixos e do custo atual por token** (preço
vigente da API da Anthropic + câmbio do dia). Assim, se a Anthropic
mudar o preço do modelo ou o câmbio variar, apenas a cota de tokens se
recalcula automaticamente — o preço cobrado do seller e a margem
mínima garantida nunca precisam ser reajustados manualmente neste
documento.

Fórmula:

```
custo_maximo_R$ = preco_mensal × (1 − margem_alvo)
cota_tokens_mes = custo_maximo_R$ / custo_por_token_atual
```

Onde `custo_por_token_atual` é derivado do preço vigente da API
(US$/milhão de tokens de entrada e saída) e do câmbio USD/BRL do
momento do cálculo — não um valor fixo neste documento.

**Peso entre tokens de entrada e saída (decidido):** como o preço de
saída da Anthropic é ~5x o de entrada (US$15/mi vs. US$3/mi), tratar
todo token como igual distorceria o custo real. `custo_por_token_atual`
é uma **média ponderada** entre os dois preços, usando uma proporção
de mix entrada:saída por execução:

- **Estimativa inicial** (sem dado real ainda): ~12.500 tokens de
  entrada / 6.000 de saída por execução (ponto médio da faixa de
  referência do documento de engenharia do Shopee Ads Analyzer, §6.3
  daquele documento — 5.000–20.000 de entrada, até 6.000 de saída),
  proporção aproximada de 2:1 entrada:saída.
- **Recalibração automática:** assim que houver **volume mínimo de
  execuções reais registradas** (referência: 100 execuções), o sistema
  passa a calcular essa proporção a partir da **média real** medida
  (entrada e saída realmente consumidas), substituindo a estimativa
  inicial — sem intervenção manual. A proporção real continua sendo
  recalculada periodicamente conforme mais dados se acumulam (mesmo
  job que já atualiza a cota, ver abaixo), garantindo que a estimativa
  nunca fique "grudada" numa referência antiga.

**Frequência de recálculo (decidido):** job automático **diário**
busca a cotação USD/BRL vigente e recalcula a cota de tokens de todos
os planos com esse câmbio atualizado — a variação cambial é o
componente que de fato muda dia a dia, e o custo de rodar essa busca
uma vez por dia é desprezível. Já o **preço da API da Anthropic fica
fixo** no valor vigente até ser **atualizado manualmente** no sistema
quando houver anúncio oficial de mudança de preço — historicamente não
é algo que varia com frequência nem sem aviso público, geralmente
ligado a lançamento de novo modelo, então não justifica automação
nem checagem em tempo real a cada chamada de API (latência
desnecessária sem ganho real para o seller).

**Histórico de alterações de preço (decidido):** cada atualização
manual do preço da API é registrada com **data da mudança, valor
anterior e valor novo** — não é só substituído. Serve pra auditoria
(entender por que a cota de um mês específico foi diferente) e pra
recalcular retroativamente se necessário. O cálculo do dia a dia
sempre usa o valor vigente; o histórico é só para consulta/auditoria,
nunca influencia o cálculo corrente.

**Arredondamento (decidido):** qualquer cálculo de tokens que resulte
em valor fracionário — a cota mensal (`cota_tokens_mes`), o saldo
restante após uma execução, o saldo transportado em upgrade/downgrade
— é sempre **arredondado para baixo** (piso/floor), nunca para o
inteiro mais próximo. Critério conservador: garante que o custo real
de operação nunca ultrapasse o teto de margem prometido em nenhum
plano, mesmo por uma fração de token.

**Fonte de verdade do controle de saldo (decidido):** o sistema
controla saldo e cota **em tokens**, não em R$ — é o que a Anthropic
efetivamente cobra e o que a cota mede. O **R$ equivalente** é
calculado e guardado apenas como um **snapshot histórico por
execução** (usando o câmbio e o preço vigente naquele momento), só
para relatórios e auditoria de custo real — nunca para decidir se uma
execução pode ou não rodar. Toda checagem de saldo/cota/bloqueio usa
exclusivamente o valor em tokens.

### 3.3 Planos

Preço e margem-alvo são os valores fixos e comerciais. A cota de
tokens abaixo é a **cota de referência**, calculada com os parâmetros
atuais (preço da API `claude-sonnet-5`: US$ 3/milhão tokens de entrada,
US$ 15/milhão de saída; câmbio de referência R$ 5,50 — ambos sujeitos a
mudar, recalculando a cota automaticamente conforme §3.2):

| Plano | Stores ativas | Preço mensal | Margem-alvo (pior caso) | Custo máx. de API (fixo) | Cota de tokens/mês (referência atual) | Execuções possíveis (~) |
|---|---|---|---|---|---|---|
| Básico | 1 | R$ 39,00 | 73% | R$ 10,35 | 300.000 | ~18 |
| Intermediário | 2 | R$ 69,00 | 65% | R$ 24,15 | 700.000 | ~42 |
| Completo | 5 | R$ 149,00 | 54% | R$ 69,00 | 2.000.000 | ~121 |

O `custo_maximo_R$` (coluna fixa) é o que realmente ancora a margem —
ele nunca muda, mesmo que o preço de API/câmbio mude. A cota de tokens
e o número de execuções possíveis são a variável que se ajusta para
manter esse teto de custo constante.

---

## 4. Combo (PRODUCTS + ADS_ANALYZER)

Assinatura dos dois módulos juntos, no mesmo ato (RN-COMBO-002).
Implementado como `subscriptions.combo_group_id` compartilhado entre
as duas assinaturas resultantes (`docs/arquitetura-banco-de-dados.md`
§3) — não existe mais um plano "Full" nem uma tabela `plan_modules`
separada; esse desenho foi substituído na sincronização com a
engenharia (Etapa C do plano de execução). Desconto de 15% sobre a
soma dos dois módulos avulsos, para incentivar assinatura combinada.

| Plano | PRODUCTS avulso | ADS_ANALYZER avulso | Soma avulsa | Combo (15% off) |
|---|---|---|---|---|
| Básico | R$ 15,00 | R$ 39,00 | R$ 54,00 | R$ 46,00 |
| Intermediário | R$ 25,00 | R$ 69,00 | R$ 94,00 | R$ 80,00 |
| Completo | R$ 45,00 | R$ 149,00 | R$ 194,00 | R$ 165,00 |

**Confirmado como decisão final.** 15% está dentro da faixa usual de
desconto de bundle em SaaS (10–20%) — suficiente para incentivar a
combinação sem corroer a margem do módulo mais caro de operar. Mesmo
no pior caso (plano Completo, onde a margem do ADS_ANALYZER já é a
mais apertada dos três — 54%), aplicando o desconto de 15%
proporcionalmente sobre a fatia do Ads dentro do combo, a margem cai
para ~45,5% (receita de R$ 126,65 contra custo máximo de R$ 69,00) —
ainda uma folga saudável, não uma operação no zero a zero.

**Reativação após cancelamento/inadimplência do combo:** o seller não
é obrigado a reativar o combo inteiro para voltar a usar a
plataforma — ele escolhe livremente reativar só PRODUCTS, só
ADS_ANALYZER, ou os dois separadamente (pagando o preço avulso de cada
módulo, sem o desconto de combo automático). O desconto de combo só
volta a valer se o seller assinar explicitamente os dois módulos
juntos de novo.

**Assinatura em momentos diferentes (não simultânea):** o desconto de
combo só se aplica quando os dois módulos são assinados juntos, no
mesmo ato. Se o seller já assina PRODUCTS e, depois, decide assinar
ADS_ANALYZER separadamente (ou vice-versa), paga o preço avulso cheio
do módulo novo, sem desconto — mesma regra vale ao contrário. O
desconto de combo não é aplicado retroativamente nem combinando
assinaturas feitas em momentos distintos; exige a decisão conjunta no
momento da compra.

**Reembolso parcial do combo dentro dos 7 dias (decidido):** o
direito de arrependimento (§7.5) não exige tudo ou nada quando a
compra é um combo — a legislação (art. 49 do CDC) garante o reembolso
integral do contrato em 7 dias, mas não obriga a tratar os dois
módulos como uma unidade indivisível. Por isso, dentro do prazo de 7
dias, o seller pode cancelar **apenas um** dos dois módulos do combo,
mantendo o outro ativo:

- O módulo cancelado é reembolsado integralmente, proporcional à
  fatia dele dentro do preço do combo.
- O módulo mantido **deixa de ter o desconto de combo** a partir da
  **próxima renovação**, passando a cobrar o preço avulso cheio
  daquele módulo (mesma regra geral de combo do §4 — desconto só vale
  quando os dois módulos estão assinados juntos).
- Fora dos 7 dias, essa flexibilidade não existe mais: cancelar um dos
  dois módulos do combo depois do prazo de arrependimento simplesmente
  faz o módulo mantido perder o desconto de combo na renovação
  seguinte, sem reembolso do período já pago (regra padrão do §7.5).

---

## 4.5 Ciclos de pagamento antecipado (trimestral, semestral, anual)

Além do ciclo mensal padrão (§5.0), o seller pode optar por pagar
antecipadamente por um período mais longo, com desconto — reduz
churn e melhora fluxo de caixa. O desconto é aplicado sobre a soma dos
meses do ciclo escolhido, e vale para qualquer módulo, plano ou
combo:

| Ciclo | Meses cobrados de uma vez | Desconto |
|---|---|---|
| Mensal (padrão) | 1 | — |
| Trimestral | 3 | 5% |
| Semestral | 6 | 8% |
| Anual | 12 | 12% |

- Desconto conservador, escalonado por prazo, calibrado para não
  comprometer a margem mais apertada do catálogo (ADS_ANALYZER
  Completo, 54% — ver §4).
- A data de aniversário (§5.0) passa a ser a data de renovação do
  ciclo escolhido (ex: trimestral renova a cada 3 meses, na mesma data
  do mês), não muda para mensal no meio do caminho.
- Cancelamento dentro de um ciclo pago antecipadamente segue a mesma
  regra do §7.5 (reembolso integral só dentro dos 7 dias legais de
  arrependimento); fora desse prazo, não há reembolso proporcional
  pelos meses restantes do ciclo já pago.
- A cota de tokens do ADS_ANALYZER continua resetando **mensalmente**
  dentro do ciclo pago antecipadamente (ex: um seller anual ainda tem
  a cota de tokens resetada 12 vezes ao longo do ano, uma por mês) —
  o pagamento antecipado não muda a cadência de consumo de tokens,
  só a cadência de cobrança.
- **Troca de ciclo ou de plano fica bloqueada durante o ciclo vigente:**
  uma vez escolhido um ciclo mais longo (trimestral, semestral ou
  anual), o seller não pode trocar de ciclo nem de plano no meio do
  caminho — essa opção só fica disponível **perto do vencimento** (ex:
  últimos dias do ciclo) ou **após o vencimento**, no momento da
  renovação. Não há troca antecipada com cálculo de crédito ou
  reembolso proporcional pelo tempo não usado do ciclo atual; o seller
  simplesmente aguarda o fim do período já pago para migrar de plano
  ou de ciclo.

---

## 5. Regras de consumo de cota (ADS_ANALYZER)

### 5.0 Ciclo de cobrança e reset da cota

O ciclo é mensal, na **data de aniversário da assinatura** de cada
seller (não um dia fixo do mês igual para todos) — ex: quem assina no
dia 9, é cobrado e tem a cota de tokens resetada todo dia 9 do mês
seguinte. Simplifica a implementação (sem rateio proporcional no
primeiro mês parcial) e é o padrão mais comum em SaaS.

Essa data de aniversário é a referência para todas as regras deste
documento que dependem de "início/fim do ciclo": o aviso de 80% de
consumo (§5.1), a expiração dos pacotes avulsos (§5.2) e o cálculo de
saldo em trocas de plano no meio do ciclo (§5.3).

**Esclarecimento (adicionado após a introdução dos ciclos antecipados,
§4.5):** existem dois "ciclos" diferentes, e este documento às vezes
usa a palavra pros dois. O **ciclo de cobrança** é o que o seller
escolhe (mensal, trimestral, semestral ou anual) — é quando ele é
cobrado de novo. O **período de tokens** é sempre mensal, sem exceção,
mesmo dentro de um ciclo de cobrança mais longo (RN-TOKEN-005) — um
seller anual é cobrado 1x, mas tem a cota resetada 12 vezes ao longo
do ano. Onde este documento fala em "reset da cota a cada ciclo/
renovação" (aqui e em §5.2/§5.3), leia-se **período mensal de
tokens**, não ciclo de cobrança — os dois só coincidem no plano
mensal.

### 5.1 Esgotamento da cota mensal

Ao esgotar a cota, o esgotamento é tratado como gatilho de upsell, não
bloqueio seco:

- Novas execuções ficam bloqueadas para o ciclo vigente, com tela
  mostrando claramente "sua cota mensal acabou" e chamada direta para
  upgrade ou compra de pacote avulso.
- **Aviso preventivo em ~80% de consumo da cota**, antes do
  esgotamento total, para o seller decidir com antecedência (upgrade,
  pacote avulso, ou reduzir o ritmo), em vez de ser surpreendido no
  meio de uma análise que precisava rodar.
- Consistente com a barra de progresso de consumo já exibida durante
  o mês inteiro (§3.2).

### 5.2 Pacote avulso de tokens extras

Preço por token = **3x** o preço por token do plano Básico — múltiplo
alto o bastante para tornar o upgrade claramente mais vantajoso em uso
recorrente, mas não punitivo para quem só precisa de um resgate
pontual (ex: pico sazonal de fim de ano).

- Preço por token do plano Básico (referência): R$ 39,00 / 300.000 =
  R$ 0,00013/token.
- Preço por token do pacote avulso: 3x = R$ 0,00039/token.

| Pacote avulso | Tokens | Execuções (~) | Preço |
|---|---|---|---|
| Extra P | 50.000 | ~3 | R$ 19,50 |
| Extra M | 100.000 | ~6 | R$ 39,00 |
| Extra G | 150.000 | ~9 | R$ 58,50 |

Comparação de referência: comprar 2x o pacote Extra P (100.000 tokens,
R$ 39,00 no total) custa mais, só naquele mês, do que a diferença de
R$ 30,00/mês entre o plano Básico e o Intermediário — e o upgrade dá
400.000 tokens extras *todo mês*, não só uma vez.

**Expiração do pacote avulso:** os tokens do pacote avulso expiram ao
final do **período mensal de tokens** vigente, junto com a cota normal
do plano — não acumulam para o mês seguinte, mesmo que o seller esteja
num ciclo de cobrança mais longo (trimestral/semestral/anual, §4.5).
Mesma lógica de bônus de plano de celular: é um reforço pontual para
aquele mês, não um crédito permanente. Isso também simplifica o
cálculo de saldo em trocas de plano no meio do período de tokens
(§5.3, só ciclo mensal), que já reseta a cota todo mês.

**Reajuste dos preços dos pacotes avulsos (decidido):** apesar do
preço por token do pacote avulso ser calculado como "3x o preço por
token do plano Básico", os valores em R$ da tabela acima **ficam
fixos** — não recalculam automaticamente junto com a cota mensal
(que é recalculada todo dia pelo job de câmbio, §3.2). Revisão desses
preços é **manual**, feita junto com a atualização manual do preço da
API da Anthropic — mesma cadência, mesmo evento. Evita o efeito
estranho de um preço avulso mudando de um dia pro outro sem aviso.

### 5.3 Troca de plano no meio do período de tokens (só ciclo mensal)

**Escopo (esclarecido nesta revisão):** o mecanismo abaixo só se aplica
a sellers no **ciclo de cobrança mensal**. Quem está num ciclo
antecipado (trimestral/semestral/anual) não pode trocar de plano no
meio do caminho — essa troca fica bloqueada até perto do vencimento
ou vencido (§4.5, "troca de ciclo ou de plano fica bloqueada durante o
ciclo vigente"). Só faz sentido falar em "meio do ciclo" pra quem tem
um ciclo curto o bastante (1 mês) pra essa flexibilidade valer a pena
oferecer; oferecer o mesmo meio-termo num ciclo anual desfaria a
própria trava que o §4.5 decidiu existir.

Ao trocar de plano (upgrade ou downgrade), o consumo de tokens já
realizado no plano anterior **é transportado/subtraído da cota do
plano novo** — sem bônus na troca, sem perda do consumo já feito.

Fórmula: `saldo_disponível_após_troca = cota_do_plano_novo − tokens_já_consumidos_no_período_atual`

**Exemplo:** seller no Básico (300.000 tokens), consumiu 50% (150.000
tokens). Faz upgrade para o Intermediário (700.000 tokens) no meio do
período → saldo disponível: 700.000 − 150.000 = 550.000 tokens.

**Downgrade com saldo negativo:** se o consumo já realizado for maior
que a cota do plano novo, o saldo fica negativo e o acesso ao módulo
**é bloqueado pelo restante do período de tokens atual** — que é
sempre **mensal**, e nesse caso coincide com o próprio ciclo de
cobrança (já que este mecanismo só vale pra quem está no mensal).
Volta normalmente no próximo reset, já com a cota cheia do plano
menor. O downgrade tem efeito imediato (não é postergado), mas o
custo dessa decisão é o próprio seller ficar sem acesso até o próximo
mês — sem reembolso ou cota extra automática.

---

## 6. Stores ativas e cadastradas por plano (regra unificada — PRODUCTS e ADS_ANALYZER)

O número de Stores de cada plano (§2 e §3.3) é o número de Stores que
podem estar **ativas** para aquele módulo. Como o cadastro de Stores é
único por seller (não duplicado por módulo — ver bullet "Conta
compartilhada entre módulos" abaixo), o **teto de cadastro é o maior
entre os limites de Stores ativas dos módulos que o seller tiver
assinado** — se o seller assina só um módulo, o teto de cadastro é
simplesmente o limite daquele plano; se assina os dois, o teto passa a
ser o maior dos dois limites, **por definição, não como excedente
indevido**. Dentro desse total cadastrado, cada módulo continua
aplicando seu próprio limite de Stores *ativas*, que pode ser menor
que o total cadastrado.

- **Agnóstico de marketplace:** as Stores contam pelo total, qualquer
  combinação entre Shopee, TikTok Shop, Shein etc. — não há trava por
  marketplace individual. Consistente com o modelo de dados (Seller →
  Store N, multi-marketplace por natureza).
- **Conta compartilhada entre módulos:** como o cadastro de Stores é
  único por seller (não duplicado por módulo), quando PRODUCTS e
  ADS_ANALYZER estão em planos com números diferentes de Stores (ex:
  PRODUCTS Completo = 5, ADS_ANALYZER Básico = 1), o teto de cadastro
  do seller é o **maior** dos dois — no exemplo, 5. Dentro desse total
  cadastrado, cada módulo aplica seu próprio limite de Stores
  *ativas*.
- **Downgrade:** se o seller tinha 5 Stores cadastradas (e ativas) em
  um plano Completo e faz downgrade para um plano de 2, o novo teto de
  cadastro passa a ser 2. O seller pode escolher manualmente quais 2
  permanecem ativas a qualquer momento; se ele não escolher, a regra
  padrão é por **ordem de cadastro**, mantendo ativas as Stores mais
  antigas e desvinculando as mais recentes primeiro. Essa ordem não é
  fixa: se o seller desvincular manualmente uma Store que não é a mais
  recente (ex: uma do meio da lista), as Stores mais novas que ainda
  cabem no novo limite sobem de posição e passam a ficar ativas no
  lugar dela. Dados históricos das Stores desvinculadas seguem a regra
  de retenção normal (§8.2), caso o seller volte a fazer upgrade
  depois.
- **Regra idêntica nos dois módulos:** PRODUCTS segue exatamente a
  mesma lógica do ADS_ANALYZER.
- **Escolha da Store ativa (decisão já tomada, agora registrada):**
  quando o número de Stores cadastradas excede o limite de Stores
  ativas de um módulo (ex: 5 cadastradas, plano do ADS_ANALYZER cobre
  1 ativa), o seller escolhe quais Stores ficam ativas naquele módulo
  diretamente na aba do módulo (ex: um dropdown na aba ADS_ANALYZER
  listando as Stores cadastradas, marcando quais estão ativas dentro
  do limite do plano). A quantidade de Stores que podem ficar ativas
  ao mesmo tempo varia conforme o plano contratado daquele módulo — a
  escolha pode ser trocada pelo seller a qualquer momento, respeitando
  sempre o limite vigente.

**Implicação técnica:** necessário um campo por Store indicando se
está "ativa" para cada módulo, com validação de que tanto o total de
Stores cadastradas quanto o total de Stores ativas por módulo nunca
excedam o limite do plano vigente do seller. Não confundir com
`stock_sync_status` (webhooks de estoque do PRODUCTS) — é um controle
de ativação separado, por módulo.

**Exclusão de Store pelo próprio seller (fora do fluxo de downgrade):**
diferente da desvinculação automática por downgrade (onde o histórico
fica retido, §11), aqui é uma ação deliberada do seller (ex: a loja
fechou) — o tratamento é diferente:

- Antes de confirmar, o sistema mostra um **aviso explícito** de que
  os dados dessa Store (histórico de produtos e/ou de análises de
  anúncios, conforme os módulos assinados) serão **excluídos**, não
  apenas desvinculados/retidos.
- Confirmada a exclusão, ela é **imediata e definitiva** — sem
  período de carência ou retenção posterior (diferente da retenção de
  7/30 dias do §8.2, que é para cancelamento/inadimplência da
  assinatura como um todo, não para exclusão pontual de uma Store).
- A vaga no teto de cadastro (§6) é **liberada na hora**, permitindo
  cadastrar uma nova Store imediatamente no lugar da excluída.
- O aviso prévio antes da confirmação é o que cobre o risco de erro —
  não há segunda chance depois de confirmado.

---

## 7. Trial gratuito sem cartão (PRODUCTS e ADS_ANALYZER)

### 7.0 Trial do módulo PRODUCTS: limite por volume, sem prazo

A lógica de risco do PRODUCTS é diferente da do ADS_ANALYZER: não há
custo de API/tokens envolvido, o custo real é armazenamento (banco de
dados e imagens) por tempo indefinido caso o trial não tenha limite
algum. Por isso o trial do PRODUCTS não usa prazo de dias — usa um
**limite de volume**, sem data de expiração:

- Sem cartão de crédito, sem necessidade de assinar nada para começar.
- Limite de **15 produtos cadastrados**, em **1 Store** apenas.
- Sem limite de tempo: o seller pode ficar nesse trial indefinidamente
  enquanto não precisar de mais produtos ou mais Stores — não há
  pressão de prazo, a pressão é de volume.
- Ao tentar cadastrar o 16º produto (ou uma 2ª Store), o sistema
  bloqueia a ação e direciona para upgrade a um plano pago.
- Mesma verificação de CNPJ do trial do ADS_ANALYZER (§7.3) se aplica
  aqui, para evitar múltiplas contas trial pelo mesmo seller.

### 7.1 Modelo do trial do ADS_ANALYZER: relatório parcial, não completo

Em vez de (ou além de) liberar apenas exportação de dados brutos para
IA externa — que esconde o principal valor percebido do produto (o
relatório pronto) —, o trial concede ao seller uma **cota gratuita
fixa de 3 execuções reais** do pipeline, gerando o relatório pela
própria plataforma, sem exigir cartão de crédito.

**Decisão revisada:** as 3 execuções do trial geram um **relatório
parcial**, não o relatório completo de 8 seções (RF-08). Isso prova
que a IA analisou os dados reais daquela loja (não é demonstração
genérica), mas retém exatamente a parte de maior valor prático,
reforçando a conversão para plano pago:

| Seção do relatório | Trial (gratuito) | Plano pago |
|---|---|---|
| Resumo executivo | Liberado | Liberado |
| Tabela comparativa (hoje vs. ontem) | Liberado | Liberado |
| Top performers | Liberado | Liberado |
| SKUs de atenção | Bloqueado — CTA de assinatura | Liberado |
| Oportunidades | Bloqueado — CTA de assinatura | Liberado |
| Riscos | Bloqueado — CTA de assinatura | Liberado |
| Plano de ação priorizado | Bloqueado — CTA de assinatura | Liberado |

Racional: o trial mostra o diagnóstico (o motor de IA funciona, leu os
números reais da loja), mas esconde a prescrição (o que fazer com
isso) — que é o motivo concreto de pagar.

**Por que 3 execuções, e não 1:** a análise de anúncios é
fundamentalmente comparativa (hoje vs. ontem, conforme o prompt em
`documento-engenharia-shopee-ads-analyzer.md` §6.2). Com apenas 1
execução, não existe "ontem" ainda, e o sistema cai no modo de
degradação graciosa (RNF-07) — o modo mais pobre do produto, sem
nenhuma comparação. Com 3 execuções (mesmo em dias não consecutivos),
o trial garante pelo menos uma comparação real entre dois dias
diferentes, mostrando a funcionalidade central de fato funcionando.

### 7.2 Dois prompts de IA distintos (trial vs. pagante)

O relatório parcial do trial é gerado por um **prompt dedicado,
separado do prompt completo** usado pelos planos pagos — não é o mesmo
prompt com o texto cortado depois de gerado. Isso importa por três
motivos:

- **Economia também na entrada, não só na saída:** o prompt do trial
  não envia ao modelo as heurísticas de diagnóstico das seções
  bloqueadas (as 10 regras de interpretação voltadas a
  oportunidades/riscos/plano de ação) — reduz o tamanho do prompt de
  entrada, além da saída.
- **Sem risco de vazamento:** o modelo nunca recebe instrução sobre as
  seções bloqueadas, então não há chance de "escapar" uma dica de
  risco ou oportunidade dentro do texto liberado.
- **Custo de saída real, não só visual:** o `max_tokens` da chamada ao
  prompt do trial é configurado mais baixo, compatível com a cota
  reduzida (§7.3) — o modelo é instruído a nunca gerar as seções
  bloqueadas, então a economia é na própria chamada de API, não apenas
  no que é exibido na tela.
- O prompt trial fecha com uma chamada à ação clara: "Assine para ver
  oportunidades, riscos e plano de ação priorizado desta loja."
- O prompt pago é o já existente (`documento-engenharia-shopee-ads-analyzer.md`
  §6.2), sem alterações.

### 7.3 Cota de tokens do trial (isolada da cota dos planos pagos)

**Número de referência: 60.000 tokens no total** para as 3 execuções
do trial (cerca de 20.000 tokens por execução) — teto próprio, menor
que a cota do plano Básico pago (300.000 tokens/mês), e isolado dela.

- Como o relatório do trial é parcial (3 das 7 seções de conteúdo,
  sem as heurísticas de diagnóstico mais pesadas), o custo por
  execução já é naturalmente menor que uma execução completa — a cota
  de 20.000 tokens/execução dá folga confortável mesmo para uma loja
  grande (~150 SKUs, ~26.000 tokens numa execução *completa* de
  referência, §3.1).
- Custo máximo real de API por conta trial, no pior caso: cerca de 2
  reais (proporcional ao custo máximo do plano Básico, §3.3). Mesmo
  que um volume grande de contas seja criado só para abusar, sem
  nunca converter, o prejuízo por conta é pequeno e conhecido de
  antemão.
- **Qual limite prevalece quando conflitam (decidido):** o trial tem
  dois limites simultâneos — 3 execuções e 60.000 tokens no total — e
  o **teto de tokens sempre prevalece** sobre o número de execuções.
  Numa loja grande, se a 2ª ou 3ª execução ultrapassaria os 60.000
  tokens do total, essa execução é **cortada/bloqueada antes de
  rodar**, mesmo que o seller ainda não tenha usado as 3 execuções
  contratadas — o teto de custo de API nunca é furado. Nesse caso, o
  seller vê uma barra de progresso de consumo do trial (mesmo padrão
  visual da cota dos planos pagos, §3.2), mostrando a porcentagem já
  usada dos 60.000 tokens, para deixar claro por que uma execução foi
  bloqueada antes de completar as 3.

### 7.4 Verificação anti-abuso: CNPJ + score de risco combinado

A verificação anti-abuso original (bloqueio só por CNPJ já usado)
tinha uma lacuna: não impedia a mesma pessoa de cadastrar múltiplos
CNPJs distintos (cada um legitimamente válido e ativo) para repetir o
trial indefinidamente — ex: seller com várias lojas, ou que abre um
novo MEI. A verificação na Receita Federal confirma autenticidade do
CNPJ, mas não pega esse padrão de abuso.

**Decisão:** em vez de vincular o CPF do titular ao CNPJ (descartado —
gera fricção e não foi a direção escolhida), a lacuna é coberta
reforçando o **score de risco combinando sinais**, sem depender de
nenhum dado do titular da empresa.

**Camada 1 — score de risco combinando sinais (não uma trava única):**

Nenhum sinal isolado bloqueia por si só — a coincidência de 2 ou mais
sinais é que dispara restrição automática da nova conta, evitando
bloquear casos legítimos como cônjuge ou familiar com CNPJ separado
(ex: uma segunda loja no nome da esposa) usando aparelho e e-mail
próprios.

**Decidido (mecanismo simplificado, sem fila de revisão manual
automática, mas com contestação disponível ao seller):** ao bater 2
ou mais sinais coincidentes, a conta é **restrita automaticamente**
(trial bloqueado, sem execução liberada) — sem depender de alguém
revisar antes de agir. O sistema apenas envia um **e-mail de
notificação** para acompanhamento, que pode ser lido quando der.
Simplicidade proposital para o estágio atual do negócio (operação
solo): o prejuízo máximo por conta trial abusada já é pequeno e
conhecido (§7.3), então não se justifica um fluxo de revisão manual
rodando por padrão para toda restrição.

**Canal de contestação (cobre o falso positivo):** a conta restrita
vê uma tela explicando o bloqueio, com um botão/formulário de
**"contestar bloqueio"** — o seller preenche uma justificativa
rápida (ex: "sou cônjuge do titular de outra conta, tenho CNPJ
próprio"), o que abre um chamado para revisão manual. Só nesse
momento — quando o próprio seller se manifesta — existe revisão
humana; não há checagem manual de toda restrição automática, apenas
das que forem contestadas. Cobre o caso legítimo (ex: cônjuge ou
familiar com CNPJ separado, usando aparelho e e-mail próprios) sem
exigir infraestrutura de revisão constante para um risco que já é
baixo por natureza.

- Impressão digital do dispositivo/navegador (device fingerprint) que
  criou a conta — sinal mais preciso que IP: identifica o aparelho
  físico específico usado no cadastro, sem bloquear duas pessoas
  diferentes (ex: cônjuges) usando aparelhos diferentes na mesma rede.
- Mesmo e-mail ou celular já usado em cadastro anterior (mesmo com
  CNPJ diferente).
- Endereço de IP/rede — **sinal de apoio, não decisivo isoladamente**:
  IP de operadora móvel é compartilhado por milhares de clientes reais
  (NAT), e IP residencial pode ser legitimamente compartilhado por
  família ou equipe. Só pesa quando coincide com outro sinal.
- Limite de contas trial por dispositivo dentro de uma janela de
  tempo (ex: no máximo 1 conta trial por dispositivo a cada 90 dias)
  — mais leniente que um bloqueio permanente, cobre o caso de troca de
  dono do aparelho.

**Camada 2 — filtros baratos contra abuso automatizado/em massa:**

- Cloudflare na borda (plano gratuito) cobrindo rate limiting por IP e
  bloqueio de bots simples antes de a requisição chegar na
  aplicação — resolve os dois itens abaixo sem precisar codar isso na
  mão, mas não substitui o score de risco da Camada 1 (não identifica
  dispositivo/e-mail repetido entre contas diferentes).
- Rate limit de criação de conta por IP/rede num período curto (ex:
  máximo 3 contas novas por IP por dia) — não bloqueia, mas desacelera
  a criação de contas em lote.
- Confirmação de e-mail obrigatória (clique em link) antes de liberar
  a primeira execução — elimina bots simples com e-mail descartável
  que nunca abrem a caixa de entrada.
- Bloqueio de domínios de e-mail descartável conhecidos (listas
  públicas, ex: serviços de "e-mail temporário") — filtro gratuito.
- Atraso proposital na liberação da 1ª execução real (minutos a poucas
  horas, não instantâneo) — sem efeito perceptível para o seller
  legítimo, mas quebra o modelo de quem tenta gerar execuções em lote
  rapidamente.

**Camada 3 — monitoramento (sem sistema anti-fraude sofisticado):**

- Acompanhar a taxa de conversão trial → pago desde o primeiro mês. Se
  essa taxa cair muito abaixo do esperado (referência: abaixo de 5%)
  enquanto o número de contas trial crescer de forma desproporcional,
  é sinal de abuso em massa — permite reagir (apertar a verificação)
  antes do custo escalar, mesmo sem saber os detalhes do padrão.

**Retenção da conta gratuita (mantido de v2.1):** contas gratuitas não
são excluídas do banco de dados mesmo após esgotarem a cota —
permanecem persistidas por um período longo, para que o CNPJ e os
sinais de risco (§7.4) fiquem registrados e bloqueiem uma nova
tentativa de conta gratuita associada a eles.

**Decidido:** a cota gratuita de 3 execuções (§7.1) substitui
totalmente a opção de exportação de dados brutos como forma de trial.
Ter os dois caminhos ao mesmo tempo era redundante e confundia a
decisão do seller sem ganho real — a cota gratuita já entrega o valor
mais forte (relatório pronto, ainda que parcial), tornando a
exportação de dados brutos um caminho estritamente mais pobre e
desnecessário.

---

## 7.5 Direito de arrependimento (reembolso em 7 dias)

Por força do artigo 49 do Código de Defesa do Consumidor, toda compra
feita fora do estabelecimento físico (o caso de uma assinatura SaaS
online) dá ao consumidor o direito de desistir em até **7 dias
corridos** a partir da contratação, com **reembolso integral**, sem
necessidade de justificativa.

- Vale para qualquer módulo e qualquer plano, incluindo o combo.
- **Reembolso é sempre integral dentro do prazo, mesmo que o seller já
  tenha consumido tokens da cota do ADS_ANALYZER nesse período** — não
  há alternativa legal para descontar esse custo do reembolso; é
  tratado como uma perda absorvida pela operação, natural do modelo,
  não recorrente por seller (o direito só existe uma vez por
  contratação).
- Fora dos 7 dias, não há reembolso proporcional por cancelamento no
  meio do ciclo — o acesso permanece até o fim do ciclo já pago (regra
  padrão de SaaS mensal), só não há mais nova cobrança no ciclo
  seguinte.

---

## 8. Retenção de dados

### 8.1 Plano gratuito/trial (Ads Analyzer)

O snapshot diário é salvo normalmente no banco durante o trial (via
execuções reais da cota gratuita — §7), para que, caso o seller
assine, o histórico já esteja disponível desde o primeiro dia pago.

- Snapshots de conta gratuita expiram e são excluídos automaticamente
  **30 dias** após a criação, via job de limpeza, se o seller não
  assinar um plano pago do ADS_ANALYZER até então.
- Se assinar dentro dos 30 dias, os snapshots acumulados passam a
  contar como histórico normal (não expiram mais).
- Racional: custo de manter esses dados é baixo (armazenamento, sem
  chamada de IA envolvida no caso da exportação), então o risco
  financeiro de reter 30 dias é pequeno frente ao ganho de conversão.

### 8.15 Régua de inadimplência (antes do bloqueio)

Não há lei brasileira fixando prazo exato ou número de tentativas de
cobrança para serviços continuados (o CDC exige aviso prévio antes de
suspender o serviço, mas não define prazo) — a régua abaixo é definida
pela própria operação, dentro do razoável praticado no mercado:

- A partir do **vencimento** não pago, o sistema tenta a cobrança
  automaticamente **uma vez por dia, por 5 dias corridos**.
- Durante esses 5 dias, o acesso ao módulo **permanece liberado
  normalmente** — é uma tolerância para falhas pontuais de cartão
  (saldo, cartão vencido, etc.), não uma punição imediata.
- Se nenhuma das 5 tentativas for bem-sucedida, no 6º dia o acesso ao
  módulo é **bloqueado** (mesmo comportamento de cancelamento, sem
  excluir dados).
- O prazo de retenção de dados (tabela abaixo, §8.2) começa a contar
  **a partir do bloqueio** (dia 6), não a partir do vencimento
  original.

**Falha na primeira cobrança (não recorrência) — regra diferente:** a
régua acima (5 tentativas com acesso liberado) vale só para
assinatura **já ativa**, com pelo menos um ciclo pago com sucesso
antes. Se o cartão for recusado já na **cobrança inicial**, no ato da
assinatura, não há tolerância de acesso: a conta **não ativa
nenhum acesso pago** (nem ao módulo, nem à cota) até o pagamento ser
aprovado. Não se aplica aqui a lógica de "tolerância a falha pontual
de quem já é cliente" — sem primeiro pagamento aprovado, não existe
assinatura ativa para tolerar.

### 8.2 Cancelamento ou inadimplência de plano pago

Diferente do trial (onde reter dados por mais tempo busca converter um
desconhecido), aqui o objetivo é deixar claro e visível o tamanho da
perda, para reforçar a urgência de regularizar — sem prolongar demais
o custo de armazenamento de uma conta que já demonstrou não estar
pagando. O prazo é **diferenciado por módulo**:

**Início da contagem no cancelamento voluntário (sem inadimplência):**
quando o próprio seller cancela (em vez de ficar inadimplente), o
acesso continua normalmente até o fim do ciclo já pago (§7.5) — seja
ele mensal, trimestral, semestral ou anual. O prazo de retenção abaixo
só começa a contar **a partir do fim desse ciclo pago**, nunca no
momento em que o cancelamento é solicitado. Ex: um seller no plano
anual que cancela no 1º mês só perde o acesso ao fim dos 12 meses já
pagos, e só a partir daí começam a contar os 7 ou 30 dias de retenção
— mesmo princípio já usado para inadimplência (§8.15), onde a
contagem começa no bloqueio, não no vencimento original.

| Módulo | Prazo de retenção após cancelamento/inadimplência |
|---|---|
| PRODUCTS (estoque/produtos) | 30 dias |
| ADS_ANALYZER (histórico de análises) | 7 dias |

- PRODUCTS usa o prazo mais longo porque os dados de estoque são o
  próprio negócio operacional do seller (perda imediata seria grave),
  e o custo de armazenamento é baixo.
- ADS_ANALYZER usa 7 dias porque é um histórico de análise mais
  "descartável" no dia a dia do negócio do seller.
- No plano combo ("Full"), a régua vale **por módulo**, não pelo plano
  como um todo: ao ficar inadimplente, o seller vê dois avisos
  distintos ("em 7 dias seu histórico de anúncios será perdido" e "em
  30 dias seus produtos e estoque serão apagados"), cada um com seu
  próprio relógio.
- Se o pagamento for regularizado dentro do prazo, os dados continuam
  intactos, sem qualquer perda. Esgotado o prazo, a exclusão é
  permanente, sem segunda tolerância.

---

## 9. Retenção pelo valor acumulado (princípio)

A fidelização deve vir do valor real que a plataforma acumula para o
seller ao longo do tempo, nunca de dificultar a saída dele ou reter os
dados artificialmente (ex: dificultar exportação, apagar histórico
para punir cancelamento). É mais sustentável a longo prazo e evita
ressentimento/cancelamento em massa quando o cliente encontra
alternativa.

A âncora natural do produto é o **histórico acumulado do
ADS_ANALYZER**: quanto mais tempo o seller está na plataforma, mais
snapshots diários existem, e mais rico é o contexto histórico enviado
à IA em cada análise (comparações de 30, 60, 90+ dias em vez de só
"hoje vs. ontem"). A qualidade da análise cresce organicamente com o
tempo de permanência — benefício real, não apenas percebido.

**Roadmap técnico futuro (fora do MVP atual):** o prompt de análise
(`docs/documento-engenharia-shopee-ads-analyzer.md` §6.2) hoje compara
só hoje vs. ontem. Para essa proposta de valor se concretizar, precisa
evoluir para incorporar janelas históricas maiores (médias móveis de
30 dias, tendência de ACOS ao longo do tempo) conforme o histórico por
Store acumular dados suficientes.

---

## 10. Variáveis consideradas neste modelo

- Número de Stores ativas por plano (métrica de cobrança principal em
  ambos os módulos, agnóstica de marketplace).
- Cota de tokens de IA mensal (métrica de cobrança adicional, exclusiva
  do ADS_ANALYZER) — **não é mais um valor fixo**: é calculada
  dinamicamente a partir do preço comercial fixo e da margem-alvo
  fixa de cada plano, dividida pelo custo atual por token (§3.2). Isso
  isola o preço e a margem cobrados do seller de qualquer variação de
  custo de API ou câmbio.
- Custo real de API por execução, variável por porte da loja (SKUs
  ativos), tratado em três cenários de referência (pequena/média/
  grande) para dimensionar as cotas.
- Frequência de execução: 1x/dia por Store, já limitada em nível de
  arquitetura, independente da cota de tokens.
- Câmbio dólar/real (usado R$ 5,50 como referência nesta versão) e
  preço vigente da API da Anthropic — ambos são **entradas do cálculo
  dinâmico da cota** (§3.2), não valores fixos no documento.
- Desconto de combo por assinatura combinada dos dois módulos.
- Posicionamento competitivo por módulo: PRODUCTS como mercado
  comoditizado (preço mais baixo, compete por praticidade),
  ADS_ANALYZER como funcionalidade de nicho (preço reflete
  exclusividade, não só custo).
- Verificação de CNPJ somada a um score de risco por
  dispositivo/e-mail/rede como controle anti-abuso de contas
  gratuitas/trial (§7.4) — reforça a trava original sem exigir CPF do
  titular.
- Relatório parcial (3 de 7 seções) e prompt de IA dedicado para o
  trial, com cota de tokens própria, menor e isolada da cota dos
  planos pagos (§7.1-§7.3).

---

## 11. Pontos ainda em aberto

Nesta revisão foram resolvidos: tamanho da cota gratuita do trial (3
execuções — §7.1), confirmação do desconto de combo de 15% (§4), teto
de Stores cadastradas (igual ao teto do plano vigente — §6), o
problema de reajuste manual por câmbio/preço de API (resolvido pelo
modelo dinâmico de cota por margem, com job diário de câmbio e
atualização manual do preço da API — §3.2), a lacuna do trial permitir
múltiplas contas via múltiplos CNPJs legítimos (mitigada pelo score de
risco combinado — §7.4, sem exigir CPF do titular), o mecanismo de
resposta ao score de risco (restrição automática + e-mail de
notificação, sem fila de revisão manual — §7.4), a remoção da
exportação de dados brutos como alternativa ao trial (§7.1/§8.1,
mantida só a cota gratuita de execuções reais), a reativação de
módulos após cancelamento do combo (seller escolhe livremente, sem
obrigação de reativar o combo inteiro — §4), e a expiração mensal dos
pacotes avulsos de tokens (§5.2, sem acúmulo entre ciclos).

**Decidido:** quando Stores são desvinculadas de um módulo por
downgrade (§6), o histórico dessas Stores **fica retido**, seguindo a
mesma lógica de retenção já usada em outras partes do documento (§8) —
não é excluído nem some da visualização de forma definitiva. Racional:
o custo de manter esses dados é baixo, e mantém a porta aberta para um
upgrade futuro reincluir a Store no módulo com o histórico já
disponível, sem perda de contexto — mesmo princípio de retenção pelo
valor acumulado já adotado no documento (§9).

### Lacunas identificadas na revisão criteriosa (setembro/2026) — todas resolvidas

Sete lacunas foram levantadas em varredura completa e crítica do
documento, incluindo uma contradição interna real. Todas foram
resolvidas e incorporadas ao corpo do documento:

1. Contradição interna no §6 sobre o teto de cadastro de Stores —
   corrigida: o teto é explicitamente o maior entre os limites dos
   módulos assinados, não um "extra" indevido (§6).
2. Downgrade sem escolha do seller sobre quais Stores permanecem —
   resolvido: desvinculação automática por ordem de cadastro (mais
   recentes primeiro), com possibilidade de escolha manual a qualquer
   momento (§6).
3. Ausência de trial para o módulo PRODUCTS — resolvido: trial por
   limite de volume (15 produtos, 1 Store), sem prazo de dias (§7.0).
4. Ausência de regra de reembolso/arrependimento — resolvido: direito
   de arrependimento de 7 dias (CDC art. 49), com reembolso integral
   mesmo já havendo consumo de tokens (§7.5).
5. Ciclo de cobrança e reset de cota indefinidos — resolvido: ciclo
   mensal na data de aniversário da assinatura de cada seller (§5.0).
6. Ausência de plano anual/desconto por antecipação — resolvido:
   ciclos trimestral (5%), semestral (8%) e anual (12%) de desconto,
   além do mensal padrão (§4.5).
7. Régua de inadimplência incompleta — resolvido: 5 tentativas diárias
   de cobrança após o vencimento, acesso liberado durante essas
   tentativas, bloqueio no 6º dia, e o prazo de retenção de dados
   passa a contar a partir do bloqueio (§8.15).

Todos os pontos identificados nesta revisão foram resolvidos.

### Segunda rodada — lacunas identificadas via framework de 6 perguntas
(Quem/O quê/Quando/E se/Qual resultado/O que não pode acontecer) —
todas resolvidas

Sete novas lacunas foram levantadas aplicando o framework de 6
perguntas a cada ação/regra do documento. Todas foram resolvidas e
incorporadas ao corpo do documento:

1. Troca de ciclo de pagamento no meio do caminho (mensal ↔
   trimestral/semestral/anual) — resolvido: fica bloqueada durante o
   ciclo vigente; só é possível trocar de ciclo ou de plano perto do
   vencimento ou já vencido, sem crédito ou reembolso proporcional
   pelo tempo não usado (§4.5).
2. Início da contagem de retenção em cancelamento voluntário (sem
   inadimplência) — resolvido: a contagem de 7/30 dias só começa a
   partir do **fim do ciclo já pago**, nunca no momento em que o
   cancelamento é solicitado, valendo para qualquer ciclo (mensal,
   trimestral, semestral, anual) (§8.2).
3. Conflito entre os dois limites do trial do ADS_ANALYZER (3
   execuções vs. 60.000 tokens) — resolvido: o teto de tokens sempre
   prevalece; uma execução é cortada antes de rodar se ultrapassar o
   total de 60.000 tokens, mesmo antes de completar as 3 execuções,
   com barra de progresso mostrando o consumo (§7.3).
4. Reembolso de combo em 7 dias, mas só de um módulo — resolvido:
   permitido cancelar apenas um dos dois módulos dentro do prazo de
   arrependimento, com reembolso proporcional à fatia cancelada; o
   módulo mantido perde o desconto de combo a partir da próxima
   renovação (§4).
5. Falso positivo no score de risco (§7.4) — resolvido: criado canal
   de contestação ("contestar bloqueio") disponível para o seller
   restrito, abrindo revisão manual só para quem contesta — sem fila
   de revisão automática para toda restrição (§7.4).
6. Exclusão de Store pelo próprio seller (fora do fluxo de downgrade)
   — resolvido: aviso explícito de que os dados serão excluídos antes
   da confirmação; exclusão imediata e definitiva (sem carência), e a
   vaga no teto de cadastro libera na hora (§6).
7. Falha de pagamento na primeira cobrança (não recorrência) —
   resolvido: a régua de 5 tentativas com acesso liberado só vale para
   assinatura já ativa; na cobrança inicial, sem pagamento aprovado
   não há nenhum acesso liberado (§8.15).

Todos os pontos identificados nesta segunda rodada foram resolvidos.

### Terceira rodada — máquina de estados e modelo de tokens (RN-XXX) — todas resolvidas

Ao formalizar a máquina de estados da assinatura e fechar o modelo de
tokens (documentadas em detalhe em `easy-manager-regras-negocio.md`),
surgiram mais lacunas, todas resolvidas:

**Máquina de estados:**
1. Corte de acesso no direito de arrependimento — resolvido: acesso
   cortado imediatamente ao pedido, não espera nenhum marco
   (RN-PAY-004).
2. Retorno a `ATIVO` a partir de retenção alcançada por cancelamento
   voluntário — resolvido: exige checkout completo do zero, mesmo com
   os dados ainda existindo dentro da janela de retenção (RN-SUB-010).
3. Desistência do cancelamento voluntário já solicitado — resolvido:
   não é possível, é irreversível (RN-SUB-009).

**Modelo de tokens:**
4. Peso entre tokens de entrada e saída no custo por token — resolvido:
   média ponderada, estimativa inicial ~2:1 (12.500/6.000 por
   execução), recalibrada automaticamente após 100 execuções reais
   (RN-TOKEN-006, §3.2).
5. Regra de arredondamento — resolvido: sempre para baixo/piso em
   qualquer cálculo de tokens (RN-TOKEN-007, §3.2).
6. Histórico de alterações de preço da API — resolvido: versionado
   (data, valor anterior, valor novo) (RN-TOKEN-008, §3.2).
7. Controle de saldo em tokens, R$, ou ambos — resolvido: tokens é a
   fonte de verdade; R$ é só snapshot histórico por execução
   (RN-TOKEN-009, §3.2).

Todos os pontos desta terceira rodada foram resolvidos.
