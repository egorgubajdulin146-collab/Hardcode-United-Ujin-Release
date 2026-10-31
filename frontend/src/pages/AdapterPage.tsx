import { useState, type FormEvent } from "react";
import { Wifi, Search, AlertCircle, Loader2 } from "lucide-react";
import { readSignal, AuthError, isAuthenticated } from "../api/authClient";
import type { UjinAdapterSignalValue } from "../api/auth";
import styles from "./AdapterPage.module.css";

export function AdapterPage() {
  const [serialnumber, setSerialnumber] = useState("");
  const [signalName, setSignalName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<UjinAdapterSignalValue | null>(null);

  const authed = isAuthenticated();

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (loading) return;

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const data = await readSignal(serialnumber.trim(), signalName.trim());
      setResult(data);
    } catch (err) {
      if (err instanceof AuthError) {
        setError(err.message);
      } else {
        setError("Неизвестная ошибка");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <Wifi size={24} aria-hidden="true" />
        <h1 className={styles.title}>Диагностика адаптера Ujin</h1>
      </div>

      <p className={styles.description}>
        Чтение сигнала устройства через адаптер. Это отдельная функция интеграции,
        не конструктор и не состояние устройства.
      </p>

      <form onSubmit={handleSubmit} className={styles.form}>
        <div className={styles.field}>
          <label htmlFor="serialnumber" className={styles.label}>
            Serialnumber устройства
          </label>
          <input
            id="serialnumber"
            type="text"
            className={styles.input}
            value={serialnumber}
            onChange={(e) => setSerialnumber(e.target.value)}
            placeholder="Например, test-device"
            required
            disabled={loading}
          />
        </div>

        <div className={styles.field}>
          <label htmlFor="signalName" className={styles.label}>
            Имя сигнала
          </label>
          <input
            id="signalName"
            type="text"
            className={styles.input}
            value={signalName}
            onChange={(e) => setSignalName(e.target.value)}
            placeholder="Например, lamp"
            required
            disabled={loading}
          />
        </div>

        <button
          type="submit"
          className={styles.submitButton}
          disabled={loading || !authed}
        >
          {loading ? (
            <>
              <Loader2 size={16} className={styles.spinner} aria-hidden="true" />
              <span>Чтение…</span>
            </>
          ) : (
            <>
              <Search size={16} aria-hidden="true" />
              <span>Прочитать сигнал</span>
            </>
          )}
        </button>

        {!authed && (
          <p className={styles.authHint}>
            Требуется авторизация. Войдите через форму входа.
          </p>
        )}
      </form>

      {error && (
        <div className={styles.error} role="alert">
          <AlertCircle size={16} aria-hidden="true" />
          <span>{error}</span>
        </div>
      )}

      {result && (
        <div className={styles.result}>
          <h2 className={styles.resultTitle}>Результат</h2>
          <div className={styles.resultCard}>
            <div className={styles.resultRow}>
              <span className={styles.resultKey}>Сигнал</span>
              <span className={styles.resultValue}>{result.name}</span>
            </div>
            <div className={styles.resultRow}>
              <span className={styles.resultKey}>Значение</span>
              <span className={`${styles.resultValue} ${styles.resultValueHighlight}`}>
                {result.value}
              </span>
            </div>
          </div>
          <p className={styles.resultNote}>
            Значение получено от адаптера (строка). Не приводится к числу автоматически.
          </p>
        </div>
      )}
    </div>
  );
}
