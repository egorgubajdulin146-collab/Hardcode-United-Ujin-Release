import { useState, useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Workflow, Plus, Pencil, Trash2, AlertCircle, Loader2, ChevronLeft, ChevronRight, RefreshCw, ArrowRight } from "lucide-react";
import { endpoints } from "../api/endpoints";
import { api, ApiError, NetworkError, TimeoutError } from "../api/client";
import { useSession } from "../api/sessionContext";
import { isMock } from "../api/dataSource";
import { mockScenarioPage, mockDevices } from "../mocks/data";
import { ScenarioForm } from "../components/ScenarioForm";
import { LoadingState, EmptyState, ErrorState, MockBadge } from "../components/States";
import type { Scenario, ScenarioWrite, Device } from "../api/types";
import styles from "./ScenariosCrudPage.module.css";

type Mode = "list" | "create" | "edit";

export function ScenariosCrudPage() {
  const [mode, setMode] = useState<Mode>("list");
  const [editing, setEditing] = useState<Scenario | null>(null);
  const [page, setPage] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [conflictType, setConflictType] = useState<"version" | "cycle" | "read_only" | "validation" | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Scenario | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const mock = isMock();
  const { terminateSession } = useSession();
  const queryClient = useQueryClient();
  const pageSize = 20;

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["scenarios", page, pageSize],
    queryFn: ({ signal }) => endpoints.scenarios.list({ page, size: pageSize }, signal),
    enabled: !mock,
  });

  // Загружаем устройства для формы
  const { data: devicesData } = useQuery({
    queryKey: ["devices-for-scenarios"],
    queryFn: ({ signal }) => endpoints.devices.list({ size: 100 }, signal),
    enabled: !mock,
  });

  const scenarioPage = mock ? mockScenarioPage : data;
  const devices: Device[] = mock ? mockDevices : devicesData?.items ?? [];

  const refresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ["scenarios"] });
  }, [queryClient]);

  // Загрузка свежей версии при конфликте
  const handleReloadFresh = async () => {
    if (!editing) return;
    setSaving(true);
    try {
      const fresh = await endpoints.scenarios.get(editing.id);
      setEditing(fresh);
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
        if (err.status === 401) {
          terminateSession();
          return;
        }
        const code = err.problem?.code;
        if (code === "SCENARIO_CYCLE") {
          setConflictType("cycle");
          setSaveError("Сценарий создаёт цикл. Измените устройство или сигнал.");
          return;
        }
        if (code === "SIGNAL_READ_ONLY") {
          setConflictType("read_only");
          setSaveError("Сигнал действия доступен только для чтения. Выберите управляемый сигнал.");
          return;
        }
        if (err.status === 422) {
          setConflictType("validation");
          setSaveError(err.problem?.detail ?? "Ошибка валидации.");
          return;
        }
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
        expectedVersion: editing.version,
      });
      refresh();
      setMode("list");
      setEditing(null);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          terminateSession();
          return;
        }
        if (err.status === 409) {
          setConflictType("version");
          setSaveError("Конфликт версии: сценарий изменён другим пользователем.");
          return;
        }
        const code = err.problem?.code;
        if (code === "SCENARIO_CYCLE") {
          setConflictType("cycle");
          setSaveError("Сценарий создаёт цикл. Измените устройство или сигнал.");
          return;
        }
        if (code === "SIGNAL_READ_ONLY") {
          setConflictType("read_only");
          setSaveError("Сигнал действия доступен только для чтения.");
          return;
        }
        if (err.status === 422) {
          setConflictType("validation");
          setSaveError(err.problem?.detail ?? "Ошибка валидации.");
          return;
        }
      }
      setSaveError(err instanceof Error ? err.message : "Ошибка сохранения");
    } finally {
      setSaving(false);
    }
  };

  // Удаление
  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setSaving(true);
    setDeleteError(null);
    try {
      await api.delete(`/api/v1/scenarios/${deleteTarget.id}?expectedVersion=${deleteTarget.version}`);
      refresh();
      setDeleteTarget(null);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          terminateSession();
          return;
        }
        if (err.status === 409) {
          setDeleteError("Сценарий изменён. Обновите страницу.");
          return;
        }
      }
      setDeleteError(err instanceof Error ? err.message : "Ошибка удаления");
    } finally {
      setSaving(false);
    }
  };

  // Рендер
  if (isLoading && !mock) return <LoadingState />;
  if (error && !mock) {
    const desc =
      error instanceof ApiError
        ? error.status === 401
          ? "Требуется авторизация"
          : error.problem?.detail ?? `Ошибка ${error.status}`
        : error instanceof TimeoutError
          ? "Превышено время ожидания"
          : error instanceof NetworkError
            ? "Сервер недоступен"
            : "Неизвестная ошибка";
    return <ErrorState title="Не удалось загрузить сценарии" description={desc} onRetry={() => refetch()} />;
  }

  // Форма
  if (mode === "create" || mode === "edit") {
    return (
      <div className={styles.page}>
        <ScenarioForm
          initial={mode === "edit" && editing ? {
            title: editing.title,
            enabled: editing.enabled,
            executionMode: editing.executionMode,
            trigger: editing.trigger,
            action: editing.action,
            expectedVersion: editing.version,
          } : undefined}
          devices={devices}
          onSubmit={mode === "edit" ? handleUpdate : handleCreate}
          onCancel={() => { setMode("list"); setEditing(null); setSaveError(null); setConflictType(null); }}
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
        </div>
        <div className={styles.headerActions}>
          <button type="button" className={styles.iconBtn} onClick={() => refetch()} title="Обновить">
            <RefreshCw size={16} />
          </button>
          <button
            type="button"
            className={styles.primaryBtn}
            onClick={() => { setMode("create"); setSaveError(null); setConflictType(null); }}
            disabled={mock}
          >
            <Plus size={16} /> Создать сценарий
          </button>
        </div>
      </div>

      {mock && (
        <div className={styles.mockNotice}>
          Режим MOCK: данные синтетические. Создание/редактирование отключены.
        </div>
      )}

      {items.length === 0 ? (
        <EmptyState
          title="Сценарии не найдены"
          description={mock ? "Переключите источник на API" : "Создайте первый сценарий"}
        />
      ) : (
        <div className={styles.list}>
          {items.map((scenario) => (
            <div key={scenario.id} className={styles.card}>
              <div className={styles.cardIcon}>
                <Workflow size={20} />
              </div>
              <div className={styles.cardInfo}>
                <span className={styles.cardTitle}>{scenario.title}</span>
                <div className={styles.cardFlow}>
                  <span className={styles.flowBadge}>
                    {scenario.trigger.deviceId.slice(0, 8)}….{scenario.trigger.signalKey}
                  </span>
                  <span className={styles.flowOp}>= {scenario.trigger.value}</span>
                  <ArrowRight size={12} className={styles.flowArrow} />
                  <span className={styles.flowBadge}>
                    {scenario.action.deviceId.slice(0, 8)}….{scenario.action.signalKey}
                  </span>
                  <span className={styles.flowOp}>= {scenario.action.value}</span>
                </div>
                <span className={styles.cardMeta}>
                  {scenario.executionMode} · {scenario.enabled ? "Включён" : "Выключен"} · v{scenario.version}
                </span>
              </div>
              <div className={styles.cardActions}>
                <button
                  type="button"
                  className={styles.iconBtn}
                  onClick={() => { setEditing(scenario); setMode("edit"); setSaveError(null); setConflictType(null); }}
                  disabled={mock}
                  title="Редактировать"
                >
                  <Pencil size={16} />
                </button>
                <button
                  type="button"
                  className={`${styles.iconBtn} ${styles.iconBtnDanger}`}
                  onClick={() => { setDeleteTarget(scenario); setDeleteError(null); }}
                  disabled={mock}
                  title="Удалить"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            </div>
          ))}
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

      {/* Диалог удаления */}
      {deleteTarget && (
        <div className={styles.dialogOverlay}>
          <div className={styles.dialog}>
            <h2 className={styles.dialogTitle}>Удалить сценарий?</h2>
            <p className={styles.dialogText}>
              «{deleteTarget.title}» будет удалён безвозвратно.
            </p>
            {deleteError && (
              <div className={styles.dialogError} role="alert">
                <AlertCircle size={16} />
                <span>{deleteError}</span>
              </div>
            )}
            <div className={styles.dialogActions}>
              <button
                type="button"
                className={styles.cancelBtn}
                onClick={() => { setDeleteTarget(null); setDeleteError(null); }}
                disabled={saving}
              >
                Отмена
              </button>
              <button
                type="button"
                className={styles.dangerBtn}
                onClick={handleDeleteConfirm}
                disabled={saving}
              >
                {saving ? <Loader2 size={16} className={styles.spinner} /> : null}
                Удалить
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
