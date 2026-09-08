import styles from "./Toolbar.module.css"

const TABS = [
  { key: "all", label: "All" },
  { key: "in", label: "In stock" },
  { key: "low", label: "Low stock" },
  { key: "out", label: "Out of stock" },
]

const FilterTabs = ({ tab, onChange, counts }) => {
  return (
    <div className={styles.tabs}>
      {TABS.map((t) => (
        <button
          key={t.key}
          className={`${styles.tab} ${tab === t.key ? styles.active : ""}`}
          onClick={() => onChange(t.key)}
        >
          {t.label} <span className={styles.tabCount}>{counts[t.key]}</span>
        </button>
      ))}
    </div>
  )
}

export default FilterTabs
