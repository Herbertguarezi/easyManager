import { API_URL } from "../../api"
import styles from "./ProductTable.module.css"

const stockMeta = (product, lowStockThreshold) => {
  if (product.amount === 0) return { color: "var(--danger)", label: "Out of stock" }
  if (product.amount <= lowStockThreshold)
    return { color: "var(--warn)", label: `${product.amount} left · low` }
  return { color: "var(--ok)", label: `${product.amount} in stock` }
}

const resolvePhoto = (photoUrl) => {
  if (!photoUrl) return null
  return photoUrl.startsWith("http") ? photoUrl : `${API_URL}${photoUrl}`
}

const ProductTable = ({
  products,
  selectedIds,
  onToggle,
  onOpen,
  openId,
  onClearSelection,
  onDeleteSelected,
  lowStockThreshold,
  error,
}) => {
  const maxAmount = Math.max(1, ...products.map((p) => p.amount))

  return (
    <div className={styles.table}>
      {selectedIds.length > 0 && (
        <div className={styles.selectionBar}>
          <span className={styles.selectionLabel}>
            {selectedIds.length} {selectedIds.length === 1 ? "product selected" : "products selected"}
          </span>
          <div className={styles.selectionSpacer} />
          <button className={styles.dangerButton} onClick={onDeleteSelected}>
            Delete selected
          </button>
          <button className={styles.linkButton} onClick={onClearSelection}>
            Clear
          </button>
        </div>
      )}

      <div className={styles.headRow}>
        <span></span>
        <span>Product</span>
        <span>Inventory</span>
        <span></span>
      </div>

      {error && <p className={styles.error}>{error}</p>}

      {!error && products.length === 0 && <p className={styles.empty}>No products found.</p>}

      {products.map((product) => {
        const meta = stockMeta(product, lowStockThreshold)
        const checked = selectedIds.includes(product.id)
        const photo = resolvePhoto(product.photoUrl)
        return (
          <div
            key={product.id}
            className={`${styles.row} ${product.id === openId ? styles.selected : ""}`}
            onClick={() => onOpen(product.id)}
          >
            <span
              className={`${styles.checkbox} ${checked ? styles.checked : ""}`}
              onClick={(e) => {
                e.stopPropagation()
                onToggle(product.id)
              }}
            >
              {checked ? "✓" : ""}
            </span>

            <span className={styles.productCell}>
              <span
                className={`${styles.thumb} ${!photo ? styles.thumbPlaceholder : ""}`}
                style={photo ? { backgroundImage: `url(${photo})` } : undefined}
              />
              <span className={styles.productText}>
                <span className={styles.productName}>{product.name}</span>
                <span className={styles.productBarcode}>{product.barcode}</span>
              </span>
            </span>

            <span className={styles.stockCell}>
              <span className={styles.stockLabel} style={{ color: meta.color }}>
                {meta.label}
              </span>
              <span className={styles.stockBar}>
                <span
                  className={styles.stockBarFill}
                  style={{
                    width: `${Math.max(3, Math.round((product.amount / maxAmount) * 100))}%`,
                    background: meta.color,
                  }}
                />
              </span>
            </span>

            <span className={styles.chevron}>›</span>
          </div>
        )
      })}

      <div className={styles.footerRow}>
        <span>Showing {products.length} product{products.length === 1 ? "" : "s"}</span>
      </div>
    </div>
  )
}

export default ProductTable
