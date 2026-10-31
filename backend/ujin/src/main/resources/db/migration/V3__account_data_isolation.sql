-- Добавляем колонку owner для изоляции данных аккаунтов
ALTER TABLE devices ADD COLUMN owner VARCHAR(120);
ALTER TABLE scenarios ADD COLUMN owner VARCHAR(120);
ALTER TABLE local_signal_state ADD COLUMN owner VARCHAR(120);
ALTER TABLE runtime_events ADD COLUMN owner VARCHAR(120);
ALTER TABLE runtime_commands ADD COLUMN owner VARCHAR(120);
ALTER TABLE runtime_runs ADD COLUMN owner VARCHAR(120);

-- Существующие записи остаются с owner=NULL
-- Владелец будет назначен при первом обращении или вручную администратором
-- Безопасный способ: UPDATE devices SET owner = '<login>' WHERE owner IS NULL;
