package com.shastage.ujin.web;

import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.web.controller.CatalogController;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {CatalogController.class, com.shastage.ujin.web.controller.RuntimeController.class, com.shastage.ujin.web.controller.UjinSignalController.class, com.shastage.ujin.web.controller.PlatformBindingController.class})
public class CatalogErrors {
    public record Problem(String type, String title, int status, String code, String detail) {}

    @ExceptionHandler(CatalogException.class)
    public ResponseEntity<Problem> catalog(CatalogException error) {
        return ResponseEntity.status(error.status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(
                        new Problem(
                                "about:blank",
                                HttpStatus.valueOf(error.status).getReasonPhrase(),
                                error.status,
                                error.code,
                                error.getMessage()));
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Problem> staleVersion(Exception error) {
        return catalog(
                new CatalogException(
                        409, "VERSION_CONFLICT", "Object has changed; reload before saving"));
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<Problem> malformed(Exception error) {
        return catalog(
                new CatalogException(400, "INVALID_REQUEST", "Invalid request body or parameter"));
    }
}
