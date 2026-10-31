import { useQuery } from "@tanstack/react-query";
import { PlayCircle, CheckCircle, XCircle, Clock, HelpCircle } from "lucide-react";
import { endpoints } from "../api/endpoints";
import { mockRuns } from "../mocks/data";
import { LoadingState, EmptyState, ErrorState, MockBadge } from "../components/States";
import type { Run, RunStatus } from "../api/types";
import styles from "./Pages.module.css";

const USE_MOCK = true;

function StatusIcon({ status }: { status: RunStatus }) {
  switch (status) {
    case "SUCCEEDED":
      return <CheckCircle size={16} className={styles.statusSuccess} aria-label="Успешно" />;
    case "FAILED":
      return <XCircle size={16} className={styles.statusError} aria-label="Ошибка" />;
    case "PENDING":
      return <Clock size={16} className={styles.statusPending} aria-label="В ожидании" />;
    case "UNKNOWN":
      return <HelpCircle size={16} className={styles.statusUnknown} aria-label="Неизвестно" />;
    default:
      return <PlayCircle size={16} aria-hidden="true" />;
  }
}

function RunCard({ run }: { run: Run }) {
  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <StatusIcon status={run.status} />
        <h3 className={styles.cardTitle}>
          {run.input.signalKey} = {run.input.value} → {run.action.signalKey} = {run.action.value}
        </h3>
        {USE_MOCK && <MockBadge />}
      </div>
      <div className={styles.cardMeta}>
        <span className={styles.badge}>{run.executionMode}</span>
        <span className={`${styles.badge} ${
          run.status === "SUCCEEDED" ? styles.badgeSuccess :
          run.status === "FAILED" ? styles.badgeError : styles.badgeOutline
        }`}>
          {run.status === "SUCCEEDED" ? "Успешно" :
           run.status === "FAILED" ? "Ошибка" :
           run.status === "PENDING" ? "В ожидании" :
           run.status === "SKIPPED" ? "Пропущен" : "Неизвестно"}
        </span>
        <span className={styles.timestamp}>
          {new Date(run.createdAt).toLocaleString("ru-RU")}
        </span>
      </div>
      {run.error && (
        <div className={styles.errorDetail}>
          <strong>{run.error.code}:</strong> {run.error.detail}
        </div>
      )}
      {run.skipReason && (
        <div className={styles.skipReason}>
          Причина пропуска: {run.skipReason}
        </div>
      )}
    </div>
  );
}

export function RunsPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["runs"],
    queryFn: () => endpoints.runs.list(),
    enabled: !USE_MOCK,
  });

  const runs = USE_MOCK ? mockRuns : data?.items ?? [];

  if (isLoading && !USE_MOCK) return <LoadingState />;
  if (error && !USE_MOCK) {
    return (
      <ErrorState
        title="Не удалось загрузить журнал"
        description="Проверьте подключение к серверу"
        onRetry={() => refetch()}
      />
    );
  }

  return (
    <div>
      <div className={styles.pageHeader}>
        <h1 className={styles.pageTitle}>Выполнение</h1>
        <p className={styles.pageDescription}>
          Журнал запусков, результатов и ошибок
        </p>
      </div>

      {runs.length === 0 ? (
        <EmptyState
          title="Запусков пока нет"
          description="Здесь появится история выполнения сценариев"
        />
      ) : (
        <div className={styles.cardGrid}>
          {runs.map((r) => (
            <RunCard key={r.id} run={r} />
          ))}
        </div>
      )}
    </div>
  );
}
