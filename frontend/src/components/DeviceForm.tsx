import { useState, type FormEvent } from "react";
import { Plus, Trash2, AlertCircle, Loader2, X } from "lucide-react";
import type { DeviceWrite, SignalDefinition, EventDefinition } from "../api/types";
import styles from "./DeviceForm.module.css";

interface Props {
  initial?: DeviceWrite & { expectedVersion?: number };
  onSubmit: (data: DeviceWrite & { expectedVersion?: number }) => Promise<void>;
  onCancel: () => void;
  loading?: boolean;
  error?: string | null;
}

const EMPTY_SIGNAL: SignalDefinition = {
  key: "",
  title: "",
  group: "property",
  management: "r",
  valueType: "NUMBER",
  control: "input",
};

const EMPTY_EVENT: EventDefinition = {
  key: "",
  title: "",
  signalKey: "",
  emission: "CHANGE",
};

export function DeviceForm({ initial, onSubmit, onCancel, loading, error }: Props) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [family, setFamily] = useState(initial?.family ?? "");
  const [signals, setSignals] = useState<SignalDefinition[]>(initial?.signals ?? []);
  const [events, setEvents] = useState<EventDefinition[]>(initial?.events ?? []);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    await onSubmit({
      title: title.trim(),
      family: family.trim(),
      signals,
      events,
      ...(initial?.expectedVersion ? { expectedVersion: initial.expectedVersion } : {}),
    });
  };

  const addSignal = () => setSignals([...signals, { ...EMPTY_SIGNAL }]);
  const removeSignal = (i: number) => setSignals(signals.filter((_, idx) => idx !== i));
  const updateSignal = (i: number, patch: Partial<SignalDefinition>) => {
    setSignals(signals.map((s, idx) => (idx === i ? { ...s, ...patch } : s)));
  };

  const addEvent = () => setEvents([...events, { ...EMPTY_EVENT }]);
  const removeEvent = (i: number) => setEvents(events.filter((_, idx) => idx !== i));
  const updateEvent = (i: number, patch: Partial<EventDefinition>) => {
    setEvents(events.map((e, idx) => (idx === i ? { ...e, ...patch } : e)));
  };

  return (
    <form onSubmit={handleSubmit} className={styles.form}>
      <div className={styles.header}>
        <h2 className={styles.title}>
          {initial?.expectedVersion ? "Редактировать устройство" : "Новое устройство"}
        </h2>
        <button type="button" className={styles.closeBtn} onClick={onCancel} aria-label="Закрыть">
          <X size={16} />
        </button>
      </div>

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

      <div className={styles.field}>
        <label className={styles.label}>Семейство</label>
        <input
          className={styles.input}
          value={family}
          onChange={(e) => setFamily(e.target.value)}
          placeholder="leak, light…"
          pattern="^[a-z][a-z0-9_]*$"
          maxLength={64}
          required
          disabled={loading}
        />
      </div>

      {/* Сигналы */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h3 className={styles.sectionTitle}>Сигналы ({signals.length})</h3>
          <button type="button" className={styles.addBtn} onClick={addSignal} disabled={loading}>
            <Plus size={14} /> Добавить
          </button>
        </div>
        {signals.map((s, i) => (
          <div key={i} className={styles.signalCard}>
            <div className={styles.signalRow}>
              <input
                className={styles.inputSmall}
                placeholder="key"
                value={s.key}
                onChange={(e) => updateSignal(i, { key: e.target.value })}
                pattern="^[a-z][a-z0-9_]*$"
                maxLength={64}
                required
                disabled={loading}
              />
              <input
                className={styles.inputSmall}
                placeholder="Название"
                value={s.title}
                onChange={(e) => updateSignal(i, { title: e.target.value })}
                maxLength={120}
                required
                disabled={loading}
              />
              <button
                type="button"
                className={styles.removeBtn}
                onClick={() => removeSignal(i)}
                disabled={loading}
                aria-label="Удалить сигнал"
              >
                <Trash2 size={14} />
              </button>
            </div>
            <div className={styles.signalRow}>
              <select
                className={styles.select}
                value={s.group}
                onChange={(e) => {
                  const group = e.target.value as SignalDefinition["group"];
                  const mgmt = group === "property" ? "r" : group === "setting" ? "rw" : s.management;
                  updateSignal(i, { group, management: mgmt as SignalDefinition["management"] });
                }}
                disabled={loading}
              >
                <option value="property">property</option>
                <option value="capability">capability</option>
                <option value="setting">setting</option>
              </select>
              <select
                className={styles.select}
                value={s.management}
                onChange={(e) => updateSignal(i, { management: e.target.value as SignalDefinition["management"] })}
                disabled={loading}
              >
                <option value="r">r</option>
                <option value="rw">rw</option>
                <option value="w">w</option>
              </select>
              <input
                className={styles.inputSmall}
                placeholder="control"
                value={s.control}
                onChange={(e) => updateSignal(i, { control: e.target.value })}
                maxLength={40}
                required
                disabled={loading}
              />
            </div>
            <div className={styles.signalRow}>
              <input
                className={styles.inputSmall}
                placeholder="Единицы"
                value={s.unit ?? ""}
                onChange={(e) => updateSignal(i, { unit: e.target.value || undefined })}
                maxLength={24}
                disabled={loading}
              />
              <input
                className={styles.inputSmall}
                placeholder="min"
                type="number"
                value={s.min ?? ""}
                onChange={(e) => updateSignal(i, { min: e.target.value ? Number(e.target.value) : undefined })}
                disabled={loading}
              />
              <input
                className={styles.inputSmall}
                placeholder="max"
                type="number"
                value={s.max ?? ""}
                onChange={(e) => updateSignal(i, { max: e.target.value ? Number(e.target.value) : undefined })}
                disabled={loading}
              />
            </div>
          </div>
        ))}
      </div>

      {/* События */}
      <div className={styles.section}>
        <div className={styles.sectionHeader}>
          <h3 className={styles.sectionTitle}>События ({events.length})</h3>
          <button type="button" className={styles.addBtn} onClick={addEvent} disabled={loading}>
            <Plus size={14} /> Добавить
          </button>
        </div>
        {events.map((e, i) => (
          <div key={i} className={styles.signalCard}>
            <div className={styles.signalRow}>
              <input
                className={styles.inputSmall}
                placeholder="key"
                value={e.key}
                onChange={(ev) => updateEvent(i, { key: ev.target.value })}
                pattern="^[a-z][a-z0-9_]*$"
                maxLength={64}
                required
                disabled={loading}
              />
              <input
                className={styles.inputSmall}
                placeholder="Название"
                value={e.title}
                onChange={(ev) => updateEvent(i, { title: ev.target.value })}
                maxLength={120}
                required
                disabled={loading}
              />
              <button
                type="button"
                className={styles.removeBtn}
                onClick={() => removeEvent(i)}
                disabled={loading}
                aria-label="Удалить событие"
              >
                <Trash2 size={14} />
              </button>
            </div>
            <div className={styles.signalRow}>
              <select
                className={styles.select}
                value={e.signalKey}
                onChange={(ev) => updateEvent(i, { signalKey: ev.target.value })}
                disabled={loading}
              >
                <option value="">— сигнал —</option>
                {signals.map((s) => (
                  <option key={s.key} value={s.key}>{s.key}</option>
                ))}
              </select>
            </div>
          </div>
        ))}
      </div>

      {error && (
        <div className={styles.error} role="alert">
          <AlertCircle size={16} />
          <span>{error}</span>
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
