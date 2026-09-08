# Easy Manager

## Antes de qualquer tarefa
Leia, nesta ordem:
1. docs/software-engineering.md — decisões de arquitetura e requisitos funcionais
2. docs/arquitetura-banco-de-dados.md — schema, tipos, constraints, migrations
3. docs/glossario-traducao.md — mapeamento PT→EN de nomenclatura de código
4. PROGRESS.md — o que já foi feito e o que falta

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
  seguindo convenção Flyway (`V{n}__descricao.sql`)

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
