package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.TenantMembership;

public record TenantMembershipDto(Long tenantId, String tenantName, MembershipRole role) {

    public static TenantMembershipDto from(TenantMembership membership) {
        return new TenantMembershipDto(
                membership.getTenant().getId(),
                membership.getTenant().getName(),
                membership.getRole());
    }
}
