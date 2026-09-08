import { useState } from "react"
import Modal from "../Modal"
import { createProduct, updateProduct } from "../../api"
import styles from "./ProductFormModal.module.css"

const ProductFormModal = ({ mode, product, onClose, onSaved }) => {
  const isEdit = mode === "edit"
  const [name, setName] = useState(product?.name ?? "")
  const [amount, setAmount] = useState(product?.amount ?? 1)
  const [barcode, setBarcode] = useState(product?.barcode ?? "")
  const [file, setFile] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!isEdit && !file) {
      setError("Selecione uma imagem para o produto.")
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      if (isEdit) {
        await updateProduct(product.id, { name, amount: Number(amount) })
      } else {
        await createProduct({ name, amount, barcode, file })
      }
      onSaved()
    } catch (err) {
      setError(err.message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={isEdit ? "Editar produto" : "Novo produto"} onClose={onClose}>
      <form className={styles.form} onSubmit={handleSubmit}>
        {error && <p className={styles.error}>{error}</p>}

        <div className={styles.field}>
          <label className={styles.label}>Nome</label>
          <input value={name} onChange={(e) => setName(e.target.value)} required />
        </div>

        <div className={styles.field}>
          <label className={styles.label}>Quantidade</label>
          <input
            type="number"
            min="0"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
          />
        </div>

        {!isEdit && (
          <>
            <div className={styles.field}>
              <label className={styles.label}>Código de barras</label>
              <input value={barcode} onChange={(e) => setBarcode(e.target.value)} required />
            </div>

            <div className={styles.field}>
              <label className={styles.label}>Imagem</label>
              <input
                type="file"
                accept="image/*"
                onChange={(e) => setFile(e.target.files[0])}
                required
              />
            </div>
          </>
        )}

        <div className={styles.actions}>
          <button type="button" className={styles.secondaryButton} onClick={onClose}>
            Cancelar
          </button>
          <button type="submit" className={styles.primaryButton} disabled={submitting}>
            {submitting ? "Salvando..." : isEdit ? "Salvar" : "Adicionar produto"}
          </button>
        </div>
      </form>
    </Modal>
  )
}

export default ProductFormModal
