package com.shastage.ujin.domain.service;

import com.shastage.ujin.datasource.client.*;
import com.shastage.ujin.datasource.entity.PublicationEntity;
import com.shastage.ujin.datasource.repository.jpa.PublicationJpaRepository;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.repository.CatalogStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.time.Instant;
import java.util.*;

@Service
public class PublicationService {
    public record Write(UUID publicationId, String signalKey, Double value) {}
    public record Receipt(UUID publicationId, UUID deviceId, String signalKey, double value,
                          String status, String reason, Instant createdAt, Instant observedAt) {}
    public record Plan(String serialNumber, String extGuid, String apartmentId, String publishClass,
                       String platformName, String shortName, double wireValue, boolean dictionaryMissing) {}
    private record Prepared(PublicationEntity entity, boolean created) {}
    private final PublicationJpaRepository repository;
    private final CatalogService catalog;
    private final CatalogStore store;
    private final PlatformBindingService binding;
    private final LocalRuntimeService runtime;
    private final PublicationProperties config;
    private final TransactionTemplate tx;
    private final UjinApiClient client;
    private final JsonMapper json = JsonMapper.builder().build();

    public PublicationService(PublicationJpaRepository repository, CatalogService catalog,
            CatalogStore store, PlatformBindingService binding, LocalRuntimeService runtime,
            PublicationProperties config, PlatformTransactionManager transactions,
            @Value("${ujin.api.base-url}") URI baseUrl) {
        this.repository = repository; this.catalog = catalog; this.store = store;
        this.binding = binding; this.runtime = runtime; this.config = config;
        tx = new TransactionTemplate(transactions);
        client = new UjinApiClient(baseUrl);
    }

    public Receipt publish(UUID deviceId, Write write, String owner, String token) {
        requireToken(token);
        if (write == null || write.publicationId() == null || write.signalKey() == null
                || write.value() == null || !Double.isFinite(write.value()))
            throw error(422, "VALIDATION_ERROR", "publicationId, signalKey and finite value are required");
        // Commit the reservation before any external write. Replays never resend it,
        // including after a process crash between the two publication phases.
        Prepared prepared = tx.execute(status -> prepare(deviceId, write, owner, token));
        if (!prepared.created()) return view(prepared.entity());
        PublicationEntity receipt = prepared.entity();
        Plan plan = json.readValue(receipt.plan, Plan.class);
        try {
            if (plan.dictionaryMissing()) {
                send(plan, plan.shortName());
                Thread.sleep(Math.max(0, Math.min(5000, config.getDictionaryDelayMs())));
            }
            send(plan, plan.platformName());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return finish(receipt.id, owner, "UNKNOWN", "INTERRUPTED", null);
        } catch (UjinApiException failure) {
            boolean rejected = switch (failure.kind()) {
                case UNAUTHORIZED, FORBIDDEN, ACCESS_DENIED, PLATFORM_ERROR -> true;
                default -> false;
            };
            return finish(receipt.id, owner, rejected ? "FAILED" : "UNKNOWN",
                    "UJIN_" + failure.kind().name(), null);
        }
        // error=0 is only an acknowledgement. Confirm via a separate resident GET.
        finish(receipt.id, owner, "UNKNOWN", "AWAITING_READBACK", null);
        return check(deviceId, receipt.id, owner, token);
    }

