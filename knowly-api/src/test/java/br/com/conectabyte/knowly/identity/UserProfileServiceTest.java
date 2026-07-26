package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.ProfileFieldsDto;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectGlobalPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrant;
import br.com.conectabyte.knowly.tenancy.DirectPermissionGrantRepository;
import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Permission;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers {@link UserProfileService}'s full REQ-8..14a decision matrix, per
 * specify/features/identity-profile-model/SPEC.md/PLAN.md.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class UserProfileServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private UserProfileService userProfileService;

    private static final ProfileFieldsDto FIELDS =
            new ProfileFieldsDto("Jane Doe", null, null, null, null);

    private User user(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    private TenantMembership membershipOf(User user, Tenant tenant, MembershipRole role) {
        return tenantMembershipRepository.saveAndFlush(new TenantMembership(user, tenant, role));
    }

    private void grantTenantPermission(TenantMembership membership, Permission permission) {
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(membership, permission));
    }

    private void grantGlobalPermission(User user, GlobalPermission permission) {
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(user, permission));
    }

    // ---- view (REQ-8/9/10/10a/10b/10c) ----

    @Test
    void getOwnProfileAlwaysSucceeds() {
        User caller = user("own-profile@example.com");

        assertThat(userProfileService.getOwnProfile(caller).userId()).isEqualTo(caller.getId());
    }

    @Test
    void viewingAnotherUsersProfileWithNoApplicableRightIsRejected() {
        User caller = user("view-norights@example.com");
        User target = user("view-target-norights@example.com");

        assertThatThrownBy(() -> userProfileService.getProfile(caller, target.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void aTenantScopedProfileViewHolderCanViewAnyMemberOfThatTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("View Co"));
        User caller = user("view-holder@example.com");
        User target = user("view-target@example.com");
        TenantMembership callerMembership = membershipOf(caller, tenant, MembershipRole.MEMBER);
        membershipOf(target, tenant, MembershipRole.MEMBER);
        grantTenantPermission(callerMembership, Permission.PROFILE_VIEW);

        assertThat(userProfileService.getProfile(caller, target.getId()).userId())
                .isEqualTo(target.getId());
    }

    @Test
    void aGlobalScopedProfileViewHolderCanViewAnyUser() {
        User caller = user("global-view-holder@example.com");
        caller.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(caller);
        grantGlobalPermission(caller, GlobalPermission.PROFILE_VIEW);
        User target = user("global-view-target@example.com");

        assertThat(userProfileService.getProfile(caller, target.getId()).userId())
                .isEqualTo(target.getId());
    }

    @Test
    void aMemberAdminCanViewAnyMemberOfTheirTenantWithNoSeparateGrant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Admin View Co"));
        User admin = user("admin-view@example.com");
        User target = user("admin-view-target@example.com");
        membershipOf(admin, tenant, MembershipRole.MEMBER_ADMIN);
        membershipOf(target, tenant, MembershipRole.MEMBER);

        assertThat(userProfileService.getProfile(admin, target.getId()).userId())
                .isEqualTo(target.getId());
    }

    @Test
    void aStaffAdminCanViewAnyUserWithNoSeparateGrant() {
        User staffAdmin = user("staff-admin-view@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User target = user("staff-admin-view-target@example.com");

        assertThat(userProfileService.getProfile(staffAdmin, target.getId()).userId())
                .isEqualTo(target.getId());
    }

    // ---- direct edit (REQ-11/12/13/13a/14/14a) ----

    @Test
    void aMemberAdminCanEditSelfAndOthersWithinTheirTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Admin Edit Co"));
        User admin = user("admin-edit@example.com");
        User other = user("admin-edit-other@example.com");
        membershipOf(admin, tenant, MembershipRole.MEMBER_ADMIN);
        membershipOf(other, tenant, MembershipRole.MEMBER);

        assertThat(userProfileService.directEdit(admin, admin.getId(), FIELDS).fullName())
                .isEqualTo("Jane Doe");
        assertThat(userProfileService.directEdit(admin, other.getId(), FIELDS).fullName())
                .isEqualTo("Jane Doe");
    }

    @Test
    void aStaffAdminCanEditSelfAndAnyOtherUser() {
        User staffAdmin = user("staff-admin-edit@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        User other = user("staff-admin-edit-other@example.com");

        assertThat(userProfileService.directEdit(staffAdmin, staffAdmin.getId(), FIELDS).fullName())
                .isEqualTo("Jane Doe");
        assertThat(userProfileService.directEdit(staffAdmin, other.getId(), FIELDS).fullName())
                .isEqualTo("Jane Doe");
    }

    @Test
    void aTenantScopedProfileEditHolderCanEditAnOtherMemberButNotThemselves() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Co"));
        User holder = user("edit-holder@example.com");
        User other = user("edit-holder-other@example.com");
        TenantMembership holderMembership = membershipOf(holder, tenant, MembershipRole.MEMBER);
        membershipOf(other, tenant, MembershipRole.MEMBER);
        grantTenantPermission(holderMembership, Permission.PROFILE_EDIT);

        assertThat(userProfileService.directEdit(holder, other.getId(), FIELDS).fullName())
                .isEqualTo("Jane Doe");
        assertThatThrownBy(() -> userProfileService.directEdit(holder, holder.getId(), FIELDS))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void aGlobalScopedProfileEditHolderCanEditAnOtherUserButNotThemselves() {
        User holder = user("global-edit-holder@example.com");
        holder.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(holder);
        grantGlobalPermission(holder, GlobalPermission.PROFILE_EDIT);
        User other = user("global-edit-holder-other@example.com");

        assertThat(userProfileService.directEdit(holder, other.getId(), FIELDS).fullName())
                .isEqualTo("Jane Doe");
        assertThatThrownBy(() -> userProfileService.directEdit(holder, holder.getId(), FIELDS))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void aCallerWithNoApplicableRightIsRejectedEntirely() {
        User caller = user("edit-norights@example.com");
        User other = user("edit-norights-other@example.com");

        assertThatThrownBy(() -> userProfileService.directEdit(caller, other.getId(), FIELDS))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
