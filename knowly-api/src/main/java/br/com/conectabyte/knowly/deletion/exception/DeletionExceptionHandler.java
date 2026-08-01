package br.com.conectabyte.knowly.deletion.exception;

import br.com.conectabyte.knowly.deletion.dto.DeletionErrorResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DeletionExceptionHandler {

    @ExceptionHandler(DeletionConfirmationInvalidException.class)
    public ResponseEntity<DeletionErrorResponseDto> handleDeletionConfirmationInvalid(
            DeletionConfirmationInvalidException ex) {
        return ResponseEntity.badRequest()
                .body(new DeletionErrorResponseDto("DELETION_CONFIRMATION_INVALID"));
    }
}
