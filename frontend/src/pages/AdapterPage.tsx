import { useState, useRef, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Wifi, Search, AlertCircle, Loader2, Database } from "lucide-react";
import { readSignal, AuthError } from "../api/authClient";
import { useSession } from "../api/sessionContext";
import { isMock } from "../api/dataSource";
import type { UjinAdapterSignalValue } from "../api/auth";
import styles from "./AdapterPage.module.css";

export function AdapterPage() {
  const [serialnumber, setSerialnumber] = useState("");
  const [signalName, setSignalName] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<UjinAdapterSignalValue | null>(null);

  const { setAuthed, terminateSession } = useSession();
  const mock = isMock();
  const navigate = useNavigate();

  const abortRef = useRef<AbortController | null>(null);
  const requestEpoch = useRef(0);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (loading || mock) return;

    // Отменяем предыдущий запрос
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const epoch = ++requestEpoch.current;

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const data = await readSignal(serialnumber.trim(), signalName.trim());

      // Проверяем, что запрос всё ещё актуален
      if (epoch !== requestEpoch.current) return;

      setResult(data);
      setAuthed(true);
    } catch (err) {
      if (epoch !== requestEpoch.current) return;

      if (err instanceof AuthError) {
        if (err.kind === "invalid_credentials") {
          terminateSession();
          navigate("/login");
          return;
        }
        setError(err.message);
      } else {
        setError("Неизвестная ошибка");
      }
    } finally {
      if (epoch === requestEpoch.current) {
        setLoading(false);
      }
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
          disabled={loading || mock}
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

        {mock && (
          <p className={styles.authHint}>
            <Database size={14} aria-hidden="true" /> Адаптер работает только в API-режиме.
            Переключите источник данных.
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
