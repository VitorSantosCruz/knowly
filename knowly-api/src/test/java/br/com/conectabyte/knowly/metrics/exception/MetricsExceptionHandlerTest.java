package br.com.conectabyte.knowly.metrics.exception;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.metrics.dto.MetricsErrorResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class MetricsExceptionHandlerTest {

    private final MetricsExceptionHandler handler = new MetricsExceptionHandler();

    @Test
    void invalidPeriodExceptionMapsTo400WithInvalidPeriodCode() {
        ResponseEntity<MetricsErrorResponseDto> response =
                handler.handleInvalidPeriod(new InvalidPeriodException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new MetricsErrorResponseDto("INVALID_PERIOD"));
    }
}
