import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { endpoints } from "../api/endpoints";
import { ApiError } from "../api/client";
import { useSession } from "../api/sessionContext";
import type { CommandReceipt, Device, EventReceipt, ExecutionMode, SignalDefinition } from "../api/types";
import styles from "./DeviceRuntimePanel.module.css";

const COMMAND_STATUS = { PENDING: "Ожидает подтверждения", CONFIRMED: "Выполнено", FAILED: "Ошибка выполнения", UNKNOWN: "Результат неизвестен" };
const EVENT_STATUS = { ACCEPTED: "Событие принято", PROCESSED: "Событие обработано", FAILED: "Ошибка обработки" };
const ISSUE_LABEL: Record<string, string> = {
  MISSING_SIGNAL: "Сигнал отсутствует на платформе",
  MISSING_VALUE: "Платформа не передала значение",
  BAD_SCHEMA: "Некорректное значение",
  OUT_OF_RANGE: "Значение вне диапазона",
};
function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.problem?.code) {
      case "PUBLICATION_NOT_CONFIGURED":
        return "Публикация для этого устройства ещё не настроена на сервере.";
      case "LOCAL_STATE_CHANGED":
        return "Показание изменилось. Обновите состояние перед публикацией.";
      case "PUBLICATION_UNRESOLVED":
        return "Сначала проверьте чтением предыдущую публикацию этого сигнала.";
      case "PLATFORM_NOT_BOUND":
        return "Устройство не привязано к платформенному SN. Добавьте привязку в карточке устройства.";
      case "UJIN_DEVICE_NOT_VISIBLE":
        return "Привязанный SN сейчас не виден в Ujin.";
      case "UJIN_AUTH_REQUIRED":
        return "Нужен вход в Ujin для чтения состояния.";
      case "UJIN_ACCESS_DENIED":
        return "Ujin запретил доступ к данным устройства.";
      case "UJIN_UNAVAILABLE":
        return "Платформа Ujin недоступна (сеть или таймаут).";
      case "UJIN_BAD_RESPONSE":
      case "UJIN_PLATFORM_ERROR":
        return "Ujin вернул некорректный ответ.";
    }
    if (error.status === 404 || error.status === 501) return "Управление этим устройством пока недоступно на сервере.";
    if (error.status === 403) return "Нет разрешения на эту операцию.";
    return error.problem?.detail ?? `Ошибка сервера (${error.status})`;
  }
  return error instanceof Error ? error.message : "Не удалось выполнить запрос.";
}
function ValueInput({ signal, value, onChange, disabled }: {
  signal: SignalDefinition; value: string; onChange: (value: string) => void; disabled: boolean;
}) {
  return signal.enumValues ? (
    <select aria-label={`Значение: ${signal.title}`} value={value} onChange={e => onChange(e.target.value)} disabled={disabled} required>
      <option value="">Выберите значение</option>
      {signal.enumValues.map(v => <option key={v} value={v}>{v}</option>)}
    </select>
  ) : <input aria-label={`Значение: ${signal.title}`} type="number" step="any" required min={signal.min} max={signal.max}
    value={value} disabled={disabled} onChange={e => onChange(e.target.value)} />;
}

