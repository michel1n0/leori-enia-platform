package com.leori.enia.governance.infrastructure.web;

import com.leori.enia.governance.application.exception.AISystemAlreadyRegisteredException;
import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.domain.exception.AIInitiativeNotApprovedForSystemRegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice(assignableTypes = AISystemController.class)
public class AISystemErrorHandler {

    @ExceptionHandler(AIInitiativeNotFoundException.class)
    ResponseEntity<ErrorResponse> initiativeNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("AI_INITIATIVE_NOT_FOUND", "AI initiative not found"));
    }

    @ExceptionHandler(AIInitiativeNotApprovedForSystemRegistrationException.class)
    ResponseEntity<ErrorResponse> initiativeNotApproved() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "AI_INITIATIVE_NOT_APPROVED_FOR_SYSTEM_REGISTRATION",
                        "AI initiative is not approved for AI system registration"));
    }

    @ExceptionHandler(AISystemAlreadyRegisteredException.class)
    ResponseEntity<ErrorResponse> alreadyRegistered() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("AI_SYSTEM_ALREADY_REGISTERED", "AI system already registered"));
    }

    @ExceptionHandler(AISystemNotFoundException.class)
    ResponseEntity<ErrorResponse> systemNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("AI_SYSTEM_NOT_FOUND", "AI system not found"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> invalidPathVariable(MethodArgumentTypeMismatchException exception) {
        if ("id".equals(exception.getName())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_AI_SYSTEM_ID", "Invalid AI system id"));
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
