import { useState } from "react"
import { API_URL, deleteProduct, updateProduct } from "../../api"
import styles from "./DetailPanel.module.css"

const stockMeta = (product, lowStockThreshold) => {
  if (product.amount === 0) return { color: "var(--danger)", label: "Out of stock" }
  if (product.amount <= lowStockThreshold)
    return { color: "var(--warn)", label: `${product.amount} left · low stock` }
  return { color: "var(--ok)", label: `${product.amount} in stock` }
}

const DetailPanel = ({ product, lowStockThreshold, onEdit, onDeleted, onRestocked }) => {
  const [restockQty, setRestockQty] = useState(10)
  const [busy, setBusy] = useState(false)

  if (!product) {
    return (
      <aside className={styles.panel}>
        <p className={styles.empty}>Select a product to see its details.</p>
      </aside>
    )
  }

  const meta = stockMeta(product, lowStockThreshold)
  const photo = product.photoUrl
    ? product.photoUrl.startsWith("http")
      ? product.photoUrl
      : `${API_URL}${product.photoUrl}`
    : null

  const handleRestock = async () => {
    const qty = Number(restockQty)
    if (!qty) return
    setBusy(true)
    try {
      await updateProduct(product.id, { name: product.name, amount: product.amount + qty })
      onRestocked()
    } catch (err) {
      alert(err.message)
    } finally {
      setBusy(false)
    }
  }

  const handleDelete = async () => {
    if (!confirm(`Excluir "${product.name}"?`)) return
    setBusy(true)
    try {
      await deleteProduct(product.id)
      onDeleted()
    } catch (err) {
      alert(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <aside className={styles.panel}>
      <div className={styles.head}>
        <div
          className={`${styles.photo} ${!photo ? styles.photoPlaceholder : ""}`}
          style={photo ? { backgroundImage: `url(${photo})` } : undefined}
        />
        <div className={styles.headText}>
          <span className={styles.name}>{product.name}</span>
          <span className={styles.barcode}>{product.barcode}</span>
          <span className={styles.status} style={{ color: meta.color }}>
            {meta.label}
          </span>
        </div>
      </div>

      <div className={styles.actions}>
        <button className={styles.secondaryButton} onClick={onEdit}>
          Edit
        </button>
        <button className={styles.primaryButton} onClick={handleRestock} disabled={busy}>
          Restock
        </button>
        <button className={styles.dangerButton} onClick={handleDelete} disabled={busy}>
          Delete product
        </button>
      </div>

      <div className={styles.card}>
        <span className={styles.cardLabel}>Quick restock</span>
        <div className={styles.restockRow}>
          <span>Add</span>
          <input
            className={styles.restockInput}
            type="number"
            min="1"
            value={restockQty}
            onChange={(e) => setRestockQty(e.target.value)}
          />
          <span>units</span>
        </div>
      </div>
    </aside>
  )
}

export default DetailPanel
