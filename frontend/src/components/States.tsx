import type { ReactNode } from "react";
import { Loader2 } from "lucide-react";
import styles from "./States.module.css";

export function LoadingState({ label = "Загрузка…" }: { label?: string }) {
  return (
    <div className={styles.state} role="status" aria-live="polite">
      <Loader2 className={styles.spinner} size={24} />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className={styles.state}>
      <p className={styles.stateTitle}>{title}</p>
      {description && <p className={styles.stateDescription}>{description}</p>}
      {action}
    </div>
  );
}

export function ErrorState({
  title = "Ошибка",
  description,
  onRetry,
}: {
  title?: string;
  description?: string;
  onRetry?: () => void;
}) {
  return (
    <div className={styles.state} role="alert">
      <p className={styles.stateTitle} style={{ color: "var(--color-error)" }}>
        {title}
      </p>
      {description && <p className={styles.stateDescription}>{description}</p>}
      {onRetry && (
        <button type="button" className={styles.retryButton} onClick={onRetry}>
          Повторить
        </button>
      )}
    </div>
  );
}

export function MockBadge() {
  return (
    <span className={styles.mockBadge} title="Данные из мока, не реальный API">
      MOCK
    </span>
  );
}
