package com.shastage.ujin.domain.service;

import com.shastage.ujin.datasource.client.PlatformDevice;
import com.shastage.ujin.datasource.client.UjinApiClient;
import com.shastage.ujin.datasource.client.UjinApiException;
import com.shastage.ujin.datasource.entity.BindingSignal;
import com.shastage.ujin.datasource.entity.PlatformBindingEntity;
import com.shastage.ujin.datasource.repository.jpa.PlatformBindingJpaRepository;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.model.Catalog.Device;
import com.shastage.ujin.domain.model.Catalog.Signal;
import com.shastage.ujin.domain.model.PlatformBinding;
import com.shastage.ujin.domain.repository.CatalogStore;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Transactional
public class PlatformBindingService {
    private static final java.util.regex.Pattern DECIMAL =
            java.util.regex.Pattern.compile("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?$");

    private final CatalogService catalogService;
    private final CatalogStore catalogStore;
    private final PlatformBindingJpaRepository bindings;
    private final UjinApiClient client;
    private PublicationProperties publication = new PublicationProperties();

    @org.springframework.beans.factory.annotation.Autowired
    public void setPublicationProperties(PublicationProperties properties) { publication = properties; }

    public PlatformBindingService(
            CatalogService catalogService,
            CatalogStore catalogStore,
            PlatformBindingJpaRepository bindings,
            @Value("${ujin.api.base-url}") URI baseUrl) {
        this.catalogService = catalogService;
        this.catalogStore = catalogStore;
        this.bindings = bindings;
        this.client = new UjinApiClient(baseUrl);
    }

    @Transactional(readOnly = true)
    public PlatformBinding.View getBinding(UUID deviceId, String owner) {
        catalogService.device(deviceId, owner);
        return viewOf(
                load(deviceId, owner)
                        .orElseThrow(() ->
                                new CatalogException(
                                        404,
                                        "PLATFORM_NOT_BOUND",
                                        "Device is not bound to a platform serial number")));
    }

