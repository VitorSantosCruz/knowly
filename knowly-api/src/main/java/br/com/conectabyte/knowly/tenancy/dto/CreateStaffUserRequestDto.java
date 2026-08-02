package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code profile} is required (REQ-7, per
 * specify/features/mandatory-complete-profile/SPEC.md/PLAN.md) -- a staff-creation request missing
 * any mandatory profile field is rejected by Bean Validation before {@code StaffService
 * .createStaffUser} is ever entered.
 *
 * <p>{@code role} is optional (REQ-1, per
 * specify/features/user-role-selection-at-creation/SPEC.md/PLAN.md) -- {@code null} or {@code
 * STAFF} defaults to today's behavior (REQ-4); {@code STAFF_ADMIN} is only honored for a {@code
 * STAFF_ADMIN} caller (REQ-2/REQ-3, enforced in {@code StaffService}).
 */
public record CreateStaffUserRequestDto(
        @Email @NotBlank String email,
        GlobalRole role,
        @NotNull @Valid MandatoryProfileFieldsDto profile) {}
