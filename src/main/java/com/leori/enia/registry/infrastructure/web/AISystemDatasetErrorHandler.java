package com.leori.enia.registry.infrastructure.web;

import com.leori.enia.governance.application.exception.AISystemNotFoundException;
import com.leori.enia.registry.application.exception.DatasetAlreadyAssociatedWithAISystemException;
import com.leori.enia.registry.application.exception.DatasetNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AISystemDatasetController.class)
public class AISystemDatasetErrorHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> invalidPathVariable(MethodArgumentTypeMismatchException exception) {
        if ("aiSystemId".equals(exception.getName())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_AI_SYSTEM_ID", "Invalid AI system id"));
        }
        if ("datasetId".equals(exception.getName())) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_DATASET_ID", "Invalid dataset id"));
        }
        throw exception;
    }

    @ExceptionHandler(AISystemNotFoundException.class)
    ResponseEntity<ErrorResponse> systemNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("AI_SYSTEM_NOT_FOUND", "AI system not found"));
    }

    @ExceptionHandler(DatasetNotFoundException.class)
    ResponseEntity<ErrorResponse> datasetNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("DATASET_NOT_FOUND", "Dataset not found"));
    }

    @ExceptionHandler(DatasetAlreadyAssociatedWithAISystemException.class)
    ResponseEntity<ErrorResponse> duplicateAssociation() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(
                        "DATASET_ALREADY_ASSOCIATED_WITH_AI_SYSTEM",
                        "Dataset already associated with AI system"
                ));
    }

    record ErrorResponse(String code, String message) {
    }
}
