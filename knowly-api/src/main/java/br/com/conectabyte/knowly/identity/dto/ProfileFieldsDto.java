package br.com.conectabyte.knowly.identity.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Plaintext personal-data fields (excluding {@code avatarUrl}, which is self-only and never part of
 * this shared direct-edit/request DTO -- REQ-10), used both as the request body for direct
 * edit/submit-request and as part of {@code UserProfileDto}/{@code ProfileEditRequestDto}
 * responses. {@code cpf}/{@code rg} are decrypted into this shape only after the caller's
 * applicable right has already been confirmed (REQ-4). No blind-index field is ever part of this
 * DTO -- derived, not client-settable.
 */
public record ProfileFieldsDto(
        String fullName,
        String cpf,
        String rg,
        String rgOrgaoEmissor,
        LocalDate birthDate,
        AddressDto address,
        List<ContactDto> contacts) {}
