package com.shastage.ujin.datasource.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class UjinApiClient {
    private final URI base;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public UjinApiClient(URI base) {
        boolean loopback = "http".equals(base.getScheme()) && "127.0.0.1".equals(base.getHost());
        if ((!"https".equals(base.getScheme()) && !loopback) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("Use an HTTPS API base URL without credentials or query parameters");
        }
        this.base = URI.create(base.toString().replaceAll("/+$", ""));
    }

    public String readSignal(String token, String serialnumber, String signalName) {
        requireValue(token, "token");
        requireValue(serialnumber, "serialnumber");
        requireValue(signalName, "signalName");
        URI uri = URI.create(base + "/bmsdevice/get-device-info/?token=" + encode(token)
                + "&serialnumber=" + encode(serialnumber));
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
                throw new IllegalStateException("Ujin returned HTTP " + status);
            }
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(1_048_577);
                if (bytes.length > 1_048_576) {
                    throw new IllegalStateException("Ujin response is too large");
                }
                response = new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to read Ujin API; check connection and access");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        JsonNode root;
        try {
            root = mapper.readTree(response);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Ujin returned invalid JSON");
        }
        if (root == null || !root.path("error").isIntegralNumber() || root.path("error").asInt() != 0) {
            throw new IllegalStateException("Ujin rejected the request");
        }
        for (JsonNode signal : root.path("data").path("signals")) {
            if (signalName.equals(signal.path("name").asString(""))) {
                JsonNode value = signal.path("state").path("value");
                if (value.isNumber() || value.isString() || value.isBoolean()) {
                    return value.asString();
                }
                throw new IllegalStateException("Requested signal has no scalar value");
            }
        }
        throw new IllegalStateException("Requested signal was not found");
    }

    public String authenticate(String login, String password, String app) {
        requireValue(login, "login");
        requireValue(password, "password");
        requireValue(app, "app");
        HttpURLConnection connection = null;
        try {
            URI uri = URI.create(base + "/auth/authenticate/");
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);
            byte[] body = ("login=" + encode(login) + "&password=" + encode(password)
                    + "&app=" + encode(app)).getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            if (connection.getResponseCode() != 200) {
                throw new IllegalStateException("Ujin authentication failed");
            }
            JsonNode root;
            try (var input = connection.getInputStream()) {
                byte[] response = input.readNBytes(1_048_577);
                if (response.length > 1_048_576) throw new IllegalStateException("Ujin response is too large");
                root = mapper.readTree(new String(response, StandardCharsets.UTF_8));
            }
            if (root == null || !root.path("error").isIntegralNumber() || root.path("error").asInt() != 0) {
                throw new IllegalStateException("Ujin authentication rejected");
            }
            String token = root.path("data").path("user").path("token").asString("");
            if (token.isBlank()) token = root.path("data").path("token").asString("");
            if (token.isBlank()) throw new IllegalStateException("Ujin authentication returned no token");
            return token;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to authenticate with Ujin");
        } finally {
            if (connection != null) connection.disconnect();
        }
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
