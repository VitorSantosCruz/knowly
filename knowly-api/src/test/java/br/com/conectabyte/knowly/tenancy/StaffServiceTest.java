package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.ContactType;
import br.com.conectabyte.knowly.identity.dto.ContactDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryAddressDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.identity.exception.UserNotFoundException;
import br.com.conectabyte.knowly.tenancy.dto.AuditEventDto;
import br.com.conectabyte.knowly.tenancy.dto.PageResponseDto;
import br.com.conectabyte.knowly.tenancy.exception.AccessGroupPermissionNotGrantedException;
import br.com.conectabyte.knowly.tenancy.exception.InvalidPaginationException;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit-style coverage of {@link StaffService#createStaffUser}'s REQ-2/REQ-3/REQ-4/REQ-5 role
 * selection (see specify/features/user-role-selection-at-creation/SPEC.md and PLAN.md's testing
 * strategy).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class StaffServiceTest {

    private static MandatoryProfileFieldsDto mandatoryProfile() {
        return new MandatoryProfileFieldsDto(
                "Test User",
                "52998224725",
                "BR",
                new MandatoryAddressDto(
                        "Rua Um, 100", "Centro", "Sao Paulo", "SP", "01000-000", "BR"),
                List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));
    }

    @Autowired private UserRepository userRepository;
    @Autowired private StaffService staffService;
    @Autowired private TenantContext tenantContext;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private GlobalAccessGroupRepository globalAccessGroupRepository;
    @Autowired private GlobalAccessGroupPermissionRepository globalAccessGroupPermissionRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        tenantContext.clear();
    }

    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private User staffAdmin(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF_ADMIN);
        user = userRepository.saveAndFlush(user);
        tenantContext.setStaffAdmin(true);
        return user;
    }

    private User limitedStaff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        user = userRepository.saveAndFlush(user);
        tenantContext.setStaffAdmin(false);
        return user;
    }

    // REQ-2: a STAFF_ADMIN caller can create a new STAFF_ADMIN.

    @Test
    void createStaffUserWithRoleStaffAdminSucceedsForAStaffAdminCaller() {
        staffAdmin("caller-staffadmin@example.com");
        authenticateAs("caller-staffadmin@example.com");

        User created =
                staffService.createStaffUser(
                        "new-staffadmin@example.com", GlobalRole.STAFF_ADMIN, mandatoryProfile());

        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF_ADMIN);
    }

    // REQ-3: a STAFF caller (with or without STAFF_USER_CREATE) requesting STAFF_ADMIN is
    // rejected, no user created — permission grants never substitute for caller identity.

    @Test
    void createStaffUserWithRoleStaffAdminRejectsAStaffCaller() {
        limitedStaff("caller-staff@example.com");
        authenticateAs("caller-staff@example.com");

        assertThatThrownBy(
                        () ->
                                staffService.createStaffUser(
                                        "rejected-staffadmin@example.com",
                                        GlobalRole.STAFF_ADMIN,
                                        mandatoryProfile()))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(userRepository.findByEmailIgnoreCase("rejected-staffadmin@example.com"))
                .isEmpty();
    }

    @Test
    void createStaffUserWithRoleStaffAdminRejectsAStaffCallerEvenWithStaffUserCreateGranted() {
        User staff = limitedStaff("caller-staff-granted@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.STAFF_USER_CREATE));
        authenticateAs("caller-staff-granted@example.com");

        assertThatThrownBy(
                        () ->
                                staffService.createStaffUser(
                                        "rejected-staffadmin-granted@example.com",
                                        GlobalRole.STAFF_ADMIN,
                                        mandatoryProfile()))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(userRepository.findByEmailIgnoreCase("rejected-staffadmin-granted@example.com"))
                .isEmpty();
    }

    // REQ-4: role=STAFF or omitted (null) behaves exactly as today.

    @Test
    void createStaffUserWithRoleStaffCreatesAStaffRow() {
        staffAdmin("caller-default@example.com");
        authenticateAs("caller-default@example.com");

        User created =
                staffService.createStaffUser(
                        "new-staff@example.com", GlobalRole.STAFF, mandatoryProfile());

        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF);
    }

    @Test
    void createStaffUserWithNullRoleDefaultsToStaff() {
        staffAdmin("caller-null-role@example.com");
        authenticateAs("caller-null-role@example.com");

        User created =
                staffService.createStaffUser(
                        "new-staff-null-role@example.com", null, mandatoryProfile());

        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF);
    }

    // REQ-5: no floor/ceiling check applies to creating a STAFF_ADMIN — succeeds whether zero or
    // many STAFF_ADMINs already exist.

    @Test
    void createStaffUserWithRoleStaffAdminSucceedsWithManyExistingStaffAdmins() {
        staffAdmin("caller-many-1@example.com");
        staffAdmin("existing-staffadmin-1@example.com");
        staffAdmin("existing-staffadmin-2@example.com");
        authenticateAs("caller-many-1@example.com");

        User created =
                staffService.createStaffUser(
                        "another-staffadmin@example.com",
                        GlobalRole.STAFF_ADMIN,
                        mandatoryProfile());

        assertThat(created.getGlobalRole()).isEqualTo(GlobalRole.STAFF_ADMIN);
    }

    // specify/features/paginated-audit-trail/SPEC.md REQ-1/REQ-2/REQ-3

    /**
     * Deliberately does not touch {@code tenantContext} (unlike {@link #staffAdmin(String)}/{@link
     * #limitedStaff(String)}) — those helpers set the caller's ThreadLocal staff-admin bypass flag,
     * which would be wrongly overwritten if used to create a *target* user after the *actor* has
     * already been authenticated in the same test.
     */
    private User plainTarget(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    private void insertAuditEvent(Long actorUserId, String action) {
        auditEventRepository.save(
                new AuditEvent(actorUserId, null, action, null, null, AuditOutcome.SUCCESS));
    }

    @Test
    void getAuditTrailReturnsPageResponseDtoWithDefaultsWhenNotSupplied() {
        staffAdmin("audit-page-default-actor@example.com");
        authenticateAs("audit-page-default-actor@example.com");
        User target = plainTarget("audit-page-default-target@example.com");
        insertAuditEvent(target.getId(), "action-1");
        insertAuditEvent(target.getId(), "action-2");

        PageResponseDto<AuditEventDto> result = staffService.getAuditTrail(target.getId(), 0, 20);

        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getAuditTrailClampsSizeToMaxPageSizeWhenRequestingMore() {
        staffAdmin("audit-page-clamp-actor@example.com");
        authenticateAs("audit-page-clamp-actor@example.com");
        User target = plainTarget("audit-page-clamp-target@example.com");

        PageResponseDto<AuditEventDto> result = staffService.getAuditTrail(target.getId(), 0, 500);

        assertThat(result.size()).isEqualTo(100);
    }

    @Test
    void getAuditTrailRejectsNegativePage() {
        staffAdmin("audit-page-neg-page-actor@example.com");
        authenticateAs("audit-page-neg-page-actor@example.com");
        User target = plainTarget("audit-page-neg-page-target@example.com");

        assertThatThrownBy(() -> staffService.getAuditTrail(target.getId(), -1, 20))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void getAuditTrailRejectsNegativeSize() {
        staffAdmin("audit-page-neg-size-actor@example.com");
        authenticateAs("audit-page-neg-size-actor@example.com");
        User target = plainTarget("audit-page-neg-size-target@example.com");

        assertThatThrownBy(() -> staffService.getAuditTrail(target.getId(), 0, -5))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void getAuditTrailRejectsZeroSize() {
        staffAdmin("audit-page-zero-size-actor@example.com");
        authenticateAs("audit-page-zero-size-actor@example.com");
        User target = plainTarget("audit-page-zero-size-target@example.com");

        assertThatThrownBy(() -> staffService.getAuditTrail(target.getId(), 0, 0))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void getAuditTrailStillThrowsUserNotFoundForANonexistentUserId() {
        User actor = staffAdmin("audit-page-404-actor@example.com");
        authenticateAs("audit-page-404-actor@example.com");
        long nonexistentUserId = actor.getId() + 999_999L;

        assertThatThrownBy(() -> staffService.getAuditTrail(nonexistentUserId, 0, 20))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void getAuditTrailStillThrowsPermissionDeniedForACallerWithoutTheGrant() {
        limitedStaff("audit-page-denied-actor@example.com");
        authenticateAs("audit-page-denied-actor@example.com");
        User target = plainTarget("audit-page-denied-target@example.com");

        assertThatThrownBy(() -> staffService.getAuditTrail(target.getId(), 0, 20))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // role-permission-revoke REQ-4: staff-scope mirror of TenantServiceTest's regrant-reactivates
    // assertion.

    @Test
    void grantAccessGroupPermissionReactivatesASoftDeletedRowInsteadOfInsertingADuplicate() {
        staffAdmin("staff-regrant-reactivate-actor@example.com");
        authenticateAs("staff-regrant-reactivate-actor@example.com");
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(
                        new GlobalAccessGroup("Staff Regrant Reactivate Group"));
        staffService.grantAccessGroupPermission(group.getId(), GlobalPermission.STAFF_USER_CREATE);
        GlobalAccessGroupPermission existing =
                globalAccessGroupPermissionRepository
                        .findByGlobalAccessGroupAndPermission(
                                group, GlobalPermission.STAFF_USER_CREATE)
                        .orElseThrow();
        existing.setDeletedAt(java.time.Instant.now());
        globalAccessGroupPermissionRepository.saveAndFlush(existing);

        staffService.grantAccessGroupPermission(group.getId(), GlobalPermission.STAFF_USER_CREATE);

        GlobalAccessGroupPermission reactivated =
                globalAccessGroupPermissionRepository
                        .findByGlobalAccessGroupAndPermission(
                                group, GlobalPermission.STAFF_USER_CREATE)
                        .orElseThrow();
        assertThat(reactivated.getId()).isEqualTo(existing.getId());
        assertThat(reactivated.getDeletedAt()).isNull();
    }

    // role-permission-revoke REQ-2/REQ-3/REQ-6/REQ-7/REQ-8: revokeAccessGroupPermission (staff
    // scope), same three cases as TenantServiceTest's tenant-scope coverage.

    @Test
    void revokeAccessGroupPermissionRejectsAnUnknownAccessGroupId() {
        staffAdmin("staff-revoke-unknown-actor@example.com");
        authenticateAs("staff-revoke-unknown-actor@example.com");

        assertThatThrownBy(
                        () ->
                                staffService.revokeAccessGroupPermission(
                                        -1L, GlobalPermission.STAFF_USER_CREATE))
                .isInstanceOf(TenantAccessDeniedException.class);
    }

    @Test
    void revokeAccessGroupPermissionRejectsAPermissionNeverGranted() {
        staffAdmin("staff-revoke-never-granted-actor@example.com");
        authenticateAs("staff-revoke-never-granted-actor@example.com");
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(
                        new GlobalAccessGroup("Staff Revoke Never Granted Group"));

        assertThatThrownBy(
                        () ->
                                staffService.revokeAccessGroupPermission(
                                        group.getId(), GlobalPermission.STAFF_USER_CREATE))
                .isInstanceOf(AccessGroupPermissionNotGrantedException.class);
    }

    @Test
    void revokeAccessGroupPermissionRejectsAnAlreadyRevokedPermission() {
        staffAdmin("staff-revoke-twice-actor@example.com");
        authenticateAs("staff-revoke-twice-actor@example.com");
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(
                        new GlobalAccessGroup("Staff Revoke Twice Group"));
        staffService.grantAccessGroupPermission(group.getId(), GlobalPermission.STAFF_USER_CREATE);
        staffService.revokeAccessGroupPermission(group.getId(), GlobalPermission.STAFF_USER_CREATE);

        assertThatThrownBy(
                        () ->
                                staffService.revokeAccessGroupPermission(
                                        group.getId(), GlobalPermission.STAFF_USER_CREATE))
                .isInstanceOf(AccessGroupPermissionNotGrantedException.class);
    }

    @Test
    void revokeAccessGroupPermissionSoftDeletesTheRowWithoutRemovingIt() {
        staffAdmin("staff-revoke-softdelete-actor@example.com");
        authenticateAs("staff-revoke-softdelete-actor@example.com");
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(
                        new GlobalAccessGroup("Staff Revoke Softdelete Group"));
        staffService.grantAccessGroupPermission(group.getId(), GlobalPermission.STAFF_USER_CREATE);
        GlobalAccessGroupPermission granted =
                globalAccessGroupPermissionRepository
                        .findByGlobalAccessGroupAndPermission(
                                group, GlobalPermission.STAFF_USER_CREATE)
                        .orElseThrow();

        staffService.revokeAccessGroupPermission(group.getId(), GlobalPermission.STAFF_USER_CREATE);

        GlobalAccessGroupPermission revoked =
                globalAccessGroupPermissionRepository.findById(granted.getId()).orElseThrow();
        assertThat(revoked.getDeletedAt()).isNotNull();
    }
}
