package br.com.conectabyte.knowly.identity.exception;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.tenancy.dto.TenancyErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Covers {@link IdentityExceptionHandler}'s mapping of {@link InvalidCpfException}, per
 * specify/features/identity-profile-model-v2/PLAN.md's 2026-08-02 amendment (REQ-4a).
 */
class IdentityExceptionHandlerTest {

    private final IdentityExceptionHandler handler = new IdentityExceptionHandler();

    @Test
    void invalidCpfExceptionMapsTo400WithInvalidCpfCodeAndNoSubmittedValue() {
        ResponseEntity<TenancyErrorResponseDto> response =
                handler.handleInvalidCpf(new InvalidCpfException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new TenancyErrorResponseDto("INVALID_CPF"));
    }
}
