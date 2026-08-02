package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.deletion.DeletionConfirmationTokenService;
import br.com.conectabyte.knowly.identity.ContactType;
import br.com.conectabyte.knowly.identity.dto.ContactDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryAddressDto;
import br.com.conectabyte.knowly.identity.dto.MandatoryProfileFieldsDto;
import br.com.conectabyte.knowly.tenancy.dto.AddressDto;
import br.com.conectabyte.knowly.tenancy.dto.CreateTenantRequestDto;
import br.com.conectabyte.knowly.tenancy.dto.PageResponseDto;
import br.com.conectabyte.knowly.tenancy.dto.TenantSummaryDto;
import br.com.conectabyte.knowly.tenancy.exception.InvalidPaginationException;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import br.com.conectabyte.knowly.tenancy.exception.TenantAlreadyExistsException;
import java.time.LocalDate;
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

    private static MandatoryProfileFieldsDto mandatoryProfile() {
        return new MandatoryProfileFieldsDto(
                "Test User",
                LocalDate.of(1990, 1, 1),
                "12345678901",
                "123456",
                "SSP",
                new MandatoryAddressDto(
                        "01000-000", "Rua Um", null, null, "Centro", "Sao Paulo", "SP", "Brasil"),
                List.of(new ContactDto(null, ContactType.OTHER, "value", null, false)));
    }

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TenantService tenantService;
    @Autowired private TenantContext tenantContext;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private UserAccessGroupRepository userAccessGroupRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private DeletionConfirmationTokenService deletionConfirmationTokenService;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;

    private String memberRemovalWord(User actor, Long membershipId) {
        return deletionConfirmationTokenService.generate(
                "tenant-member", membershipId.toString(), actor, null);
    }

    private String permissionRevocationWord(User actor, Long membershipId, Permission permission) {
        return deletionConfirmationTokenService.generate(
                "tenant-permission", membershipId + ":" + permission, actor, null);
    }

    private String accessGroupUnassignmentWord(User actor, Long membershipId, Long accessGroupId) {
        return deletionConfirmationTokenService.generate(
                "tenant-access-group", membershipId + ":" + accessGroupId, actor, null);
    }

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
                        MembershipRole.MEMBER,
                        mandatoryProfile());

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
                        MembershipRole.MEMBER,
                        mandatoryProfile());

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
                        MembershipRole.MEMBER,
                        mandatoryProfile());

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
                        MembershipRole.MEMBER,
                        mandatoryProfile());

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
                                        MembershipRole.MEMBER,
                                        mandatoryProfile()))
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
        String word =
                permissionRevocationWord(
                        admin.getUser(), admin.getId(), Permission.TENANT_MEMBER_MANAGE);

        assertThatThrownBy(
                        () ->
                                tenantService.revokePermission(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        Permission.TENANT_MEMBER_MANAGE,
                                        word))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void removeMemberRejectsAMemberAdminRemovingThemselves() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Self Remove Co"));
        TenantMembership admin = adminMembership("self-remove@example.com", tenant);
        String word = memberRemovalWord(admin.getUser(), admin.getId());

        assertThatThrownBy(
                        () ->
                                tenantService.removeMember(
                                        admin.getUser(), tenant.getId(), admin.getId(), word))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void removeMemberSelfEscalationIsRecordedAsADeniedAuditEvent() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Remove Member Audit Co"));
        TenantMembership admin = adminMembership("admin-remove-audit@example.com", tenant);
        authenticateAs("admin-remove-audit@example.com");
        String word = memberRemovalWord(admin.getUser(), admin.getId());

        assertThatThrownBy(
                        () ->
                                tenantService.removeMember(
                                        admin.getUser(), tenant.getId(), admin.getId(), word))
                .isInstanceOf(PermissionDeniedException.class);

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        admin.getUser().getId());
        assertThat(events)
                .anySatisfy(
                        event -> {
                            assertThat(event.getAction()).isEqualTo("tenant.member.remove");
                            assertThat(event.getOutcome()).isEqualTo(AuditOutcome.DENIED);
                            assertThat(event.getActorUserId()).isEqualTo(admin.getUser().getId());
                        });
    }

    @Test
    void removeMemberSucceedsWhenTargetingADifferentUser() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Remove Others Co"));
        TenantMembership admin = adminMembership("remove-others-admin@example.com", tenant);
        User otherUser = userRepository.saveAndFlush(new User("remove-other@example.com"));
        TenantMembership otherMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(otherUser, tenant, MembershipRole.MEMBER));

        String word = memberRemovalWord(admin.getUser(), otherMembership.getId());
        tenantService.removeMember(admin.getUser(), tenant.getId(), otherMembership.getId(), word);

        assertThat(tenantMembershipRepository.findById(otherMembership.getId()))
                .hasValueSatisfying(m -> assertThat(m.isActive()).isFalse());
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
        String word = accessGroupUnassignmentWord(admin.getUser(), admin.getId(), group.getId());

        assertThatThrownBy(
                        () ->
                                tenantService.unassignAccessGroup(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        group.getId(),
                                        word))
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
                                MembershipRole.MEMBER,
                                mandatoryProfile()))
                .isNotNull();

        tenantService.grantPermission(
                admin.getUser(),
                tenant.getId(),
                otherMembership.getId(),
                Permission.TENANT_MEMBER_MANAGE);
        String revokeWord =
                permissionRevocationWord(
                        admin.getUser(), otherMembership.getId(), Permission.TENANT_MEMBER_MANAGE);
        tenantService.revokePermission(
                admin.getUser(),
                tenant.getId(),
                otherMembership.getId(),
                Permission.TENANT_MEMBER_MANAGE,
                revokeWord);
        tenantService.assignAccessGroup(
                admin.getUser(), tenant.getId(), otherMembership.getId(), group.getId());
        String unassignWord =
                accessGroupUnassignmentWord(
                        admin.getUser(), otherMembership.getId(), group.getId());
        tenantService.unassignAccessGroup(
                admin.getUser(),
                tenant.getId(),
                otherMembership.getId(),
                group.getId(),
                unassignWord);
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
                                        MembershipRole.MEMBER,
                                        mandatoryProfile()))
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
        String word =
                permissionRevocationWord(
                        admin.getUser(), admin.getId(), Permission.TENANT_MEMBER_MANAGE);

        assertThatThrownBy(
                        () ->
                                tenantService.revokePermission(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        Permission.TENANT_MEMBER_MANAGE,
                                        word))
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
        String word = accessGroupUnassignmentWord(admin.getUser(), admin.getId(), group.getId());

        assertThatThrownBy(
                        () ->
                                tenantService.unassignAccessGroup(
                                        admin.getUser(),
                                        tenant.getId(),
                                        admin.getId(),
                                        group.getId(),
                                        word))
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
                                        MembershipRole.MEMBER,
                                        mandatoryProfile()))
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
                                        Permission.TENANT_MEMBER_MANAGE,
                                        "irrelevant-word"))
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
                                        group.getId(),
                                        "irrelevant-word"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // EXPANDED COVERAGE: Regression — STAFF_ADMIN can still perform self-target in these methods
    // (Different from MEMBER_ADMIN which is now blocked by requireNotSelfTarget)

    // user-role-selection-at-creation: REQ-7/REQ-8/REQ-9/REQ-10 — addMember's MEMBER_ADMIN-target
    // role selection.

    @Test
    void addMemberWithRoleMemberAdminSucceedsForAStaffAdminCaller() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Staff Admin Co"));
        User staff = staffAdmin("role-select-staffadmin@example.com");

        TenantMembership membership =
                tenantService.addMember(
                        staff,
                        tenant.getId(),
                        "new-memberadmin-by-staff@example.com",
                        MembershipRole.MEMBER_ADMIN,
                        mandatoryProfile());

        assertThat(membership.getRole()).isEqualTo(MembershipRole.MEMBER_ADMIN);
    }

    @Test
    void addMemberWithRoleMemberAdminSucceedsForThatTenantsMemberAdminCaller() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Tenant Admin Co"));
        TenantMembership admin = adminMembership("role-select-tenantadmin@example.com", tenant);

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "new-memberadmin-by-tenant-admin@example.com",
                        MembershipRole.MEMBER_ADMIN,
                        mandatoryProfile());

        assertThat(membership.getRole()).isEqualTo(MembershipRole.MEMBER_ADMIN);
    }

    @Test
    void addMemberWithRoleMemberAdminRejectsAPlainMemberCaller() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Plain Member Co"));
        User plainMember = userRepository.saveAndFlush(new User("role-select-plain@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(plainMember, tenant, MembershipRole.MEMBER));

        assertThatThrownBy(
                        () ->
                                tenantService.addMember(
                                        plainMember,
                                        tenant.getId(),
                                        "rejected-memberadmin@example.com",
                                        MembershipRole.MEMBER_ADMIN,
                                        mandatoryProfile()))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(userRepository.findByEmailIgnoreCase("rejected-memberadmin@example.com"))
                .isEmpty();
    }

    @Test
    void addMemberWithRoleMemberAdminRejectsAPlainMemberCallerEvenWithAGrantedPermission() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Granted Member Co"));
        User plainMember = userRepository.saveAndFlush(new User("role-select-granted@example.com"));
        TenantMembership plainMembership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(plainMember, tenant, MembershipRole.MEMBER));
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(plainMembership, Permission.TENANT_MEMBER_MANAGE));

        assertThatThrownBy(
                        () ->
                                tenantService.addMember(
                                        plainMember,
                                        tenant.getId(),
                                        "rejected-memberadmin-granted@example.com",
                                        MembershipRole.MEMBER_ADMIN,
                                        mandatoryProfile()))
                .isInstanceOf(PermissionDeniedException.class);

        assertThat(userRepository.findByEmailIgnoreCase("rejected-memberadmin-granted@example.com"))
                .isEmpty();
    }

    @Test
    void addMemberWithRoleMemberAdminRejectsAMemberAdminOfADifferentTenant() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Role Select Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Role Select Tenant B"));
        TenantMembership adminOfA =
                adminMembership("role-select-cross-tenant@example.com", tenantA);

        assertThatThrownBy(
                        () ->
                                tenantService.addMember(
                                        adminOfA.getUser(),
                                        tenantB.getId(),
                                        "rejected-cross-tenant-memberadmin@example.com",
                                        MembershipRole.MEMBER_ADMIN,
                                        mandatoryProfile()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void addMemberWithRoleMemberSucceedsUnchanged() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Default Co"));
        TenantMembership admin = adminMembership("role-select-default@example.com", tenant);

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "new-plain-member@example.com",
                        MembershipRole.MEMBER,
                        mandatoryProfile());

        assertThat(membership.getRole()).isEqualTo(MembershipRole.MEMBER);
    }

    @Test
    void addMemberWithNullRoleDefaultsToMember() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Null Co"));
        TenantMembership admin = adminMembership("role-select-null@example.com", tenant);

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "new-null-role-member@example.com",
                        null,
                        mandatoryProfile());

        assertThat(membership.getRole()).isEqualTo(MembershipRole.MEMBER);
    }

    @Test
    void addMemberWithRoleMemberAdminSucceedsWithManyExistingMemberAdminsInTheTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Role Select Many Admins Co"));
        TenantMembership admin = adminMembership("role-select-many-1@example.com", tenant);
        User otherAdmin1 = userRepository.saveAndFlush(new User("role-select-many-2@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(otherAdmin1, tenant, MembershipRole.MEMBER_ADMIN));
        User otherAdmin2 = userRepository.saveAndFlush(new User("role-select-many-3@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(otherAdmin2, tenant, MembershipRole.MEMBER_ADMIN));

        TenantMembership membership =
                tenantService.addMember(
                        admin.getUser(),
                        tenant.getId(),
                        "another-memberadmin@example.com",
                        MembershipRole.MEMBER_ADMIN,
                        mandatoryProfile());

        assertThat(membership.getRole()).isEqualTo(MembershipRole.MEMBER_ADMIN);
    }

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

    // tenant-creation: TenantService#createTenant unit coverage (PLAN.md's "Testing strategy").

    private CreateTenantRequestDto createTenantRequest(
            String name, String taxId, String adminEmail, MembershipRole role) {
        return new CreateTenantRequestDto(
                name,
                name + " Ltda",
                taxId,
                "BR",
                "contact-" + taxId + "@example.com",
                "11999999999",
                new AddressDto("01000-000", "Rua Um", "1", null, "Centro", "Sao Paulo", "SP"),
                adminEmail,
                mandatoryProfile(),
                role);
    }

    @Test
    void createTenantPersistsEveryNewFieldAndDefaultsRoleToMemberAdmin() {
        User staff = staffAdmin("create-tenant-full@example.com");
        authenticateAs("create-tenant-full@example.com");
        String taxId = "12345678000199";
        CreateTenantRequestDto request =
                createTenantRequest("Full Field Co", taxId, "admin-full@example.com", null);

        Tenant created = tenantService.createTenant(staff, request);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Full Field Co");
        assertThat(created.getLegalName()).isEqualTo("Full Field Co Ltda");
        assertThat(created.getTaxId()).isEqualTo(taxId);
        assertThat(created.getCountry()).isEqualTo("BR");
        assertThat(created.getContactEmail()).isEqualTo("contact-" + taxId + "@example.com");
        assertThat(created.getContactPhone()).isEqualTo("11999999999");
        assertThat(created.getPostalCode()).isEqualTo("01000-000");
        assertThat(created.getStreet()).isEqualTo("Rua Um");
        assertThat(created.getNumber()).isEqualTo("1");
        assertThat(created.getNeighborhood()).isEqualTo("Centro");
        assertThat(created.getCity()).isEqualTo("Sao Paulo");
        assertThat(created.getState()).isEqualTo("SP");

        User admin = userRepository.findByEmailIgnoreCase("admin-full@example.com").orElseThrow();
        TenantMembership membership =
                tenantMembershipRepository.findByUserAndActiveTrue(admin).get(0);
        assertThat(membership.getRole()).isEqualTo(MembershipRole.MEMBER_ADMIN);
        assertThat(membership.getTenant().getId()).isEqualTo(created.getId());

        var events = auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(staff.getId());
        assertThat(events)
                .anySatisfy(event -> assertThat(event.getAction()).isEqualTo("tenant.create"));
    }

    @Test
    void createTenantHonorsAnExplicitlySubmittedRole() {
        User staff = staffAdmin("create-tenant-explicit-role@example.com");
        String taxId = "12345678000280";
        CreateTenantRequestDto request =
                createTenantRequest(
                        "Explicit Role Co",
                        taxId,
                        "admin-explicit@example.com",
                        MembershipRole.MEMBER);

        tenantService.createTenant(staff, request);

        User admin =
                userRepository.findByEmailIgnoreCase("admin-explicit@example.com").orElseThrow();
        assertThat(tenantMembershipRepository.findByUserAndActiveTrue(admin).get(0).getRole())
                .isEqualTo(MembershipRole.MEMBER);
    }

    @Test
    void createTenantWithATaxIdCollisionThrowsAndPersistsNoRow() {
        User staff = staffAdmin("create-tenant-collision@example.com");
        String taxId = "12345678000371";
        Tenant collidingTenant = new Tenant("Existing Tax Owner");
        collidingTenant.setTaxId(taxId);
        tenantRepository.saveAndFlush(collidingTenant);

        CreateTenantRequestDto request =
                createTenantRequest("Colliding Co", taxId, "admin-collision@example.com", null);

        assertThatThrownBy(() -> tenantService.createTenant(staff, request))
                .isInstanceOf(TenantAlreadyExistsException.class);

        assertThat(userRepository.findByEmailIgnoreCase("admin-collision@example.com")).isEmpty();
    }

    @Test
    void createTenantWithAnAlreadyExistingAdminEmailThrowsAndPersistsNoTenant() {
        User staff = staffAdmin("create-tenant-existing-email@example.com");
        userRepository.saveAndFlush(new User("already-exists@example.com"));

        CreateTenantRequestDto request =
                createTenantRequest(
                        "Existing Email Co", "12345678000462", "already-exists@example.com", null);

        long tenantCountBefore = tenantRepository.count();

        assertThatThrownBy(() -> tenantService.createTenant(staff, request))
                .isInstanceOf(TenantAlreadyExistsException.class);

        assertThat(tenantRepository.count()).isEqualTo(tenantCountBefore);
    }

    // tenant-crud REQ-11/PLAN.md "Architectural decisions": a soft-deleted tenant is rejected at
    // the switch-time chokepoints the same way a "no access" tenant already is.

    @Test
    void requireActiveMembershipRejectsASoftDeletedTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Deleted Membership Co"));
        User user = userRepository.saveAndFlush(new User("soft-deleted-membership@example.com"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));
        tenant.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);

        assertThatThrownBy(() -> tenantService.requireActiveMembership(user, tenant.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException
                                .class);
    }

    @Test
    void requireTenantRejectsASoftDeletedTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Deleted Act As Co"));
        User staff = staffAdmin("soft-deleted-act-as@example.com");
        tenant.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);

        assertThatThrownBy(() -> tenantService.requireTenant(staff, tenant.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException
                                .class);
    }

    @Test
    void getActiveTenantRejectsASoftDeletedTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Deleted Active Co"));
        User user = userRepository.saveAndFlush(new User("soft-deleted-active@example.com"));
        tenant.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);

        assertThatThrownBy(() -> tenantService.getActiveTenant(user, tenant.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException
                                .class);
    }

    // tenant-crud: TenantService#editTenant unit coverage (PLAN.md's "Testing strategy").

    @Test
    void editTenantUpdatesOnlySuppliedFieldsLeavingOthersUntouched() {
        User staff = staffAdmin("edit-partial@example.com");
        authenticateAs("edit-partial@example.com");
        tenantContext.setStaffAdmin(true);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Partial Co"));
        String originalLegalName = tenant.getLegalName();
        String originalTaxId = tenant.getTaxId();

        var request =
                new br.com.conectabyte.knowly.tenancy.dto.EditTenantRequestDto(
                        "Renamed Co", null, null, null, null, null, null, null, null, null, null);

        var result = tenantService.editTenant(staff, tenant.getId(), request);

        assertThat(result.name()).isEqualTo("Renamed Co");
        assertThat(result.legalName()).isEqualTo(originalLegalName);
        assertThat(result.taxId()).isEqualTo(originalTaxId);

        Tenant persisted = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Renamed Co");
        assertThat(persisted.getLegalName()).isEqualTo(originalLegalName);
    }

    @Test
    void editTenantRejectsAPresentButBlankMandatoryFieldWithNoPartialUpdate() {
        User staff = staffAdmin("edit-blank@example.com");
        authenticateAs("edit-blank@example.com");
        tenantContext.setStaffAdmin(true);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Blank Co"));
        String originalName = tenant.getName();

        var request =
                new br.com.conectabyte.knowly.tenancy.dto.EditTenantRequestDto(
                        "", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> tenantService.editTenant(staff, tenant.getId(), request))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.InvalidTenantEditException
                                .class);

        Tenant persisted = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo(originalName);
    }

    @Test
    void editTenantAllowsComplementToBeBlanked() {
        User staff = staffAdmin("edit-complement@example.com");
        authenticateAs("edit-complement@example.com");
        tenantContext.setStaffAdmin(true);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Complement Co"));
        tenant.setComplement("Suite 1");
        tenantRepository.saveAndFlush(tenant);

        var request =
                new br.com.conectabyte.knowly.tenancy.dto.EditTenantRequestDto(
                        null, null, null, null, null, null, null, "", null, null, null);

        var result = tenantService.editTenant(staff, tenant.getId(), request);

        assertThat(result.complement()).isEmpty();
    }

    @Test
    void editTenantOnASoftDeletedTenantThrowsTenantNotFound() {
        User staff = staffAdmin("edit-deleted@example.com");
        authenticateAs("edit-deleted@example.com");
        tenantContext.setStaffAdmin(true);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Deleted Co"));
        tenant.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);

        var request =
                new br.com.conectabyte.knowly.tenancy.dto.EditTenantRequestDto(
                        "New Name", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> tenantService.editTenant(staff, tenant.getId(), request))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.TenantNotFoundException.class);
    }

    @Test
    void editTenantOnANonExistentTenantThrowsTenantNotFound() {
        User staff = staffAdmin("edit-missing@example.com");
        authenticateAs("edit-missing@example.com");
        tenantContext.setStaffAdmin(true);
        var request =
                new br.com.conectabyte.knowly.tenancy.dto.EditTenantRequestDto(
                        "New Name", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> tenantService.editTenant(staff, Long.MAX_VALUE, request))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.TenantNotFoundException.class);
    }

    @Test
    void editTenantSucceedsForStaffGrantedTenantEditAndTenantView() {
        User staff = limitedStaff("edit-granted@example.com");
        authenticateAs("edit-granted@example.com");
        globalPermissionServiceGrant(staff, GlobalPermission.TENANT_EDIT);
        globalPermissionServiceGrant(staff, GlobalPermission.TENANT_VIEW);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit Granted Co"));

        var request =
                new br.com.conectabyte.knowly.tenancy.dto.EditTenantRequestDto(
                        "Granted Renamed Co",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        var result = tenantService.editTenant(staff, tenant.getId(), request);

        assertThat(result.name()).isEqualTo("Granted Renamed Co");
    }

    @Test
    void editTenantRejectsStaffGrantedOnlyTenantEditWithoutTenantView() {
        User staff = limitedStaff("edit-no-view@example.com");
        authenticateAs("edit-no-view@example.com");
        globalPermissionServiceGrant(staff, GlobalPermission.TENANT_EDIT);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Edit No View Co"));

        var request =
                new br.com.conectabyte.knowly.tenancy.dto.EditTenantRequestDto(
                        "Rejected Rename Co",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> tenantService.editTenant(staff, tenant.getId(), request))
                .isInstanceOf(PermissionDeniedException.class);
    }

    private void globalPermissionServiceGrant(User user, GlobalPermission permission) {
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(user, permission));
    }

    // tenant-crud: TenantService#deleteTenant unit coverage (PLAN.md's "Testing strategy").

    private String tenantDeletionWord(User actor, Long tenantId) {
        return deletionConfirmationTokenService.generate(
                "tenant", tenantId.toString(), actor, null);
    }

    @Test
    void deleteTenantSoftDeletesAndDeactivatesEveryActiveMembership() {
        User staff = staffAdmin("delete-basic@example.com");
        authenticateAs("delete-basic@example.com");
        tenantContext.setStaff(true);
        tenantContext.setStaffAdmin(true);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Basic Co"));
        User member = userRepository.saveAndFlush(new User("delete-basic-member@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(member, tenant, MembershipRole.MEMBER));
        String word = tenantDeletionWord(staff, tenant.getId());

        tenantService.deleteTenant(staff, tenant.getId(), word);

        Tenant persisted = tenantRepository.findById(tenant.getId()).orElseThrow();
        assertThat(persisted.getDeletedAt()).isNotNull();
        assertThat(tenantMembershipRepository.findById(membership.getId()))
                .hasValueSatisfying(m -> assertThat(m.isActive()).isFalse());
    }

    @Test
    void deleteTenantRejectsAMissingOrInvalidConfirmationWordAndAppliesNoChange() {
        User staff = staffAdmin("delete-invalid-word@example.com");
        authenticateAs("delete-invalid-word@example.com");
        tenantContext.setStaff(true);
        tenantContext.setStaffAdmin(true);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Invalid Word Co"));

        assertThatThrownBy(() -> tenantService.deleteTenant(staff, tenant.getId(), "wrong-word"))
                .isInstanceOf(
                        br.com.conectabyte.knowly.deletion.exception
                                .DeletionConfirmationInvalidException.class);

        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getDeletedAt()).isNull();
    }

    @Test
    void deleteTenantOnAnAlreadySoftDeletedTenantThrowsTenantNotFound() {
        User staff = staffAdmin("delete-already-deleted@example.com");
        authenticateAs("delete-already-deleted@example.com");
        tenantContext.setStaff(true);
        tenantContext.setStaffAdmin(true);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Already Deleted Co"));
        tenant.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(tenant);
        String word = tenantDeletionWord(staff, tenant.getId());

        assertThatThrownBy(() -> tenantService.deleteTenant(staff, tenant.getId(), word))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.TenantNotFoundException.class);
    }

    @Test
    void deleteTenantOnANonExistentTenantThrowsTenantNotFound() {
        User staff = staffAdmin("delete-missing@example.com");
        authenticateAs("delete-missing@example.com");
        tenantContext.setStaff(true);
        tenantContext.setStaffAdmin(true);

        assertThatThrownBy(
                        () -> tenantService.deleteTenant(staff, Long.MAX_VALUE, "irrelevant-word"))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.TenantNotFoundException.class);
    }

    @Test
    void deleteTenantRejectsStaffGrantedOnlyTenantDeleteWithoutTenantView() {
        User staff = limitedStaff("delete-no-view@example.com");
        authenticateAs("delete-no-view@example.com");
        tenantContext.setStaff(true);
        globalPermissionServiceGrant(staff, GlobalPermission.TENANT_DELETE);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete No View Co"));

        assertThatThrownBy(
                        () -> tenantService.deleteTenant(staff, tenant.getId(), "irrelevant-word"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void deleteTenantSucceedsForStaffGrantedTenantDeleteAndTenantView() {
        User staff = limitedStaff("delete-granted@example.com");
        authenticateAs("delete-granted@example.com");
        tenantContext.setStaff(true);
        globalPermissionServiceGrant(staff, GlobalPermission.TENANT_DELETE);
        globalPermissionServiceGrant(staff, GlobalPermission.TENANT_VIEW);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Granted Co"));
        String word = tenantDeletionWord(staff, tenant.getId());

        tenantService.deleteTenant(staff, tenant.getId(), word);

        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getDeletedAt())
                .isNotNull();
    }

    @Test
    void deleteTenantDoesNotBlockRegardlessOfMembershipCount() {
        User staff = staffAdmin("delete-volume@example.com");
        authenticateAs("delete-volume@example.com");
        tenantContext.setStaff(true);
        tenantContext.setStaffAdmin(true);
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Delete Volume Co"));
        for (int i = 0; i < 25; i++) {
            User member =
                    userRepository.saveAndFlush(new User("delete-volume-" + i + "@example.com"));
            tenantMembershipRepository.saveAndFlush(
                    new TenantMembership(member, tenant, MembershipRole.MEMBER));
        }
        String word = tenantDeletionWord(staff, tenant.getId());

        tenantService.deleteTenant(staff, tenant.getId(), word);

        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getDeletedAt())
                .isNotNull();
    }

    // tenant-crud REQ-19/REQ-20/REQ-21: active-vs-deactivated listing split.

    @Test
    void listAllTenantsExcludesASoftDeletedTenant() {
        User admin = staffAdmin("list-excludes-deleted@example.com");
        String marker = "ListExcludeDeleted" + System.nanoTime();
        tenantRepository.saveAndFlush(new Tenant(marker + "Active"));
        Tenant deleted = tenantRepository.saveAndFlush(new Tenant(marker + "Deleted"));
        deleted.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(deleted);

        var page = tenantService.listAllTenants(admin, 0, 20, marker);

        assertThat(page.content())
                .extracting(TenantSummaryDto::name)
                .containsExactly(marker + "Active");
    }

    @Test
    void listDeactivatedTenantsReturnsOnlySoftDeletedTenantsWithDeletedAtPopulated() {
        User admin = staffAdmin("list-deactivated@example.com");
        authenticateAs("list-deactivated@example.com");
        tenantContext.setStaffAdmin(true);
        String marker = "ListDeactivated" + System.nanoTime();
        tenantRepository.saveAndFlush(new Tenant(marker + "Active"));
        Tenant deleted = tenantRepository.saveAndFlush(new Tenant(marker + "Deleted"));
        deleted.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(deleted);

        var page = tenantService.listDeactivatedTenants(admin, 0, 20, marker);

        assertThat(page.content())
                .extracting(TenantSummaryDto::name)
                .containsExactly(marker + "Deleted");
        assertThat(page.content().get(0).deletedAt()).isNotNull();
    }

    @Test
    void listDeactivatedTenantsRejectsStaffWithOnlyTenantActAsAny() {
        User staff = limitedStaff("list-deactivated-ungranted@example.com");
        authenticateAs("list-deactivated-ungranted@example.com");
        globalPermissionServiceGrant(staff, GlobalPermission.TENANT_ACT_AS_ANY);

        assertThatThrownBy(() -> tenantService.listDeactivatedTenants(staff, 0, 20, null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void listDeactivatedTenantsSucceedsForStaffGrantedTenantDeleteAndTenantView() {
        User staff = limitedStaff("list-deactivated-granted@example.com");
        authenticateAs("list-deactivated-granted@example.com");
        globalPermissionServiceGrant(staff, GlobalPermission.TENANT_DELETE);
        globalPermissionServiceGrant(staff, GlobalPermission.TENANT_VIEW);
        String marker = "ListDeactivatedGranted" + System.nanoTime();
        Tenant deleted = tenantRepository.saveAndFlush(new Tenant(marker));
        deleted.setDeletedAt(java.time.Instant.now());
        tenantRepository.saveAndFlush(deleted);

        var page = tenantService.listDeactivatedTenants(staff, 0, 20, marker);

        assertThat(page.content()).extracting(TenantSummaryDto::name).containsExactly(marker);
    }

    @Test
    void nonStaffCannotCreateATenant() {
        User user = userRepository.saveAndFlush(new User("non-staff-create-tenant@example.com"));
        CreateTenantRequestDto request =
                createTenantRequest(
                        "Forbidden Co", "12345678000553", "admin-forbidden@example.com", null);

        assertThatThrownBy(() -> tenantService.createTenant(user, request))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(userRepository.findByEmailIgnoreCase("admin-forbidden@example.com")).isEmpty();
    }
}
