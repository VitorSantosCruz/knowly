package br.com.conectabyte.knowly.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * The full mandatory profile field set (SPEC's completeness definition), reused as-is by staff
 * creation, {@code addMember}, and the bootstrap completion endpoint -- per
 * specify/features/mandatory-complete-profile/PLAN.md's "one shared mandatory profile fields DTO
 * shape" decision. {@code avatar_url} is deliberately absent (SPEC: excluded from completeness).
 * {@code rg}/{@code rgOrgaoEmissor}/{@code birthDate} were removed entirely, 2026-08-02;
 * {@code cpf} renamed {@code taxId}, {@code countryCode} added (mandatory for completeness, per
 * mandatory-complete-profile/SPEC.md's 2026-08-02 fourth amendment).
 */
public record MandatoryProfileFieldsDto(
        @NotBlank String fullName,
        @NotBlank String taxId,
        @NotBlank String countryCode,
        @NotNull @Valid MandatoryAddressDto address,
        @NotEmpty List<@Valid ContactDto> contacts) {}
