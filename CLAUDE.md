# Easy Manager

## Antes de qualquer tarefa
Leia, nesta ordem:
1. docs/software-engineering.md — decisões de arquitetura e requisitos funcionais
2. docs/arquitetura-banco-de-dados.md — schema, tipos, constraints, migrations
3. docs/regras-negocio.md — regras de negócio numeradas (RN-XXX), invariantes
   e a máquina de estados da assinatura (§13) — é a fonte de verdade para
   qualquer coisa sobre planos, trial, cobrança, tokens ou entitlements
4. docs/glossario-traducao.md — mapeamento PT→EN de nomenclatura de código
5. PROGRESS.md — o que já foi feito e o que falta

Se uma tarefa tocar em assinatura, plano, trial, cobrança, tokens ou
qualquer verificação de `EntitlementService`, releia o RN-XXX relevante
em docs/regras-negocio.md antes de implementar — não decida a regra de
negócio a partir do código existente ou por inferência.

## Processo
- Desenvolvimento é orientado a testes (TDD): para cada item, escreva o
  teste que falha primeiro (red), implemente o mínimo pra passar (green),
  refatore, e só então siga para o próximo item. Nenhum item conta como
  concluído sem teste automatizado cobrindo o comportamento.
- Código (nomes de classe, método, tabela, coluna, variável, valor de
  enum, comentário, mensagem de commit) sai em **inglês**, seguindo
  docs/glossario-traducao.md. Os documentos de arquitetura ficam em
  português — não traduza os documentos, só o código.
- Se um termo novo aparecer e não estiver no glossário, escolha a
  tradução, adicione a docs/glossario-traducao.md, e só depois use no
  código.
- **Nunca edite docs/software-engineering.md, docs/arquitetura-banco-de-dados.md,
  docs/regras-negocio.md, docs/precificacao.md ou docs/diagramas-uml.md
  diretamente** — mesmo quando achar um erro, uma contradição entre
  eles, ou uma regra de negócio faltando. Esses documentos são
  mantidos fora deste repositório; só são corrigidos ali e reenviados.
  Em vez de editar, registre o achado na seção "Log de decisões
  durante a implementação" do PROGRESS.md, explicando a contradição ou
  a lacuna encontrada. Se a lacuna bloquear a tarefa atual, pare e
  pergunte antes de prosseguir, em vez de assumir a regra. Se não
  bloquear, pode seguir com uma decisão conservadora, mas deixe
  registrado que não é uma decisão de negócio confirmada. Exceção:
  docs/glossario-traducao.md continua self-service, como já descrito
  acima.
- Trabalhe uma fase do roadmap por vez (§14 de docs/software-engineering.md),
  na ordem listada em PROGRESS.md. Não adiante itens de fases seguintes
  sem pedido explícito.
- Ao concluir um item, marque-o em PROGRESS.md. Registre qualquer desvio
  do que está descrito nos documentos na seção "Log de decisões durante
  a implementação" do PROGRESS.md — isso é revisado antes de avançar de
  fase.
- Multi-tenancy é regra transversal (§3 de docs/software-engineering.md):
  toda consulta a recurso autenticado filtra por seller_id, direto ou
  por relação indireta. Nunca busque um recurso só por ID.

## Comandos
- Testes backend: `./mvnw test`
- Testes frontend: `npm test`
- Subir ambiente local: `docker-compose up`
- Nova migration: criar arquivo em `src/main/resources/db/migration/`
  seguindo convenção Flyway (`V{n}__descricao.sql`), onde `{n}` é o
  **próximo número livre no repositório** — confira o diretório antes
  de nomear. A numeração que aparece nos documentos de arquitetura
  (ex: "V10–V19") é só ordem relativa entre as tabelas descritas ali,
  não o número real — eles divergem desde a Fase 1 (migrations de Auth
  já ocuparam V2–V4) e nenhum dos dois lados é atualizado em tempo
  real. Nunca edite uma migration já aplicada: numeração errada vira
  migration nova corretiva, não edição da antiga.

## Não fazer
- Não usar `spring.jpa.hibernate.ddl-auto=update` — schema só muda via
  migration Flyway nova, nunca editando uma já aplicada.
- Não commitar `.env`, chave, token ou qualquer segredo em texto puro.
- Não armazenar token de sessão (refresh token, tokens de confirmação de
  email/reset de senha) em texto puro — sempre hash, conforme os
  documentos.
- Não reaproveitar a mesma variável de ambiente para finalidades de
  segredo diferentes (JWT, criptografia de token de marketplace, webhook,
  confirmação de email são chaves distintas).
