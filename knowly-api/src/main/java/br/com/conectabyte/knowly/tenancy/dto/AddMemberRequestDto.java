package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code profile} is required (REQ-8, per
 * specify/features/mandatory-complete-profile/SPEC.md/PLAN.md) -- an {@code addMember} request
 * missing any mandatory profile field is rejected by Bean Validation before {@code TenantService
 * .addMember} is ever entered.
 */
public record AddMemberRequestDto(
        @Email @NotBlank String email,
        @NotNull MembershipRole role,
        @NotNull @Valid MandatoryProfileFieldsDto profile) {}
