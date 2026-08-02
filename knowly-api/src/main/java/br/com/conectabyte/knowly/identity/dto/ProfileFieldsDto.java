package br.com.conectabyte.knowly.identity.dto;

import java.util.List;

/**
 * Plaintext personal-data fields (excluding {@code avatarUrl}, which is self-only and never part
 * of this shared direct-edit/request DTO -- REQ-10), used both as the request body for direct
 * edit/submit-request and as part of {@code UserProfileDto}/{@code ProfileEditRequestDto}
 * responses. {@code taxId} is decrypted into this shape only after the caller's applicable right
 * has already been confirmed (REQ-4). No blind-index field is ever part of this DTO -- derived,
 * not client-settable. {@code countryCode} (ISO 3166-1 alpha-2) drives {@code taxId}'s
 * country-conditional validation and the associated {@code address}'s default country (REQ-1b).
 * {@code rg}/{@code rgOrgaoEmissor}/{@code birthDate} were removed entirely, 2026-08-02.
 */
public record ProfileFieldsDto(
        String fullName,
        String taxId,
        String countryCode,
        AddressDto address,
        List<ContactDto> contacts) {}
