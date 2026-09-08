import styles from "./TopBar.module.css"

const TopBar = ({ query, onQuery, onNewProduct }) => {
  return (
    <header className={styles.bar}>
      <div className={styles.titleBlock}>
        <span className={styles.eyebrow}>Catalog</span>
        <h1 className={styles.title}>Products &amp; inventory</h1>
      </div>

      <div className={styles.spacer} />

      <label className={styles.search}>
        <span className={styles.searchDot} />
        <input
          className={styles.searchInput}
          value={query}
          onChange={(e) => onQuery(e.target.value)}
          placeholder="Search products, barcodes"
        />
      </label>

      <button className={styles.primaryButton} onClick={onNewProduct}>
        New product
      </button>
    </header>
  )
}

export default TopBar
