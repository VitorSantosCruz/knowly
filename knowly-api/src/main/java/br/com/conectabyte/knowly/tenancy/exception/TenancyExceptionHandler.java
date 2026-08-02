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

    @ExceptionHandler(StaffUserAlreadyExistsException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleStaffUserAlreadyExists(
            StaffUserAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new TenancyErrorResponseDto("STAFF_USER_ALREADY_EXISTS"));
    }

    @ExceptionHandler(NotificationAlreadyResolvedException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleNotificationAlreadyResolved(
            NotificationAlreadyResolvedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new TenancyErrorResponseDto("NOTIFICATION_ALREADY_RESOLVED"));
    }

    @ExceptionHandler(NotificationNotFoundException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleNotificationNotFound(
            NotificationNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new TenancyErrorResponseDto("NOTIFICATION_NOT_FOUND"));
    }

    @ExceptionHandler(InvalidPaginationException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleInvalidPagination(
            InvalidPaginationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new TenancyErrorResponseDto("INVALID_PAGINATION"));
    }

    @ExceptionHandler(LastAdminRemainingException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleLastAdminRemaining(
            LastAdminRemainingException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new TenancyErrorResponseDto("LAST_ADMIN_REMAINING"));
    }
}
