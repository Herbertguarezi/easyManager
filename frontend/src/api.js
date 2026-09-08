export const API_URL = "http://localhost:8080"

export async function getProducts() {
  const res = await fetch(`${API_URL}/products`)
  if (!res.ok) throw new Error("Erro ao buscar produtos")
  return res.json()
}

export async function createProduct({ name, amount, barcode, file }) {
  const formData = new FormData()
  formData.append("name", name)
  formData.append("amount", amount)
  formData.append("barcode", barcode)
  formData.append("file", file)

  const res = await fetch(`${API_URL}/products`, {
    method: "POST",
    body: formData,
  })
  if (!res.ok) throw new Error("Erro ao criar produto")
  return res.json()
}

// The backend only persists name/amount on update, even though the
// endpoint accepts a full Product payload — see ProductRepositoryPersistenceAdapter.
export async function updateProduct(id, { name, amount }) {
  const res = await fetch(`${API_URL}/products/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id, name, amount }),
  })
  if (!res.ok) throw new Error("Erro ao atualizar produto")
  return res.text()
}

export async function deleteProduct(id) {
  const res = await fetch(`${API_URL}/products/${id}`, { method: "DELETE" })
  if (!res.ok) throw new Error("Erro ao deletar produto")
}
