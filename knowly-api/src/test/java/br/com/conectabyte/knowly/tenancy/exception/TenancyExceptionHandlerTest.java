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
}
