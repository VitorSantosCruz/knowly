package br.com.conectabyte.knowly.auth.exception;

import br.com.conectabyte.knowly.auth.dto.AuthErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<AuthErrorResponseDto> handleInvalidCredentials(
            InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponseDto("INVALID_CREDENTIALS"));
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<AuthErrorResponseDto> handleAccountLocked(AccountLockedException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new AuthErrorResponseDto("ACCOUNT_LOCKED"));
    }

    @ExceptionHandler(CaptchaRequiredException.class)
    public ResponseEntity<AuthErrorResponseDto> handleCaptchaRequired(CaptchaRequiredException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new AuthErrorResponseDto("CAPTCHA_REQUIRED"));
    }

    /**
     * Every controller's {@code currentUser()} helper re-resolves the authenticated principal's
     * {@code User} row by email on each request; if the session/authentication is still valid but
     * that row is gone (e.g. account deleted), that's a stale session, not a 404 -- mapped to 401
     * so the frontend's existing 401-triggered redirect-to-login handles it.
     */
    @ExceptionHandler(AuthenticatedUserNotFoundException.class)
    public ResponseEntity<AuthErrorResponseDto> handleAuthenticatedUserNotFound(
            AuthenticatedUserNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponseDto("AUTHENTICATED_USER_NOT_FOUND"));
    }
}
