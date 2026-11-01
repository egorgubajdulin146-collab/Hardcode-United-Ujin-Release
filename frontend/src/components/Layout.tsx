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
  const { authed, demo, onLogout } = useSession();
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
          <svg className={styles.logoMark} viewBox="0 0 64 64" aria-hidden="true">
            <rect x="4" y="4" width="56" height="56" rx="14" fill="currentColor" />
            <path d="M20 40 V26 a4 4 0 0 1 4-4 h4" fill="none" stroke="#fff" strokeWidth="5" strokeLinecap="round" />
            <path d="M44 24 V38 a4 4 0 0 1-4 4 h-4" fill="none" stroke="#fff" strokeWidth="5" strokeLinecap="round" />
            <circle cx="32" cy="32" r="5" fill="#fff" />
          </svg>
          <div>
            <span className={styles.logoText}>Редактор сценариев</span>
            <span className={styles.logoBadge}>Hardcode United · Ujin</span>
          </div>
        </div>
        <ul className={styles.navList}>
          {navItems.filter(item => !demo || item.to !== "/adapter").map(({ to, label, icon: Icon }) => (
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
          <p className={styles.disclaimer}>Неофициальный инструмент команды. Не является продуктом Ujin.</p>
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
          {!demo && <button
            type="button"
            className={styles.sourceToggle}
            onClick={toggleSource}
            title={`Источник данных: ${mock ? "демонстрационные устройства" : "подключённые устройства"}`}
          >
            {mock ? <Database size={16} /> : <Globe size={16} />}
            <span>{mock ? "Готовые примеры" : "Подключение"}</span>
          </button>}

        </div>
      </nav>
      <main className={styles.content}>
        {demo && <aside role="status" className={styles.demoNotice}>
          <strong>Гостевое демо — локальные Java-эмуляторы</strong>
          <span> Создайте датчик и лампу из шаблонов, затем правило «протечка → свет». До 8 устройств и 8 сценариев, 30 минут. После выхода доступ к этим данным теряется. Ujin и оборудование не подключены.</span>
        </aside>}
        {children}
      </main>
    </div>
  );
}
