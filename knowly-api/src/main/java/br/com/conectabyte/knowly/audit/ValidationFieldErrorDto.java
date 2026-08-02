package br.com.conectabyte.knowly.audit;

/**
 * One {@code @Valid} field-validation failure, per {@link ValidationErrorResponseDto}'s contract --
 * shape matches what the frontend's {@code complete-profile-page.component.ts}/{@code
 * tenant-create-page.component.ts} already parse (`{"errors": [{"field": ..., "message": ...}]}`).
 */
public record ValidationFieldErrorDto(String field, String message) {}
