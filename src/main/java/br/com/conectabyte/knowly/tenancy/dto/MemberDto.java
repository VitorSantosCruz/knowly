package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.TenantMembership;

public record MemberDto(Long membershipId, String email, MembershipRole role) {

    public static MemberDto from(TenantMembership membership) {
        return new MemberDto(
                membership.getId(), membership.getUser().getEmail(), membership.getRole());
    }
}
