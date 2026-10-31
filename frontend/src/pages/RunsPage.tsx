import { useQuery } from "@tanstack/react-query";
import { PlayCircle, CheckCircle, XCircle, Clock, HelpCircle, Ban, SkipForward } from "lucide-react";
import { endpoints } from "../api/endpoints";
import { getDataSource, isMock } from "../api/dataSource";
import { mockRunPage } from "../mocks/data";
import { LoadingState, EmptyState, ErrorState, MockBadge } from "../components/States";
import { ApiError, NetworkError, TimeoutError } from "../api/client";
import type { Run, RunStatus } from "../api/types";
import styles from "./Pages.module.css";

function StatusIcon({ status }: { status: RunStatus }) {
  switch (status) {
    case "SUCCEEDED":
      return <CheckCircle size={16} className={styles.statusSuccess} aria-label="Успешно" />;
    case "FAILED":
      return <XCircle size={16} className={styles.statusError} aria-label="Ошибка" />;
    case "BLOCKED":
      return <Ban size={16} className={styles.statusBlocked} aria-label="Заблокировано" />;
    case "SKIPPED":
      return <SkipForward size={16} className={styles.statusSkipped} aria-label="Пропущено" />;
    case "PENDING":
      return <Clock size={16} className={styles.statusPending} aria-label="В ожидании" />;
    case "UNKNOWN":
      return <HelpCircle size={16} className={styles.statusUnknown} aria-label="Неизвестно" />;
    default:
      return <PlayCircle size={16} aria-hidden="true" />;
  }
}

const STATUS_LABELS: Record<RunStatus, string> = {
  SUCCEEDED: "Успешно",
  FAILED: "Ошибка",
  BLOCKED: "Заблокировано",
  SKIPPED: "Пропущено",
  PENDING: "В ожидании",
  UNKNOWN: "Неизвестно",
};

const STATUS_BADGE: Record<RunStatus, string> = {
  SUCCEEDED: styles.badgeSuccess,
  FAILED: styles.badgeError,
  BLOCKED: styles.badgeBlocked,
  SKIPPED: styles.badgeOutline,
  PENDING: styles.badgeOutline,
  UNKNOWN: styles.badgeOutline,
};

function RunCard({ run, mock }: { run: Run; mock: boolean }) {
  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <StatusIcon status={run.status} />
        <h3 className={styles.cardTitle}>
          {run.input
            ? `${run.input.signalKey} = ${run.input.value}`
            : `Событие ${run.eventId.slice(0, 8)}`}
          {run.action
            ? ` → ${run.action.signalKey} = ${run.action.value}`
            : ""}
        </h3>
        {mock && <MockBadge />}
      </div>
      <div className={styles.cardMeta}>
        <span className={styles.badge}>{run.executionMode}</span>
        <span className={`${styles.badge} ${STATUS_BADGE[run.status]}`}>
          {STATUS_LABELS[run.status]}
        </span>
        <span className={styles.timestamp}>
          {new Date(run.createdAt).toLocaleString("ru-RU")}
        </span>
      </div>

      {run.reason && (
        <div className={styles.reasonLine}>
          Причина: {run.reason}
        </div>
      )}

      {run.resolution && (
        <div className={styles.resolutionLine}>
          <strong>{run.resolution.kind}:</strong> {run.resolution.note}
        </div>
      )}

      {run.entries.length > 0 && (
        <details className={styles.entriesDetails}>
          <summary>Журнал ({run.entries.length})</summary>
          <ul className={styles.entriesList}>
            {run.entries.map((e, i) => (
              <li key={i} className={styles.entryItem}>
                <span className={styles.entryStage}>{e.stage}</span>
                <span className={styles.entryMessage}>{e.message}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

export function RunsPage() {
  const mock = isMock();
  const source = getDataSource();

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["runs", source],
    queryFn: ({ signal }) => endpoints.runs.list(undefined, signal),
    enabled: !mock,
  });

  const page = mock ? mockRunPage : data;

  if (isLoading && !mock) return <LoadingState />;
  if (error && !mock) {
    const desc =
      error instanceof ApiError
        ? error.status === 401
          ? "Требуется авторизация"
          : error.status === 403
            ? "Нет доступа"
            : error.problem?.detail ?? `Ошибка сервера (${error.status})`
        : error instanceof TimeoutError
          ? "Превышено время ожидания"
          : error instanceof NetworkError
            ? "Сервер недоступен"
            : "Неизвестная ошибка";
    return (
      <ErrorState
        title="Не удалось загрузить журнал"
        description={desc}
        onRetry={() => refetch()}
      />
    );
  }

  const runs = page?.items ?? [];

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
            <RunCard key={r.id} run={r} mock={mock} />
          ))}
        </div>
      )}
    </div>
  );
}
