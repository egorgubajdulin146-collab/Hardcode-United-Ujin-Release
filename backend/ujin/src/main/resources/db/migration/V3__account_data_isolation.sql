-- Добавляем колонку owner для изоляции данных аккаунтов
ALTER TABLE devices ADD COLUMN owner VARCHAR(120);
ALTER TABLE scenarios ADD COLUMN owner VARCHAR(120);
ALTER TABLE local_signal_state ADD COLUMN owner VARCHAR(120);
ALTER TABLE runtime_events ADD COLUMN owner VARCHAR(120);
ALTER TABLE runtime_commands ADD COLUMN owner VARCHAR(120);
ALTER TABLE runtime_runs ADD COLUMN owner VARCHAR(120);

-- Существующие записи остаются с owner=NULL и не доступны ни одному пользователю.
-- Безопасное назначение владельца: выбрать конкретные связанные записи
-- и назначить им владельца вручную, например:
--   UPDATE devices SET owner = 'user1' WHERE id = '<device-id>';
--   UPDATE scenarios SET owner = 'user1' WHERE id = '<scenario-id>';
--   UPDATE local_signal_state SET owner = 'user1' WHERE device_id = '<device-id>';
-- Не назначать все NULL-записи одному пользователю — это раскроет чужие данные.
