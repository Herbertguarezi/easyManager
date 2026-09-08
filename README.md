# 🧾 Projeto: Easy Manager

Este é um sistema completo de gerenciamento de estoque, desenvolvido com foco em eficiência, escalabilidade e boas práticas de desenvolvimento, incluindo arquitetura em camadas e um front-end moderno com React.

---

## 🚀 Tecnologias utilizadas

### Back-end
- ✅ Java 21+ (atualmente Java 24)
- ✅ Spring Boot
- ✅ JPA/Hibernate
- ✅ MariaDB

### Front-end
- ✅ React
- ✅ React Router DOM
- ✅ CSS Modules

---

## 🔧 Funcionalidades

- Criação, edição e remoção de produtos
- Upload de imagens de produtos
- Controle de estoque (entrada e saída)
- Consulta com filtros (nome, quantidade, etc.)

---


## 🚢 Como executar com Docker Compose

### 📁 Pré-requisitos
- [Docker](https://www.docker.com/products/docker-desktop) instalado
- Docker Compose instalado

### 📄 Passos:

1. Clone o repositório:

   ```bash
   git clone https://github.com/seu-usuario/easyManager.git
   cd easyManager
   ```

2. Crie um arquivo `.env` na raiz (esse arquivo **não é versionado** — nunca commite `.env` com valores reais) com as seguintes variáveis:

   ```env
   DB_USER=usuario
   DB_PASSWORD=senha
   CORS_ALLOWED_ORIGIN=http://localhost:5173
   JWT_SECRET=troque-por-um-segredo-aleatorio
   MARKETPLACE_ENCRYPTION_KEY=troque-por-um-segredo-aleatorio
   WEBHOOK_SECRET=troque-por-um-segredo-aleatorio
   EMAIL_TOKEN_SECRET=troque-por-um-segredo-aleatorio
   ```

   Cada segredo (`JWT_SECRET`, `MARKETPLACE_ENCRYPTION_KEY`, `WEBHOOK_SECRET`,
   `EMAIL_TOKEN_SECRET`) deve ser um valor distinto — não reutilize a mesma
   chave para finalidades diferentes.

3. Suba os containers:

   ```bash
   docker-compose up --build
   ```

4. Acesse no navegador:

   - 🔗 Front-end: [http://localhost:5173](http://localhost:5173)
   - 🔗 Back-end (API REST): [http://localhost:8080](http://localhost:8080)

---
