package com.aiantfarm.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest req) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> fe.getField() + " " + (fe.getDefaultMessage() == null ? "is invalid" : fe.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        if (message.isBlank()) message = "Validation failed";

        return warn(HttpStatus.BAD_REQUEST, message, req, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest req) {

        String message = ex.getConstraintViolations()
                .stream()
                .map(ApiErrorHandler::formatViolation)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) message = "Constraint violation";

        return warn(HttpStatus.BAD_REQUEST, message, req, ex);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return warn(HttpStatus.BAD_REQUEST, "Malformed request body", req, ex);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return warn(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'", req, ex);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return warn(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed", req, ex);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(
            NoHandlerFoundException ex, HttpServletRequest req) {
        return warn(HttpStatus.NOT_FOUND, "Resource not found", req, ex);
    }

    // ---- Propagated statuses --------------------------------------------------------------------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest req) {

        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = (ex.getReason() == null || ex.getReason().isBlank())
                ? defaultMessage(status)
                : ex.getReason();

        return status.is4xxClientError()
                ? warn(status, message, req, ex)
                : error(status, message, req, ex);
    }

    // ---- 5xx: catch-all -------------------------------------------------------------------------

    @ExceptionHandler(org.springframework.security.authentication.BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            org.springframework.security.authentication.BadCredentialsException ex, HttpServletRequest req) {
        return warn(HttpStatus.UNAUTHORIZED, "Invalid Username/Password", req, ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest req) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", req, ex);
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static String formatViolation(ConstraintViolation<?> v) {
        String path = v.getPropertyPath() == null ? "" : v.getPropertyPath().toString();
        String msg = Objects.toString(v.getMessage(), "is invalid");
        return path.isBlank() ? msg : (path + " " + msg);
    }

    private ResponseEntity<ErrorResponse> warn(HttpStatus status, String message, HttpServletRequest req, Exception ex) {
        String tid = traceId(req);
        // Log at WARN including the exception so stacktrace is recorded
        log.warn("[{}] {} (traceId={}, ex={} : {})", status.value(), message, tid, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return build(status, message, tid);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message, HttpServletRequest req, Exception ex) {
        String tid = traceId(req);
        // Log at ERROR including the exception so stacktrace is recorded
        log.error("[{}] {} (traceId={}, ex={} : {})", status.value(), message, tid, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return build(status, message, tid);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String traceId) {
        return ResponseEntity.status(status).body(new ErrorResponse(codeFrom(status), message, traceId));
    }

    private static String defaultMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Bad request";
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "HTTP method not allowed";
            case UNAUTHORIZED -> "Unauthorized";
            case FORBIDDEN -> "Forbidden";
            default -> status.getReasonPhrase();
        };
    }

    private static String codeFrom(HttpStatus status) {
        return status.is5xxServerError() ? "INTERNAL_ERROR" : status.name(); // e.g., BAD_REQUEST, NOT_FOUND
    }

    private String traceId(HttpServletRequest req) {
        // Prefer MDC, fall back to common headers, then random UUID
        String tid = firstNonBlank(
                MDC.get("traceId"), MDC.get("trace_id"), MDC.get("X-B3-TraceId"), MDC.get("trace.id"),
                req.getHeader("X-Request-Id"), req.getHeader("X-Amzn-Trace-Id"), req.getHeader("traceparent")
        );
        if (isBlank(tid)) {
            Object attr = req.getAttribute("traceId");
            if (attr != null) tid = String.valueOf(attr);
        }
        return isBlank(tid) ? UUID.randomUUID().toString() : tid;
    }

    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (!isBlank(v)) return v;
        return null;
    }

    // Standardized response payload
    public record ErrorResponse(String code, String message, String traceId) {}
}
