package br.com.conectabyte.knowly.metrics.exception;

import br.com.conectabyte.knowly.metrics.dto.MetricsErrorResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MetricsExceptionHandler {

    @ExceptionHandler(InvalidPeriodException.class)
    public ResponseEntity<MetricsErrorResponseDto> handleInvalidPeriod(InvalidPeriodException ex) {
        return ResponseEntity.badRequest().body(new MetricsErrorResponseDto("INVALID_PERIOD"));
    }
}
