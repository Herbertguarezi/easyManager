import styles from "./Sidebar.module.css"

const Sidebar = () => {
  return (
    <aside className={styles.sidebar}>
      <div className={styles.brand}>
        <div className={styles.logo}>E</div>
        <div className={styles.brandText}>
          <span className={styles.brandName}>EasyManager</span>
          <span className={styles.brandSub}>Store admin</span>
        </div>
      </div>

      <nav className={styles.nav}>
        <div className={styles.navLabel}>Operate</div>
        <div className={`${styles.navItem} ${styles.active}`}>
          <span className={styles.navDot} />
          Products
        </div>
      </nav>

      <div className={styles.footer}>
        <div className={styles.avatar}>HG</div>
        <div className={styles.footerText}>
          <span className={styles.footerName}>Herbert Guarezi</span>
          <span className={styles.footerRole}>Owner</span>
        </div>
      </div>
    </aside>
  )
}

export default Sidebar
