// Источник данных: mock или api
// Переключение через переменную окружения VITE_DATA_SOURCE=mock|api
// или через UI-переключатель (localStorage)

export type DataSource = "mock" | "api";

const ENV_SOURCE = import.meta.env.VITE_DATA_SOURCE as DataSource | undefined;
const STORAGE_KEY = "ujin-data-source";

function resolveSource(): DataSource {
  const stored = localStorage.getItem(STORAGE_KEY) as DataSource | null;
  if (stored === "mock" || stored === "api") return stored;
  return ENV_SOURCE ?? "mock";
}

let currentSource: DataSource = resolveSource();

export function getDataSource(): DataSource {
  return currentSource;
}

export function setDataSource(source: DataSource): void {
  currentSource = source;
  localStorage.setItem(STORAGE_KEY, source);
}

export function isMock(): boolean {
  return currentSource === "mock";
}
