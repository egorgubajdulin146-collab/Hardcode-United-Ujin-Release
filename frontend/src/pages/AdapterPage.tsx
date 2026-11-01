import { useState, useRef, useEffect, type FormEvent } from "react";
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

  const { terminateSession, sessionEpoch } = useSession();
  const mock = isMock();
  const navigate = useNavigate();

  const abortRef = useRef<AbortController | null>(null);
  // Ref для актуального значения epoch — обновляется при каждом рендере
  const currentEpochRef = useRef(sessionEpoch);
  currentEpochRef.current = sessionEpoch;

  // Отмена запроса при размонтировании
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (loading || mock) return;

    // Отменяем предыдущий запрос
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    // Запоминаем epoch на момент старта запроса
    const startEpoch = currentEpochRef.current;

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const data = await readSignal(
        serialnumber.trim(),
        signalName.trim(),
        controller.signal
      );

      // Проверяем: запрос не отменён и сессия не изменилась
      if (controller.signal.aborted) return;
      if (currentEpochRef.current !== startEpoch) return;

      setResult(data);
      // Не вызываем setAuthed(true) — чтение не устанавливает авторизацию
    } catch (err) {
      if (controller.signal.aborted) return;
      if (currentEpochRef.current !== startEpoch) return;

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
      if (!controller.signal.aborted && currentEpochRef.current === startEpoch) {
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
            Серийный номер устройства в Ujin (SN)
          </label>
          <input
            id="serialnumber"
            type="text"
            className={styles.input}
            value={serialnumber}
            onChange={(e) => setSerialnumber(e.target.value)}
            placeholder="Скопируйте SN из Ujin или выданных доступов"
            aria-describedby="adapter-sn-help"
            required
            disabled={loading}
          />
        </div>

        <p id="adapter-sn-help" className={styles.description}>Нужен номер назначенного вам устройства Ujin. UUID устройства из нашего каталога сюда не подходит.</p>

        <div className={styles.field}>
          <label htmlFor="signalName" className={styles.label}>
            Техническое имя сигнала
          </label>
          <input
            id="signalName"
            type="text"
            className={styles.input}
            value={signalName}
            onChange={(e) => setSignalName(e.target.value)}
            placeholder="Ключ сигнала из настроек устройства в Ujin"
            aria-describedby="adapter-signal-help"
            required
            disabled={loading}
          />
        </div>

        <p id="adapter-signal-help" className={styles.description}>
          Укажите технический ключ сигнала из настроек устройства в Ujin. Он может отличаться от названия, которое видит пользователь.
        </p>

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
