package com.shastage.ujin.datasource.client;

public final class UjinApiException extends IllegalStateException {
    public enum Kind {
        UNAUTHORIZED,
        FORBIDDEN,
        ACCESS_DENIED,
        PLATFORM_ERROR,
        BAD_RESPONSE,
        UNAVAILABLE,
        SIGNAL_NOT_FOUND
    }

    private final Kind kind;
    private final String detail;

    public UjinApiException(Kind kind) {
        this(kind, null);
    }

    public UjinApiException(Kind kind, String detail) {
        super("Ujin API: " + kind.name() + (detail != null ? " — " + detail : ""));
        this.kind = kind;
        this.detail = detail;
    }

    public Kind kind() {
        return kind;
    }

    public String detail() {
        return detail;
    }
}
