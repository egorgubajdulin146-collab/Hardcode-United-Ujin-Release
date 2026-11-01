ALTER TABLE runtime_commands ADD COLUMN reason VARCHAR(80);
ALTER TABLE runtime_commands ADD COLUMN updated_at VARCHAR(40);
ALTER TABLE runtime_commands ADD COLUMN resolution_note VARCHAR(1000);
ALTER TABLE runtime_commands ADD COLUMN resolved_at VARCHAR(40);

-- Последняя успешно применённая задача платформы на сигнал.
-- Запись создаётся только после успешного применения, повтор task_key не исполняется.
CREATE TABLE platform_task_acks (
    device_id UUID NOT NULL,
    signal_key VARCHAR(64) NOT NULL,
    task_key VARCHAR(255) NOT NULL,
    applied_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (device_id, signal_key)
);
