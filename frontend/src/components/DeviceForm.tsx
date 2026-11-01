import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { Plus, Trash2, AlertCircle, Loader2, X, Download } from "lucide-react";
import type { DeviceWrite, SignalDefinition, EventDefinition } from "../api/types";
import styles from "./DeviceForm.module.css";

interface Props {
  initial?: DeviceWrite & { expectedVersion?: number };
  onSubmit: (data: DeviceWrite & { expectedVersion?: number }) => Promise<void>;
  onCancel: () => void;
  loading?: boolean;
  error?: string | null;
  conflictType?: "version" | "in_use" | null;
  onReloadFresh?: () => void;
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

const GROUP_MGMT: Record<string, string[]> = {
  property: ["r"],
  capability: ["rw", "w"],
  setting: ["rw"],
};

const READABLE_MGMT = ["r", "rw"];
const CONTROL_LABELS: Record<string, string> = { switch: "Переключатель", range: "Регулятор", counter: "Счётчик", input: "Числовое поле", button: "Кнопка" };

const STARTER_TEMPLATES: DeviceWrite[] = [
  {
    title: "Датчик протечки", family: "leak",
    signals: [
      { key: "leak", title: "Протечка", group: "property", management: "r", valueType: "NUMBER", control: "switch", min: 0, max: 1, enumValues: [0, 1] },
      { key: "battery", title: "Батарея", group: "property", management: "r", valueType: "NUMBER", control: "counter", min: 0, max: 100 },
    ],
    events: [{ key: "leak_changed", title: "Изменение протечки", signalKey: "leak", emission: "CHANGE" }],
  },
  {
    title: "Сигнальная лампа", family: "light",
    signals: [
      { key: "lamp", title: "Свет", group: "capability", management: "rw", valueType: "NUMBER", control: "switch", min: 0, max: 1, enumValues: [0, 1] },
      { key: "brightness", title: "Яркость", group: "capability", management: "rw", valueType: "NUMBER", control: "range", min: 1, max: 100 },
      { key: "offline_timeout", title: "Таймер авто-выключения", group: "setting", management: "rw", valueType: "NUMBER", control: "counter", min: 0, max: 3600 },
    ],
    events: [],
  },
];

function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return <label className={styles.controlField}><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>;
}

