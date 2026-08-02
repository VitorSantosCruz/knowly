package br.com.conectabyte.knowly.tenancy.exception;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.tenancy.dto.TenancyErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenancyExceptionHandlerTest {

    private final TenancyExceptionHandler handler = new TenancyExceptionHandler();

    @Test
    void invalidPaginationExceptionMapsTo400WithInvalidPaginationCode() {
        ResponseEntity<TenancyErrorResponseDto> response =
                handler.handleInvalidPagination(new InvalidPaginationException("Invalid page"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new TenancyErrorResponseDto("INVALID_PAGINATION"));
    }

    @Test
    void tenantAlreadyExistsExceptionMapsTo409WithTenantAlreadyExistsCode() {
        ResponseEntity<TenancyErrorResponseDto> response =
                handler.handleTenantAlreadyExists(new TenantAlreadyExistsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody())
                .isEqualTo(new TenancyErrorResponseDto("TENANT_ALREADY_EXISTS"));
    }
}
