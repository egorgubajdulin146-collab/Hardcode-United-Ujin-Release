import { useEffect, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { endpoints } from "../api/endpoints";
import { ApiError, NetworkError, TimeoutError } from "../api/client";
import type { PlatformBindingMapping, SignalDefinition } from "../api/types";
import styles from "./PlatformBindingSection.module.css";

interface Props {
  deviceId: string;
  signals: SignalDefinition[];
  platformLink: string;
}

function bindingErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const code = error.problem?.code;
    switch (code) {
      case "PLATFORM_NOT_BOUND":
        return "Устройство пока не привязано к платформенному SN.";
      case "UJIN_SN_NOT_VISIBLE":
        return "Текущая сессия Ujin не видит этот SN. Привязка не сохранена.";
      case "UJIN_DEVICE_NOT_VISIBLE":
        return "Привязанный SN сейчас не виден в Ujin.";
      case "UJIN_AUTH_REQUIRED":
        return "Нужен вход в Ujin, чтобы проверить SN перед привязкой.";
      case "UJIN_ACCESS_DENIED":
        return "Ujin запретил доступ к данным устройств.";
      case "UJIN_UNAVAILABLE":
        return "Платформа Ujin недоступна (сеть или таймаут).";
      case "UJIN_BAD_RESPONSE":
        return "Ujin вернул некорректный ответ.";
      case "UJIN_PLATFORM_ERROR":
        return "Платформа вернула ошибку.";
      case "BINDING_IN_USE":
        return "Есть незавершённая операция. Проверьте её результат перед изменением привязки.";
      case "VERSION_CONFLICT":
        return "Привязка изменилась на сервере. Перезагрузите и повторите сохранение.";
      case "DEVICE_NOT_FOUND":
        return "Устройство недоступно.";
    }
    if (error.status === 401) return "Требуется вход в Ujin.";
    if (error.status === 403) return "Нет разрешения на эту операцию.";
    return error.problem?.detail ?? `Ошибка сервера (${error.status})`;
  }
  if (error instanceof TimeoutError) return "Превышено время ожидания.";
  if (error instanceof NetworkError) return "Сервер недоступен.";
  return error instanceof Error ? error.message : "Не удалось выполнить запрос.";
}

export function PlatformBindingSection({ deviceId, signals, platformLink }: Props) {
  const queryClient = useQueryClient();
  const binding = useQuery({
    queryKey: ["platform-binding", deviceId],
    queryFn: ({ signal }) => endpoints.devices.getBinding(deviceId, signal),
    retry: false,
  });
  const [serialNumber, setSerialNumber] = useState("");
  const [mappingTexts, setMappingTexts] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const loaded = binding.data;
  useEffect(() => {
    if (loaded) {
      setSerialNumber(loaded.serialNumber);
      setMappingTexts(
        Object.fromEntries(loaded.signalMappings.map((m) => [m.signalKey, m.platformName]))
      );
    }
  }, [loaded]);

  const notBound =
    binding.error instanceof ApiError && binding.error.problem?.code === "PLATFORM_NOT_BOUND";
  const version = loaded?.version;

  const save = async (event: FormEvent) => {
    event.preventDefault();
    const sn = serialNumber.trim();
    const signalMappings: PlatformBindingMapping[] = [];
    for (const signal of signals) {
      const platformName = (mappingTexts[signal.key] ?? "").trim();
      if (platformName) signalMappings.push({ signalKey: signal.key, platformName });
    }
    if (!sn) {
      setError("Укажите платформенный SN строкой.");
      return;
    }
    if (signalMappings.length === 0) {
      setError("Укажите хотя бы одно соответствие сигнала.");
      return;
    }
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const saved = await endpoints.devices.putBinding(deviceId, {
        serialNumber: sn,
        signalMappings,
        ...(version !== undefined ? { expectedVersion: version } : {}),
      });
      setMessage(
        `Привязка сохранена: SN ${saved.serialNumber}, версия ${saved.version}. Видимость устройства проверена; наличие имён сигналов проверяется при чтении. Команды Ujin пока недоступны.`
      );
      void queryClient.invalidateQueries({ queryKey: ["platform-binding", deviceId] });
      void queryClient.invalidateQueries({ queryKey: ["device", deviceId] });
    } catch (err) {
      setError(bindingErrorMessage(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className={styles.section} aria-label="Привязка к Ujin">
      <div className={styles.header}>
        <h3 className={styles.title}>Привязка к Ujin</h3>
        <span>Статус: {({ BOUND: "Привязано", NOT_CONFIGURED: "Не настроено" } as Record<string, string>)[platformLink] ?? platformLink}</span>
      </div>
      <p className={styles.status}>
        {binding.isLoading
          ? "Загрузка привязки…"
          : notBound
            ? "Привязка отсутствует. Укажите точный SN и имена сигналов из панели сигналов Ujin."
            : binding.error
              ? bindingErrorMessage(binding.error)
              : loaded
                ? `Привязан SN ${loaded.serialNumber} (версия ${loaded.version}). При сохранении сессия Ujin видела устройство; имена сигналов проверяются при чтении.`
                : ""}
      </p>
      <form onSubmit={save}>
        <div className={styles.field}>
          <label className={styles.label} htmlFor="binding-sn">
            Серийный номер устройства в Ujin (SN)
          </label>
          <input
            id="binding-sn"
            placeholder="Скопируйте SN назначенного устройства из Ujin"
            aria-describedby="binding-sn-help"
            className={styles.input}
            value={serialNumber}
            onChange={(e) => setSerialNumber(e.target.value)}
            maxLength={128}
            required
            disabled={saving || binding.isLoading}
          />
        </div>
        <p id="binding-sn-help" className={styles.hint}>Номер берётся из карточки Ujin или выданных доступов. UUID нашего каталога сюда не подходит.</p>
        <div className={styles.field}>
          <span className={styles.label}>Какие показания читать из Ujin</span>
          <p className={styles.hint}>Для нужного сигнала укажите его точное техническое имя в Ujin. Остальные поля можно оставить пустыми. Пример: для «Протечка» — leak, только если такое имя есть на платформе.</p>
          {signals.map((signal) => (
            <div className={styles.mappingRow} key={signal.key}>
              <span className={styles.mappingKey} title={signal.key}>
                {signal.title} ({signal.key})
              </span>
              <input
                className={styles.input}
                aria-label={`Имя сигнала Ujin для ${signal.key}`}
                value={mappingTexts[signal.key] ?? ""}
                onChange={(e) =>
                  setMappingTexts((current) => ({ ...current, [signal.key]: e.target.value }))
                }
                maxLength={255}
                disabled={saving || binding.isLoading}
                placeholder="Точное имя из Ujin, например leak"
              />
            </div>
          ))}
        </div>
        <div className={styles.actions}>
          <button type="submit" className={styles.saveBtn} disabled={saving || binding.isLoading}>
            {version !== undefined ? "Сохранить привязку" : "Привязать"}
          </button>
        </div>
      </form>
      {message && <p className={styles.ok}>{message}</p>}
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
      <p className={styles.hint}>
        SN проверяется по списку устройств резидента. Имена сигналов хранятся как строки и
        сопоставляются по точному совпадению при чтении. Если имя не найдено, карточка покажет
        имена, которые вернула платформа; отсутствие сигнала не подменяется нулём. Управление Ujin
        остаётся отключённым до подтверждённого канала команд.
      </p>
    </section>
  );
}
