package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.validation.ValidTaxId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * REQ-9 (2026-08-02 amendment): {@code POST /api/tenants} creates the tenant and its first admin
 * atomically -- {@code adminEmail}/{@code profile}/{@code role} carry the first admin's login
 * identity, complete mandatory profile (reusing {@code identity.dto.MandatoryProfileFieldsDto}
 * verbatim, per specify/features/mandatory-complete-profile/PLAN.md), and role (defaulting to
 * {@code MEMBER_ADMIN}, not {@code MEMBER} -- see specify/features/tenant-creation/PLAN.md's
 * "Architectural decisions").
 *
 * <p>{@code taxId} itself carries no Bean Validation annotation -- its format rule depends on
 * {@code country} (REQ-6), so it's covered by the class-level {@link ValidTaxId} constraint instead
 * of a per-field one (see that annotation's Javadoc for why).
 */
@ValidTaxId
public record CreateTenantRequestDto(
        @NotBlank String name,
        @NotBlank String legalName,
        String taxId,
        @NotBlank String country,
        @Email @NotBlank String contactEmail,
        @NotBlank String contactPhone,
        @Valid @NotNull AddressDto address,
        @Email @NotBlank String adminEmail,
        @NotNull @Valid MandatoryProfileFieldsDto profile,
        MembershipRole role) {}
