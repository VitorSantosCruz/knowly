package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class PermissionServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private AccessGroupPermissionRepository accessGroupPermissionRepository;
    @Autowired private UserAccessGroupRepository userAccessGroupRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private PermissionService permissionService;

    private TenantMembership newMembership(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Acme"));

        return tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));
    }

    @Test
    void effectivePermissionsIncludesDirectGrants() {
        TenantMembership membership = newMembership("direct@example.com");

        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.TENANT_MEMBER_MANAGE));

        assertThat(permissionService.effectivePermissions(membership))
                .containsExactly(Permission.TENANT_MEMBER_MANAGE);
    }

    @Test
    void effectivePermissionsIncludesAccessGroupGrants() {
        TenantMembership membership = newMembership("group@example.com");
        AccessGroup group =
                accessGroupRepository.saveAndFlush(
                        new AccessGroup(membership.getTenant(), "Editors"));
        accessGroupPermissionRepository.saveAndFlush(
                new AccessGroupPermission(group, Permission.TENANT_MEMBER_MANAGE));
        userAccessGroupRepository.saveAndFlush(new UserAccessGroup(membership, group));

        assertThat(permissionService.effectivePermissions(membership))
                .containsExactly(Permission.TENANT_MEMBER_MANAGE);
    }

    @Test
    void effectivePermissionsIsAUnionWithNoDuplicatesWhenBothPathsGrantTheSamePermission() {
        TenantMembership membership = newMembership("both@example.com");
        AccessGroup group =
                accessGroupRepository.saveAndFlush(
                        new AccessGroup(membership.getTenant(), "Editors"));
        accessGroupPermissionRepository.saveAndFlush(
                new AccessGroupPermission(group, Permission.TENANT_MEMBER_MANAGE));
        userAccessGroupRepository.saveAndFlush(new UserAccessGroup(membership, group));
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.TENANT_MEMBER_MANAGE));

        assertThat(permissionService.effectivePermissions(membership))
                .containsExactly(Permission.TENANT_MEMBER_MANAGE);
    }

    @Test
    void effectivePermissionsIsEmptyWithNoGrants() {
        TenantMembership membership = newMembership("nogrants@example.com");

        assertThat(permissionService.effectivePermissions(membership)).isEmpty();
    }

    @Test
    void hasPermissionReflectsGrantsImmediately() {
        TenantMembership membership = newMembership("immediate@example.com");

        assertThat(permissionService.hasPermission(membership, Permission.TENANT_MEMBER_MANAGE))
                .isFalse();

        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.TENANT_MEMBER_MANAGE));

        assertThat(permissionService.hasPermission(membership, Permission.TENANT_MEMBER_MANAGE))
                .isTrue();
    }
}
