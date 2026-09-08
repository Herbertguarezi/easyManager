import { useEffect, useMemo, useState } from "react"
import Sidebar from "./Components/Layout/Sidebar"
import TopBar from "./Components/Layout/TopBar"
import StatsBar from "./Components/Products/StatsBar"
import FilterTabs from "./Components/Products/FilterTabs"
import ProductTable from "./Components/Products/ProductTable"
import DetailPanel from "./Components/Products/DetailPanel"
import ProductFormModal from "./Components/Products/ProductFormModal"
import { getProducts, deleteProduct } from "./api"
import styles from "./App.module.css"

const LOW_STOCK_THRESHOLD = 5

function App() {
  const [products, setProducts] = useState([])
  const [error, setError] = useState(null)
  const [query, setQuery] = useState("")
  const [tab, setTab] = useState("all")
  const [selectedIds, setSelectedIds] = useState([])
  const [openId, setOpenId] = useState(null)
  const [modal, setModal] = useState(null)

  const loadProducts = () => {
    getProducts()
      .then((data) => {
        setProducts(data)
        setError(null)
      })
      .catch((err) => setError(err.message))
  }

  useEffect(loadProducts, [])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return products
      .filter((p) => {
        if (tab === "low") return p.amount > 0 && p.amount <= LOW_STOCK_THRESHOLD
        if (tab === "out") return p.amount === 0
        if (tab === "in") return p.amount > LOW_STOCK_THRESHOLD
        return true
      })
      .filter(
        (p) =>
          !q ||
          p.name.toLowerCase().includes(q) ||
          (p.barcode || "").toLowerCase().includes(q)
      )
  }, [products, query, tab])

  const openProduct = products.find((p) => p.id === openId) || null

  const toggleSelected = (id) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    )
  }

  const handleDeleteSelected = async () => {
    if (!confirm(`Excluir ${selectedIds.length} produto(s)?`)) return
    for (const id of selectedIds) {
      try {
        await deleteProduct(id)
      } catch (err) {
        console.error(err)
      }
    }
    setSelectedIds([])
    loadProducts()
  }

  return (
    <div className={styles.shell}>
      <Sidebar />
      <div className={styles.main}>
        <TopBar query={query} onQuery={setQuery} onNewProduct={() => setModal({ mode: "create" })} />

        <div className={styles.content}>
          <section className={styles.listPane}>
            <StatsBar products={products} lowStockThreshold={LOW_STOCK_THRESHOLD} />

            <FilterTabs
              tab={tab}
              onChange={setTab}
              counts={{
                all: products.length,
                in: products.filter((p) => p.amount > LOW_STOCK_THRESHOLD).length,
                low: products.filter((p) => p.amount > 0 && p.amount <= LOW_STOCK_THRESHOLD).length,
                out: products.filter((p) => p.amount === 0).length,
              }}
            />

            <ProductTable
              products={filtered}
              selectedIds={selectedIds}
              onToggle={toggleSelected}
              onOpen={setOpenId}
              openId={openProduct?.id}
              onClearSelection={() => setSelectedIds([])}
              onDeleteSelected={handleDeleteSelected}
              lowStockThreshold={LOW_STOCK_THRESHOLD}
              error={error}
            />
          </section>

          <DetailPanel
            product={openProduct}
            lowStockThreshold={LOW_STOCK_THRESHOLD}
            onEdit={() => setModal({ mode: "edit", product: openProduct })}
            onDeleted={() => {
              setOpenId(null)
              loadProducts()
            }}
            onRestocked={loadProducts}
          />
        </div>
      </div>

      {modal && (
        <ProductFormModal
          mode={modal.mode}
          product={modal.product}
          onClose={() => setModal(null)}
          onSaved={() => {
            setModal(null)
            loadProducts()
          }}
        />
      )}
    </div>
  )
}

export default App
