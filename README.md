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

2. Crie um arquivo `.env` na raiz com o seguinte conteúdo:

   ```env
   DB_USER=usuario
   DB_PASSWORD=senha
   ```

3. Suba os containers:

   ```bash
   docker-compose up --build
   ```

4. Acesse no navegador:

   - 🔗 Front-end: [http://localhost:5173](http://localhost:5173)
   - 🔗 Back-end (API REST): [http://localhost:8080](http://localhost:8080)

---
