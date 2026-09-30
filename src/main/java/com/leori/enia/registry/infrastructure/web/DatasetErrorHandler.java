package com.leori.enia.registry.infrastructure.web;

import com.leori.enia.registry.application.exception.DatasetNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice(assignableTypes = DatasetController.class)
public class DatasetErrorHandler {

    @ExceptionHandler(DatasetNotFoundException.class)
    ResponseEntity<ErrorResponse> datasetNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("DATASET_NOT_FOUND", "Dataset not found"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> invalidPathVariable(MethodArgumentTypeMismatchException exception) {
        if ("id".equals(exception.getName())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_DATASET_ID", "Invalid dataset id"));
        }
        throw exception;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ValidationErrorResponse> validationFailed(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(
                "VALIDATION_ERROR", "Request validation failed", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> malformedRequest() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("MALFORMED_REQUEST", "Request body is malformed"));
    }

    record ErrorResponse(String code, String message) {
    }

    record ValidationErrorResponse(String code, String message, Map<String, String> fields) {
    }
}
