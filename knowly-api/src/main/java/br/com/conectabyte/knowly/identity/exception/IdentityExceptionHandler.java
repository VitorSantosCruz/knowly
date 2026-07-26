package br.com.conectabyte.knowly.identity.exception;

import br.com.conectabyte.knowly.tenancy.dto.TenancyErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Exception-to-status mapping for identity-profile-model's own failure modes, following the same
 * shape as {@code br.com.conectabyte.knowly.tenancy.exception.TenancyExceptionHandler}
 * (`PermissionDeniedException`/`TenantAccessDeniedException` etc. are already handled there and
 * reused as-is by {@code UserProfileService}/{@code ProfileEditRequestService}).
 */
@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(PendingProfileEditRequestExistsException.class)
    public ResponseEntity<TenancyErrorResponseDto> handlePendingProfileEditRequestExists(
            PendingProfileEditRequestExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new TenancyErrorResponseDto("PENDING_PROFILE_EDIT_REQUEST_EXISTS"));
    }

    @ExceptionHandler(ProfileFieldConflictException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleProfileFieldConflict(
            ProfileFieldConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new TenancyErrorResponseDto("PROFILE_FIELD_CONFLICT"));
    }

    @ExceptionHandler(ProfileEditRequestNotFoundException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleProfileEditRequestNotFound(
            ProfileEditRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new TenancyErrorResponseDto("PROFILE_EDIT_REQUEST_NOT_FOUND"));
    }

    @ExceptionHandler(ProfileEditRequestAlreadyResolvedException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleProfileEditRequestAlreadyResolved(
            ProfileEditRequestAlreadyResolvedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new TenancyErrorResponseDto("PROFILE_EDIT_REQUEST_ALREADY_RESOLVED"));
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<TenancyErrorResponseDto> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new TenancyErrorResponseDto("USER_NOT_FOUND"));
    }
}
