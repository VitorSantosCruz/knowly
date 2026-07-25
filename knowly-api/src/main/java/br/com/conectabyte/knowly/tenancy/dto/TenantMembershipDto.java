package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.TenantMembership;

public record TenantMembershipDto(
        Long tenantId, String tenantName, MembershipRole role, boolean active) {

    public static TenantMembershipDto from(TenantMembership membership, boolean active) {
        return new TenantMembershipDto(
                membership.getTenant().getId(),
                membership.getTenant().getName(),
                membership.getRole(),
                active);
    }
}
