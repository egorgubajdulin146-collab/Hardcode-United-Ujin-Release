package com.shastage.ujin.datasource.client;

public final class UjinApiException extends IllegalStateException {
    public enum Kind {
        UNAUTHORIZED,
        FORBIDDEN,
        BAD_RESPONSE,
        UNAVAILABLE,
        SIGNAL_NOT_FOUND
    }

    private final Kind kind;

    public UjinApiException(Kind kind) {
        super("Ujin API: " + kind.name());
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
