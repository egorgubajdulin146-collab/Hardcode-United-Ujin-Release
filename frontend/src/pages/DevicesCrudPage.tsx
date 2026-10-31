import { useState, useCallback } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Cpu, Plus, Pencil, Trash2, AlertCircle, Loader2, ChevronLeft, ChevronRight, RefreshCw } from "lucide-react";
import { endpoints } from "../api/endpoints";
import { ApiError, NetworkError, TimeoutError } from "../api/client";
import { useSession } from "../api/sessionContext";
import { isMock } from "../api/dataSource";
import { mockDevicePage } from "../mocks/data";
import { DeviceForm } from "../components/DeviceForm";
import { LoadingState, EmptyState, ErrorState, MockBadge } from "../components/States";
import type { Device, DeviceWrite, DeviceDependencies } from "../api/types";
import styles from "./DevicesCrudPage.module.css";

type Mode = "list" | "create" | "edit";

export function DevicesCrudPage() {
  const [mode, setMode] = useState<Mode>("list");
  const [editing, setEditing] = useState<Device | null>(null);
  const [page, setPage] = useState(0);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<Device | null>(null);
  const [deleteDeps, setDeleteDeps] = useState<DeviceDependencies | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const mock = isMock();
  const { terminateSession } = useSession();
  const queryClient = useQueryClient();
  const pageSize = 20;

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ["devices", page, pageSize],
    queryFn: ({ signal }) => endpoints.devices.list({ page, size: pageSize }, signal),
    enabled: !mock,
  });

  const devicePage = mock ? mockDevicePage : data;

  const refresh = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ["devices"] });
  }, [queryClient]);

  // Создание
  const handleCreate = async (data: DeviceWrite) => {
    setSaving(true);
    setSaveError(null);
    try {
      await endpoints.devices.create(data);
      refresh();
      setMode("list");
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        terminateSession();
        return;
      }
      setSaveError(err instanceof Error ? err.message : "Ошибка сохранения");
    } finally {
      setSaving(false);
    }
  };

  // Редактирование
  const handleUpdate = async (data: DeviceWrite & { expectedVersion?: number }) => {
    if (!editing) return;
    setSaving(true);
    setSaveError(null);
    try {
      await endpoints.devices.update(editing.id, {
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
          setSaveError("Конфликт версии: устройство изменено другим пользователем. Загрузите актуальную версию.");
          return;
        }
      }
      setSaveError(err instanceof Error ? err.message : "Ошибка сохранения");
    } finally {
      setSaving(false);
    }
  };

  // Удаление
  const handleDeleteClick = async (device: Device) => {
    setDeleteTarget(device);
    setDeleteError(null);
    try {
      const deps = await endpoints.devices.getDependencies(device.id);
      setDeleteDeps(deps);
    } catch {
      setDeleteDeps(null);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setSaving(true);
    setDeleteError(null);
    try {
      await endpoints.devices.remove(deleteTarget.id, deleteTarget.version);
      refresh();
      setDeleteTarget(null);
      setDeleteDeps(null);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.status === 401) {
          terminateSession();
          return;
        }
        if (err.status === 409) {
          setDeleteError("Устройство используется в сценариях или изменено. Обновите страницу.");
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
    return <ErrorState title="Не удалось загрузить устройства" description={desc} onRetry={() => refetch()} />;
  }

  // Форма создания/редактирования
  if (mode === "create" || mode === "edit") {
    return (
      <div className={styles.page}>
        <DeviceForm
          initial={mode === "edit" && editing ? {
            title: editing.title,
            family: editing.family,
            signals: editing.signals,
            events: editing.events,
            expectedVersion: editing.version,
          } : undefined}
          onSubmit={mode === "edit" ? handleUpdate : handleCreate}
          onCancel={() => { setMode("list"); setEditing(null); setSaveError(null); }}
          loading={saving}
          error={saveError}
        />
      </div>
    );
  }

  const items = devicePage?.items ?? [];
  const total = devicePage?.total ?? 0;
  const totalPages = Math.ceil(total / pageSize);

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <h1 className={styles.title}>Устройства</h1>
          {mock && <MockBadge />}
        </div>
        <div className={styles.headerActions}>
          <button type="button" className={styles.iconBtn} onClick={() => refetch()} title="Обновить">
            <RefreshCw size={16} />
          </button>
          <button
            type="button"
            className={styles.primaryBtn}
            onClick={() => { setMode("create"); setSaveError(null); }}
            disabled={mock}
          >
            <Plus size={16} /> Добавить устройство
          </button>
        </div>
      </div>

      {mock && (
        <div className={styles.mockNotice}>
          Режим MOCK: данные синтетические. Создание/редактирование/удаление отключены.
        </div>
      )}

      {items.length === 0 ? (
        <EmptyState
          title="Устройства не найдены"
          description={mock ? "Переключите источник на API" : "Создайте первое устройство"}
        />
      ) : (
        <div className={styles.list}>
          {items.map((device) => (
            <div key={device.id} className={styles.card}>
              <div className={styles.cardIcon}>
                <Cpu size={20} />
              </div>
              <div className={styles.cardInfo}>
                <span className={styles.cardTitle}>{device.title}</span>
                <span className={styles.cardMeta}>
                  {device.family} · v{device.version} · {device.signals.length} сигналов
                  {device.events.length > 0 && ` · ${device.events.length} событий`}
                </span>
              </div>
              <div className={styles.cardActions}>
                <button
                  type="button"
                  className={styles.iconBtn}
                  onClick={() => { setEditing(device); setMode("edit"); setSaveError(null); }}
                  disabled={mock}
                  title="Редактировать"
                >
                  <Pencil size={16} />
                </button>
                <button
                  type="button"
                  className={`${styles.iconBtn} ${styles.iconBtnDanger}`}
                  onClick={() => handleDeleteClick(device)}
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
            <h2 className={styles.dialogTitle}>Удалить устройство?</h2>
            <p className={styles.dialogText}>
              «{deleteTarget.title}» будет удалено безвозвратно.
            </p>
            {deleteDeps && deleteDeps.scenarioIds.length > 0 && (
              <div className={styles.dialogWarning}>
                <AlertCircle size={16} />
                <span>
                  Устройство используется в {deleteDeps.scenarioIds.length} сценариях.
                  Удаление отклонено сервером.
                </span>
              </div>
            )}
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
                onClick={() => { setDeleteTarget(null); setDeleteDeps(null); setDeleteError(null); }}
                disabled={saving}
              >
                Отмена
              </button>
              <button
                type="button"
                className={styles.dangerBtn}
                onClick={handleDeleteConfirm}
                disabled={saving || (deleteDeps !== null && deleteDeps.scenarioIds.length > 0)}
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
