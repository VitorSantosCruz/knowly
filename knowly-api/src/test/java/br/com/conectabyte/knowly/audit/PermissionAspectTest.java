package br.com.conectabyte.knowly.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.AccessGroup;
import br.com.conectabyte.knowly.tenancy.AccessGroupPermission;
import br.com.conectabyte.knowly.tenancy.AccessGroupPermissionRepository;
import br.com.conectabyte.knowly.tenancy.AccessGroupRepository;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.UserAccessGroup;
import br.com.conectabyte.knowly.tenancy.UserAccessGroupRepository;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, PermissionAspectTest.Config.class})
@SpringBootTest
@ActiveProfiles("test")
class PermissionAspectTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private AccessGroupPermissionRepository accessGroupPermissionRepository;
    @Autowired private UserAccessGroupRepository userAccessGroupRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private ProtectedService protectedService;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private TenantMembership newMembership(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Acme"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));

        authenticateAs(email);
        tenantContext.setActiveTenantId(tenant.getId());

        return membership;
    }

    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void deniesWhenMembershipLacksThePermission() {
        newMembership("nogrant@example.com");

        assertThatThrownBy(protectedService::doProtectedThing)
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void allowsWhenPermissionGrantedDirectly() {
        TenantMembership membership = newMembership("direct@example.com");
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.TENANT_MEMBER_MANAGE));

        assertThat(protectedService.doProtectedThing()).isEqualTo("done");
    }

    @Test
    void allowsWhenPermissionGrantedViaAccessGroup() {
        TenantMembership membership = newMembership("group@example.com");
        AccessGroup group =
                accessGroupRepository.saveAndFlush(
                        new AccessGroup(membership.getTenant(), "Editors"));
        accessGroupPermissionRepository.saveAndFlush(
                new AccessGroupPermission(group, Permission.TENANT_MEMBER_MANAGE));
        userAccessGroupRepository.saveAndFlush(new UserAccessGroup(membership, group));

        assertThat(protectedService.doProtectedThing()).isEqualTo("done");
    }

    @Test
    void memberAdminBypassesTheCheckInTheirActiveTenantWithZeroExplicitGrants() {
        User user = userRepository.saveAndFlush(new User("member-admin@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Acme"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER_ADMIN));

        authenticateAs("member-admin@example.com");
        tenantContext.setActiveTenantId(tenant.getId());

        assertThat(protectedService.doProtectedThing()).isEqualTo("done");
    }

    @Test
    void memberAdminBypassDoesNotApplyToADifferentActiveTenantWhereTheyAreNotAdmin() {
        User user = userRepository.saveAndFlush(new User("member-admin-other@example.com"));
        Tenant adminTenant = tenantRepository.saveAndFlush(new Tenant("Acme"));
        Tenant otherTenant = tenantRepository.saveAndFlush(new Tenant("Other Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, adminTenant, MembershipRole.MEMBER_ADMIN));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, otherTenant, MembershipRole.MEMBER));

        authenticateAs("member-admin-other@example.com");
        tenantContext.setActiveTenantId(otherTenant.getId());

        assertThatThrownBy(protectedService::doProtectedThing)
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void inactiveMemberAdminMembershipDoesNotGrantTheBypass() {
        User user = userRepository.saveAndFlush(new User("inactive-member-admin@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Acme"));
        TenantMembership membership =
                new TenantMembership(user, tenant, MembershipRole.MEMBER_ADMIN);
        membership.setActive(false);
        tenantMembershipRepository.saveAndFlush(membership);

        authenticateAs("inactive-member-admin@example.com");
        tenantContext.setActiveTenantId(tenant.getId());

        assertThatThrownBy(protectedService::doProtectedThing)
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void staffBypassesTheCheckRegardlessOfTenantContext() {
        User staff = userRepository.saveAndFlush(new User("staff@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staff);
        authenticateAs("staff@example.com");
        tenantContext.setStaff(true);
        tenantContext.setStaffAdmin(true);

        assertThat(protectedService.doProtectedThing()).isEqualTo("done");
    }

    // EXPANDED COVERAGE: REQ-2/REQ-3 cross-tenant with NO membership

    @Test
    void memberAdminBypassFailsWhenActorHasNoMembershipInTargetTenant() {
        User admin = userRepository.saveAndFlush(new User("cross-admin-no-member@example.com"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Cross A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Cross B"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenantA, MembershipRole.MEMBER_ADMIN));

        authenticateAs("cross-admin-no-member@example.com");
        tenantContext.setActiveTenantId(tenantB.getId());

        // User is MEMBER_ADMIN in tenant A but has NO membership at all in tenant B
        assertThatThrownBy(protectedService::doProtectedThing)
                .isInstanceOf(PermissionDeniedException.class);
    }

    // EXPANDED COVERAGE: REQ-6 — Inactive MEMBER_ADMIN in one tenant
    // does not bypass checks in that tenant, but can act as active MEMBER elsewhere

    @Test
    void inactiveMemberAdminBypassFailsEvenWhenPrimaryAdmin() {
        User admin = userRepository.saveAndFlush(new User("inactive-primary@example.com"));
        Tenant tenantInactive = tenantRepository.saveAndFlush(new Tenant("Inactive Primary"));
        TenantMembership membership =
                new TenantMembership(admin, tenantInactive, MembershipRole.MEMBER_ADMIN);
        membership.setActive(false);
        tenantMembershipRepository.saveAndFlush(membership);

        authenticateAs("inactive-primary@example.com");
        tenantContext.setActiveTenantId(tenantInactive.getId());

        // Inactive membership does not grant bypass, even if role is MEMBER_ADMIN
        assertThatThrownBy(protectedService::doProtectedThing)
                .isInstanceOf(PermissionDeniedException.class);
    }

    // EXPANDED COVERAGE: Regression — Plain MEMBER still fails without explicit grant

    @Test
    void plainMemberWithoutExplicitGrantStillFails() {
        newMembership("plain-member@example.com");

        // Plain MEMBER with no grant should fail
        assertThatThrownBy(protectedService::doProtectedThing)
                .isInstanceOf(PermissionDeniedException.class);
    }

    // EXPANDED COVERAGE: Regression — Active vs inactive membership status enforcement

    @Test
    void activeButNonAdminMemberWithExplicitGrantPasses() {
        TenantMembership membership = newMembership("active-with-grant@example.com");
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.TENANT_MEMBER_MANAGE));

        assertThat(protectedService.doProtectedThing()).isEqualTo("done");
    }

    // permission-granularity-model REQ-2/REQ-5: ARTICLE_EDIT/ARTICLE_DELETE require ARTICLE_VIEW.

    @Test
    void articleEditWithArticleViewProceeds() {
        TenantMembership membership = newMembership("edit-with-view@example.com");
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_EDIT));
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_VIEW));

        assertThat(protectedService.editArticle()).isEqualTo("edited");
    }

    @Test
    void articleEditWithoutArticleViewIsDenied() {
        TenantMembership membership = newMembership("edit-without-view@example.com");
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_EDIT));

        assertThatThrownBy(protectedService::editArticle)
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void articleDeleteWithArticleViewProceeds() {
        TenantMembership membership = newMembership("delete-with-view@example.com");
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_DELETE));
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_VIEW));

        assertThat(protectedService.deleteArticle()).isEqualTo("deleted");
    }

    @Test
    void articleDeleteWithoutArticleViewIsDenied() {
        TenantMembership membership = newMembership("delete-without-view@example.com");
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_DELETE));

        assertThatThrownBy(protectedService::deleteArticle)
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void articleViewAloneNeverGrantsEditOrDelete() {
        TenantMembership membership = newMembership("view-alone@example.com");
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_VIEW));

        assertThatThrownBy(protectedService::editArticle)
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(protectedService::deleteArticle)
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void articleCreateAloneIsUnaffectedByTheViewDependency() {
        TenantMembership membership = newMembership("create-alone@example.com");
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, Permission.ARTICLE_CREATE));

        assertThat(protectedService.createArticle()).isEqualTo("created");
    }

    static class ProtectedService {
        @RequiresPermission(Permission.TENANT_MEMBER_MANAGE)
        String doProtectedThing() {
            return "done";
        }

        @RequiresPermission(Permission.ARTICLE_EDIT)
        String editArticle() {
            return "edited";
        }

        @RequiresPermission(Permission.ARTICLE_DELETE)
        String deleteArticle() {
            return "deleted";
        }

        @RequiresPermission(Permission.ARTICLE_CREATE)
        String createArticle() {
            return "created";
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        ProtectedService protectedService() {
            return new ProtectedService();
        }
    }
}
