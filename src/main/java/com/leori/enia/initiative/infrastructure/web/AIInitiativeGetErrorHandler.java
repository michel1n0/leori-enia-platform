package com.leori.enia.initiative.infrastructure.web;

import com.leori.enia.initiative.application.exception.AIInitiativeNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice(assignableTypes = AIInitiativeController.class)
public class AIInitiativeGetErrorHandler {

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

    record ErrorResponse(String code, String message) {
    }
}
