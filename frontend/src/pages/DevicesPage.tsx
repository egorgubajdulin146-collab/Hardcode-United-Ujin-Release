import { useQuery } from "@tanstack/react-query";
import { Cpu } from "lucide-react";
import { endpoints } from "../api/endpoints";
import { mockDevices } from "../mocks/data";
import { LoadingState, EmptyState, ErrorState, MockBadge } from "../components/States";
import type { Device } from "../api/types";
import styles from "./Pages.module.css";

// Временно используем мок-данные, пока API не готов
const USE_MOCK = true;

function DeviceCard({ device }: { device: Device }) {
  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <Cpu size={20} aria-hidden="true" />
        <h3 className={styles.cardTitle}>{device.title}</h3>
        {USE_MOCK && <MockBadge />}
      </div>
      <div className={styles.cardMeta}>
        <span className={styles.badge}>{device.family}</span>
        <span className={`${styles.badge} ${styles.badgeOutline}`}>
          {device.platformLink === "NOT_CONFIGURED" ? "Не привязан" : device.platformLink}
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
    </div>
  );
}

export function DevicesPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["devices"],
    queryFn: () => endpoints.devices.list(),
    enabled: !USE_MOCK,
  });

  const devices = USE_MOCK ? mockDevices : data ?? [];

  if (isLoading && !USE_MOCK) return <LoadingState />;
  if (error && !USE_MOCK) {
    return (
      <ErrorState
        title="Не удалось загрузить устройства"
        description="Проверьте подключение к серверу"
        onRetry={() => refetch()}
      />
    );
  }

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
            <DeviceCard key={d.id} device={d} />
          ))}
        </div>
      )}
    </div>
  );
}
