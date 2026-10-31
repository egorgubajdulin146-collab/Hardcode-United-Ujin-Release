import { useQuery } from "@tanstack/react-query";
import { Cpu } from "lucide-react";
import { endpoints } from "../api/endpoints";
import { getDataSource, isMock } from "../api/dataSource";
import { mockDevicePage } from "../mocks/data";
import { LoadingState, EmptyState, ErrorState, MockBadge } from "../components/States";
import { ApiError, NetworkError, TimeoutError } from "../api/client";
import type { Device } from "../api/types";
import styles from "./Pages.module.css";

function DeviceCard({ device, mock }: { device: Device; mock: boolean }) {
  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <Cpu size={20} aria-hidden="true" />
        <h3 className={styles.cardTitle}>{device.title}</h3>
        {mock && <MockBadge />}
      </div>
      <div className={styles.cardMeta}>
        <span className={styles.badge}>{device.family}</span>
        <span className={`${styles.badge} ${styles.badgeOutline}`}>
          {device.platformLink === "NOT_CONFIGURED" ? "Не привязан" :
           device.platformLink === "BOUND" ? "Привязан" : "Заблокирован"}
        </span>
      </div>
      <div className={styles.signalList}>
        {device.signals.map((s) => (
          <div key={s.key} className={styles.signalItem}>
            <span className={styles.signalKey}>{s.key}</span>
            <span className={styles.signalTitle}>{s.title}</span>
            <span className={`${styles.badge} ${styles.badgeSmall}`}>
              {s.management === "r" ? "только чтение" : "управление"}
            </span>
          </div>
        ))}
      </div>
      {device.events.length > 0 && (
        <div className={styles.eventList}>
          <span className={styles.eventLabel}>События:</span>
          {device.events.map((e) => (
            <span key={e.key} className={`${styles.badge} ${styles.badgeSmall}`}>
              {e.title}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

export function DevicesPage() {
  const mock = isMock();
  const source = getDataSource();

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["devices", source],
    queryFn: ({ signal }) => endpoints.devices.list(undefined, signal),
    enabled: !mock,
  });

  const page = mock ? mockDevicePage : data;

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
        title="Не удалось загрузить устройства"
        description={desc}
        onRetry={() => refetch()}
      />
    );
  }

  const devices = page?.items ?? [];

  return (
    <div>
      <div className={styles.pageHeader}>
        <h1 className={styles.pageTitle}>Устройства</h1>
        <p className={styles.pageDescription}>
          Конструктор устройств и декларация возможностей
        </p>
      </div>

      {devices.length === 0 ? (
        <EmptyState
          title="Устройства не добавлены"
          description="Создайте первое устройство в конструкторе"
        />
      ) : (
        <div className={styles.cardGrid}>
          {devices.map((d) => (
            <DeviceCard key={d.id} device={d} mock={mock} />
          ))}
        </div>
      )}
    </div>
  );
}
