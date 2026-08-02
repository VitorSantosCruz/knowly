package br.com.conectabyte.knowly.audit;

import java.util.List;

/**
 * The structured 400 body every {@code @Valid} DTO validation failure in the app returns, per the
 * frontend's existing error-to-field-name mapping contract (see {@link
 * CreationValidationAuditAdvice}'s Javadoc for how this got wired in as the app's sole {@code
 * MethodArgumentNotValidException} handler).
 */
public record ValidationErrorResponseDto(List<ValidationFieldErrorDto> errors) {}