    private Prepared prepare(UUID deviceId, Write write, String owner, String token) {
        store.lock();
        var device = catalog.device(deviceId, owner);
        var existing = repository.findById(write.publicationId());
        if (existing.isPresent()) {
            var old = existing.get();
            if (!owner.equals(old.owner) || !deviceId.equals(old.deviceId)
                    || !write.signalKey().equals(old.signalKey) || old.value != write.value())
                throw error(409, "IDEMPOTENCY_CONFLICT", "Publication ID is already used");
            return new Prepared(old, false);
        }
        if (repository.existsByDeviceIdAndOwnerAndSignalKeyAndStatusIn(
                deviceId, owner, write.signalKey(), List.of("PENDING", "UNKNOWN")))
            throw error(409, "PUBLICATION_UNRESOLVED", "Check the previous publication before sending another");
        if (!config.isEnabled() || blank(config.getUst()))
            throw error(503, "PUBLICATION_NOT_CONFIGURED", "Publication is not configured on this server");
        var declaration = device.signals().stream().filter(s -> s.key().equals(write.signalKey()))
                .findFirst().orElseThrow(() -> error(422, "VALIDATION_ERROR", "Unknown signal"));
        if ("w".equals(declaration.management()))
            throw error(422, "SIGNAL_NOT_READABLE", "Write-only commands are not state readings");
        double value = write.value();
        if ((declaration.min() != null && value < declaration.min())
                || (declaration.max() != null && value > declaration.max())
                || (declaration.enumValues() != null && !declaration.enumValues().isEmpty()
                    && !declaration.enumValues().contains(value)))
            throw error(422, "VALIDATION_ERROR", "Value is outside the signal declaration");
        var local = runtime.getDeviceState(deviceId, "LOCAL", owner);
        if (!"ONLINE".equals(local.get("availability")))
            throw error(409, "DEVICE_OFFLINE", "Emulator is offline");
        var localValues = (List<?>) local.get("values");
        boolean current = localValues.stream().anyMatch(item -> item instanceof Map<?, ?> sample
                && write.signalKey().equals(sample.get("signalKey"))
                && sample.get("value") instanceof Number number && number.doubleValue() == value);
        if (!current) throw error(409, "LOCAL_STATE_CHANGED", "Refresh the local reading before publishing");
        var bound = binding.getBinding(deviceId, owner);
        var mapping = bound.signalMappings().stream().filter(m -> m.signalKey().equals(write.signalKey()))
                .findFirst().orElseThrow(() -> error(422, "SIGNAL_NOT_BOUND", "Signal has no platform mapping"));
        var targets = config.getTargets().stream()
                .filter(t -> bound.serialNumber().equals(t.getSerialNumber())).toList();
        if (targets.size() != 1) throw notConfigured();
        var target = targets.getFirst();
        if (blank(target.getExtGuid()) || blank(target.getApartmentId()) || blank(target.getModel())
                || blank(target.getPublishClass())) throw notConfigured();
        var signals = target.getSignals().stream().filter(s -> write.signalKey().equals(s.getSignalKey())
                && mapping.platformName().equals(s.getPlatformName())).toList();
        if (signals.size() != 1) throw notConfigured();
        var signal = signals.getFirst();
        if (blank(signal.getShortName())) throw notConfigured();
        double wire = value == 0 && signal.getZeroWireValue() != null ? signal.getZeroWireValue() : value;
        if (!Double.isFinite(wire)) throw notConfigured();
        PlatformDevice visible;
        try { visible = visible(token, bound.serialNumber()); }
        catch (UjinApiException ex) { throw upstream(ex); }
        boolean missing = visible.signals().stream().noneMatch(s -> mapping.platformName().equals(s.name()));
        var receipt = new PublicationEntity();
        receipt.id = write.publicationId(); receipt.deviceId = deviceId; receipt.owner = owner;
        receipt.signalKey = write.signalKey(); receipt.value = value;
        receipt.plan = json.writeValueAsString(new Plan(bound.serialNumber(), target.getExtGuid(),
                target.getApartmentId(), target.getPublishClass(), mapping.platformName(),
                signal.getShortName(), wire, missing));
        receipt.status = "PENDING"; receipt.reason = "DISPATCHING"; receipt.createdAt = Instant.now();
        return new Prepared(repository.saveAndFlush(receipt), true);
    }

    private void send(Plan plan, String signal) {
        client.publishGroup(config.getUst(), plan.apartmentId(), plan.extGuid(),
                plan.publishClass(), signal, plan.wireValue());
    }

    public List<Receipt> list(UUID deviceId, String owner) {
        return tx.execute(status -> {
            catalog.device(deviceId, owner);
            Map<UUID, Receipt> receipts = new LinkedHashMap<>();
            repository.findByDeviceIdAndOwnerAndStatusIn(deviceId, owner, List.of("PENDING", "UNKNOWN"))
                    .forEach(p -> receipts.put(p.id, view(p)));
            repository.findTop20ByDeviceIdAndOwnerOrderByCreatedAtDesc(deviceId, owner)
                    .forEach(p -> receipts.putIfAbsent(p.id, view(p)));
            return List.copyOf(receipts.values());
        });
    }

    public Receipt get(UUID deviceId, UUID id, String owner) {
        return tx.execute(status -> view(owned(deviceId, id, owner)));
    }

