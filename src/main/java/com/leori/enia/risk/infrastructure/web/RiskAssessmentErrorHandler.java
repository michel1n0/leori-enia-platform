package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.risk.application.exception.RiskAssessmentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice(assignableTypes = RiskAssessmentController.class)
public class RiskAssessmentErrorHandler {

    @ExceptionHandler(AISystemNotFoundException.class)
    ResponseEntity<ErrorResponse> systemNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("AI_SYSTEM_NOT_FOUND", "AI system not found"));
    }

    @ExceptionHandler(RiskAssessmentNotFoundException.class)
    ResponseEntity<ErrorResponse> riskAssessmentNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RISK_ASSESSMENT_NOT_FOUND", "Risk assessment not found"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> invalidPathVariable(MethodArgumentTypeMismatchException exception) {
        if ("id".equals(exception.getName())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_RISK_ASSESSMENT_ID", "Invalid risk assessment id"));
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
