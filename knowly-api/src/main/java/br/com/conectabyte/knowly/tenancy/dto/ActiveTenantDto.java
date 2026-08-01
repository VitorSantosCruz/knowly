package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The caller's session-derived active tenant (see {@code TenantContext#getActiveTenantId()}).
 * {@code role} is only present when a real {@code TenantMembership} row exists for (caller, tenant)
 * -- omitted for staff acting as a tenant without holding a membership.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActiveTenantDto(Long tenantId, String tenantName, MembershipRole role) {

    public static ActiveTenantDto from(Tenant tenant, MembershipRole role) {
        return new ActiveTenantDto(tenant.getId(), tenant.getName(), role);
    }
}
