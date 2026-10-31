package com.shastage.ujin.domain.exception;

public class CatalogException extends RuntimeException {
    public final int status;
    public final String code;
    public CatalogException(int status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }
}
