package br.com.conectabyte.knowly.auth.exception;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.auth.dto.AuthErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    @Test
    void mapsInvalidCredentialsTo401() {
        ResponseEntity<AuthErrorResponseDto> response =
                handler.handleInvalidCredentials(new InvalidCredentialsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void mapsAccountLockedTo429() {
        ResponseEntity<AuthErrorResponseDto> response =
                handler.handleAccountLocked(new AccountLockedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void mapsCaptchaRequiredTo400() {
        ResponseEntity<AuthErrorResponseDto> response =
                handler.handleCaptchaRequired(new CaptchaRequiredException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("CAPTCHA_REQUIRED");
    }
}
