import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { LogIn, AlertCircle, Loader2 } from "lucide-react";
import { login, loginDemo, fetchCsrf, AuthError } from "../api/authClient";
import { useSession } from "../api/sessionContext";
import styles from "./LoginPage.module.css";

export function LoginPage() {
  const [loginValue, setLoginValue] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const { onLogin, demoAvailable } = useSession();
  const startDemo = async () => {
    if (loading) return;
    setLoading(true); setError(null);
    try { await fetchCsrf(); await loginDemo(); setPassword(""); onLogin(true); navigate("/devices"); }
    catch (error) { setError(error instanceof AuthError ? error.message : "Не удалось начать демо."); }
    finally { setLoading(false); }
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (loading) return;

    setLoading(true);
    setError(null);

    try {
      await fetchCsrf();
      await login({ login: loginValue, password });
      setPassword("");
      onLogin();
      navigate("/adapter");
    } catch (err) {
      if (err instanceof AuthError) {
        setError(err.message);
      } else {
        setError("Неизвестная ошибка");
      }
      setPassword("");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.header}>
          <LogIn size={24} aria-hidden="true" />
          <h1 className={styles.title}>Вход в систему</h1>
        </div>

        <form onSubmit={handleSubmit} className={styles.form}>
          <div className={styles.field}>
            <label htmlFor="login" className={styles.label}>
              Логин
            </label>
            <input
              id="login"
              type="text"
              className={styles.input}
              value={loginValue}
              onChange={(e) => setLoginValue(e.target.value)}
              autoComplete="username"
              required
              disabled={loading}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="password" className={styles.label}>
              Пароль
            </label>
            <input
              id="password"
              type="password"
              className={styles.input}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
              disabled={loading}
            />
          </div>

          {error && (
            <div className={styles.error} role="alert">
              <AlertCircle size={16} aria-hidden="true" />
              <span>{error}</span>
            </div>
          )}

          <button type="submit" className={styles.submitButton} disabled={loading}>
            {loading ? (
              <>
                <Loader2 size={16} className={styles.spinner} aria-hidden="true" />
                <span>Вход…</span>
              </>
            ) : (
              <span>Войти</span>
            )}
          </button>
        </form>
        {demoAvailable && <div className={styles.demoSection}>
          <button type="button" className={styles.demoButton} disabled={loading} onClick={() => void startDemo()}>Попробовать без Ujin</button>
          <p>Создайте устройства и сценарий, запустите локальную эмуляцию и проверьте журнал. Доступы Ujin не нужны.</p>
          <small>Отдельная гостевая сессия на 30 минут: до 8 устройств и 8 сценариев. После выхода доступ к этим данным теряется. Реальное оборудование недоступно.</small>
        </div>}
      </div>
      <p className={styles.footerNote}>
        Неофициальный инструмент команды Hardcode United для работы с платформой Ujin.
      </p>
    </div>
  );
}
