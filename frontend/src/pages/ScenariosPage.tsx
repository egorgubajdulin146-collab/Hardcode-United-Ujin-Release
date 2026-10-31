import { useQuery } from "@tanstack/react-query";
import { Workflow, ArrowRight, Zap } from "lucide-react";
import { endpoints } from "../api/endpoints";
import { mockScenarios, mockDevices } from "../mocks/data";
import { LoadingState, EmptyState, ErrorState, MockBadge } from "../components/States";
import type { Scenario } from "../api/types";
import styles from "./Pages.module.css";

const USE_MOCK = true;

function getDeviceTitle(deviceId: string): string {
  return mockDevices.find((d) => d.id === deviceId)?.title ?? deviceId.slice(0, 8);
}

function ScenarioCard({ scenario }: { scenario: Scenario }) {
  return (
    <div className={styles.card}>
      <div className={styles.cardHeader}>
        <Workflow size={20} aria-hidden="true" />
        <h3 className={styles.cardTitle}>{scenario.title}</h3>
        {USE_MOCK && <MockBadge />}
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
            {getDeviceTitle(scenario.trigger.deviceId)}.{scenario.trigger.signalKey} = {scenario.trigger.value}
          </span>
        </div>
        <ArrowRight size={16} aria-hidden="true" className={styles.flowArrow} />
        <div className={styles.flowStep}>
          <span className={styles.flowLabel}>Действие</span>
          <span className={styles.flowValue}>
            {getDeviceTitle(scenario.action.deviceId)}.{scenario.action.signalKey} = {scenario.action.value}
          </span>
        </div>
      </div>
    </div>
  );
}

export function ScenariosPage() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["scenarios"],
    queryFn: () => endpoints.scenarios.list(),
    enabled: !USE_MOCK,
  });

  const scenarios = USE_MOCK ? mockScenarios : data ?? [];

  if (isLoading && !USE_MOCK) return <LoadingState />;
  if (error && !USE_MOCK) {
    return (
      <ErrorState
        title="Не удалось загрузить сценарии"
        description="Проверьте подключение к серверу"
        onRetry={() => refetch()}
      />
    );
  }

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
          description="Создайте правило в редакторе сценариев"
          action={
            <button type="button" className={styles.primaryButton}>
              <Zap size={16} aria-hidden="true" /> Создать сценарий
            </button>
          }
        />
      ) : (
        <div className={styles.cardGrid}>
          {scenarios.map((s) => (
            <ScenarioCard key={s.id} scenario={s} />
          ))}
        </div>
      )}
    </div>
  );
}