    public Receipt check(UUID deviceId, UUID id, String owner, String token) {
        requireToken(token);
        var receipt = tx.execute(status -> owned(deviceId, id, owner));
        if (receipt.status.equals("FAILED") || receipt.status.equals("CONFIRMED")) return view(receipt);
        // A manual check must not race an in-flight send. After a crash this bounded
        // lease expires; only reading is permitted, never retransmission.
        if (receipt.status.equals("PENDING") && receipt.createdAt.plusSeconds(90).isAfter(Instant.now()))
            return view(receipt);
        Plan plan = json.readValue(receipt.plan, Plan.class);
        try {
            var device = visible(token, plan.serialNumber());
            var matches = device.signals().stream().filter(s -> plan.platformName().equals(s.name())).toList();
            boolean matched = matches.size() == 1 && number(matches.getFirst()) != null
                    && number(matches.getFirst()) == plan.wireValue();
            return finish(id, owner, matched ? "CONFIRMED" : "UNKNOWN",
                    matched ? "VALUE_OBSERVED" : "VALUE_NOT_OBSERVED", Instant.now());
        } catch (UjinApiException ex) {
            return finish(id, owner, "UNKNOWN", "UJIN_" + ex.kind().name(), null);
        } catch (CatalogException ex) {
            return finish(id, owner, "UNKNOWN", "UJIN_DEVICE_NOT_VISIBLE", null);
        }
    }

    private PlatformDevice visible(String token, String sn) {
        var matches = client.fetchDeviceSignals(token).stream().filter(d -> sn.equals(d.id())).toList();
        if (matches.size() != 1)
            throw error(403, "UJIN_DEVICE_NOT_VISIBLE", "Resident session does not uniquely identify this device");
        return matches.getFirst();
    }

    private PublicationEntity owned(UUID deviceId, UUID id, String owner) {
        catalog.device(deviceId, owner);
        return repository.findById(id).filter(p -> owner.equals(p.owner) && deviceId.equals(p.deviceId))
                .orElseThrow(() -> error(404, "PUBLICATION_NOT_FOUND", "Publication not found"));
    }

    private Receipt finish(UUID id, String owner, String status, String reason, Instant observed) {
        return tx.execute(transaction -> {
            store.lock();
            var row = repository.findById(id).filter(p -> owner.equals(p.owner)).orElseThrow();
            // Concurrent readbacks cannot downgrade an already terminal observation.
            if (!row.status.equals("CONFIRMED") && !row.status.equals("FAILED")) {
                row.status = status; row.reason = reason; row.observedAt = observed;
                repository.saveAndFlush(row);
            }
            return view(row);
        });
    }

    private static Double number(PlatformDevice.PlatformSignal signal) {
        if (signal.kind() == PlatformDevice.PlatformSignal.ValueKind.NUMBER)
            return signal.number() != null && Double.isFinite(signal.number()) ? signal.number() : null;
        if (signal.kind() != PlatformDevice.PlatformSignal.ValueKind.STRING || signal.text() == null) return null;
        String text = signal.text().trim();
        if (!text.matches("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$")) return null;
        try { double value = Double.parseDouble(text); return Double.isFinite(value) ? value : null; }
        catch (NumberFormatException ex) { return null; }
    }
    private static Receipt view(PublicationEntity p) {
        return new Receipt(p.id, p.deviceId, p.signalKey, p.value, p.status, p.reason, p.createdAt, p.observedAt);
    }
    private static boolean blank(String text) { return text == null || text.isBlank(); }
    private static void requireToken(String token) {
        if (blank(token)) throw error(401, "UJIN_AUTH_REQUIRED", "Active Ujin session required");
    }
    private static CatalogException notConfigured() {
        return error(503, "PUBLICATION_NOT_CONFIGURED", "No unique operator-assigned publication target for this binding");
    }
    private static CatalogException upstream(UjinApiException ex) {
        int status = switch (ex.kind()) {
            case UNAUTHORIZED -> 401;
            case FORBIDDEN, ACCESS_DENIED -> 403;
            case UNAVAILABLE -> 503;
            default -> 502;
        };
        return error(status, "UJIN_" + ex.kind().name(), "Cannot verify platform access");
    }
    private static CatalogException error(int status, String code, String detail) {
        return new CatalogException(status, code, detail);
    }
}
