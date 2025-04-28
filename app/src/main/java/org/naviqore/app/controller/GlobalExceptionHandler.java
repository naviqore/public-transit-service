package org.naviqore.app.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static ResponseEntity<Object> buildErrorResponse(HttpStatusCode statusCode, HttpServletRequest request,
                                                             String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", statusCode.value());
        body.put("error", HttpStatus.valueOf(statusCode.value()).getReasonPhrase());
        body.put("path", request.getRequestURI());
        body.put("message", message);

        return new ResponseEntity<>(body, statusCode);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Object> handleResponseStatusException(ResponseStatusException ex,
                                                                HttpServletRequest request) {
        return buildErrorResponse(ex.getStatusCode(), request, ex.getReason());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                   HttpServletRequest request) {
        String message = String.format(
                "The value '%s' is not valid for the parameter '%s'; it must be one of the following: %s.",
                ex.getValue(), ex.getName(),
                Arrays.toString(Objects.requireNonNull(ex.getRequiredType()).getEnumConstants()));

        return buildErrorResponse(HttpStatus.BAD_REQUEST, request, message);
    }

}
