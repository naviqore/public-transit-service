package org.naviqore.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.naviqore.app.exception.*;
import org.naviqore.service.exception.ConnectionRoutingException;
import org.naviqore.service.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API endpoints using Spring's built-in RFC 9457 Problem Details support via
 * {@link ProblemDetail}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Base URI for problem types using tag URI scheme per RFC 9457 Section 3.1.1 and RFC 4151.
     */
    private static final String PROBLEM_TYPE_BASE = "tag:naviqore.org:";

    // Validation Exceptions (400 Bad Request)

    @ExceptionHandler(InvalidCoordinatesException.class)
    public ProblemDetail handleInvalidCoordinates(InvalidCoordinatesException ex, HttpServletRequest request) {
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Invalid Coordinates", ex.getMessage(),
                "invalid-coordinates", request);
    }

    @ExceptionHandler(InvalidParametersException.class)
    public ProblemDetail handleInvalidParameters(InvalidParametersException ex, HttpServletRequest request) {
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Invalid Parameters", ex.getMessage(), "invalid-parameters",
                request);
    }

    @ExceptionHandler(UnsupportedRoutingFeatureException.class)
    public ProblemDetail handleUnsupportedRoutingFeature(UnsupportedRoutingFeatureException ex,
                                                         HttpServletRequest request) {
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Unsupported Routing Feature", ex.getMessage(),
                "unsupported-routing-feature", request);
    }

    @ExceptionHandler(InvalidDateTimeException.class)
    public ProblemDetail handleInvalidDateTime(InvalidDateTimeException ex, HttpServletRequest request) {
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Invalid DateTime", ex.getMessage(), "invalid-datetime",
                request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        ProblemDetail problemDetail = createProblemDetail(HttpStatus.BAD_REQUEST, "Constraint Violation",
                "Validation failed for one or more parameters.", "constraint-violation", request);

        // Add field-level validation errors as extension property
        Map<String, Object> violations = new HashMap<>();
        ex.getConstraintViolations()
                .forEach(violation -> violations.put(getFieldName(violation),
                        Map.of("rejectedValue", violation.getInvalidValue(), "message", violation.getMessage())));
        problemDetail.setProperty("violations", violations);

        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problemDetail = createProblemDetail(HttpStatus.BAD_REQUEST, "Method Argument Not Valid",
                "Validation failed for one or more fields.", "method-argument-not-valid", request);

        // add field-level validation errors as extension property
        Map<String, Object> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(FieldError::getField, error -> Map.of("rejectedValue",
                                error.getRejectedValue() != null ? error.getRejectedValue() : "null", "message",
                                error.getDefaultMessage() != null ? error.getDefaultMessage() : "Validation failed"),
                        (existing, _) -> existing // Handle duplicate keys
                ));
        problemDetail.setProperty("errors", errors);

        return problemDetail;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        String detail = "Failed to read request body. Please check your JSON syntax and data types.";
        if (ex.getCause() != null) {
            String causeMessage = ex.getCause().getMessage();
            if (causeMessage != null && !causeMessage.isBlank()) {
                detail = causeMessage;
            }
        }

        log.warn("Invalid request body: {}", ex.getMessage());
        return createProblemDetail(HttpStatus.BAD_REQUEST, "Malformed Request Body", detail, "malformed-request-body",
                request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                              HttpServletRequest request) {
        String detail = String.format("Required parameter '%s' of type '%s' is missing.", ex.getParameterName(),
                ex.getParameterType());

        ProblemDetail problemDetail = createProblemDetail(HttpStatus.BAD_REQUEST, "Missing Request Parameter", detail,
                "missing-request-parameter", request);
        problemDetail.setProperty("parameter", ex.getParameterName());
        problemDetail.setProperty("expectedType", ex.getParameterType());

        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                          HttpServletRequest request) {
        // build detail message based on whether the type is an enum or not
        String detail = (ex.getRequiredType() != null && ex.getRequiredType().isEnum()) ? String.format(
                "The value '%s' is not valid for the parameter '%s'; it must be one of the following: %s.",
                ex.getValue(), ex.getName(), Arrays.toString(ex.getRequiredType().getEnumConstants())) : String.format(
                "The value '%s' is not valid for the parameter '%s'; expected type: %s.", ex.getValue(), ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        ProblemDetail problemDetail = createProblemDetail(HttpStatus.BAD_REQUEST, "Type Mismatch", detail,
                "type-mismatch", request);
        problemDetail.setProperty("parameter", ex.getName());
        problemDetail.setProperty("rejectedValue", ex.getValue());
        problemDetail.setProperty("expectedType",
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        return problemDetail;
    }

    // Not Found Exceptions (404 Not Found)

    @ExceptionHandler(StopNotFoundException.class)
    public ProblemDetail handleStopNotFoundInRouting(StopNotFoundException ex, HttpServletRequest request) {
        ProblemDetail problemDetail = createProblemDetail(HttpStatus.NOT_FOUND, "Stop Not Found", ex.getMessage(),
                "stop-not-found", request);
        problemDetail.setProperty("stopId", ex.getStopId());
        if (ex.getStopType().isPresent()) {
            problemDetail.setProperty("stopType", ex.getStopType().get().name().toLowerCase());
        }
        return problemDetail;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFoundException(NotFoundException ex, HttpServletRequest request) {
        return createProblemDetail(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage(), "not-found", request);
    }

    // Service Exceptions (500 Internal Server Error)

    @ExceptionHandler(ConnectionRoutingException.class)
    public ProblemDetail handleConnectionRoutingException(ConnectionRoutingException ex, HttpServletRequest request) {
        log.error("Connection routing failed for request to {}: {}", request.getRequestURI(), ex.getMessage(), ex);

        return createProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Routing Error",
                "Failed to compute route. Please try again later.", "routing-error", request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        // Log the full exception for debugging (important for production troubleshooting)
        log.error("Unexpected error processing request: method={}, uri={}, query={}, error={}", request.getMethod(),
                request.getRequestURI(), request.getQueryString(), ex.getMessage(), ex);

        // Don't expose internal details to clients in production
        return createProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again later.", "internal-server-error", request);
    }

    private ProblemDetail createProblemDetail(HttpStatus status, String title, String detail, String problemType,
                                              HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(URI.create(PROBLEM_TYPE_BASE + problemType));
        problemDetail.setInstance(URI.create(
                request.getRequestURI() + (request.getQueryString() != null ? "?" + request.getQueryString() : "")));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("method", request.getMethod());

        // add request ID if available (for distributed tracing)
        String requestId = request.getHeader("X-Request-ID");
        if (requestId != null && !requestId.isBlank()) {
            problemDetail.setProperty("requestId", requestId);
        }

        return problemDetail;
    }

    private String getFieldName(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        String[] parts = propertyPath.split("\\.");
        return parts.length > 0 ? parts[parts.length - 1] : propertyPath;
    }

}
