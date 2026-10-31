import { NavLink, Outlet } from "react-router-dom";
import { Cpu, Workflow, PlayCircle } from "lucide-react";
import { MockBadge } from "./States";
import styles from "./Layout.module.css";

const navItems = [
  { to: "/devices", label: "Устройства", icon: Cpu },
  { to: "/scenarios", label: "Сценарии", icon: Workflow },
  { to: "/runs", label: "Выполнение", icon: PlayCircle },
];

export function Layout() {
  return (
    <div className={styles.layout}>
      <nav className={styles.sidebar} aria-label="Основная навигация">
        <div className={styles.logo}>
          <span className={styles.logoText}>Ujin Editor</span>
          <MockBadge />
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
      </nav>
      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  );
}
