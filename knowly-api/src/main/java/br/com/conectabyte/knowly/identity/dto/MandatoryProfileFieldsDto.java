package br.com.conectabyte.knowly.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * The full mandatory profile field set (SPEC's completeness definition), reused as-is by staff
 * creation, {@code addMember}, and the bootstrap completion endpoint -- per
 * specify/features/mandatory-complete-profile/PLAN.md's "one shared mandatory profile fields DTO
 * shape" decision. {@code avatar_url} is deliberately absent (SPEC: excluded from completeness).
 */
public record MandatoryProfileFieldsDto(
        @NotBlank String fullName,
        @NotNull LocalDate birthDate,
        @NotBlank String cpf,
        @NotBlank String rg,
        @NotBlank String rgOrgaoEmissor,
        @NotNull @Valid MandatoryAddressDto address,
        @NotEmpty List<@Valid ContactDto> contacts) {}
