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
 *
 * <p>{@code role} is optional (REQ-6, per
 * specify/features/user-role-selection-at-creation/SPEC.md/PLAN.md) -- {@code null} or {@code
 * MEMBER} defaults to today's behavior (REQ-9); {@code MEMBER_ADMIN} is only honored for a {@code
 * STAFF_ADMIN} or that tenant's {@code MEMBER_ADMIN} caller (REQ-7/REQ-8, enforced in {@code
 * TenantService}).
 */
public record AddMemberRequestDto(
        @Email @NotBlank String email,
        MembershipRole role,
        @NotNull @Valid MandatoryProfileFieldsDto profile) {}
