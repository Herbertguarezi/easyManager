const API_URL = 'http://localhost:8080/products';

// Função para enviar apenas a imagem
async function enviarImagem(file) {
  const formData = new FormData();
  formData.append('imagem', file);

  const response = await fetch(`${API_URL}/imagem`, {
    method: 'POST',
    body: formData
  });

  if (!response.ok) throw new Error('Erro no upload de imagem');
  return await response.text(); // retorna a URL da imagem
}

// Envio do formulário
document.getElementById('form-produto').addEventListener('submit', async (e) => {
  e.preventDefault();
  const form = e.target;

  const name = form.nome.value;
  const amount = parseInt(form.quantidade.value);
  const photo = form.imagem.files[0];

  if (!name || !amount || !photo) {
    alert("Preencha todos os campos.");
    return;
  }

  try {
    // 1. Envia a imagem
    const photoUrl = await enviarImagem(photo);

    // 2. Envia os dados do produto com JSON
    const produto = { name, amount, photoUrl };

    const response = await fetch(API_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(produto)
    });

    if (!response.ok) throw new Error('Erro ao cadastrar produto');

    alert('Produto cadastrado com sucesso!');
    form.reset();
    carregarProdutos();
  } catch (err) {
    alert(err.message);
    console.error(err);
  }
});

// Função para buscar todos os produtos
async function carregarProdutos() {
  try {
    const response = await fetch(API_URL);
    const produtos = await response.json();

    const lista = document.getElementById('lista-produtos');
    lista.innerHTML = '';

    produtos.forEach(produto => {
      const div = document.createElement('div');
      div.className = 'produto';

      div.innerHTML = `
        <strong>${produto.nome}</strong><br />
        Quantidade: ${produto.quantidade}<br />
        <img src="${produto.imagemUrl}" alt="Imagem de ${produto.nome}" />
      `;

      lista.appendChild(div);
    });
  } catch (err) {
    console.error('Erro ao carregar produtos:', err);
  }
}

// Chamada inicial para carregar lista ao abrir a página
carregarProdutos();
