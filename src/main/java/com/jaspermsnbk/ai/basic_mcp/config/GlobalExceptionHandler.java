package com.jaspermsnbk.ai.basic_mcp.config;

import com.jaspermsnbk.ai.basic_mcp.service.DuplicateDocumentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    public record ErrorResponse(String error, String message, int status) {}

    @ExceptionHandler(DuplicateDocumentException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateDocumentException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("DUPLICATE_DOCUMENT", e.getMessage(), 409));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIo(IOException e) {
        return ResponseEntity.status(HttpStatusCode.valueOf(422))
            .body(new ErrorResponse("PROCESSING_ERROR", "Could not read PDF", 422));
    }
}
