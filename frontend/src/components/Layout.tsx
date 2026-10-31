import type { ReactNode } from "react";
import { NavLink, useNavigate } from "react-router-dom";
import { Cpu, Workflow, PlayCircle, Database, Globe, LogIn, LogOut, Wifi } from "lucide-react";
import { setDataSource, isMock } from "../api/dataSource";
import { logout, clearCsrf } from "../api/authClient";
import { useSession } from "../api/sessionContext";
import styles from "./Layout.module.css";

const navItems = [
  { to: "/devices", label: "Устройства", icon: Cpu },
  { to: "/scenarios", label: "Сценарии", icon: Workflow },
  { to: "/runs", label: "Выполнение", icon: PlayCircle },
  { to: "/adapter", label: "Адаптер", icon: Wifi },
];

export function Layout({ children }: { children: ReactNode }) {
  const mock = isMock();
  const { authed, onLogout } = useSession();
  const navigate = useNavigate();

  const toggleSource = () => {
    setDataSource(mock ? "api" : "mock");
    window.location.reload();
  };

  const handleLogout = async () => {
    try {
      await logout();
      clearCsrf();
      onLogout();
      navigate("/login");
    } catch {
      alert("Не удалось выйти. Серверная сессия может быть активна.");
    }
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
          {authed ? (
            <button
              type="button"
              className={styles.sourceToggle}
              onClick={handleLogout}
              title="Выйти"
            >
              <LogOut size={16} />
              <span>Выйти</span>
            </button>
          ) : (
            <NavLink
              to="/login"
              className={({ isActive }) =>
                `${styles.sourceToggle} ${isActive ? styles.navLinkActive : ""}`
              }
            >
              <LogIn size={16} />
              <span>Войти</span>
            </NavLink>
          )}
          <button
            type="button"
            className={styles.sourceToggle}
            onClick={toggleSource}
            title={`Источник данных: ${mock ? "демонстрационные устройства" : "подключённые устройства"}`}
          >
            {mock ? <Database size={16} /> : <Globe size={16} />}
            <span>{mock ? "Демо" : "Подключение"}</span>
          </button>

        </div>
      </nav>
      <main className={styles.content}>
        {children}
      </main>
    </div>
  );
}