    public PlatformBinding.Saved putBinding(
            UUID deviceId, PlatformBinding.Write write, String owner, String ujinToken) {
        if (write == null) throw invalid("Request body is required");
        Device device = catalogService.device(deviceId, owner);
        String serialNumber = requireSerial(write.serialNumber());
        List<PlatformBinding.Mapping> mappings = requireMappings(write.signalMappings(), device);
        catalogStore.lock();
        Optional<PlatformBindingEntity> existing = load(deviceId, owner);
        PlatformBindingEntity entity;
        boolean created;
        if (existing.isEmpty()) {
            if (write.expectedVersion() != null)
                throw new CatalogException(
                        409,
                        "VERSION_CONFLICT",
                        "Device has no platform binding; omit expectedVersion to bind");
            entity = new PlatformBindingEntity();
            entity.setDeviceId(deviceId);
            entity.setOwner(owner);
            created = true;
        } else {
            if (write.expectedVersion() == null)
                throw new CatalogException(
                        409,
                        "VERSION_CONFLICT",
                        "expectedVersion is required to replace an existing binding");
            version(write.expectedVersion(), existing.get().getVersion() + 1);
            entity = existing.get();
            created = false;
        }
        requireToken(ujinToken);
        requireVisibleSerial(ujinToken, serialNumber);
        entity.setSerialNumber(serialNumber);
        entity.getSignals().clear();
        for (var mapping : mappings) {
            entity.getSignals().add(new BindingSignal(mapping.signalKey(), mapping.platformName()));
        }
        var saved = bindings.saveAndFlush(entity);
        catalogStore.setPlatformLink(deviceId, "BOUND", owner);
        return new PlatformBinding.Saved(viewOf(saved), created);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> readUjinState(UUID deviceId, String owner, String ujinToken) {
        Device device = catalogService.device(deviceId, owner);
        var binding =
                load(deviceId, owner)
                        .orElseThrow(() ->
                                new CatalogException(
                                        409,
                                        "PLATFORM_NOT_BOUND",
                                        "Device is not bound to a platform serial number"));
        requireToken(ujinToken);
        Instant observedAt = Instant.now();
        List<PlatformDevice> snapshot = fetchSnapshot(ujinToken);
        var devices = snapshot.stream()
                .filter(entry -> binding.getSerialNumber().equals(entry.id())).toList();
        if (devices.size() > 1) throw ambiguousSerial();
        PlatformDevice platform = devices.stream().findFirst().orElseThrow(() ->
                new CatalogException(404, "UJIN_DEVICE_NOT_VISIBLE",
                        "Resident session does not see the bound serial number"));
        List<Map<String, Object>> values = new ArrayList<>();
        List<Map<String, Object>> issues = new ArrayList<>();
        for (BindingSignal mapping : binding.getSignals()) {
            String signalKey = mapping.getSignalKey();
            Signal declaration =
                    device.signals().stream()
                            .filter(s -> s.key().equals(signalKey))
                            .findFirst()
                            .orElse(null);
            if (declaration == null) {
                issues.add(issue(signalKey, "BAD_SCHEMA", "Local signal is not declared on device"));
                continue;
            }
            List<PlatformDevice.PlatformSignal> matches =
                    platform.signals().stream()
                            .filter(sample -> mapping.getPlatformName().equals(sample.name()))
                            .toList();
            if (matches.isEmpty()) {
                issues.add(issue(signalKey, "MISSING_SIGNAL", "Signal is absent on the platform"));
                continue;
            }
            if (matches.size() > 1) {
                issues.add(issue(signalKey, "BAD_SCHEMA", "Platform signal name is ambiguous"));
                continue;
            }
            PlatformDevice.PlatformSignal sample = matches.get(0);
            if (sample.kind() == PlatformDevice.PlatformSignal.ValueKind.NULL) {
                issues.add(issue(signalKey, "MISSING_VALUE", "Platform reported no value"));
                continue;
            }
            Double value = strictNumber(sample);
            if (value != null) value = decodeConfiguredZero(binding.getSerialNumber(), signalKey, mapping.getPlatformName(), value);
            if (value == null) {
                issues.add(issue(signalKey, "BAD_SCHEMA", "Value is not a finite number"));
                continue;
            }
            if ((declaration.min() != null && value < declaration.min())
                    || (declaration.max() != null && value > declaration.max())
                    || (declaration.enumValues() != null && !declaration.enumValues().isEmpty()
                        && !declaration.enumValues().contains(value))) {
                issues.add(issue(signalKey, "OUT_OF_RANGE", "Value is outside the declared range"));
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("signalKey", signalKey);
            entry.put("value", value);
            entry.put("updatedAt", observedAt.toString());
            Instant measuredAt = parseInstant(sample.dt());
            if (measuredAt != null) entry.put("measuredAt", measuredAt.toString());
            values.add(entry);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deviceId", deviceId.toString());
        result.put("executionMode", "UJIN");
        result.put("values", values);
        result.put("availability", "UNKNOWN");
        result.put("observedAt", observedAt.toString());
        if (!issues.isEmpty()) result.put("signalIssues", issues);
        return result;
    }

    private Optional<PlatformBindingEntity> load(UUID deviceId, String owner) {
        return bindings.findByDeviceIdAndOwner(deviceId, owner);
    }

    private double decodeConfiguredZero(String serial, String key, String name, double value) {
        var targets = publication.getTargets().stream().filter(t -> serial.equals(t.getSerialNumber())).toList();
        if (targets.size() != 1) return value;
        var signals = targets.getFirst().getSignals().stream()
                .filter(s -> key.equals(s.getSignalKey()) && name.equals(s.getPlatformName())).toList();
        if (signals.size() != 1 || signals.getFirst().getZeroWireValue() == null) return value;
        return value == signals.getFirst().getZeroWireValue() ? 0 : value;
    }

    private static PlatformBinding.View viewOf(PlatformBindingEntity entity) {
        return new PlatformBinding.View(
                entity.getDeviceId(),
                entity.getSerialNumber(),
                entity.getSignals().stream()
                        .map(s -> new PlatformBinding.Mapping(s.getSignalKey(), s.getPlatformName()))
                        .toList(),
                entity.getVersion() + 1);
    }

    private static String requireSerial(String serialNumber) {
        if (serialNumber == null) throw invalid("serialNumber is required");
        String sn = serialNumber.trim();
        if (sn.isEmpty() || sn.length() > 128)
            throw invalid("serialNumber must be 1..128 characters");
        return sn;
    }

    private static List<PlatformBinding.Mapping> requireMappings(
            List<PlatformBinding.Mapping> mappings, Device device) {
        if (mappings == null || mappings.isEmpty() || mappings.size() > 100)
            throw invalid("signalMappings are required");
        Set<String> keys = new HashSet<>();
        Set<String> names = new HashSet<>();
        List<PlatformBinding.Mapping> result = new ArrayList<>();
        for (var mapping : mappings) {
            if (mapping == null) throw invalid("Invalid signal mapping");
            String key = mapping.signalKey();
            String name = mapping.platformName();
            if (key == null || key.length() > 64 || !key.matches("^[a-z][a-z0-9_]*$"))
                throw invalid("Invalid signalKey");
            if (name == null || name.isBlank() || name.length() > 255)
                throw invalid("Invalid platformName");
            if (device.signals().stream().noneMatch(s -> s.key().equals(key)))
                throw invalid("Unknown signal key: " + key);
            if (!keys.add(key)) throw invalid("Duplicate signal key in mapping");
            if (!names.add(name)) throw invalid("Ambiguous platform name in mapping");
            result.add(new PlatformBinding.Mapping(key, name));
        }
        return List.copyOf(result);
    }

    private void requireToken(String ujinToken) {
        if (ujinToken == null || ujinToken.isBlank())
            throw new CatalogException(
                    401, "UJIN_AUTH_REQUIRED", "Active Ujin session is required");
    }

    private void requireVisibleSerial(String ujinToken, String serialNumber) {
        long matches = fetchSnapshot(ujinToken).stream()
                .filter(d -> serialNumber.equals(d.id())).count();
        if (matches > 1) throw ambiguousSerial();
        if (matches == 0)
            throw new CatalogException(
                    422,
                    "UJIN_SN_NOT_VISIBLE",
                    "Resident session does not see this serial number");
    }

    private static CatalogException ambiguousSerial() {
        return new CatalogException(502, "UJIN_BAD_RESPONSE",
                "Platform snapshot contains an ambiguous device serial number");
    }

    private List<PlatformDevice> fetchSnapshot(String ujinToken) {
        try {
            return client.fetchDeviceSignals(ujinToken);
        } catch (UjinApiException error) {
            throw upstream(error);
        }
    }

    private static CatalogException upstream(UjinApiException error) {
        return new CatalogException(
                switch (error.kind()) {
                    case UNAUTHORIZED -> 401;
                    case FORBIDDEN, ACCESS_DENIED -> 403;
                    case UNAVAILABLE -> 503;
                    case SIGNAL_NOT_FOUND -> 404;
                    case BAD_RESPONSE, PLATFORM_ERROR -> 502;
                },
                "UJIN_" + error.kind().name(),
                switch (error.kind()) {
                    case UNAUTHORIZED -> "Ujin authentication is required";
                    case FORBIDDEN, ACCESS_DENIED -> "No access to device data";
                    case UNAVAILABLE -> "Ujin platform is unavailable";
                    case SIGNAL_NOT_FOUND -> "Requested device or signal was not found";
                    case BAD_RESPONSE -> "Ujin returned an invalid response";
                    case PLATFORM_ERROR -> "Ujin returned a platform error";
                });
    }

    private static Double strictNumber(PlatformDevice.PlatformSignal sample) {
        Double value =
                switch (sample.kind()) {
                    case NUMBER -> sample.number();
                    case STRING -> parseDecimal(sample.text());
                    case BOOLEAN, NULL, OTHER -> null;
                };
        return value != null && Double.isFinite(value) ? value : null;
    }

    private static Double parseDecimal(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!DECIMAL.matcher(trimmed).matches()) return null;
        try {
            return Double.valueOf(trimmed);
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static Instant parseInstant(String dt) {
        if (dt == null || dt.isBlank()) return null;
        try {
            return Instant.parse(dt);
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private static Map<String, Object> issue(String signalKey, String status, String detail) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("signalKey", signalKey);
        issue.put("status", status);
        issue.put("detail", detail);
        return issue;
    }

    private static void version(Long expected, long actual) {
        if (expected == null || expected <= 0)
            throw invalid("expectedVersion is required and positive");
        if (expected.longValue() != actual)
            throw new CatalogException(
                    409, "VERSION_CONFLICT", "Object has changed; reload before saving");
    }

    private static CatalogException invalid(String detail) {
        return new CatalogException(422, "VALIDATION_ERROR", detail);
    }
}
