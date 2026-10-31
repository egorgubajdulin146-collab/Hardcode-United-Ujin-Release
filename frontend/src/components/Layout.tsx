import { NavLink, Outlet } from "react-router-dom";
import { Cpu, Workflow, PlayCircle, Database, Globe } from "lucide-react";
import { setDataSource, isMock } from "../api/dataSource";
import { MockBadge } from "./States";
import styles from "./Layout.module.css";

const navItems = [
  { to: "/devices", label: "Устройства", icon: Cpu },
  { to: "/scenarios", label: "Сценарии", icon: Workflow },
  { to: "/runs", label: "Выполнение", icon: PlayCircle },
];

export function Layout() {
  const mock = isMock();

  const toggleSource = () => {
    setDataSource(mock ? "api" : "mock");
    window.location.reload();
  };

  return (
    <div className={styles.layout}>
      <nav className={styles.sidebar} aria-label="Основная навигация">
        <div className={styles.logo}>
          <span className={styles.logoText}>Ujin Editor</span>
          <span className={styles.logoBadge}>Hardcode United</span>
        </div>
        <ul className={styles.navList}>
          {navItems.map(({ to, label, icon: Icon }) => (
            <li key={to}>
              <NavLink
                to={to}
                className={({ isActive }) =>
                  `${styles.navLink} ${isActive ? styles.navLinkActive : ""}`
                }
              >
                <Icon size={20} aria-hidden="true" />
                <span>{label}</span>
              </NavLink>
            </li>
          ))}
        </ul>
        <div className={styles.sidebarFooter}>
          <button
            type="button"
            className={styles.sourceToggle}
            onClick={toggleSource}
            title={`Источник данных: ${mock ? "мок-данные" : "реальный API"}`}
          >
            {mock ? <Database size={16} /> : <Globe size={16} />}
            <span>{mock ? "MOCK" : "API"}</span>
          </button>
          {mock && <MockBadge />}
        </div>
      </nav>
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
}
