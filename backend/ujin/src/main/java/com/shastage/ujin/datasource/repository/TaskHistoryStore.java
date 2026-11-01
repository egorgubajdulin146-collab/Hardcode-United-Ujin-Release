package com.shastage.ujin.datasource.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

/** Participates in the same transaction and catalogue lock as emulator application. */
@Repository
public class TaskHistoryStore {
    private final JdbcTemplate jdbc;
    public TaskHistoryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean contains(UUID device, String signal, String key) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM platform_task_history WHERE device_id=? AND signal_key=? AND task_key=?", Long.class, device, signal, key) > 0;
    }
    public void record(UUID device, String signal, String key, String appliedAt) {
        jdbc.update("INSERT INTO platform_task_history(device_id,signal_key,task_key,applied_at) VALUES (?,?,?,?)", device, signal, key, appliedAt);
    }
}
