package com.shastage.ujin.web.demo;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A guest owns one short-lived Java LOCAL sandbox; never a shared Ujin account. */
@Service
public class GuestDemoService {
    public static final String ATTRIBUTE = "GUEST_DEMO_SCOPE";
    public static final String PREFIX = "demo-session:";
    public static final int LIFETIME_SECONDS = 1800;
    public static final int MAX_DEVICES = 8;
    public static final int MAX_SCENARIOS = 8;
    private final boolean enabled;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ConcurrentHashMap<String, Scope> active = new ConcurrentHashMap<>();
    private long minute = -1;
    private int starts;

    public static final class Scope {
        public final String owner = PREFIX + UUID.randomUUID();
        public final Instant expiresAt = Instant.now().plusSeconds(LIFETIME_SECONDS);
        public int writes;
        public long writeMinute = -1;
        public int minuteWrites;
        public boolean closed;
    }
    public GuestDemoService(@Value("${ujin.demo.enabled:false}") boolean enabled,
            JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.enabled = enabled; this.jdbc = jdbc; this.tx = new TransactionTemplate(transactions);
    }
    public boolean enabled() { return enabled; }
    public synchronized Scope create() {
        long now = System.currentTimeMillis() / 60000;
        if (minute != now) { minute = now; starts = 0; }
        if (!enabled || starts >= 30 || active.size() >= 64) return null;
        // Absolute database caps also bound orphaned sandboxes after application restarts.
        if (countAll("devices") >= 512 || countAll("scenarios") >= 512
                || countAll("runtime_events") >= 10000 || countAll("runtime_commands") >= 10000
                || countAll("runtime_runs") >= 20000) return null;
        starts++;
        Scope scope = new Scope(); active.put(scope.owner, scope); return scope;
    }
    public boolean active(Scope scope) { return active.get(scope.owner) == scope; }
    public Scope scope(HttpSession session) {
        Object value = session == null ? null : session.getAttribute(ATTRIBUTE);
        return value instanceof Scope scope ? scope : null;
    }
    public boolean permitWrite(Scope scope, String method, String path) {
        long now = System.currentTimeMillis() / 60000;
        if (scope.writeMinute != now) { scope.writeMinute = now; scope.minuteWrites = 0; }
        if (scope.writes >= 300 || scope.minuteWrites >= 60) return false;
        if ("POST".equals(method) && path.equals("/api/v1/devices")
                && (count("devices", scope.owner) >= MAX_DEVICES || countAll("devices") >= 512)) return false;
        if ("POST".equals(method) && path.equals("/api/v1/scenarios")
                && (count("scenarios", scope.owner) >= MAX_SCENARIOS || countAll("scenarios") >= 512)) return false;
        if (countAll("runtime_events") >= 10000 || countAll("runtime_commands") >= 10000
                || countAll("runtime_runs") >= 20000) return false;
        scope.writes++; scope.minuteWrites++; return true;
    }
    private long count(String table, String owner) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE owner=?", Long.class, owner);
    }
    private long countAll(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE owner LIKE ?", Long.class, PREFIX + "%");
    }
    public void close(Scope scope) {
        synchronized (scope) {
            if (scope.closed) return;
            scope.closed = true;
            try {
                tx.execute(status -> {
                    jdbc.queryForObject("SELECT id FROM catalog_lock WHERE id=1 FOR UPDATE", Integer.class);
                    String owner = scope.owner;
                    jdbc.update("DELETE FROM runtime_run_entries WHERE run_id IN (SELECT id FROM runtime_runs WHERE owner=?)", owner);
                    for (String table : new String[]{"runtime_runs", "runtime_events", "runtime_commands", "local_signal_state", "publications", "scenarios", "platform_bindings"})
                        jdbc.update("DELETE FROM " + table + " WHERE owner=?", owner);
                    for (String table : new String[]{"platform_task_history", "platform_task_acks"})
                        jdbc.update("DELETE FROM " + table + " WHERE device_id IN (SELECT id FROM devices WHERE owner=?)", owner);
                    jdbc.update("DELETE FROM signal_enum_values WHERE signal_id IN (SELECT id FROM device_signals WHERE device_id IN (SELECT id FROM devices WHERE owner=?))", owner);
                    jdbc.update("DELETE FROM device_signals WHERE device_id IN (SELECT id FROM devices WHERE owner=?)", owner);
                    jdbc.update("DELETE FROM device_events WHERE device_id IN (SELECT id FROM devices WHERE owner=?)", owner);
                    jdbc.update("DELETE FROM devices WHERE owner=?", owner);
                    return null;
                });
            } finally { active.remove(scope.owner, scope); }
        }
    }
}
