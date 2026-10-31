import { useState } from "react";
import {
  Droplets,
  Lightbulb,
  Plus,
  Settings,
  X,
  Construction,
} from "lucide-react";
import { MockBadge } from "../components/States";
import styles from "./RoomDevicesPage.module.css";

// MOCK-данные комнат и устройств (в контракте комнат нет)
const MOCK_ROOMS = [
  { id: "living", title: "Гостиная", deviceCount: 2 },
  { id: "kitchen", title: "Кухня", deviceCount: 1 },
  { id: "bathroom", title: "Ванная", deviceCount: 1 },
  { id: "bedroom", title: "Спальня", deviceCount: 0 },
];

interface MockDevice {
  id: string;
  title: string;
  family: string;
  icon: typeof Droplets;
  status: "online" | "offline";
  state: string;
  room: string;
}

const MOCK_DEVICES: MockDevice[] = [
  {
    id: "dev-1",
    title: "Датчик протечки",
    family: "leak",
    icon: Droplets,
    status: "online",
    state: "Сухо",
    room: "bathroom",
  },
  {
    id: "dev-2",
    title: "Светильник",
    family: "light",
    icon: Lightbulb,
    status: "online",
    state: "Выключен",
    room: "living",
  },
  {
    id: "dev-3",
    title: "Датчик протечки (кухня)",
    family: "leak",
    icon: Droplets,
    status: "offline",
    state: "Нет данных",
    room: "kitchen",
  },
  {
    id: "dev-4",
    title: "Торшер",
    family: "light",
    icon: Lightbulb,
    status: "online",
    state: "Включён, 80%",
    room: "living",
  },
];

function DeviceCard({
  device,
  selected,
  onSelect,
}: {
  device: MockDevice;
  selected: boolean;
  onSelect: () => void;
}) {
  const Icon = device.icon;
  return (
    <button
      type="button"
      className={`${styles.deviceCard} ${selected ? styles.deviceCardSelected : ""}`}
      onClick={onSelect}
    >
      <div className={styles.deviceIcon}>
        <Icon size={24} aria-hidden="true" />
      </div>
      <div className={styles.deviceInfo}>
        <span className={styles.deviceTitle}>{device.title}</span>
        <span className={styles.deviceState}>{device.state}</span>
      </div>
      <div className={styles.deviceMeta}>
        <span
          className={`${styles.statusDot} ${
            device.status === "online" ? styles.statusOnline : styles.statusOffline
          }`}
          aria-label={device.status === "online" ? "Онлайн" : "Офлайн"}
        />
        <span className={styles.statusText}>
          {device.status === "online" ? "Онлайн" : "Офлайн"}
        </span>
      </div>
    </button>
  );
}

function DisabledAction({
  icon: Icon,
  label,
  hint,
}: {
  icon: typeof Droplets;
  label: string;
  hint: string;
}) {
  return (
    <div className={styles.disabledAction}>
      <button type="button" className={styles.emulateButton} disabled>
        <Icon size={16} aria-hidden="true" />
        <span>{label}</span>
      </button>
      <p className={styles.emulateHint}>{hint}</p>
    </div>
  );
}