export function DeviceRuntimePanel({ device, onClose }: { device: Device; onClose: () => void }) {
  const [mode, setMode] = useState<ExecutionMode>("LOCAL");
  const [values, setValues] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [command, setCommand] = useState<CommandReceipt | null>(null);
  const [eventLookupId, setEventLookupId] = useState<string | null>(null);
  const [event, setEvent] = useState<EventReceipt | null>(null);
  const [publication, setPublication] = useState<PublicationReceipt | null>(null);
  const { terminateSession } = useSession();
  const active = useRef(true);
  const queryClient = useQueryClient();
  useEffect(() => { active.current = true; return () => { active.current = false; }; }, []);
  const state = useQuery({
    queryKey: ["device-state", device.id, mode],
    queryFn: ({ signal }) => endpoints.devices.getState(device.id, mode, signal),
    retry: false,
    refetchInterval: 5000,
  });
  const integration = useQuery({
    queryKey: ["ujin-status"], queryFn: ({ signal }) => endpoints.ujin.getStatus(signal), enabled: mode === "UJIN", retry: false,
  });
  const publications = useQuery({
    queryKey: ["publications", device.id],
    queryFn: ({ signal }) => endpoints.publications.list(device.id, signal),
    enabled: device.platformLink === "BOUND", retry: false,
  });
  const receipts = Array.isArray(publications.data) ? publications.data : [];
  const publicationReceipts = publication
    ? [publication, ...receipts.filter(p => p.publicationId !== publication.publicationId)] : receipts;
  const publishReading = async (signalKey: string, value: number) => {
    if (busy) return;
    const pending: PublicationReceipt = { publicationId: crypto.randomUUID(), deviceId: device.id,
      signalKey, value, status: "PENDING", reason: "DISPATCHING", createdAt: new Date().toISOString() };
    setPublication(pending); setBusy(true); setError(null);
    try {
      const saved = await endpoints.publications.send(device.id, { publicationId: pending.publicationId, signalKey, value });
      if (active.current) setPublication(saved);
    } catch (err) {
      if (!active.current) return;
      if (err instanceof ApiError && err.status === 401) terminateSession();
      const preflightFailure = err instanceof ApiError && ["PUBLICATION_NOT_CONFIGURED", "UJIN_UNAVAILABLE", "UJIN_BAD_RESPONSE",
        "UJIN_PLATFORM_ERROR"].includes(err.problem?.code ?? "");
      const uncertain = !(err instanceof ApiError) || (!preflightFailure && err.status >= 500) || err.status === 408;
      setPublication(uncertain ? { ...pending, status: "UNKNOWN", reason: "RESPONSE_NOT_RECEIVED" } : null);
      setError(errorMessage(err));
    } finally {
      if (active.current) { setBusy(false); void publications.refetch(); }
    }
  };
  const checkPublication = async (receipt: PublicationReceipt) => {
    setBusy(true); setError(null);
    try {
      const checked = await endpoints.publications.readback(device.id, receipt.publicationId);
      if (active.current) setPublication(checked);
    } catch (err) {
      if (active.current) {
        if (err instanceof ApiError && err.status === 401) terminateSession();
        setError(errorMessage(err));
      }
    } finally { if (active.current) { setBusy(false); void publications.refetch(); } }
  };
  const commandStatus = useQuery({
    queryKey: ["command", command?.commandId],
    queryFn: ({ signal }) => endpoints.commands.get(command!.commandId, signal),
    enabled: command?.status === "PENDING" || command?.status === "UNKNOWN",
    retry: false,
    refetchInterval: query => query.state.data?.status === "CONFIRMED" || query.state.data?.status === "FAILED" ? false : 3000,
  });
  const eventStatus = useQuery({
    queryKey: ["event", event?.eventId ?? eventLookupId],
    queryFn: ({ signal }) => endpoints.events.get((event?.eventId ?? eventLookupId)!, signal),
    enabled: event?.status === "ACCEPTED" || eventLookupId !== null, retry: false,
    refetchInterval: query => query.state.data?.status === "PROCESSED" || query.state.data?.status === "FAILED" ? false : 3000,
  });
  const python = mode === "LOCAL" && state.data?.provider === "PYTHON";
  const currentCommand = commandStatus.data ?? command;
  const currentEvent = eventStatus.data ?? event;
  const unresolved = currentCommand?.status === "PENDING" || currentCommand?.status === "UNKNOWN" || currentEvent?.status === "ACCEPTED" || (eventLookupId !== null && !currentEvent);
  const online = !state.error && state.data?.availability === "ONLINE";
  const canCommand = mode === "LOCAL"
    ? online
    : device.platformLink === "BOUND" && !integration.error && integration.data?.downlink.status === "AVAILABLE";
  const requestError = state.error ?? integration.error ?? commandStatus.error ?? eventStatus.error ?? publications.error;
  useEffect(() => {
    if (requestError instanceof ApiError && requestError.status === 401) terminateSession();
  }, [requestError, terminateSession]);
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ["device-state", device.id] });
    void queryClient.invalidateQueries({ queryKey: ["runs"] });
  };
  const pollPython = async () => {
    if (busy || unresolved) return;
    setBusy(true); setError(null);
    const eventId = crypto.randomUUID();
    try { const receipt = await endpoints.localPython.poll(eventId); if(active.current){setEvent(receipt);refresh();} }
    catch(err){if(active.current)setError(errorMessage(err));}
    finally{if(active.current)setBusy(false);}
  };
  const setAvailability = async (availability: "ONLINE" | "OFFLINE") => {
    if (mode !== "LOCAL" || busy || unresolved) return;
    setBusy(true); setError(null);
    try {
      const updated = await endpoints.devices.setAvailability(device.id, availability);
      if (active.current) {
        queryClient.setQueryData(["device-state", device.id, "LOCAL"], updated);
        refresh();
      }
    } catch (err) {
      if (!active.current) return;
      if (err instanceof ApiError && err.status === 401) terminateSession();
      else setError(`${errorMessage(err)} Обновите состояние перед повтором.`);
      refresh();
    } finally { if (active.current) setBusy(false); }
  };
  const sendCommand = async (signal: SignalDefinition) => {
    const raw = values[signal.key] ?? "";
    const value = Number(raw);
    if (!raw.trim() || !Number.isFinite(value) || (signal.min !== undefined && value < signal.min) ||
      (signal.max !== undefined && value > signal.max) || (signal.enumValues && !signal.enumValues.includes(value))) {
      setError("Введите значение из допустимого диапазона."); return;
    }
    if (busy || unresolved || !canCommand) return;
    setBusy(true); setError(null); setCommand(null);
    const commandId = crypto.randomUUID();
    try {
      const receipt = await endpoints.commands.send(device.id, { commandId, signalKey: signal.key, value, executionMode: mode });
      if (active.current) { setCommand(receipt); refresh(); }
    } catch (err) {
      if (!active.current) return;
      if (err instanceof ApiError && err.status === 401) terminateSession();
      else {
        if (!(err instanceof ApiError) || err.status >= 500 || err.status === 408) setCommand({ commandId, deviceId: device.id, executionMode: mode, status: "UNKNOWN" });
        setError(`${errorMessage(err)} Повторная команда автоматически не отправляется. Если ответ потерян, проверьте журнал перед повтором.`);
      }
    } finally { if (active.current) setBusy(false); }
  };
  const checkCommandResult = async () => {
    if (!currentCommand || busy) return;
    setBusy(true); setError(null);
    try {
      const receipt = await (python ? endpoints.localPython.check(currentCommand.commandId) : endpoints.commands.check(currentCommand.commandId));
      if (active.current) setCommand(receipt);
    } catch (err) {
      if (!active.current) return;
      if (err instanceof ApiError && err.status === 401) terminateSession();
      else setError(errorMessage(err));
    } finally {
      if (active.current) {
        setBusy(false);
        void queryClient.invalidateQueries({ queryKey: ["command"] });
      }
    }
  };
  const stimulate = async (value: 0 | 1) => {
    if (busy || unresolved || !online || mode !== "LOCAL") return;
    setBusy(true); setError(null); setEvent(null); setEventLookupId(null);
    const eventId = crypto.randomUUID();
    try {
      const receipt = await endpoints.stimuli.send(device.id, { eventId, kind: "WET", value });
      if (active.current) { setEvent(receipt); refresh(); }
    } catch (err) {
      if (!active.current) return;
      if (err instanceof ApiError && err.status === 401) terminateSession();
      else {
        if (!(err instanceof ApiError) || err.status >= 500 || err.status === 408) setEventLookupId(eventId);
        setError(`${errorMessage(err)} Проверьте обработку события перед повторной отправкой.`);
      }
    } finally { if (active.current) setBusy(false); }
  };
  return <section className={styles.panel} aria-label={`Управление: ${device.title}`}>
    <header><div><h2>{device.title}</h2><p>Состояние и управление</p></div><button onClick={onClose} type="button" disabled={busy}>Закрыть</button></header>
    <div className={styles.toolbar}>
      <label>Контур <select value={mode} disabled={busy || !!unresolved} onChange={e => { setMode(e.target.value as ExecutionMode); setError(null); setCommand(null); setEvent(null); setEventLookupId(null); }}>
        <option value="LOCAL">Локальный</option><option value="UJIN">Ujin</option>
      </select></label>
      <button type="button" onClick={() => { void state.refetch(); }} disabled={state.isFetching}>Обновить состояние</button>
      <span>{state.isLoading ? "Загрузка…" : state.error ? "Состояние недоступно" : state.data?.availability === "ONLINE" ? "На связи" : state.data?.availability === "OFFLINE" ? "Не на связи" : "Состояние неизвестно"}</span>
    </div>
    {mode === "LOCAL" && <label>
      <input type="checkbox" checked={online} disabled={busy || !!unresolved || state.isLoading || !!state.error}
        onChange={e => void setAvailability(e.target.checked ? "ONLINE" : "OFFLINE")} />
      Эмулятор онлайн
    </label>}
    {python && <p role="status">Внешний LOCAL-Python эмулятор. Это отдельный процесс, без подключения к Ujin.</p>}
    {python && device.family === "leak" && <div>
      <p>Измените протечку в Python-эмуляторе, затем проверьте показание. При первом чтении устанавливается исходное состояние без запуска действия.</p>
      <button type="button" disabled={busy || !!unresolved || !online} onClick={() => void pollPython()}>Проверить показание Python</button>
    </div>}
    {requestError && <p role="alert" className={styles.error}>{errorMessage(requestError)}</p>}
    {mode === "UJIN" && state.data?.observedAt && (
      <p>Наблюдение: {new Date(state.data.observedAt).toLocaleString("ru-RU")} (время чтения backend, не время измерения устройством)</p>
    )}
    {mode === "UJIN" && !canCommand && <p>{device.platformLink !== "BOUND"
      ? "Управление Ujin недоступно: устройство не привязано к платформенному SN. Привязка — в карточке устройства."
      : integration.data?.downlink.status !== "AVAILABLE"
        ? `Управление Ujin отключено: ${integration.data?.downlink.reason ?? "командный канал не подтверждён"}`
        : "Управление Ujin недоступно: состояние устройства неизвестно или запрос не выполнен."}</p>}
    <div className={styles.signals}>{device.signals.map(signal => {
      const value = state.data?.values.find(v => v.signalKey === signal.key);
      const issue = state.data?.signalIssues?.find(i => i.signalKey === signal.key);
      return <article key={signal.key}>
        <h3>{signal.title}</h3><p>{signal.management === "w" ? "Команда" : value ? `${value.value}${signal.unit ? ` ${signal.unit}` : ""}` : "Нет данных"}</p>
        {value && <small>Обновлено: {new Date(value.updatedAt).toLocaleString("ru-RU")}</small>}
        {value?.measuredAt && <small>Измерено устройством: {new Date(value.measuredAt).toLocaleString("ru-RU")}</small>}
        {!value && issue && <small role="status">{ISSUE_LABEL[issue.status] ?? issue.detail}</small>}
        {mode === "LOCAL" && signal.management !== "w" && device.platformLink === "BOUND" && value &&
          <button type="button" disabled={busy || !online || publications.isPending || !!publications.error ||
            publicationReceipts.some(p => p.signalKey === signal.key && (p.status === "PENDING" || p.status === "UNKNOWN"))}
            onClick={() => void publishReading(signal.key, value.value)}>Опубликовать {signal.title} в Ujin</button>}
        {signal.management !== "r" && <form onSubmit={e => { e.preventDefault(); void sendCommand(signal); }}>
          <ValueInput signal={signal} value={values[signal.key] ?? ""} disabled={busy || !!unresolved || !canCommand}
            onChange={value => setValues(v => ({ ...v, [signal.key]: value }))} />
          <button disabled={busy || !!unresolved || !canCommand} type="submit">Отправить</button>
          <small>{signal.enumValues ? `Значения: ${signal.enumValues.join(", ")}` : `Диапазон: ${signal.min ?? "−∞"} … ${signal.max ?? "+∞"}`}</small>
        </form>}
      </article>;
    })}</div>
    {device.family === "leak" && mode === "LOCAL" && !python && <div className={styles.toolbar}>
      <span>Датчик протечки</span><button type="button" disabled={busy || !!unresolved || !online} onClick={() => void stimulate(1)}>Обнаружить воду</button>
      <button type="button" disabled={busy || !!unresolved || !online} onClick={() => void stimulate(0)}>Вода отсутствует</button>
    </div>}
    <div aria-live="polite">
      {publicationReceipts.map(receipt => <div key={receipt.publicationId}>
        <p>Публикация {receipt.signalKey} = {receipt.value}: {{ PENDING: "ожидает проверки", CONFIRMED: "значение прочитано в Ujin",
          FAILED: "отклонена", UNKNOWN: "результат неизвестен" }[receipt.status]} <small>{receipt.publicationId}</small></p>
        {(receipt.status === "UNKNOWN" || receipt.status === "PENDING") &&
          <button type="button" disabled={busy} onClick={() => void checkPublication(receipt)}>Проверить чтением</button>}
      </div>)}
      {busy && <p>Отправка…</p>}{error && <p role="alert" className={styles.error}>{error}</p>}
      {currentCommand && <p>Команда: {COMMAND_STATUS[currentCommand.status]}{currentCommand.reason ? ` · ${currentCommand.reason}` : ""} <small>{currentCommand.commandId}</small></p>}
      {currentCommand && (mode === "UJIN" || python) && (currentCommand.status === "PENDING" || currentCommand.status === "UNKNOWN") &&
        <button type="button" disabled={busy} onClick={() => void checkCommandResult()}>Проверить результат команды</button>}
      {currentCommand?.resolution && <p>Отмечено как неизвестное: {currentCommand.resolution.note}</p>}
      {eventLookupId && !currentEvent && <p>Приём события не подтверждён. Проверяем результат. <small>{eventLookupId}</small></p>}
      {currentEvent && <p>{EVENT_STATUS[currentEvent.status]} · запусков: {currentEvent.runIds.length}</p>}
      {(currentEvent || currentCommand || eventLookupId) && <Link to={currentEvent || eventLookupId ? `/runs?eventId=${encodeURIComponent(currentEvent?.eventId ?? eventLookupId!)}` : "/runs"}>Открыть журнал выполнения</Link>}
    </div>
  </section>;
}
import type { PublicationReceipt } from "../api/types";