export function DeviceForm({ initial, onSubmit, onCancel, loading, error, conflictType, onReloadFresh }: Props) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [family, setFamily] = useState(initial?.family ?? "");
  const [signals, setSignals] = useState<SignalDefinition[]>(initial?.signals ?? []);
  const [events, setEvents] = useState<EventDefinition[]>(initial?.events ?? []);
  // Сырой текст enumValues для каждого сигнала
  const [enumTexts, setEnumTexts] = useState<string[]>(() =>
    (initial?.signals ?? []).map((s) => s.enumValues?.join(", ") ?? "")
  );
  const [enumErrors, setEnumErrors] = useState<string[]>([]);

  // Escape закрывает форму, как и кнопка «Закрыть».
  useEffect(() => {
    if (typeof document === "undefined") return;
    const handler = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !loading) onCancel();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [loading, onCancel]);

  const templateAvailable = !loading && !title && !family && signals.length === 0 && events.length === 0;
  const applyTemplate = (template: DeviceWrite) => {
    if (!templateAvailable) return;
    setTitle(template.title);
    setFamily(template.family);
    setSignals(template.signals.map((signal) => ({ ...signal, enumValues: signal.enumValues?.slice() })));
    setEvents(template.events.map((event) => ({ ...event })));
    setEnumTexts(template.signals.map((signal) => signal.enumValues?.join(", ") ?? ""));
    setEnumErrors([]);
  };

  // Парсинг enumValues при сохранении
  const parseEnumValues = (text: string): { values?: number[]; error?: string } => {
    const trimmed = text.trim();
    if (!trimmed) return { values: undefined };
    const parts = trimmed.split(",").map((s) => s.trim());
    const numbers: number[] = [];
    for (const part of parts) {
      if (part === "") return { error: "Между запятыми должно быть число. Например: 0, 1" };
      const num = Number(part);
      if (!Number.isFinite(num)) return { error: `«${part}» — не число` };
      numbers.push(num);
    }
    return { values: numbers };
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();

    // Парсим enumValues и проверяем ошибки
    const parsedEnums = enumTexts.map(parseEnumValues);
    const errors = parsedEnums.map((p) => p.error ?? "");
    setEnumErrors(errors);
    if (errors.some(Boolean)) return;

    const finalSignals = signals.map((s, i) => ({
      ...s,
      enumValues: parsedEnums[i].values,
    }));

    await onSubmit({
      title: title.trim(),
      family: family.trim(),
      signals: finalSignals,
      events,
      ...(initial?.expectedVersion ? { expectedVersion: initial.expectedVersion } : {}),
    });
  };

  const addSignal = () => {
    setSignals([...signals, { ...EMPTY_SIGNAL }]);
    setEnumTexts([...enumTexts, ""]);
    setEnumErrors([...enumErrors, ""]);
  };

  const removeSignal = (i: number) => {
    const removed = signals[i];
    setSignals(signals.filter((_, idx) => idx !== i));
    setEnumTexts(enumTexts.filter((_, idx) => idx !== i));
    setEnumErrors(enumErrors.filter((_, idx) => idx !== i));
    setEvents(events.filter((e) => e.signalKey !== removed.key));
  };

  const updateSignal = (i: number, patch: Partial<SignalDefinition>) => {
    const old = signals[i];
    setSignals(signals.map((s, idx) => (idx === i ? { ...s, ...patch } : s)));

    // При переименовании обновляем ссылки в событиях
    if (patch.key !== undefined && patch.key !== old.key) {
      setEvents(events.map((e) =>
        e.signalKey === old.key ? { ...e, signalKey: patch.key! } : e
      ));
    }

    // При смене management на w — убираем события, ссылающиеся на этот сигнал
    if (patch.management === "w" && old.management !== "w") {
      setEvents(events.filter((e) => e.signalKey !== old.key));
    }
  };

  const addEvent = () => setEvents([...events, { ...EMPTY_EVENT }]);
  const removeEvent = (i: number) => setEvents(events.filter((_, idx) => idx !== i));
  const updateEvent = (i: number, patch: Partial<EventDefinition>) => {
    setEvents(events.map((e, idx) => (idx === i ? { ...e, ...patch } : e)));
  };

  const readableSignals = signals.filter((s) => READABLE_MGMT.includes(s.management));

  return (
    <form onSubmit={handleSubmit} className={styles.form}>
      <div className={styles.header}>
        <h2 className={styles.title}>
          {initial?.expectedVersion ? "Редактировать устройство" : "Новое устройство"}
        </h2>
        <button type="button" className={styles.closeBtn} onClick={onCancel} aria-label="Закрыть" disabled={loading}>
          <X size={16} />
        </button>
      </div>

      {!initial && (
        <div className={styles.section}>
          <span className={styles.label}>Начать с шаблона</span>
          <div className={styles.signalRow}>
            {STARTER_TEMPLATES.map((template) => (
              <button key={template.family} type="button" className={styles.addBtn}
                disabled={!templateAvailable} onClick={() => applyTemplate(template)}>
                {template.title}
              </button>
            ))}
          </div>
          <p className={styles.label}>
            Шаблон заполняет только пустую форму. Устройство появится в вашем каталоге после сохранения.
            Привязка к Ujin настраивается отдельно и не создаёт устройство на платформе.
          </p>
        </div>
      )}

      <div className={styles.field}>
        <label className={styles.label} htmlFor="device-title">Название устройства</label>
        <input
          id="device-title"
          placeholder="Например: Датчик протечки на кухне"
          className={styles.input}
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={120}
          required
          disabled={loading}
        />
      </div>

      <div className={styles.field}>
        <label className={styles.label} htmlFor="device-family">Тип устройства</label>
        <input
          id="device-family"
          list="device-families"
          aria-describedby="family-help"
          className={styles.input}
          value={family}
          onChange={(e) => setFamily(e.target.value)}
          placeholder="Выберите leak или light либо введите свой код"
          pattern="^[a-z][a-z0-9_]*$"
          maxLength={64}
          required
          disabled={loading}
        />
      </div>

      <datalist id="device-families"><option value="leak">Датчик протечки</option><option value="light">Свет / лампа</option></datalist>
      <p id="family-help" className={styles.label}>Тип задаётся латиницей: leak — протечка, light — свет. Для первого запуска проще выбрать шаблон выше. Свой тип описывает устройство, но не добавляет ему автоматическую физику.</p>

      {/* Сигналы */}
      <div className={styles.section}>
        <p className={styles.label}>Сигналы — показания, команды и настройки устройства. Например, «Протечка» принимает 0 (сухо) или 1 (протечка).</p>
        <div className={styles.sectionHeader}>
          <h3 className={styles.sectionTitle}>Сигналы ({signals.length})</h3>
          <button type="button" className={styles.addBtn} onClick={addSignal} disabled={loading}>
            <Plus size={14} /> Добавить
          </button>
        </div>
        {signals.map((s, i) => (
          <div key={i} className={styles.signalCard}>
            <div className={styles.signalRow}>
              <Field label="Код сигнала" hint="Латиницей без пробелов, например leak или lamp. Код используется в сценариях."><input
                className={styles.inputSmall}
                placeholder="Латиницей, без пробелов"
                value={s.key}
                onChange={(e) => updateSignal(i, { key: e.target.value })}
                pattern="^[a-z][a-z0-9_]*$"
                maxLength={64}
                required
                disabled={loading}
              /></Field>
              <Field label="Название сигнала" hint="Например: Протечка или Свет."><input
                className={styles.inputSmall}
                placeholder="Название"
                value={s.title}
                onChange={(e) => updateSignal(i, { title: e.target.value })}
                maxLength={120}
                required
                disabled={loading}
              /></Field>
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
              <Field label="Назначение сигнала"><select
                className={styles.select}
                value={s.group}
                onChange={(e) => {
                  const group = e.target.value as SignalDefinition["group"];
                  const allowed = GROUP_MGMT[group] ?? ["r"];
                  updateSignal(i, { group, management: allowed[0] as SignalDefinition["management"] });
                }}
                disabled={loading}
              >
                <option value="property">Показание датчика</option>
                <option value="capability">Управление устройством</option>
                <option value="setting">Настройка</option>
              </select></Field>
              <Field label="Что разрешено"><select
                className={styles.select}
                value={s.management}
                onChange={(e) => updateSignal(i, { management: e.target.value as SignalDefinition["management"] })}
                disabled={loading}
              >
                {(GROUP_MGMT[s.group] ?? ["r"]).map((m) => (
                  <option key={m} value={m}>{{ r: "Только чтение", rw: "Чтение и изменение", w: "Только команда" }[m]}</option>
                ))}
              </select></Field>
              <Field label="Вид элемента"><select
                className={styles.select}
                value={Object.keys(CONTROL_LABELS).includes(s.control) ? s.control : "__custom__"}
                onChange={(e) => updateSignal(i, { control: e.target.value === "__custom__" ? "" : e.target.value })}
                disabled={loading}
              >
                {Object.entries(CONTROL_LABELS).map(([code, label]) => <option key={code} value={code}>{label}</option>)}
                <option value="__custom__">Свой вариант…</option>
              </select></Field>
              {!Object.keys(CONTROL_LABELS).includes(s.control) && <Field label="Код своего элемента" hint="Технический код, согласованный для вашего устройства."><input className={styles.inputSmall} value={s.control} onChange={e => updateSignal(i, { control: e.target.value })} maxLength={40} required disabled={loading} /></Field>}
            </div>
            <div className={styles.signalRow}>
              <Field label="Единица измерения" hint="Необязательно. Например: °C, %, кВт·ч."><input
                className={styles.inputSmall}
                placeholder="Единицы"
                value={s.unit ?? ""}
                onChange={(e) => updateSignal(i, { unit: e.target.value || undefined })}
                maxLength={24}
                disabled={loading}
              /></Field>
              <Field label="Минимум" hint="Необязательно. Для переключателя — 0."><input
                className={styles.inputSmall}
                placeholder="Без ограничения"
                type="number"
                step="any"
                value={s.min ?? ""}
                onChange={(e) => updateSignal(i, { min: e.target.value ? Number(e.target.value) : undefined })}
                disabled={loading}
              /></Field>
              <Field label="Максимум" hint="Необязательно. Для переключателя — 1."><input
                className={styles.inputSmall}
                placeholder="Без ограничения"
                type="number"
                step="any"
                value={s.max ?? ""}
                onChange={(e) => updateSignal(i, { max: e.target.value ? Number(e.target.value) : undefined })}
                disabled={loading}
              /></Field>
            </div>
            {/* enumValues — сырой текст */}
            <div className={styles.signalRow}>
              <Field label="Разрешённые значения" hint="Необязательно. Числа через запятую: 0, 1. Пусто — любое число в диапазоне."><input
                className={`${styles.inputSmall} ${enumErrors[i] ? styles.inputError : ""}`}
                placeholder="Например: 0, 1"
                value={enumTexts[i] ?? ""}
                onChange={(e) => {
                  const newEnumTexts = [...enumTexts];
                  newEnumTexts[i] = e.target.value;
                  setEnumTexts(newEnumTexts);
                  // Очищаем ошибку при вводе
                  const newErrors = [...enumErrors];
                  newErrors[i] = "";
                  setEnumErrors(newErrors);
                }}
                disabled={loading}
              /></Field>
            </div>
            {enumErrors[i] && (
              <div className={styles.signalError}>{enumErrors[i]}</div>
            )}
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
        {events.map((e, i) => {
          const linkedSignal = signals.find((s) => s.key === e.signalKey);
          const invalidLink = linkedSignal && !READABLE_MGMT.includes(linkedSignal.management);
          return (
            <div key={i} className={styles.signalCard}>
              <div className={styles.signalRow}>
                <Field label="Код события" hint="Например: leak_changed. Латиницей без пробелов."><input
                  className={styles.inputSmall}
                  placeholder="Латиницей, без пробелов"
                  value={e.key}
                  onChange={(ev) => updateEvent(i, { key: ev.target.value })}
                  pattern="^[a-z][a-z0-9_]*$"
                  maxLength={64}
                  required
                  disabled={loading}
                /></Field>
                <Field label="Название события" hint="Например: Изменение протечки."><input
                  className={styles.inputSmall}
                  placeholder="Название"
                  value={e.title}
                  onChange={(ev) => updateEvent(i, { title: ev.target.value })}
                  maxLength={120}
                  required
                  disabled={loading}
                /></Field>
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
                <Field label="При изменении какого сигнала" hint="Событие возникает, когда значение выбранного сигнала меняется."><select
                  className={`${styles.select} ${invalidLink ? styles.selectError : ""}`}
                  value={e.signalKey}
                  onChange={(ev) => updateEvent(i, { signalKey: ev.target.value })}
                  disabled={loading}
                  required
                >
                  <option value="">— читаемый сигнал —</option>
                  {readableSignals.map((s) => (
                    <option key={s.key} value={s.key}>{s.title} ({s.key})</option>
                  ))}
                </select></Field>
              </div>
              {invalidLink && (
                <div className={styles.signalError}>
                  Сигнал «{e.signalKey}» стал недоступным для чтения. Выберите другой.
                </div>
              )}
            </div>
          );
        })}
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