export function RoomDevicesPage() {
  const [selectedRoom, setSelectedRoom] = useState("living");
  const [selectedDevice, setSelectedDevice] = useState<string | null>("dev-2");

  const roomDevices = MOCK_DEVICES.filter((d) => d.room === selectedRoom);
  const currentDevice = MOCK_DEVICES.find((d) => d.id === selectedDevice);

  return (
    <div className={styles.page}>
      {/* Заголовок */}
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <h1 className={styles.pageTitle}>Устройства</h1>
          <MockBadge />
          <span className={styles.protoBadge}>
            <Construction size={14} aria-hidden="true" />
            Прототип
          </span>
        </div>
        <button type="button" className={styles.addButton} disabled title="Реализация в UJIN-TASK-010">
          <Plus size={18} aria-hidden="true" />
          <span>Добавить устройство</span>
        </button>
      </div>

      {/* Уведомление о прототипе */}
      <div className={styles.protoNotice}>
        Этот экран — визуальный прототип. Данные синтетические (MOCK).
        Действия отключены до реализации. API-режим для этого экрана не подключён.
      </div>

      {/* Комнаты */}
      <div className={styles.roomsBar} role="tablist" aria-label="Комнаты">
        {MOCK_ROOMS.map((room) => (
          <button
            key={room.id}
            type="button"
            role="tab"
            aria-selected={selectedRoom === room.id}
            className={`${styles.roomTab} ${
              selectedRoom === room.id ? styles.roomTabActive : ""
            }`}
            onClick={() => {
              setSelectedRoom(room.id);
              setSelectedDevice(null);
            }}
          >
            <span className={styles.roomTitle}>{room.title}</span>
            <span className={styles.roomCount}>{room.deviceCount}</span>
          </button>
        ))}
      </div>

      {/* Основная область: устройства + панель */}
      <div className={styles.content}>
        {/* Список устройств */}
        <div className={styles.deviceList}>
          {roomDevices.length === 0 ? (
            <div className={styles.emptyState}>
              <p className={styles.emptyTitle}>В комнате нет устройств</p>
              <p className={styles.emptyDescription}>
                Добавление устройств будет доступно после реализации конструктора
              </p>
            </div>
          ) : (
            roomDevices.map((device) => (
              <DeviceCard
                key={device.id}
                device={device}
                selected={selectedDevice === device.id}
                onSelect={() => setSelectedDevice(device.id)}
              />
            ))
          )}
        </div>

        {/* Панель устройства */}
        {currentDevice ? (
          <aside className={styles.devicePanel} aria-label="Настройки устройства">
            <div className={styles.panelHeader}>
              <h2 className={styles.panelTitle}>{currentDevice.title}</h2>
              <button
                type="button"
                className={styles.panelClose}
                aria-label="Закрыть"
                onClick={() => setSelectedDevice(null)}
              >
                <X size={16} aria-hidden="true" />
              </button>
            </div>

            <div className={styles.panelSection}>
              <h3 className={styles.sectionTitle}>Основное</h3>
              <div className={styles.field}>
                <span className={styles.fieldLabel}>Тип</span>
                <span className={styles.fieldValue}>{currentDevice.family}</span>
              </div>
              <div className={styles.field}>
                <span className={styles.fieldLabel}>Статус</span>
                <span className={styles.fieldValue}>
                  <span
                    className={`${styles.statusDot} ${
                      currentDevice.status === "online"
                        ? styles.statusOnline
                        : styles.statusOffline
                    }`}
                  />
                  {currentDevice.status === "online" ? "Онлайн" : "Офлайн"}
                </span>
              </div>
            </div>

            {currentDevice.family === "leak" && (
              <div className={styles.panelSection}>
                <h3 className={styles.sectionTitle}>Состояние</h3>
                <div className={styles.stateCard}>
                  <Droplets size={20} aria-hidden="true" />
                  <span>{currentDevice.state}</span>
                </div>
                <h3 className={styles.sectionTitle}>Эмуляция</h3>
                <DisabledAction
                  icon={Droplets}
                  label="Имитировать протечку"
                  hint="Будет отправлен stimulus WET=1 (не реализовано)"
                />
              </div>
            )}

            {currentDevice.family === "light" && (
              <div className={styles.panelSection}>
                <h3 className={styles.sectionTitle}>Состояние</h3>
                <div className={styles.stateCard}>
                  <Lightbulb size={20} aria-hidden="true" />
                  <span>{currentDevice.state}</span>
                </div>
                <h3 className={styles.sectionTitle}>Управление</h3>
                <DisabledAction
                  icon={Lightbulb}
                  label="Включить свет"
                  hint="Будет отправлена команда lamp=1 (не реализовано)"
                />
              </div>
            )}

            <div className={styles.panelSection}>
              <h3 className={styles.sectionTitle}>Детали</h3>
              <div className={styles.detailRow}>
                <span className={styles.detailKey}>ID устройства</span>
                <span className={styles.detailValue}>{currentDevice.id}</span>
              </div>
              <div className={styles.detailRow}>
                <span className={styles.detailKey}>Семейство</span>
                <span className={styles.detailValue}>{currentDevice.family}</span>
              </div>
            </div>
          </aside>
        ) : (
          <aside className={styles.devicePanel} aria-label="Выберите устройство">
            <div className={styles.panelEmpty}>
              <Settings size={32} aria-hidden="true" />
              <p>Выберите устройство для просмотра настроек</p>
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}
