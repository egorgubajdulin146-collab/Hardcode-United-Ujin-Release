import { useState, useCallback, useMemo, useEffect, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Workflow, Plus, Pencil, ChevronLeft, ChevronRight, RefreshCw, ArrowRight } from "lucide-react";
import { endpoints } from "../api/endpoints";
import { ApiError, NetworkError, TimeoutError } from "../api/client";
import { useSession } from "../api/sessionContext";
import { isMock } from "../api/dataSource";
import { mockScenarioPage, mockDevices } from "../mocks/data";
import { ScenarioForm } from "../components/ScenarioForm";
import { LoadingState, EmptyState, ErrorState, MockBadge } from "../components/States";
import type { Scenario, ScenarioWrite, Device, ScenarioPollResult } from "../api/types";
import styles from "./ScenariosCrudPage.module.css";

type Mode = "list" | "create" | "edit";

const POLL_LABEL: Record<string, string> = {
  PROCESSING: "Выполняется…",
  BASELINE_STORED: "Первое чтение сохранено как начальное",
  NO_CHANGE: "Значение не изменилось",
  RUN_CREATED: "Изменение — создан запуск",
  READ_ERROR: "Ошибка чтения показания",
  REJECTED: "Наблюдение не принято",
  UNKNOWN: "Результат неизвестен",
};

// Загрузка всех страниц устройств с поддержкой отмены
async function fetchAllDevices(signal?: AbortSignal): Promise<Device[]> {
  const all: Device[] = [];
  let page = 0;
  const size = 100;
  for (;;) {
    if (signal?.aborted) break;
    const resp = await endpoints.devices.list({ page, size }, signal);
    all.push(...resp.items);
    if (all.length >= resp.total || resp.items.length === 0) break;
    page++;
    if (page > 50) break;
  }
  return all;
}

