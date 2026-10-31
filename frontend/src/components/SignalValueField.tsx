import type { SignalDefinition } from "../api/types";

interface Props {
  signal?: SignalDefinition;
  family?: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  className?: string;
}

/** Keep numeric wire values while explaining known device states to the user. */
export function SignalValueField({ signal, family, label, value, onChange, disabled, className }: Props) {
  const optionLabel = (number: number) => {
    if (family === "leak" && signal?.key === "leak") {
      if (number === 0) return "0 — Сухо";
      if (number === 1) return "1 — Протечка";
    }
    if (family === "light" && signal?.key === "lamp") {
      if (number === 0) return "0 — Свет выключен";
      if (number === 1) return "1 — Свет включён";
    }
    return `${number}${signal?.unit ? ` ${signal.unit}` : ""}`;
  };
  if (signal?.enumValues?.length) {
    // Retain an invalid saved value visibly so opening an old rule never silently changes it.
    const missing = value !== "" && !signal.enumValues.includes(Number(value));
    return <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
      <span>{label}</span>
      <select className={className} value={value} onChange={event => onChange(event.target.value)} disabled={disabled} required>
        <option value="">Выберите значение</option>
        {missing && <option value={value} disabled>{value} — больше не разрешено</option>}
        {signal.enumValues.map(number => <option key={number} value={String(number)}>{optionLabel(number)}</option>)}
      </select>
    </label>;
  }
  return <label style={{ display: "flex", flexDirection: "column", gap: 4 }}>
    <span>{label}</span>
    <input className={className} type="number" step="any" value={value}
      onChange={event => onChange(event.target.value)} min={signal?.min} max={signal?.max}
      placeholder={signal?.unit ? `Число в ${signal.unit}` : "Введите число"} disabled={disabled} required />
  </label>;
}
