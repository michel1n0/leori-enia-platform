package com.leori.enia.risk.infrastructure.web;

import com.leori.enia.risk.application.exception.RiskAssessmentNotFoundException;
import com.leori.enia.risk.application.exception.RiskFindingNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice(assignableTypes = ControlController.class)
public class ControlErrorHandler {

    @ExceptionHandler(RiskAssessmentNotFoundException.class)
    ResponseEntity<ErrorResponse> riskAssessmentNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RISK_ASSESSMENT_NOT_FOUND", "Risk assessment not found"));
    }

    @ExceptionHandler(RiskFindingNotFoundException.class)
    ResponseEntity<ErrorResponse> riskFindingNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("RISK_FINDING_NOT_FOUND", "Risk finding not found"));
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