export function ScenariosCrudPage() {
  const [mode, setMode] = useState<Mode>("list");
  const [editing, setEditing] = useState<Scenario | null>(null);
  const [freshData, setFreshData] = useState<(ScenarioWrite & { expectedVersion?: number }) | null>(null);
  const [page, setPage] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [conflictType, setConflictType] = useState<"version" | "cycle" | "read_only" | "validation" | null>(null);
  const [pollResults, setPollResults] = useState<Record<string, ScenarioPollResult | { error: string }>>({});
  const [watching, setWatching] = useState<Record<string, boolean>>({});
  const watchRef = useRef<Set<string>>(new Set());

  const mock = isMock();
  const { terminateSession, demo } = useSession();

  // Закрытие страницы и logout останавливают наблюдение.
  useEffect(() => () => { watchRef.current.clear(); }, []);

  const runPoll = useCallback(async (scenario: Scenario) => {
    try {
      const result = await endpoints.scenarios.poll(scenario.id, crypto.randomUUID());
      setPollResults(current => ({ ...current, [scenario.id]: result }));
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) terminateSession();
      const message = err instanceof ApiError
        ? (err.problem?.detail ?? `Ошибка ${err.status}`)
        : err instanceof Error ? err.message : "Ошибка запроса";
      setPollResults(current => ({ ...current, [scenario.id]: { error: message } }));
    }
  }, [terminateSession]);

  const toggleWatch = useCallback((scenario: Scenario) => {
    if (watchRef.current.has(scenario.id)) {
      watchRef.current.delete(scenario.id);
      setWatching(current => ({ ...current, [scenario.id]: false }));
      return;
    }
    watchRef.current.add(scenario.id);
    setWatching(current => ({ ...current, [scenario.id]: true }));
    const loop = async () => {
      // Следующий запрос — только после завершения предыдущего.
      while (watchRef.current.has(scenario.id)) {
        await runPoll(scenario);
        await new Promise(resolve => setTimeout(resolve, 3000));
      }
    };
    void loop();
  }, [runPoll]);

  const queryClient = useQueryClient();
  const pageSize = 20;

  const { data, isLoading, error, refetch, isFetching } = useQuery({
    queryKey: ["scenarios", page, pageSize],
    queryFn: ({ signal }) => endpoints.scenarios.list({ page, size: pageSize }, signal),
    enabled: !mock,
  });

  // Загрузка всех устройств
  const {
    data: devices,
    isLoading: devicesLoading,
    error: devicesError,
    refetch: refetchDevices,
  } = useQuery({
    queryKey: ["all-devices"],
    queryFn: ({ signal }) => fetchAllDevices(signal),
    enabled: !mock,
  });

  const scenarioPage = mock ? mockScenarioPage : data;
  const deviceList: Device[] = useMemo(() => mock ? mockDevices : devices ?? [], [mock, devices]);

  // Карта устройств для быстрого поиска названий
  const deviceMap = useMemo(() => {
    const map = new Map<string, Device>();
    for (const d of deviceList) map.set(d.id, d);
    return map;
  }, [deviceList]);

  const refresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ["scenarios"] });
  }, [queryClient]);

  // Загрузка свежей версии — только получение данных, без замены формы
  const handleReloadFresh = async () => {
    if (!editing) return;
    setSaving(true);
    try {
      const fresh = await endpoints.scenarios.get(editing.id);
      // Сохраняем свежие данные отдельно; форма покажет подтверждение
      setFreshData({
        title: fresh.title,
        enabled: fresh.enabled,
        executionMode: fresh.executionMode,
        trigger: fresh.trigger,
        action: fresh.action,
        expectedVersion: fresh.version,
      });
      setSaveError(null);
      setConflictType(null);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setSaveError("Сценарий удалён другим пользователем.");
        setMode("list");
        setEditing(null);
        return;
      }
      setSaveError("Не удалось загрузить актуальную версию.");
    } finally {
      setSaving(false);
    }
  };

  // Создание
  const handleCreate = async (data: ScenarioWrite) => {
    setSaving(true);
    setSaveError(null);
    setConflictType(null);
    try {
      await endpoints.scenarios.create(data);
      refresh();
      setMode("list");
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) { terminateSession(); return; }
        const code = err.problem?.code;
        if (code === "SCENARIO_CYCLE") { setConflictType("cycle"); setSaveError("Сценарий создаёт цикл."); return; }
        if (code === "SIGNAL_READ_ONLY") { setConflictType("read_only"); setSaveError("Сигнал действия доступен только для чтения."); return; }
        if (err.status === 422) { setConflictType("validation"); setSaveError(err.problem?.detail ?? "Ошибка валидации."); return; }
      }
      setSaveError(err instanceof Error ? err.message : "Ошибка сохранения");
    } finally {
      setSaving(false);
    }
  };

  // Редактирование
  const handleUpdate = async (data: ScenarioWrite & { expectedVersion?: number }) => {
    if (!editing) return;
    setSaving(true);
    setSaveError(null);
    setConflictType(null);
    try {
      await endpoints.scenarios.update(editing.id, {
        ...data,
        expectedVersion: data.expectedVersion ?? editing.version,
      });
      refresh();
      setMode("list");
      setEditing(null);
      setFreshData(null);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) { terminateSession(); return; }
        if (err.status === 409) { setConflictType("version"); setSaveError("Конфликт версии."); return; }
        const code = err.problem?.code;
        if (code === "SCENARIO_CYCLE") { setConflictType("cycle"); setSaveError("Сценарий создаёт цикл."); return; }
        if (code === "SIGNAL_READ_ONLY") { setConflictType("read_only"); setSaveError("Сигнал действия доступен только для чтения."); return; }
        if (err.status === 422) { setConflictType("validation"); setSaveError(err.problem?.detail ?? "Ошибка валидации."); return; }
      }
      setSaveError(err instanceof Error ? err.message : "Ошибка сохранения");
    } finally {
      setSaving(false);
    }
  };

  // Рендер
  if (isLoading && !mock) return <LoadingState />;
  if (error && !mock) {
    const desc =
      error instanceof ApiError
        ? error.status === 401 ? "Требуется авторизация" : error.problem?.detail ?? `Ошибка ${error.status}`
        : error instanceof TimeoutError ? "Превышено время ожидания"
        : error instanceof NetworkError ? "Сервер недоступен"
        : "Неизвестная ошибка";
    return <ErrorState title="Не удалось загрузить сценарии" description={desc} onRetry={() => refetch()} />;
  }

  // Форма
  if (mode === "create" || mode === "edit") {
    return (
      <div className={styles.page}>
        <ScenarioForm
          localOnly={demo}
          initial={mode === "edit" && editing ? {
            title: editing.title,
            enabled: editing.enabled,
            executionMode: editing.executionMode,
            trigger: editing.trigger,
            action: editing.action,
            expectedVersion: editing.version,
          } : undefined}
          freshData={freshData}
          devices={deviceList}
          devicesLoading={devicesLoading && !mock}
          devicesError={devicesError ? "Не удалось загрузить устройства." : null}
          onRetryDevices={() => refetchDevices()}
          onSubmit={mode === "edit" ? handleUpdate : handleCreate}
          onCancel={() => { setMode("list"); setEditing(null); setSaveError(null); setConflictType(null); setFreshData(null); }}
          loading={saving}
          error={saveError}
          conflictType={conflictType}
          onReloadFresh={handleReloadFresh}
        />
      </div>
    );
  }

  const items = scenarioPage?.items ?? [];
  const total = scenarioPage?.total ?? 0;
  const totalPages = Math.ceil(total / pageSize);

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <h1 className={styles.title}>Сценарии</h1>
          {mock && <MockBadge />}
          <p className={styles.cardMeta}>
            Исполнение правил выполняет наш backend. Закрытая страница не наблюдает показания Ujin —
            проверка запускается кнопкой или переключателем наблюдения на открытой странице.
          </p>
        </div>
        <div className={styles.headerActions}>
          <button
            type="button"
            className={`${styles.iconBtn} ${isFetching ? styles.iconBtnBusy : ""}`}
            onClick={() => void refetch()}
            disabled={isFetching}
            title="Обновить"
            aria-label="Обновить список сценариев"
          >
            <RefreshCw size={16} />
          </button>
          <button
            type="button"
            className={styles.primaryBtn}
            onClick={() => { setMode("create"); setSaveError(null); setConflictType(null); setFreshData(null); }}
            disabled={mock}
          >
            <Plus size={16} /> Создать сценарий
          </button>
        </div>
      </div>

      {mock && (
        <div className={styles.mockNotice}>
          Демо: для сохранения сценариев переключитесь на подключённые устройства.
        </div>
      )}

      {items.length === 0 ? (
        <EmptyState
          title="Сценарии не найдены"
          description={mock ? "Переключите источник на API" : "Создайте первый сценарий"}
        />
      ) : (
        <div className={styles.list}>
          {items.map((scenario) => {
            const triggerDevice = deviceMap.get(scenario.trigger.deviceId);
            const actionDevice = deviceMap.get(scenario.action.deviceId);
            return (
              <div key={scenario.id} className={styles.card}>
                <div className={styles.cardIcon}>
                  <Workflow size={20} />
                </div>
                <div className={styles.cardInfo}>
                  <span className={styles.cardTitle}>{scenario.title}</span>
                  <div className={styles.cardFlow}>
                    <span className={styles.flowBadge}>
                      {triggerDevice?.title ?? scenario.trigger.deviceId.slice(0, 8)}
                      .{scenario.trigger.signalKey}
                    </span>
                    <span className={styles.flowOp}>= {scenario.trigger.value}</span>
                    <ArrowRight size={12} className={styles.flowArrow} />
                    <span className={styles.flowBadge}>
                      {actionDevice?.title ?? scenario.action.deviceId.slice(0, 8)}
                      .{scenario.action.signalKey}
                    </span>
                    <span className={styles.flowOp}>= {scenario.action.value}</span>
                  </div>
                  <span className={styles.cardMeta}>
                    {scenario.executionMode} · {scenario.enabled ? "Включён" : "Выключен — черновик"} · v{scenario.version}
                  </span>
                  {scenario.executionMode === "UJIN" && !mock && !demo && (
                    <div className={styles.cardMeta}>
                      <button
                        type="button"
                        className={styles.iconBtn}
                        onClick={() => void runPoll(scenario)}
                        title="Проверить показание Ujin"
                      >
                        Проверить показание Ujin
                      </button>
                      <label>
                        <input
                          type="checkbox"
                          checked={watching[scenario.id] ?? false}
                          onChange={() => toggleWatch(scenario)}
                        />{" "}
                        Наблюдать Ujin
                      </label>
                      {pollResults[scenario.id] && (
                        <span role="status">
                          {"error" in pollResults[scenario.id]
                            ? `Проверка: ${(pollResults[scenario.id] as { error: string }).error}`
                            : `Проверка: ${POLL_LABEL[(pollResults[scenario.id] as ScenarioPollResult).status] ?? (pollResults[scenario.id] as ScenarioPollResult).status}` +
                              ((pollResults[scenario.id] as ScenarioPollResult).reason
                                ? ` (${(pollResults[scenario.id] as ScenarioPollResult).reason})`
                                : "")}
                        </span>
                      )}
                    </div>
                  )}
                </div>
                <div className={styles.cardActions}>
                  <button
                    type="button"
                    className={styles.iconBtn}
                    onClick={() => { setEditing(scenario); setMode("edit"); setSaveError(null); setConflictType(null); setFreshData(null); }}
                    disabled={mock}
                    title="Редактировать"
                  >
                    <Pencil size={16} />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Пагинация */}
      {totalPages > 1 && (
        <div className={styles.pagination}>
          <button
            type="button"
            className={styles.iconBtn}
            onClick={() => setPage(Math.max(0, page - 1))}
            disabled={page === 0}
          >
            <ChevronLeft size={16} />
          </button>
          <span className={styles.pageInfo}>
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            className={styles.iconBtn}
            onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
            disabled={page >= totalPages - 1}
          >
            <ChevronRight size={16} />
          </button>
        </div>
      )}
    </div>
  );
}
