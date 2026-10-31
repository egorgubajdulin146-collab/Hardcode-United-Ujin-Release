import { useQuery } from "@tanstack/react-query";
import { Workflow, ArrowRight } from "lucide-react";
import { endpoints } from "../api/endpoints";
import { getDataSource, isMock } from "../api/dataSource";
import { mockScenarioPage, mockDevices } from "../mocks/data";
import { LoadingState, EmptyState, ErrorState, MockBadge } from "../components/States";
import { ApiError, NetworkError, TimeoutError } from "../api/client";
import type { Scenario } from "../api/types";
import styles from "./Pages.module.css";

function getDeviceLabel(deviceId: string, mock: boolean): string {
  if (mock) {
    return mockDevices.find((d) => d.id === deviceId)?.title ?? deviceId.slice(0, 8);
  }
  return deviceId.slice(0, 8);
}

function ScenarioCard({ scenario, mock }: { scenario: Scenario; mock: boolean }) {
  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <Workflow size={20} aria-hidden="true" />
        <h3 className={styles.cardTitle}>{scenario.title}</h3>
        {mock && <MockBadge />}
      </div>
      <div className={styles.cardMeta}>
        <span className={`${styles.badge} ${scenario.enabled ? styles.badgeSuccess : styles.badgeOutline}`}>
          {scenario.enabled ? "Включён" : "Выключен"}
        </span>
        <span className={styles.badge}>{scenario.executionMode}</span>
      </div>
      <div className={styles.scenarioFlow}>
        <div className={styles.flowStep}>
          <span className={styles.flowLabel}>Событие</span>
          <span className={styles.flowValue}>
            {getDeviceLabel(scenario.trigger.deviceId, mock)}.{scenario.trigger.signalKey} = {scenario.trigger.value}
          </span>
        </div>
        <ArrowRight size={16} aria-hidden="true" className={styles.flowArrow} />
        <div className={styles.flowStep}>
          <span className={styles.flowLabel}>Действие</span>
          <span className={styles.flowValue}>
            {getDeviceLabel(scenario.action.deviceId, mock)}.{scenario.action.signalKey} = {scenario.action.value}
          </span>
        </div>
      </div>
    </div>
  );
}

export function ScenariosPage() {
  const mock = isMock();
  const source = getDataSource();

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["scenarios", source],
    queryFn: ({ signal }) => endpoints.scenarios.list(undefined, signal),
    enabled: !mock,
  });

  const page = mock ? mockScenarioPage : data;

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
        title="Не удалось загрузить сценарии"
        description={desc}
        onRetry={() => refetch()}
      />
    );
  }

  const scenarios = page?.items ?? [];

  return (
    <div>
      <div className={styles.pageHeader}>
        <h1 className={styles.pageTitle}>Сценарии</h1>
        <p className={styles.pageDescription}>
          Правила «событие → условие → действие»
        </p>
      </div>

      {scenarios.length === 0 ? (
        <EmptyState
          title="Сценарии не созданы"
          description="Создание сценариев будет доступно в следующей версии"
        />
      ) : (
        <div className={styles.cardGrid}>
          {scenarios.map((s) => (
            <ScenarioCard key={s.id} scenario={s} mock={mock} />
          ))}
        </div>
      )}
    </div>
  );
}
