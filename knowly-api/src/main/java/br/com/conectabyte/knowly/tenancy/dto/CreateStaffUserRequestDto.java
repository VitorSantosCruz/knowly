package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code profile} is required (REQ-7, per
 * specify/features/mandatory-complete-profile/SPEC.md/PLAN.md) -- a staff-creation request missing
 * any mandatory profile field is rejected by Bean Validation before {@code StaffService
 * .createStaffUser} is ever entered.
 */
public record CreateStaffUserRequestDto(
        @Email @NotBlank String email, @NotNull @Valid MandatoryProfileFieldsDto profile) {}
