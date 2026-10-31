import { useState, useEffect, useMemo, type FormEvent } from "react";
import { X, AlertCircle, Loader2, ArrowRight, Download } from "lucide-react";
import type { ScenarioWrite, Device, Trigger, Action } from "../api/types";
import styles from "./ScenarioForm.module.css";

interface Props {
  initial?: ScenarioWrite & { expectedVersion?: number };
  devices: Device[];
  onSubmit: (data: ScenarioWrite & { expectedVersion?: number }) => Promise<void>;
  onCancel: () => void;
  loading?: boolean;
  error?: string | null;
  conflictType?: "version" | "cycle" | "read_only" | "validation" | null;
  onReloadFresh?: () => void;
}

export function ScenarioForm({ initial, devices, onSubmit, onCancel, loading, error, conflictType, onReloadFresh }: Props) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [enabled, setEnabled] = useState(initial?.enabled ?? true);
  const [executionMode, setExecutionMode] = useState(initial?.executionMode ?? "LOCAL");

  // Trigger
  const [triggerDeviceId, setTriggerDeviceId] = useState(initial?.trigger?.deviceId ?? "");
  const [triggerSignalKey, setTriggerSignalKey] = useState(initial?.trigger?.signalKey ?? "");
  const [triggerEventKey, setTriggerEventKey] = useState(initial?.trigger?.eventKey ?? "");
  const [triggerValue, setTriggerValue] = useState<string>(
    initial?.trigger?.value !== undefined ? String(initial.trigger.value) : ""
  );

  // Action
  const [actionDeviceId, setActionDeviceId] = useState(initial?.action?.deviceId ?? "");
  const [actionSignalKey, setActionSignalKey] = useState(initial?.action?.signalKey ?? "");
  const [actionValue, setActionValue] = useState<string>(
    initial?.action?.value !== undefined ? String(initial.action.value) : ""
  );

  // Обновление при смене initial
  useEffect(() => {
    if (initial) {
      setTitle(initial.title ?? "");
      setEnabled(initial.enabled ?? true);
      setExecutionMode(initial.executionMode ?? "LOCAL");
      setTriggerDeviceId(initial.trigger?.deviceId ?? "");
      setTriggerSignalKey(initial.trigger?.signalKey ?? "");
      setTriggerEventKey(initial.trigger?.eventKey ?? "");
      setTriggerValue(initial.trigger?.value !== undefined ? String(initial.trigger.value) : "");
      setActionDeviceId(initial.action?.deviceId ?? "");
      setActionSignalKey(initial.action?.signalKey ?? "");
      setActionValue(initial.action?.value !== undefined ? String(initial.action.value) : "");
    }
  }, [initial]);

  // Устройства
  const triggerDevice = useMemo(() => devices.find((d) => d.id === triggerDeviceId), [devices, triggerDeviceId]);
  const actionDevice = useMemo(() => devices.find((d) => d.id === actionDeviceId), [devices, actionDeviceId]);

  // Читаемые сигналы для триггера (r/rw)
  const triggerSignals = useMemo(
    () => triggerDevice?.signals.filter((s) => s.management === "r" || s.management === "rw") ?? [],
    [triggerDevice]
  );

  // Управляемые сигналы для действия (rw/w)
  const actionSignals = useMemo(
    () => actionDevice?.signals.filter((s) => s.management === "rw" || s.management === "w") ?? [],
    [actionDevice]
  );

  // События устройства-источника
  const triggerEvents = useMemo(
    () => triggerDevice?.events.filter((e) => e.signalKey === triggerSignalKey) ?? [],
    [triggerDevice, triggerSignalKey]
  );

  // Очистка несовместимых полей при смене устройства
  const handleTriggerDeviceChange = (deviceId: string) => {
    setTriggerDeviceId(deviceId);
    setTriggerSignalKey("");
    setTriggerEventKey("");
    setTriggerValue("");
  };

  const handleTriggerSignalChange = (signalKey: string) => {
    setTriggerSignalKey(signalKey);
    setTriggerEventKey("");
  };

  const handleActionDeviceChange = (deviceId: string) => {
    setActionDeviceId(deviceId);
    setActionSignalKey("");
    setActionValue("");
  };

  // Значения по умолчанию из enumValues
  const triggerSignal = triggerSignals.find((s) => s.key === triggerSignalKey);
  const actionSignal = actionSignals.find((s) => s.key === actionSignalKey);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

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
      ...(initial?.expectedVersion ? { expectedVersion: initial.expectedVersion } : {}),
    });
  };

  return (
    <form onSubmit={handleSubmit} className={styles.form}>
      <div className={styles.header}>
        <h2 className={styles.title}>
          {initial?.expectedVersion ? "Редактировать сценарий" : "Новый сценарий"}
        </h2>
        <button type="button" className={styles.closeBtn} onClick={onCancel} aria-label="Закрыть">
          <X size={16} />
        </button>
      </div>

      {/* Основное */}
      <div className={styles.field}>
        <label className={styles.label}>Название</label>
        <input
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
          <label className={styles.label}>Режим</label>
          <select
            className={styles.select}
            value={executionMode}
            onChange={(e) => setExecutionMode(e.target.value as "LOCAL" | "UJIN")}
            disabled={loading}
          >
            <option value="LOCAL">LOCAL</option>
            <option value="UJIN">UJIN</option>
          </select>
        </div>
        <div className={styles.field}>
          <label className={styles.label}>Состояние</label>
          <label className={styles.toggle}>
            <input
              type="checkbox"
              checked={enabled}
              onChange={(e) => setEnabled(e.target.checked)}
              disabled={loading}
            />
            <span>{enabled ? "Включён" : "Выключен"}</span>
          </label>
        </div>
      </div>

      {/* Когда → Если → Тогда */}
      <div className={styles.flowSection}>
        <h3 className={styles.flowTitle}>Условие срабатывания</h3>

        {/* Когда: событие */}
        <div className={styles.flowBlock}>
          <span className={styles.flowLabel}>Когда</span>
          <div className={styles.flowFields}>
            <select
              className={styles.select}
              value={triggerDeviceId}
              onChange={(e) => handleTriggerDeviceChange(e.target.value)}
              disabled={loading}
              required
            >
              <option value="">— устройство —</option>
              {devices.map((d) => (
                <option key={d.id} value={d.id}>{d.title}</option>
              ))}
            </select>
            <select
              className={styles.select}
              value={triggerSignalKey}
              onChange={(e) => handleTriggerSignalChange(e.target.value)}
              disabled={loading || !triggerDevice}
              required
            >
              <option value="">— сигнал —</option>
              {triggerSignals.map((s) => (
                <option key={s.key} value={s.key}>{s.key} ({s.title})</option>
              ))}
            </select>
            <select
              className={styles.select}
              value={triggerEventKey}
              onChange={(e) => setTriggerEventKey(e.target.value)}
              disabled={loading || !triggerDevice}
              required
            >
              <option value="">— событие —</option>
              {triggerEvents.map((e) => (
                <option key={e.key} value={e.key}>{e.key} ({e.title})</option>
              ))}
            </select>
          </div>
        </div>

        <ArrowRight size={16} className={styles.flowArrow} />

        {/* Если: условие */}
        <div className={styles.flowBlock}>
          <span className={styles.flowLabel}>Если</span>
          <div className={styles.flowFields}>
            <span className={styles.flowStatic}>{triggerSignalKey || "сигнал"}</span>
            <span className={styles.flowStatic}>=</span>
            <input
              className={styles.inputSmall}
              type="number"
              step="any"
              value={triggerValue}
              onChange={(e) => setTriggerValue(e.target.value)}
              placeholder="значение"
              disabled={loading}
              required
            />
          </div>
          {triggerSignal?.enumValues && triggerSignal.enumValues.length > 0 && (
            <div className={styles.enumHint}>
              Допустимые значения: {triggerSignal.enumValues.join(", ")}
            </div>
          )}
        </div>

        <ArrowRight size={16} className={styles.flowArrow} />

        {/* Тогда: действие */}
        <div className={styles.flowBlock}>
          <span className={styles.flowLabel}>Тогда</span>
          <div className={styles.flowFields}>
            <select
              className={styles.select}
              value={actionDeviceId}
              onChange={(e) => handleActionDeviceChange(e.target.value)}
              disabled={loading}
              required
            >
              <option value="">— устройство —</option>
              {devices.map((d) => (
                <option key={d.id} value={d.id}>{d.title}</option>
              ))}
            </select>
            <select
              className={styles.select}
              value={actionSignalKey}
              onChange={(e) => setActionSignalKey(e.target.value)}
              disabled={loading || !actionDevice}
              required
            >
              <option value="">— сигнал для записи —</option>
              {actionSignals.map((s) => (
                <option key={s.key} value={s.key}>{s.key} ({s.title})</option>
              ))}
            </select>
            <input
              className={styles.inputSmall}
              type="number"
              step="any"
              value={actionValue}
              onChange={(e) => setActionValue(e.target.value)}
              placeholder="значение"
              disabled={loading}
              required
            />
          </div>
          {actionSignal?.enumValues && actionSignal.enumValues.length > 0 && (
            <div className={styles.enumHint}>
              Допустимые значения: {actionSignal.enumValues.join(", ")}
            </div>
          )}
        </div>
      </div>

      {/* Ошибка и конфликт */}
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
        <button type="submit" className={styles.submitBtn} disabled={loading}>
          {loading ? <Loader2 size={16} className={styles.spinner} /> : null}
          {initial?.expectedVersion ? "Сохранить" : "Создать"}
        </button>
      </div>
    </form>
  );
}
