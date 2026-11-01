import { useState, useMemo, useRef, type FormEvent } from "react";
import { X, AlertCircle, Loader2, ArrowRight, Download, RefreshCw } from "lucide-react";
import type { ScenarioWrite, Device, Trigger, Action, SignalDefinition } from "../api/types";
import styles from "./ScenarioForm.module.css";
import { SignalValueField } from "./SignalValueField";

interface Props {
  localOnly?: boolean;
  initial?: ScenarioWrite & { expectedVersion?: number };
  freshData?: (ScenarioWrite & { expectedVersion?: number }) | null;
  devices: Device[];
  devicesLoading?: boolean;
  devicesError?: string | null;
  onRetryDevices?: () => void;
  onSubmit: (data: ScenarioWrite & { expectedVersion?: number }) => Promise<void>;
  onCancel: () => void;
  loading?: boolean;
  error?: string | null;
  conflictType?: "version" | "cycle" | "read_only" | "validation" | null;
  onReloadFresh?: () => void;
}

export function ScenarioForm({
  localOnly = false, initial, freshData, devices, devicesLoading, devicesError, onRetryDevices,
  onSubmit, onCancel, loading, error, conflictType, onReloadFresh,
}: Props) {
  // Стабильная инициализация — useRef для начального значения
  const initRef = useRef(initial);
  const [title, setTitle] = useState(initial?.title ?? "");
  const [enabled, setEnabled] = useState(initial?.enabled ?? true);
  const [executionMode, setExecutionMode] = useState(initial?.executionMode ?? "LOCAL");
  const [triggerDeviceId, setTriggerDeviceId] = useState(initial?.trigger?.deviceId ?? "");
  const [triggerSignalKey, setTriggerSignalKey] = useState(initial?.trigger?.signalKey ?? "");
  const [triggerEventKey, setTriggerEventKey] = useState(initial?.trigger?.eventKey ?? "");
  const [triggerValue, setTriggerValue] = useState<string>(
    initial?.trigger?.value !== undefined ? String(initial.trigger.value) : ""
  );
  const [actionDeviceId, setActionDeviceId] = useState(initial?.action?.deviceId ?? "");
  const [actionSignalKey, setActionSignalKey] = useState(initial?.action?.signalKey ?? "");
  const [actionValue, setActionValue] = useState<string>(
    initial?.action?.value !== undefined ? String(initial.action.value) : ""
  );

  // Подтверждение замены формы свежими данными
  const [pendingFresh, setPendingFresh] = useState<typeof freshData>(null);

  // Применение свежих данных — только по подтверждению, без повторного GET
  const applyFresh = () => {
    if (!pendingFresh) return;
    setTitle(pendingFresh.title ?? "");
    setEnabled(pendingFresh.enabled ?? true);
    setExecutionMode(pendingFresh.executionMode ?? "LOCAL");
    setTriggerDeviceId(pendingFresh.trigger?.deviceId ?? "");
    setTriggerSignalKey(pendingFresh.trigger?.signalKey ?? "");
    setTriggerEventKey(pendingFresh.trigger?.eventKey ?? "");
    setTriggerValue(pendingFresh.trigger?.value !== undefined ? String(pendingFresh.trigger.value) : "");
    setActionDeviceId(pendingFresh.action?.deviceId ?? "");
    setActionSignalKey(pendingFresh.action?.signalKey ?? "");
    setActionValue(pendingFresh.action?.value !== undefined ? String(pendingFresh.action.value) : "");
    // Заменяем базовую версию вместе с полями
    initRef.current = pendingFresh;
    setPendingFresh(null);
    // НЕ вызываем onReloadFresh — данные уже применены
  };

  const keepCurrent = () => {
    setPendingFresh(null);
  };

  // Когда пришли свежие данные — показываем подтверждение
  const freshRef = useRef<typeof freshData>(null);
  if (freshData && freshData !== freshRef.current) {
    freshRef.current = freshData;
    if (freshData !== initRef.current) {
      setPendingFresh(freshData);
    }
  }

  // Устройства
  const triggerDevice = useMemo(() => devices.find((d) => d.id === triggerDeviceId), [devices, triggerDeviceId]);
  const actionDevice = useMemo(() => devices.find((d) => d.id === actionDeviceId), [devices, actionDeviceId]);

  const triggerSignals = useMemo(
    () => triggerDevice?.signals.filter((s) => s.management === "r" || s.management === "rw") ?? [],
    [triggerDevice]
  );
  const actionSignals = useMemo(
    () => actionDevice?.signals.filter((s) => s.management === "rw" || s.management === "w") ?? [],
    [actionDevice]
  );
  const triggerEvents = useMemo(
    () => triggerDevice?.events.filter((e) => e.signalKey === triggerSignalKey) ?? [],
    [triggerDevice, triggerSignalKey]
  );

  const handleTriggerDeviceChange = (deviceId: string) => {
    setTriggerDeviceId(deviceId);
    setTriggerSignalKey("");
    setTriggerEventKey("");
    setTriggerValue("");
  };

  const handleTriggerSignalChange = (signalKey: string) => {
    setTriggerSignalKey(signalKey);
    setTriggerEventKey("");
    // Очищаем значение, если не входит в новый диапазон
    const sig = triggerSignals.find((s) => s.key === signalKey);
    if (sig && triggerValue !== "") {
      const num = Number(triggerValue);
      if (!isNaN(num)) {
        const outOfRange =
          (sig.min !== undefined && num < sig.min) ||
          (sig.max !== undefined && num > sig.max) ||
          (sig.enumValues && !sig.enumValues.includes(num));
        if (outOfRange) setTriggerValue("");
      }
    }
  };

  const handleActionDeviceChange = (deviceId: string) => {
    setActionDeviceId(deviceId);
    setActionSignalKey("");
    setActionValue("");
  };

  const handleActionSignalChange = (signalKey: string) => {
    setActionSignalKey(signalKey);
    // Очищаем значение, если не входит в новый диапазон
    const sig = actionSignals.find((s) => s.key === signalKey);
    if (sig && actionValue !== "") {
      const num = Number(actionValue);
      if (!isNaN(num)) {
        const outOfRange =
          (sig.min !== undefined && num < sig.min) ||
          (sig.max !== undefined && num > sig.max) ||
          (sig.enumValues && !sig.enumValues.includes(num));
        if (outOfRange) setActionValue("");
      }
    }
  };

  const triggerSignal = triggerSignals.find((s) => s.key === triggerSignalKey);
  const actionSignal = actionSignals.find((s) => s.key === actionSignalKey);

  // Подсказка по диапазону
  const rangeHint = (sig?: SignalDefinition) => {
    if (!sig) return null;
    if (sig.enumValues?.length) return `Допустимые: ${sig.enumValues.join(", ")}`;
    if (sig.min !== undefined && sig.max !== undefined) return `Диапазон: ${sig.min} … ${sig.max}`;
    if (sig.min !== undefined) return `От ${sig.min}`;
    if (sig.max !== undefined) return `До ${sig.max}`;
    return null;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if ((localOnly && executionMode !== "LOCAL") || (executionMode === "UJIN" && enabled)) return;
    const trigger: Trigger = {
      deviceId: triggerDeviceId,
      signalKey: triggerSignalKey,
      operator: "EQ",
      value: Number(triggerValue),
      eventKey: triggerEventKey,
    };
    const action: Action = {
      deviceId: actionDeviceId,
      signalKey: actionSignalKey,
      value: Number(actionValue),
    };
    await onSubmit({
      title: title.trim(),
      enabled,
      executionMode: executionMode as "LOCAL" | "UJIN",
      trigger,
      action,
      ...(initRef.current?.expectedVersion ? { expectedVersion: initRef.current.expectedVersion } : {}),
    });
  };

  // Если устройства не загружены — показываем состояние загрузки/ошибки
  if (devicesLoading) {
    return (
      <div className={styles.form}>
        <div className={styles.loadingState}>
          <Loader2 size={24} className={styles.spinner} />
          <span>Загрузка устройств…</span>
        </div>
      </div>
    );
  }

  if (devicesError) {
    return (
      <div className={styles.form}>
        <div className={styles.errorState}>
          <AlertCircle size={24} />
          <span>{devicesError}</span>
          {onRetryDevices && (
            <button type="button" className={styles.reloadBtn} onClick={onRetryDevices}>
              <RefreshCw size={14} /> Повторить
            </button>
          )}
        </div>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className={styles.form}>
      <div className={styles.header}>
        <h2 className={styles.title}>
          {initRef.current?.expectedVersion ? "Редактировать сценарий" : "Новый сценарий"}
        </h2>
        <button type="button" className={styles.closeBtn} onClick={onCancel} aria-label="Закрыть">
          <X size={16} />
        </button>
      </div>

      {/* Подтверждение замены */}
      {pendingFresh && (
        <div className={styles.confirmBar} role="alert">
          <AlertCircle size={16} />
          <span>Загружена актуальная версия. Заменить текущие данные?</span>
          <button type="button" className={styles.confirmBtn} onClick={applyFresh}>
            Заменить
          </button>
          <button type="button" className={styles.keepBtn} onClick={keepCurrent}>
            Оставить
          </button>
        </div>
      )}

      <div className={styles.field}>
        <label className={styles.label} htmlFor="scenario-title">Название сценария</label>
        <input
          id="scenario-title"
          placeholder="Например: При протечке включить свет"
          className={styles.input}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={120}
          required
          disabled={loading}
        />
      </div>

      <div className={styles.row}>
        <div className={styles.field}>
          <label className={styles.label} htmlFor="scenario-mode">Где выполнять сценарий</label>
          <select
            id="scenario-mode"
            aria-describedby="scenario-mode-help"
            className={styles.select}
            value={executionMode}
            onChange={(e) => {
              setExecutionMode(e.target.value as "LOCAL" | "UJIN");
              if (e.target.value === "UJIN") setEnabled(false);
            }}
            disabled={loading}
          >
            <option value="LOCAL">Локально — на эмуляторах</option>
            <option value="UJIN" disabled={localOnly}>{localOnly ? "Ujin недоступен в гостевом демо" : "Через Ujin — пока только черновик"}</option>
          </select>
        </div>
        <div className={styles.field}>
          <span className={styles.label}>Сценарий включён</span>
          <label className={styles.toggle}>
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
              disabled={loading || (executionMode === "UJIN" && !enabled)}
            />
            <span>{enabled ? "Да — реагировать на события" : "Нет — сохранить без запуска"}</span>
          </label>
        </div>
      </div>

      <p id="scenario-mode-help" className={styles.enumHint}>Локальный режим изменяет эмуляторы в приложении. Он не отправляет команды реальному оборудованию.</p>
      <p className={styles.enumHint}>Пример: когда изменится «Протечка», если значение — «1 — Протечка», включить свет на лампе.</p>

      {executionMode === "UJIN" && <p role="status">
        Исполнение Ujin ещё не подключено. Сохраните выключенный черновик или выберите LOCAL.
        {enabled && " Ранее включённый сценарий не исполняется; выключите его перед сохранением."}
      </p>}
      {devices.length === 0 && <p role="status">Сначала добавьте устройства в разделе «Устройства». Для первого сценария подойдут шаблоны датчика протечки и лампы.</p>}
      {/* Когда → Если → Тогда */}
      <div className={styles.flowSection}>
        <h3 className={styles.flowTitle}>Условие срабатывания</h3>

        <div className={styles.flowBlock}>
          <span className={styles.flowLabel}>Когда</span>
          <div className={styles.flowFields}>
            <label className={styles.field}><span className={styles.label}>Устройство-источник</span><select
              className={styles.select}
              aria-label="Устройство-источник"
              value={triggerDeviceId}
              onChange={(e) => handleTriggerDeviceChange(e.target.value)}
              disabled={loading}
              required
            >
              <option value="">— устройство —</option>
              {devices.map((d) => (
                <option key={d.id} value={d.id}>{d.title}</option>
              ))}
            </select></label>
            <label className={styles.field}><span className={styles.label}>Какое показание отслеживать</span><select
              className={styles.select}
              aria-label="Какое показание отслеживать"
              value={triggerSignalKey}
              onChange={(e) => handleTriggerSignalChange(e.target.value)}
              disabled={loading || !triggerDevice}
              required
            >
              <option value="">— сигнал —</option>
              {triggerSignals.map((s) => (
                <option key={s.key} value={s.key}>{s.title} ({s.key})</option>
              ))}
            </select></label>
            <label className={styles.field}><span className={styles.label}>Какое событие отслеживать</span><select
              className={styles.select}
              aria-label="Какое событие отслеживать"
              value={triggerEventKey}
              onChange={(e) => setTriggerEventKey(e.target.value)}
              disabled={loading || !triggerSignalKey || triggerEvents.length === 0}
              required
            >
              <option value="">— выберите событие изменения —</option>
              {triggerEvents.map((e) => (
                <option key={e.key} value={e.key}>{e.title} ({e.key})</option>
              ))}
            </select></label>
          </div>
        </div>

        {triggerDevice && !triggerSignalKey && <p className={styles.enumHint}>Сначала выберите показание этого устройства.</p>}
        {triggerSignalKey && triggerEvents.length === 0 && <p className={styles.enumHint}>Для этого показания нет события изменения. Добавьте его при редактировании устройства или выберите другой сигнал.</p>}
        <ArrowRight size={16} className={styles.flowArrow} />

        <div className={styles.flowBlock}>
          <span className={styles.flowLabel}>Если</span>
          <div className={styles.flowFields}>
            <span className={styles.flowStatic}>{triggerSignal?.title || "Выбранное показание"}</span>
            <span className={styles.flowStatic}>равно</span>
            <SignalValueField className={styles.inputSmall} signal={triggerSignal} family={triggerDevice?.family} label="Значение для срабатывания" value={triggerValue} onChange={setTriggerValue} disabled={loading || !triggerSignal} />
          </div>
          {rangeHint(triggerSignal) && (
            <div className={styles.enumHint}>{rangeHint(triggerSignal)}</div>
          )}
        </div>

        <ArrowRight size={16} className={styles.flowArrow} />

        <div className={styles.flowBlock}>
          <span className={styles.flowLabel}>Тогда</span>
          <div className={styles.flowFields}>
            <label className={styles.field}><span className={styles.label}>Устройство для действия</span><select
              className={styles.select}
              aria-label="Устройство для действия"
              value={actionDeviceId}
              onChange={(e) => handleActionDeviceChange(e.target.value)}
              disabled={loading}
              required
            >
              <option value="">— устройство —</option>
              {devices.map((d) => (
                <option key={d.id} value={d.id}>{d.title}</option>
              ))}
            </select></label>
            <label className={styles.field}><span className={styles.label}>Что изменить</span><select
              className={styles.select}
              aria-label="Что изменить"
              value={actionSignalKey}
              onChange={(e) => handleActionSignalChange(e.target.value)}
              disabled={loading || !actionDevice}
              required
            >
              <option value="">— сигнал для записи —</option>
              {actionSignals.map((s) => (
                <option key={s.key} value={s.key}>{s.title} ({s.key})</option>
              ))}
            </select></label>
            <SignalValueField className={styles.inputSmall} signal={actionSignal} family={actionDevice?.family} label="Установить значение" value={actionValue} onChange={setActionValue} disabled={loading || !actionSignal} />
          </div>
          {rangeHint(actionSignal) && (
            <div className={styles.enumHint}>{rangeHint(actionSignal)}</div>
          )}
        </div>
      </div>

      {error && (
        <div className={styles.error} role="alert">
          <AlertCircle size={16} />
          <div className={styles.errorContent}>
            <span>{error}</span>
            {conflictType === "version" && onReloadFresh && (
              <button
                type="button"
                className={styles.reloadBtn}
                onClick={onReloadFresh}
                disabled={loading}
              >
                <Download size={14} /> Загрузить актуальную версию
              </button>
            )}
          </div>
        </div>
      )}

      <div className={styles.actions}>
        <button type="button" className={styles.cancelBtn} onClick={onCancel} disabled={loading}>
          Отмена
        </button>
        <button type="submit" className={styles.submitBtn} disabled={loading || (executionMode === "UJIN" && enabled)}>
          {loading ? <Loader2 size={16} className={styles.spinner} /> : null}
          {initRef.current?.expectedVersion ? "Сохранить" : "Создать"}
        </button>
      </div>
    </form>
  );
}
