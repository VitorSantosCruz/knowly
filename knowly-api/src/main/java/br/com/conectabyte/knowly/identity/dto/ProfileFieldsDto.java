package br.com.conectabyte.knowly.identity.dto;

/**
 * Plaintext personal-data fields, used both as the request body for direct edit/submit-request and
 * as part of {@code UserProfileDto}/{@code ProfileEditRequestDto} responses. {@code rg}/{@code cpf}
 * are decrypted into this shape only after the caller's applicable right has already been confirmed
 * (REQ-4). No blind-index field is ever part of this DTO -- derived, not client-settable.
 */
public record ProfileFieldsDto(
        String fullName, String address, String rg, String cpf, String phone) {}
