package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.application.exception.AIInitiativeInvalidTransitionException;
import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import com.leori.enia.initiative.application.exception.AIInitiativeRevisionMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.Map;
import java.util.TreeMap;

@RestControllerAdvice(assignableTypes = AIInitiativeController.class)
public class AIInitiativeErrorHandler {

    @ExceptionHandler(AIInitiativeETagCodec.MissingIfMatchException.class)
    ResponseEntity<ErrorResponse> missingIfMatch() {
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                .body(new ErrorResponse("IF_MATCH_REQUIRED", "If-Match is required"));
    }

    @ExceptionHandler(AIInitiativeETagCodec.InvalidIfMatchException.class)
    ResponseEntity<ErrorResponse> invalidIfMatch() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_IF_MATCH", "If-Match must be a strong initiative ETag"));
    }

    @ExceptionHandler(AIInitiativeRevisionMismatchException.class)
    ResponseEntity<ErrorResponse> revisionMismatch() {
        return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED)
                .body(new ErrorResponse("AI_INITIATIVE_REVISION_MISMATCH", "AI initiative has changed"));
    }

    @ExceptionHandler(AIInitiativeInvalidTransitionException.class)
    ResponseEntity<ErrorResponse> invalidTransition() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_INITIATIVE_TRANSITION", "AI initiative cannot make this transition"));
    }

    @ExceptionHandler(AIInitiativeNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("AI_INITIATIVE_NOT_FOUND", "AI initiative not found"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> invalidId(MethodArgumentTypeMismatchException exception) {
        if (!"id".equals(exception.getName()) || exception.getRequiredType() != UUID.class) {
            throw exception;
        }
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("INVALID_INITIATIVE_ID", "Initiative id must be a UUID"));
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
