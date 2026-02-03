package com.erp.cashier.controller;

import com.erp.cashier.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

/**
 * Global API exception handler returning JSON error payloads.
 *
 * @author ERP Cashier Team
 * @since 2025-01-02
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Handles response status exceptions.
     *
     * @param exception response status exception
     * @return error response with status code
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = (HttpStatus) exception.getStatusCode();
        String message = exception.getReason() != null ? exception.getReason() : status.getReasonPhrase();
        return Mono.just(ResponseEntity.status(status).body(new ErrorResponse(message)));
    }

    /**
     * Handles request body parsing errors.
     *
     * @param exception input exception
     * @return error response with status code
     */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInput(ServerWebInputException exception) {
        String message = exception.getReason();
        if (!StringUtils.hasText(message)) {
            message = "Invalid request payload";
        }
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(message)));
    }

    /**
     * Handles uncaught exceptions.
     *
     * @param exception unexpected exception
     * @return error response with status code
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGeneric(Exception exception) {
        LOGGER.error("Unhandled exception", exception);
        String message = "Unexpected server error";
        if (exception instanceof IllegalArgumentException && StringUtils.hasText(exception.getMessage())) {
            message = exception.getMessage();
        }
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(message)));
    }
}
