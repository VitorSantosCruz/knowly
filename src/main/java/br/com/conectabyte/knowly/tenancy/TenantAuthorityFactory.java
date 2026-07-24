package br.com.conectabyte.knowly.tenancy;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Builds the Spring Security authorities for a session's current tenant context. */
public final class TenantAuthorityFactory {

    private TenantAuthorityFactory() {}

    public static List<GrantedAuthority> forStaff() {
        return List.of(new SimpleGrantedAuthority("ROLE_STAFF"));
    }

    public static List<GrantedAuthority> forMembership(
            TenantMembership membership, Iterable<Permission> effectivePermissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_TENANT_" + membership.getRole().name()));
        effectivePermissions.forEach(
                permission ->
                        authorities.add(new SimpleGrantedAuthority("PERM_" + permission.name())));

        return authorities;
    }
}
