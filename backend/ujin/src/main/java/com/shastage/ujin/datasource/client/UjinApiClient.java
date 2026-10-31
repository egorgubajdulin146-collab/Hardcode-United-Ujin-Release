package com.shastage.ujin.datasource.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class UjinApiClient {
    private final URI base;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public UjinApiClient(URI base) {
        boolean loopback = "http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost());
        if ((!"https".equals(base.getScheme()) && !loopback)
                || base.getHost() == null
                || base.getUserInfo() != null
                || base.getQuery() != null
                || base.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Use an HTTPS API base URL without credentials or query parameters");
        }
        this.base = URI.create(base.toString().replaceAll("/+$", ""));
    }

    // ===== Чтение сигнала через apartment/get-device-signals (резидент) =====
    public String readSignal(String token, String serialnumber, String signalName) {
        requireValue(token, "token");
        requireValue(serialnumber, "serialnumber");
        requireValue(signalName, "signalName");

        URI uri = URI.create(base + "/apartment/get-device-signals/?token=" + encode(token));
        JsonNode root = executeGet(uri);

        JsonNode data = root.path("data");
        JsonNode devices = data.path("devices");
        // Проверка схемы: data.devices должен быть массивом
        if (!devices.isArray()) {
            throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
        }
        for (JsonNode device : devices) {
            if (!device.isObject()) {
                throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
            }
            if (serialnumber.equals(device.path("id").asString(""))) {
                JsonNode signals = device.path("signals");
                if (!signals.isArray()) {
                    throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
                }
                for (JsonNode signal : signals) {
                    if (!signal.isObject()) {
                        throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
                    }
                    if (signalName.equals(signal.path("name").asString(""))) {
                        JsonNode value = signal.path("value");
                        if (value.isMissingNode() || value.isNull()) {
                            throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
                        }
                        // Поддерживаем числа (включая 0), строки и boolean (включая false)
                        if (value.isNumber() || value.isString() || value.isBoolean()) {
                            return value.asString("");
                        }
                        throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
                    }
                }
                throw new UjinApiException(UjinApiException.Kind.SIGNAL_NOT_FOUND);
            }
        }
        throw new UjinApiException(UjinApiException.Kind.SIGNAL_NOT_FOUND);
    }

    // ===== Полный снимок назначений резидента: один запрос на вызов =====
    public List<PlatformDevice> fetchDeviceSignals(String token) {
        requireValue(token, "token");
        URI uri = URI.create(base + "/apartment/get-device-signals/?token=" + encode(token));
        JsonNode root = executeGet(uri);
        JsonNode devices = root.path("data").path("devices");
        if (!devices.isArray()) {
            throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
        }
        List<PlatformDevice> result = new ArrayList<>();
        for (JsonNode device : devices) {
            if (!device.isObject()) {
                throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
            }
            JsonNode signals = device.path("signals");
            if (!signals.isArray()) {
                throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
            }
            List<PlatformDevice.PlatformSignal> parsed = new ArrayList<>();
            for (JsonNode signal : signals) {
                if (!signal.isObject()) {
                    throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
                }
                parsed.add(parseSignal(signal));
            }
            result.add(new PlatformDevice(device.path("id").asString(""), List.copyOf(parsed)));
        }
        return List.copyOf(result);
    }

    private static PlatformDevice.PlatformSignal parseSignal(JsonNode signal) {
        String name = signal.path("name").asString("");
        String dt = signal.path("dt").asString(null);
        JsonNode value = signal.path("value");
        if (value.isMissingNode() || value.isNull()) {
            return new PlatformDevice.PlatformSignal(
                    name, PlatformDevice.PlatformSignal.ValueKind.NULL, null, null, null, dt);
        }
        if (value.isNumber()) {
            return new PlatformDevice.PlatformSignal(
                    name, PlatformDevice.PlatformSignal.ValueKind.NUMBER, value.asDouble(), null, null, dt);
        }
        if (value.isString()) {
            return new PlatformDevice.PlatformSignal(
                    name, PlatformDevice.PlatformSignal.ValueKind.STRING, null, value.asString(), null, dt);
        }
        if (value.isBoolean()) {
            return new PlatformDevice.PlatformSignal(
                    name, PlatformDevice.PlatformSignal.ValueKind.BOOLEAN, null, null, value.asBoolean(), dt);
        }
        return new PlatformDevice.PlatformSignal(
                name, PlatformDevice.PlatformSignal.ValueKind.OTHER, null, null, null, dt);
    }

    // ===== Аутентификация =====
    public String authenticate(String login, String password, String app) {
        requireValue(login, "login");
        requireValue(password, "password");
        if (app == null || app.isBlank()) return authenticateRequest(login, password, null);
        try {
            return authenticateRequest(login, password, app);
        } catch (UjinApiException error) {
            if (error.kind() != UjinApiException.Kind.UNAUTHORIZED) throw error;
            return authenticateRequest(login, password, null);
        }
    }

    // ===== Вспомогательные методы =====

    private JsonNode executeGet(URI uri) {
        HttpURLConnection connection = null;
        String response;
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status != 200) {
                throw httpError(status);
            }
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(1_048_577);
                if (bytes.length > 1_048_576) {
                    throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
                }
                response = new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException error) {
            throw new UjinApiException(UjinApiException.Kind.UNAVAILABLE);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        try {
            JsonNode root = mapper.readTree(response);
            if (root == null || !root.path("error").isIntegralNumber()) {
                throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
            }
            if (root.path("error").asInt() != 0) {
                throw classifyPlatformError(root);
            }
            return root;
        } catch (RuntimeException error) {
            if (error instanceof UjinApiException uae) throw uae;
            throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
        }
    }

    // Классификация ошибок платформы — только фиксированные коды
    private UjinApiException classifyPlatformError(JsonNode root) {
        String errorCode = root.path("error_code").asString("");

        // Явный отказ доступа
        if ("Access denied".equals(errorCode)) {
            return new UjinApiException(UjinApiException.Kind.ACCESS_DENIED);
        }

        // Неизвестная ошибка платформы
        return new UjinApiException(UjinApiException.Kind.PLATFORM_ERROR);
    }

    private String authenticateRequest(String login, String password, String app) {
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(base + "/auth/authenticate/");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty(
                    "Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            byte[] body =
                    ("login="
                                    + encode(login)
                                    + "&password="
                                    + encode(password)
                                    + (app == null ? "" : "&app=" + encode(app)))
                            .getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            if (status != 200) {
                throw httpError(status);
            }
            JsonNode root;
            try (var input = connection.getInputStream()) {
                byte[] response = input.readNBytes(1_048_577);
                if (response.length > 1_048_576)
                    throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
                try {
                    root = mapper.readTree(new String(response, StandardCharsets.UTF_8));
                } catch (RuntimeException error) {
                    throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
                }
            }
            if (root != null
                    && root.path("error").isIntegralNumber()
                    && root.path("error").asInt() == 1) {
                throw new UjinApiException(UjinApiException.Kind.UNAUTHORIZED);
            }
            if (root == null
                    || !root.path("error").isIntegralNumber()
                    || root.path("error").asInt() != 0) {
                throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
            }
            String token = root.path("data").path("user").path("token").asString("");
            if (token.isBlank()) token = root.path("data").path("token").asString("");
            if (token.isBlank()) throw new UjinApiException(UjinApiException.Kind.BAD_RESPONSE);
            return token;
        } catch (IOException error) {
            throw new UjinApiException(UjinApiException.Kind.UNAVAILABLE);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static UjinApiException httpError(int status) {
        return new UjinApiException(
                switch (status) {
                    case 401 -> UjinApiException.Kind.UNAUTHORIZED;
                    case 403 -> UjinApiException.Kind.ACCESS_DENIED;
                    default -> UjinApiException.Kind.BAD_RESPONSE;
                });
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
