package com.leori.enia.evidence.infrastructure.web;

import com.leori.enia.evidence.application.exception.EvidenceNotFoundException;
import com.leori.enia.risk.application.exception.ControlImplementationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice(assignableTypes = EvidenceController.class)
public class EvidenceErrorHandler {

    @ExceptionHandler(ControlImplementationNotFoundException.class)
    ResponseEntity<ErrorResponse> controlImplementationNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("CONTROL_IMPLEMENTATION_NOT_FOUND", "Control implementation not found"));
    }

    @ExceptionHandler(EvidenceNotFoundException.class)
    ResponseEntity<ErrorResponse> evidenceNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("EVIDENCE_NOT_FOUND", "Evidence not found"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> invalidPathVariable(MethodArgumentTypeMismatchException exception) {
        if ("controlImplementationId".equals(exception.getName())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse(
                            "INVALID_CONTROL_IMPLEMENTATION_ID", "Invalid control implementation id"));
        }
        if ("id".equals(exception.getName())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_EVIDENCE_ID", "Invalid evidence id"));
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
