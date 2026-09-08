import styles from "./Toolbar.module.css"

const StatsBar = ({ products, lowStockThreshold }) => {
  const totalUnits = products.reduce((sum, p) => sum + p.amount, 0)
  const lowCount = products.filter((p) => p.amount > 0 && p.amount <= lowStockThreshold).length
  const outCount = products.filter((p) => p.amount === 0).length

  const stats = [
    { label: "Products", value: String(products.length), note: "in catalog" },
    { label: "Units in stock", value: String(totalUnits), note: "across all products" },
    {
      label: "Low stock",
      value: String(lowCount),
      note: `${lowStockThreshold} units or fewer`,
      tone: "var(--warn)",
    },
    { label: "Out of stock", value: String(outCount), note: "needs restocking", tone: "var(--danger)" },
  ]

  return (
    <div className={styles.stats}>
      {stats.map((s) => (
        <div className={styles.statCard} key={s.label}>
          <span className={styles.statLabel}>{s.label}</span>
          <span className={styles.statValue} style={{ color: s.tone }}>
            {s.value}
          </span>
          <span className={styles.statNote}>{s.note}</span>
        </div>
      ))}
    </div>
  )
}

export default StatsBar
