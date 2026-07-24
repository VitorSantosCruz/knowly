package br.com.conectabyte.knowly.tenancy.exception;

import br.com.conectabyte.knowly.tenancy.dto.TenancyErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TenancyExceptionHandler {

    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<TenancyErrorResponseDto> handlePermissionDenied(
            PermissionDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new TenancyErrorResponseDto("PERMISSION_DENIED"));
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleTenantAccessDenied(
            TenantAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new TenancyErrorResponseDto("TENANT_ACCESS_DENIED"));
    }

    @ExceptionHandler(TenantSelectionRequiredException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleTenantSelectionRequired(
            TenantSelectionRequiredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new TenancyErrorResponseDto("TENANT_SELECTION_REQUIRED"));
    }
}
