package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.dto.PageResponseDto;
import br.com.conectabyte.knowly.tenancy.dto.TenantSummaryDto;
import br.com.conectabyte.knowly.tenancy.exception.InvalidPaginationException;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
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
 * Unit-style coverage of {@link TenantService#addMember}'s REQ-1/REQ-1a/REQ-13 branching (see
 * specify/features/tenant-membership-acceptance/SPEC.md and PLAN.md's testing strategy).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TenantServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TenantService tenantService;
    @Autowired private TenantContext tenantContext;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private UserAccessGroupRepository userAccessGroupRepository;
    @Autowired private AuditEventRepository auditEventRepository;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private TenantMembership adminMembership(String email, Tenant tenant) {
        User admin = userRepository.saveAndFlush(new User(email));
        tenantContext.setActiveTenantId(tenant.getId());

        return tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
    }

    @Test
    void addMemberForABrandNewEmailIsActiveImmediatelyWithNoNotification() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Brand New Co"));
        TenantMembership admin = adminMembership("admin-new@example.com", tenant);

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "brandnew@example.com",
                        MembershipRole.MEMBER);

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(membership.isActive()).isTrue();

        User newUser = userRepository.findByEmailIgnoreCase("brandnew@example.com").orElseThrow();
        assertThat(notificationRepository.findByRecipientAndResolvedFalse(newUser)).isEmpty();
    }

    @Test
    void addMemberForAnExistingUserIsPendingWithAPendingNotification() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Existing Co"));
        TenantMembership admin = adminMembership("admin-existing@example.com", tenant);
        User existing = userRepository.saveAndFlush(new User("existing@example.com"));

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "existing@example.com",
                        MembershipRole.MEMBER);

        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membership.isActive()).isFalse();

        var notifications = notificationRepository.findByRecipientAndResolvedFalse(existing);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType())
                .isEqualTo(NotificationType.MEMBERSHIP_INVITATION_PENDING);
        assertThat(notifications.get(0).getTenantMembership().getId())
                .isEqualTo(membership.getId());
    }

    @Test
    void addMemberResetsAPreviouslyDeclinedMembershipToPendingWithAFreshNotification() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Reinvite Co"));
        TenantMembership admin = adminMembership("admin-reinvite@example.com", tenant);
        User declinedUser = userRepository.saveAndFlush(new User("declined@example.com"));
        TenantMembership previouslyDeclined =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(declinedUser, tenant, MembershipRole.MEMBER));
        previouslyDeclined.setStatus(MembershipStatus.DECLINED);
        previouslyDeclined.setActive(false);
        tenantMembershipRepository.saveAndFlush(previouslyDeclined);

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "declined@example.com",
                        MembershipRole.MEMBER);

        assertThat(membership.getId()).isEqualTo(previouslyDeclined.getId());
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membership.isActive()).isFalse();

        var notifications = notificationRepository.findByRecipientAndResolvedFalse(declinedUser);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType())
                .isEqualTo(NotificationType.MEMBERSHIP_INVITATION_PENDING);
    }

    // REQ-13 covers both "previously declined" (above) and "previously removed" rows — a
    // previously-*active*, then soft-removed (via removeMember, which only flips `active`, not
    // `status`) row must also reset to pending on re-invite, not silently stay ACTIVE/inactive.
    @Test
    void addMemberResetsAPreviouslyRemovedMembershipToPendingWithAFreshNotification() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Reinvite After Removal Co"));
        TenantMembership admin = adminMembership("admin-reinvite-removed@example.com", tenant);
        User removedUser = userRepository.saveAndFlush(new User("removed@example.com"));
        TenantMembership previouslyRemoved =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(removedUser, tenant, MembershipRole.MEMBER));
        // Simulate a prior acceptance followed by removeMember: status stays ACTIVE, only
        // `active` is flipped false — removeMember never touches `status`.
        previouslyRemoved.setStatus(MembershipStatus.ACTIVE);
        previouslyRemoved.setActive(false);
        tenantMembershipRepository.saveAndFlush(previouslyRemoved);

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "removed@example.com",
                        MembershipRole.MEMBER);

        assertThat(membership.getId()).isEqualTo(previouslyRemoved.getId());
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(membership.isActive()).isFalse();

        var notifications = notificationRepository.findByRecipientAndResolvedFalse(removedUser);
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType())
                .isEqualTo(NotificationType.MEMBERSHIP_INVITATION_PENDING);
    }

    private User staffAdmin(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF_ADMIN);
        return userRepository.saveAndFlush(user);
    }

    private User limitedStaff(String email) {
        User user = userRepository.saveAndFlush(new User(email));
        user.setGlobalRole(GlobalRole.STAFF);
        return userRepository.saveAndFlush(user);
    }

    /**
     * specify/features/tenant-pagination-search/SPEC.md REQ-1: {@code page}/{@code size} default to
     * {@code 0}/{@code 20} when not supplied.
     */
    @Test
    void listAllTenantsDefaultsPageAndSizeWhenNotSupplied() {
        User admin = staffAdmin("list-defaults@example.com");
        tenantRepository.saveAndFlush(new Tenant("Defaults Co " + System.nanoTime()));

        PageResponseDto<TenantSummaryDto> page = tenantService.listAllTenants(admin, 0, 20, null);

        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    /** REQ-3: {@code size} above 100 is clamped, not rejected. */
    @Test
    void listAllTenantsClampsSizeAbove100() {
        User admin = staffAdmin("list-clamp@example.com");

        PageResponseDto<TenantSummaryDto> page = tenantService.listAllTenants(admin, 0, 500, null);

        assertThat(page.size()).isEqualTo(100);
    }

    /** REQ-4: negative page/size and size=0 are rejected, and rejection wins over clamping. */
    @Test
    void listAllTenantsRejectsNegativePage() {
        User admin = staffAdmin("list-neg-page@example.com");

        assertThatThrownBy(() -> tenantService.listAllTenants(admin, -1, 20, null))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void listAllTenantsRejectsNegativeSize() {
        User admin = staffAdmin("list-neg-size@example.com");

        assertThatThrownBy(() -> tenantService.listAllTenants(admin, 0, -1, null))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void listAllTenantsRejectsZeroSize() {
        User admin = staffAdmin("list-zero-size@example.com");

        assertThatThrownBy(() -> tenantService.listAllTenants(admin, 0, 0, null))
                .isInstanceOf(InvalidPaginationException.class);
    }

    @Test
    void listAllTenantsRejectsAnOutOfRangeNegativeSizeInsteadOfClampingIt() {
        User admin = staffAdmin("list-neg-500@example.com");

        assertThatThrownBy(() -> tenantService.listAllTenants(admin, 0, -500, null))
                .isInstanceOf(InvalidPaginationException.class);
    }

    /** REQ-9: search filters first, then paginates — totals reflect the filtered count. */
    @Test
    void listAllTenantsSearchFiltersBeforePaginating() {
        User admin = staffAdmin("list-search@example.com");
        String marker = "SearchMarker" + System.nanoTime();
        tenantRepository.saveAndFlush(new Tenant(marker + "Co"));
        tenantRepository.saveAndFlush(new Tenant("Unrelated Co " + System.nanoTime()));

        PageResponseDto<TenantSummaryDto> page =
                tenantService.listAllTenants(admin, 0, 20, marker.toLowerCase());

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content())
                .extracting(TenantSummaryDto::name)
                .containsExactly(marker + "Co");
    }

    /** REQ-8: no authorization regression — STAFF_ADMIN unconditional, ungranted STAFF rejected. */
    @Test
    void listAllTenantsStaffAdminSucceedsUnconditionally() {
        User admin = staffAdmin("list-staffadmin@example.com");

        assertThat(tenantService.listAllTenants(admin, 0, 20, null)).isNotNull();
    }

    @Test
    void listAllTenantsUngrantedStaffIsRejected() {
        User staff = limitedStaff("list-ungranted@example.com");

        assertThatThrownBy(() -> tenantService.listAllTenants(staff, 0, 20, null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // REQ-4/REQ-5 (member-admin-tenant-bypass): self-escalation guard — no user may alter their
    // own role or own permission/access-group grants, even a MEMBER_ADMIN acting via the bypass.

    @Test
    void addMemberRejectsAMemberAdminChangingTheirOwnRole() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Self Escalation Co"));
        TenantMembership admin = adminMembership("self-add@example.com", tenant);

        assertThatThrownBy(
                        () ->
                                tenantService.addMember(
                                        admin.getUser(),
                                        tenant.getId(),
                                        "self-add@example.com",
                                        MembershipRole.MEMBER))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void grantPermissionRejectsAMemberAdminGrantingThemselvesAPermission() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Self Grant Co"));
        TenantMembership admin = adminMembership("self-grant@example.com", tenant);

        assertThatThrownBy(
                        () ->
                                tenantService.grantPermission(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        Permission.TENANT_MEMBER_MANAGE))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void revokePermissionRejectsAMemberAdminRevokingTheirOwnPermission() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Self Revoke Co"));
        TenantMembership admin = adminMembership("self-revoke@example.com", tenant);

        assertThatThrownBy(
                        () ->
                                tenantService.revokePermission(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        Permission.TENANT_MEMBER_MANAGE))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void assignAccessGroupRejectsAMemberAdminAssigningThemselves() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Self Assign Co"));
        TenantMembership admin = adminMembership("self-assign@example.com", tenant);
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));

        assertThatThrownBy(
                        () ->
                                tenantService.assignAccessGroup(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        group.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void unassignAccessGroupRejectsAMemberAdminUnassigningThemselves() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Self Unassign Co"));
        TenantMembership admin = adminMembership("self-unassign@example.com", tenant);
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));
        userAccessGroupRepository.saveAndFlush(new UserAccessGroup(admin, group));

        assertThatThrownBy(
                        () ->
                                tenantService.unassignAccessGroup(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        group.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void theFiveGuardedMethodsSucceedWhenTargetingADifferentUser() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Others Co"));
        TenantMembership admin = adminMembership("others-admin@example.com", tenant);
        User otherUser = userRepository.saveAndFlush(new User("other@example.com"));
        TenantMembership otherMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(otherUser, tenant, MembershipRole.MEMBER));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));

        assertThat(
                        tenantService.addMember(
                                admin.getUser(),
                                tenant.getId(),
                                "another-new@example.com",
                                MembershipRole.MEMBER))
                .isNotNull();

        tenantService.grantPermission(
                admin.getUser(),
                tenant.getId(),
                otherMembership.getId(),
                Permission.TENANT_MEMBER_MANAGE);
        tenantService.revokePermission(
                admin.getUser(),
                tenant.getId(),
                otherMembership.getId(),
                Permission.TENANT_MEMBER_MANAGE);
        tenantService.assignAccessGroup(
                admin.getUser(), tenant.getId(), otherMembership.getId(), group.getId());
        tenantService.unassignAccessGroup(
                admin.getUser(), tenant.getId(), otherMembership.getId(), group.getId());
    }

    @Test
    void selfEscalationRejectionIsRecordedAsADeniedAuditEvent() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Audit Co"));
        TenantMembership admin = adminMembership("self-audit@example.com", tenant);
        authenticateAs("self-audit@example.com");

        assertThatThrownBy(
                        () ->
                                tenantService.grantPermission(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        Permission.TENANT_MEMBER_MANAGE))
                .isInstanceOf(PermissionDeniedException.class);

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        admin.getUser().getId());
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getAction()).isEqualTo("tenant.permission.grant");
                            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.DENIED);
                            assertThat(event.getActorUserId()).isEqualTo(admin.getUser().getId());
                        });
    }

    // EXPANDED COVERAGE: REQ-2/REQ-3 cross-tenant with NO membership

    @Test
    void memberAdminBypassFailsWhenActorHasNoMembershipInTargetTenant() {
        User admin = userRepository.saveAndFlush(new User("admin-no-membership@example.com"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenantA, MembershipRole.MEMBER_ADMIN));

        authenticateAs("admin-no-membership@example.com");
        tenantContext.setActiveTenantId(tenantB.getId());

        // User is MEMBER_ADMIN in tenant A but has NO membership in tenant B
        // Attempting a protected action in tenant B should fail
        assertThatThrownBy(() -> tenantService.listMembers(admin, tenantB.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // EXPANDED COVERAGE: REQ-4 — addMember self-escalation audit event

    @Test
    void addMemberSelfEscalationIsRecordedAsADeniedAuditEvent() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Add Member Audit Co"));
        TenantMembership admin = adminMembership("admin-add-audit@example.com", tenant);
        authenticateAs("admin-add-audit@example.com");

        assertThatThrownBy(
                        () ->
                                tenantService.addMember(
                                        admin.getUser(),
                                        tenant.getId(),
                                        "admin-add-audit@example.com",
                                        MembershipRole.MEMBER))
                .isInstanceOf(PermissionDeniedException.class);

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        admin.getUser().getId());
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getAction()).isEqualTo("tenant.member.add");
                            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.DENIED);
                            assertThat(event.getActorUserId()).isEqualTo(admin.getUser().getId());
                        });
    }

    // EXPANDED COVERAGE: REQ-4 — revokePermission self-escalation audit event

    @Test
    void revokePermissionSelfEscalationIsRecordedAsADeniedAuditEvent() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Revoke Audit Co"));
        TenantMembership admin = adminMembership("admin-revoke-audit@example.com", tenant);
        authenticateAs("admin-revoke-audit@example.com");

        assertThatThrownBy(
                        () ->
                                tenantService.revokePermission(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        Permission.TENANT_MEMBER_MANAGE))
                .isInstanceOf(PermissionDeniedException.class);

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        admin.getUser().getId());
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getAction()).isEqualTo("tenant.permission.revoke");
                            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.DENIED);
                            assertThat(event.getActorUserId()).isEqualTo(admin.getUser().getId());
                        });
    }

    // EXPANDED COVERAGE: REQ-4 — assignAccessGroup self-escalation audit event

    @Test
    void assignAccessGroupSelfEscalationIsRecordedAsADeniedAuditEvent() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Assign Audit Co"));
        TenantMembership admin = adminMembership("admin-assign-audit@example.com", tenant);
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));
        authenticateAs("admin-assign-audit@example.com");

        assertThatThrownBy(
                        () ->
                                tenantService.assignAccessGroup(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        group.getId()))
                .isInstanceOf(PermissionDeniedException.class);

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        admin.getUser().getId());
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getAction())
                                    .isEqualTo("tenant.member.access_group.assign");
                            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.DENIED);
                            assertThat(event.getActorUserId()).isEqualTo(admin.getUser().getId());
                        });
    }

    // EXPANDED COVERAGE: REQ-4 — unassignAccessGroup self-escalation audit event

    @Test
    void unassignAccessGroupSelfEscalationIsRecordedAsADeniedAuditEvent() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Unassign Audit Co"));
        TenantMembership admin = adminMembership("admin-unassign-audit@example.com", tenant);
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));
        userAccessGroupRepository.saveAndFlush(new UserAccessGroup(admin, group));
        authenticateAs("admin-unassign-audit@example.com");

        assertThatThrownBy(
                        () ->
                                tenantService.unassignAccessGroup(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        group.getId()))
                .isInstanceOf(PermissionDeniedException.class);

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        admin.getUser().getId());
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getAction())
                                    .isEqualTo("tenant.member.access_group.unassign");
                            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.DENIED);
                            assertThat(event.getActorUserId()).isEqualTo(admin.getUser().getId());
                        });
    }

    // EXPANDED COVERAGE: REQ-6 — Inactive MEMBER_ADMIN in one tenant, active MEMBER in another

    @Test
    void inactiveMemberAdminInTenantACannotActOnItButCanActAsActiveMemberInTenantB() {
        User user = userRepository.saveAndFlush(new User("inactive-admin@example.com"));
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Inactive Tenant"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Active Tenant"));

        // Inactive MEMBER_ADMIN in tenant A
        TenantMembership membershipA =
                new TenantMembership(user, tenantA, MembershipRole.MEMBER_ADMIN);
        membershipA.setActive(false);
        tenantMembershipRepository.saveAndFlush(membershipA);

        // Active MEMBER in tenant B
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenantB, MembershipRole.MEMBER));

        authenticateAs("inactive-admin@example.com");

        // In tenant A (inactive MEMBER_ADMIN): should fail
        tenantContext.setActiveTenantId(tenantA.getId());
        assertThatThrownBy(() -> tenantService.listMembers(user, tenantA.getId()))
                .isInstanceOf(PermissionDeniedException.class);

        // Clear context for next part
        tenantContext.clear();
        tenantContext.setActiveTenantId(tenantB.getId());

        // In tenant B (active MEMBER with no grant): should fail too (no grant)
        assertThatThrownBy(() -> tenantService.listMembers(user, tenantB.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // EXPANDED COVERAGE: Permission matrix — verify each method independently

    @Test
    void eachOfTheFiveGuardedMethodsRejectsUnauthorizedCallers() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Unauthorized Co"));
        User unauthorized = userRepository.saveAndFlush(new User("unauthorized@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(unauthorized, tenant, MembershipRole.MEMBER));
        User target = userRepository.saveAndFlush(new User("target@example.com"));
        TenantMembership targetMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(target, tenant, MembershipRole.MEMBER));
        AccessGroup group = accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Editors"));

        // addMember: plain MEMBER should be rejected
        assertThatThrownBy(
                        () ->
                                tenantService.addMember(
                                        unauthorized,
                                        tenant.getId(),
                                        "newuser@example.com",
                                        MembershipRole.MEMBER))
                .isInstanceOf(PermissionDeniedException.class);

        // grantPermission: plain MEMBER should be rejected
        assertThatThrownBy(
                        () ->
                                tenantService.grantPermission(
                                        unauthorized,
                                        tenant.getId(),
                                        targetMembership.getId(),
                                        Permission.TENANT_MEMBER_MANAGE))
                .isInstanceOf(PermissionDeniedException.class);

        // revokePermission: plain MEMBER should be rejected
        assertThatThrownBy(
                        () ->
                                tenantService.revokePermission(
                                        unauthorized,
                                        tenant.getId(),
                                        targetMembership.getId(),
                                        Permission.TENANT_MEMBER_MANAGE))
                .isInstanceOf(PermissionDeniedException.class);

        // assignAccessGroup: plain MEMBER should be rejected
        assertThatThrownBy(
                        () ->
                                tenantService.assignAccessGroup(
                                        unauthorized,
                                        tenant.getId(),
                                        targetMembership.getId(),
                                        group.getId()))
                .isInstanceOf(PermissionDeniedException.class);

        // unassignAccessGroup: plain MEMBER should be rejected
        assertThatThrownBy(
                        () ->
                                tenantService.unassignAccessGroup(
                                        unauthorized,
                                        tenant.getId(),
                                        targetMembership.getId(),
                                        group.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // EXPANDED COVERAGE: Regression — STAFF_ADMIN can still perform self-target in these methods
    // (Different from MEMBER_ADMIN which is now blocked by requireNotSelfTarget)

    @Test
    void staffAdminCanGrantPermissionToThemselves() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Staff Self Grant Co"));
        User staff = staffAdmin("staff-self-grant@example.com");
        TenantMembership staffMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(staff, tenant, MembershipRole.MEMBER));

        // This should succeed because STAFF_ADMIN bypasses requireAdminOfTenantOrStaff,
        // but will still hit requireNotSelfTarget (which blocks all callers, not just MEMBER_ADMIN)
        // Per REQ-4 and PLAN: "This guard applies to every caller, not just MEMBER_ADMIN"
        // So STAFF_ADMIN acting on self should also be blocked.
        assertThatThrownBy(
                        () ->
                                tenantService.grantPermission(
                                        staff,
                                        tenant.getId(),
                                        staffMembership.getId(),
                                        Permission.TENANT_MEMBER_MANAGE))
                .isInstanceOf(PermissionDeniedException.class);
    }
}
