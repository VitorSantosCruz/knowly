package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.deletion.DeletionConfirmationTokenService;
import br.com.conectabyte.knowly.deletion.exception.DeletionConfirmationInvalidException;
import br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * specify/features/staff-rbac-management-operations/SPEC.md and PLAN.md: demote/delete/promote/
 * batch-update for tenant-scope ({@code MEMBER}/{@code MEMBER_ADMIN}) members, and the admin-target
 * rejection on the existing grant/revoke/access-group endpoints. Service-level, mirroring {@code
 * TenantServiceTest}'s direct-call style.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TenantRbacManagementOperationsTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private TenantService tenantService;
    @Autowired private TenantContext tenantContext;
    @Autowired private DirectPermissionGrantRepository directPermissionGrantRepository;
    @Autowired private DeletionConfirmationTokenService deletionConfirmationTokenService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private PlatformTransactionManager transactionManager;

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

    private Tenant tenant(String name) {
        return tenantRepository.saveAndFlush(new Tenant(name + " " + System.nanoTime()));
    }

    private TenantMembership memberAdmin(String email, Tenant tenant) {
        User user = userRepository.saveAndFlush(new User(email));
        tenantContext.setActiveTenantId(tenant.getId());
        return tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER_ADMIN));
    }

    private TenantMembership member(String email, Tenant tenant) {
        User user = userRepository.saveAndFlush(new User(email));
        tenantContext.setActiveTenantId(tenant.getId());
        return tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));
    }

    // --- Demotion (REQ-1-6, REQ-21-23) ---

    @Test
    void demoteSucceedsWhenAtLeastTwoMemberAdminsExistInTheTenant() {
        Tenant tenant = tenant("Demote Co");
        TenantMembership caller = memberAdmin("demote-caller-1@example.com", tenant);
        TenantMembership target = memberAdmin("demote-target-1@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());

        tenantService.demoteMember(caller.getUser(), tenant.getId(), target.getId());

        TenantMembership reloaded =
                tenantMembershipRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(MembershipRole.MEMBER);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        caller.getUser().getId());
        assertThat(events).anyMatch(event -> event.getAction().equals("tenant.member.demote"));
    }

    @Test
    void selfDemotionRejectedRegardlessOfAdminCount() {
        Tenant tenant = tenant("Self Demote Co");
        TenantMembership caller = memberAdmin("self-demote@example.com", tenant);
        memberAdmin("self-demote-other@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.demoteMember(
                                        caller.getUser(), tenant.getId(), caller.getId()))
                .isInstanceOf(PermissionDeniedException.class);

        TenantMembership reloaded =
                tenantMembershipRepository.findById(caller.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(MembershipRole.MEMBER_ADMIN);
    }

    @Test
    void plainMemberWithUnrelatedGrantIsRejectedFromDemotingAMemberAdmin() {
        Tenant tenant = tenant("Demote Grant Co");
        TenantMembership target = memberAdmin("demote-target-4@example.com", tenant);
        TenantMembership plain = member("demote-caller-member@example.com", tenant);
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(plain, Permission.TENANT_MEMBER_MANAGE));
        authenticateAs(plain.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.demoteMember(
                                        plain.getUser(), tenant.getId(), target.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void crossTenantMemberAdminCannotDemoteAMemberOfADifferentTenant() {
        Tenant tenantA = tenant("Cross Tenant A");
        Tenant tenantB = tenant("Cross Tenant B");
        TenantMembership adminOfA = memberAdmin("cross-demote-admin@example.com", tenantA);
        TenantMembership targetInB = memberAdmin("cross-demote-target@example.com", tenantB);
        authenticateAs(adminOfA.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.demoteMember(
                                        adminOfA.getUser(), tenantB.getId(), targetInB.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void concurrentDemoteAgainstTwoDifferentMemberAdminsInTheSameTenantAllowsExactlyOneToSucceed()
            throws Exception {
        // Same reasoning as the staff-scope equivalent
        // (StaffRbacManagementOperationsTest#concurrentDemoteAgainstTwoDifferentStaffAdminsAllowsExactlyOneToSucceed):
        // exactly two MEMBER_ADMINs in one tenant, each demoting the other.
        Tenant tenant = tenant("Concurrent Co");
        TenantMembership admin1 = memberAdmin("concurrent-admin-1@example.com", tenant);
        TenantMembership admin2 = memberAdmin("concurrent-admin-2@example.com", tenant);

        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Runnable admin2DemotesAdmin1 =
                () -> {
                    authenticateAs(admin2.getUser().getEmail());
                    tenantContext.setActiveTenantId(tenant.getId());
                    bothStarted.countDown();
                    try {
                        bothStarted.await();
                        transactionTemplate.executeWithoutResult(
                                status ->
                                        tenantService.demoteMember(
                                                admin2.getUser(), tenant.getId(), admin1.getId()));
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        failureCount.incrementAndGet();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
        Runnable admin1DemotesAdmin2 =
                () -> {
                    authenticateAs(admin1.getUser().getEmail());
                    tenantContext.setActiveTenantId(tenant.getId());
                    bothStarted.countDown();
                    try {
                        bothStarted.await();
                        transactionTemplate.executeWithoutResult(
                                status ->
                                        tenantService.demoteMember(
                                                admin1.getUser(), tenant.getId(), admin2.getId()));
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        failureCount.incrementAndGet();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };

        CompletableFuture<Void> future1 = CompletableFuture.runAsync(admin2DemotesAdmin1);
        CompletableFuture<Void> future2 = CompletableFuture.runAsync(admin1DemotesAdmin2);
        CompletableFuture.allOf(future1, future2).get();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
    }

    // --- Deletion (REQ-7-11) ---

    @Test
    void hardDeleteSucceedsWithAValidToken() {
        Tenant tenant = tenant("Hard Delete Co");
        TenantMembership caller = memberAdmin("delete-caller-1@example.com", tenant);
        TenantMembership target = member("delete-target-1@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-member-hard-delete",
                        target.getId().toString(),
                        caller.getUser(),
                        null);

        tenantService.hardDeleteMember(caller.getUser(), tenant.getId(), target.getId(), word);

        // Logical delete (2026-08-04): the row stays, marked deletedAt, rather than being removed.
        TenantMembership reloaded =
                tenantMembershipRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getDeletedAt()).isNotNull();
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    void hardDeleteRejectedWithoutOrWithAWrongToken() {
        Tenant tenant = tenant("Hard Delete Token Co");
        TenantMembership caller = memberAdmin("delete-caller-2@example.com", tenant);
        TenantMembership target = member("delete-target-2@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.hardDeleteMember(
                                        caller.getUser(), tenant.getId(), target.getId(), null))
                .isInstanceOf(DeletionConfirmationInvalidException.class);
        assertThatThrownBy(
                        () ->
                                tenantService.hardDeleteMember(
                                        caller.getUser(),
                                        tenant.getId(),
                                        target.getId(),
                                        "wrong-word"))
                .isInstanceOf(DeletionConfirmationInvalidException.class);
        assertThat(tenantMembershipRepository.findById(target.getId())).isPresent();
    }

    @Test
    void hardDeletingALoneMemberIsNeverBlockedByTheAdminFloor() {
        Tenant tenant = tenant("Lone Member Co");
        TenantMembership caller = memberAdmin("delete-caller-3@example.com", tenant);
        TenantMembership loneMember = member("delete-target-3@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-member-hard-delete",
                        loneMember.getId().toString(),
                        caller.getUser(),
                        null);

        tenantService.hardDeleteMember(caller.getUser(), tenant.getId(), loneMember.getId(), word);

        // Logical delete (2026-08-04): the row stays, marked deletedAt, rather than being removed.
        assertThat(
                        tenantMembershipRepository
                                .findById(loneMember.getId())
                                .orElseThrow()
                                .getDeletedAt())
                .isNotNull();
    }

    @Test
    void selfHardDeletionRejected() {
        Tenant tenant = tenant("Self Delete Co");
        TenantMembership caller = memberAdmin("delete-self@example.com", tenant);
        memberAdmin("delete-self-other@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-member-hard-delete",
                        caller.getId().toString(),
                        caller.getUser(),
                        null);

        assertThatThrownBy(
                        () ->
                                tenantService.hardDeleteMember(
                                        caller.getUser(), tenant.getId(), caller.getId(), word))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(tenantMembershipRepository.findById(caller.getId())).isPresent();
    }

    // --- Admin-target grant/revoke/assign rejection (REQ-17-19) ---

    @Test
    void grantPermissionAgainstAMemberAdminTargetIsRejectedAndCreatesNoGrant() {
        Tenant tenant = tenant("Guard Grant Co");
        TenantMembership caller = memberAdmin("guard-caller-1@example.com", tenant);
        TenantMembership target = memberAdmin("guard-target-1@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.grantPermission(
                                        caller.getUser(),
                                        tenant.getId(),
                                        target.getId(),
                                        Permission.TENANT_MEMBER_MANAGE))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(
                        directPermissionGrantRepository.findByTenantMembershipAndPermission(
                                target, Permission.TENANT_MEMBER_MANAGE))
                .isEmpty();
    }

    @Test
    void revokePermissionAgainstAMemberAdminTargetIsRejected() {
        Tenant tenant = tenant("Guard Revoke Co");
        TenantMembership caller = memberAdmin("guard-caller-2@example.com", tenant);
        TenantMembership target = memberAdmin("guard-target-2@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-permission",
                        target.getId() + ":" + Permission.TENANT_MEMBER_MANAGE,
                        caller.getUser(),
                        null);

        assertThatThrownBy(
                        () ->
                                tenantService.revokePermission(
                                        caller.getUser(),
                                        tenant.getId(),
                                        target.getId(),
                                        Permission.TENANT_MEMBER_MANAGE,
                                        word))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void assignAccessGroupAgainstAMemberAdminTargetIsRejected() {
        Tenant tenant = tenant("Guard Assign Co");
        TenantMembership caller = memberAdmin("guard-caller-3@example.com", tenant);
        TenantMembership target = memberAdmin("guard-target-3@example.com", tenant);
        AccessGroup group = tenantService.createAccessGroup(caller.getUser(), tenant.getId(), "G");
        authenticateAs(caller.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.assignAccessGroup(
                                        caller.getUser(),
                                        tenant.getId(),
                                        target.getId(),
                                        group.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- Batch permission update (REQ-12-16) ---

    @Test
    void batchUpdateAdditionsOnlyRequiresAndConsumesAValidToken() {
        Tenant tenant = tenant("Batch Add Co");
        TenantMembership caller = memberAdmin("batch-caller-1@example.com", tenant);
        TenantMembership target = member("batch-target-1@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.batchUpdatePermissions(
                                        caller.getUser(),
                                        tenant.getId(),
                                        target.getId(),
                                        Set.of(Permission.TENANT_MEMBER_MANAGE),
                                        null))
                .isInstanceOf(DeletionConfirmationInvalidException.class);

        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-permission-batch",
                        target.getId().toString(),
                        caller.getUser(),
                        null);
        tenantService.batchUpdatePermissions(
                caller.getUser(),
                tenant.getId(),
                target.getId(),
                Set.of(Permission.TENANT_MEMBER_MANAGE),
                word);

        assertThat(
                        directPermissionGrantRepository.findByTenantMembershipAndPermission(
                                target, Permission.TENANT_MEMBER_MANAGE))
                .isPresent();
    }

    @Test
    void batchUpdateRemovalsOnlyAlsoRequiresAToken() {
        Tenant tenant = tenant("Batch Remove Co");
        TenantMembership caller = memberAdmin("batch-caller-2@example.com", tenant);
        TenantMembership target = member("batch-target-2@example.com", tenant);
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(target, Permission.TENANT_MEMBER_MANAGE));
        authenticateAs(caller.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.batchUpdatePermissions(
                                        caller.getUser(),
                                        tenant.getId(),
                                        target.getId(),
                                        Set.of(),
                                        null))
                .isInstanceOf(DeletionConfirmationInvalidException.class);

        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-permission-batch",
                        target.getId().toString(),
                        caller.getUser(),
                        null);
        tenantService.batchUpdatePermissions(
                caller.getUser(), tenant.getId(), target.getId(), Set.of(), word);

        // Logical delete (2026-08-04): revoking sets deletedAt rather than removing the row, so
        // it must be excluded from the not-deleted finder used by permission resolution/listing.
        assertThat(
                        directPermissionGrantRepository
                                .findByTenantMembershipAndPermissionAndDeletedAtIsNull(
                                        target, Permission.TENANT_MEMBER_MANAGE))
                .isEmpty();
    }

    @Test
    void noOpBatchSucceedsWithNoTokenRequired() {
        Tenant tenant = tenant("Batch No-op Co");
        TenantMembership caller = memberAdmin("batch-caller-3@example.com", tenant);
        TenantMembership target = member("batch-target-3@example.com", tenant);
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(target, Permission.TENANT_MEMBER_MANAGE));
        authenticateAs(caller.getUser().getEmail());

        tenantService.batchUpdatePermissions(
                caller.getUser(),
                tenant.getId(),
                target.getId(),
                Set.of(Permission.TENANT_MEMBER_MANAGE),
                null);

        assertThat(
                        directPermissionGrantRepository.findByTenantMembershipAndPermission(
                                target, Permission.TENANT_MEMBER_MANAGE))
                .isPresent();
    }

    @Test
    void batchUpdateEmitsOneAuditEventPerAddedAndRemovedPermission() {
        Tenant tenant = tenant("Batch Audit Co");
        TenantMembership caller = memberAdmin("batch-caller-4@example.com", tenant);
        TenantMembership target = member("batch-target-4@example.com", tenant);
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(target, Permission.TENANT_MEMBER_MANAGE));
        authenticateAs(caller.getUser().getEmail());
        String word =
                deletionConfirmationTokenService.generate(
                        "tenant-permission-batch",
                        target.getId().toString(),
                        caller.getUser(),
                        null);

        tenantService.batchUpdatePermissions(
                caller.getUser(),
                tenant.getId(),
                target.getId(),
                Set.of(Permission.ARTICLE_VIEW),
                word);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        caller.getUser().getId());
        long batchEvents =
                events.stream()
                        .filter(event -> event.getAction().equals("tenant.permission.batch_update"))
                        .filter(event -> event.getOutcome() == AuditOutcome.SUCCESS)
                        .count();
        assertThat(batchEvents).isEqualTo(2); // one grant, one revoke
    }

    @Test
    void batchUpdateTargetingAMemberAdminIsRejectedRegardlessOfToken() {
        Tenant tenant = tenant("Batch Guard Co");
        TenantMembership caller = memberAdmin("batch-caller-5@example.com", tenant);
        TenantMembership target = memberAdmin("batch-target-5@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.batchUpdatePermissions(
                                        caller.getUser(),
                                        tenant.getId(),
                                        target.getId(),
                                        Set.of(Permission.TENANT_MEMBER_MANAGE),
                                        null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- Promotion (REQ-24-30) ---

    @Test
    void promoteSucceedsRegardlessOfExistingAdminCount() {
        Tenant tenant = tenant("Promote Co");
        TenantMembership caller = memberAdmin("promote-caller-1@example.com", tenant);
        for (int i = 0; i < 5; i++) {
            memberAdmin("promote-existing-admin-" + i + "@example.com", tenant);
        }
        TenantMembership target = member("promote-target-1@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());

        tenantService.promoteMember(caller.getUser(), tenant.getId(), target.getId());

        TenantMembership reloaded =
                tenantMembershipRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(MembershipRole.MEMBER_ADMIN);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(
                        caller.getUser().getId());
        assertThat(events).anyMatch(event -> event.getAction().equals("tenant.member.promote"));
    }

    @Test
    void plainMemberIsRejectedFromPromotingAnyoneToMemberAdmin() {
        Tenant tenant = tenant("Promote Reject Co");
        TenantMembership plain = member("promote-caller-member@example.com", tenant);
        directPermissionGrantRepository.saveAndFlush(
                new DirectPermissionGrant(plain, Permission.TENANT_MEMBER_MANAGE));
        TenantMembership target = member("promote-target-2@example.com", tenant);
        authenticateAs(plain.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.promoteMember(
                                        plain.getUser(), tenant.getId(), target.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void selfPromotionRejected() {
        Tenant tenant = tenant("Self Promote Co");
        TenantMembership caller = memberAdmin("promote-self@example.com", tenant);
        authenticateAs(caller.getUser().getEmail());

        assertThatThrownBy(
                        () ->
                                tenantService.promoteMember(
                                        caller.getUser(), tenant.getId(), caller.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // --- isLastAdminOfType detail-DTO amendment ---

    @Test
    void getMemberDetailReportsIsLastAdminOfTypeCorrectly() {
        Tenant tenant = tenant("Detail Co");
        TenantMembership sole = memberAdmin("detail-sole-admin@example.com", tenant);
        authenticateAs(sole.getUser().getEmail());

        var detail = tenantService.getMemberDetail(sole.getUser(), tenant.getId(), sole.getId());
        assertThat(detail.isLastAdminOfType()).isTrue();

        memberAdmin("detail-second-admin@example.com", tenant);
        var detailAfterSecond =
                tenantService.getMemberDetail(sole.getUser(), tenant.getId(), sole.getId());
        assertThat(detailAfterSecond.isLastAdminOfType()).isFalse();

        TenantMembership plainMember = member("detail-plain-member@example.com", tenant);
        var memberDetail =
                tenantService.getMemberDetail(sole.getUser(), tenant.getId(), plainMember.getId());
        assertThat(memberDetail.isLastAdminOfType()).isFalse();
    }

    @Test
    void getMemberDetailIsLastAdminOfTypeIsScopedPerTenant() {
        Tenant tenantA = tenant("Cross Detail A");
        Tenant tenantB = tenant("Cross Detail B");
        TenantMembership soleInA = memberAdmin("cross-detail-sole@example.com", tenantA);
        memberAdmin("cross-detail-other@example.com", tenantB);
        authenticateAs(soleInA.getUser().getEmail());
        tenantContext.setActiveTenantId(tenantA.getId());

        var detail =
                tenantService.getMemberDetail(soleInA.getUser(), tenantA.getId(), soleInA.getId());
        assertThat(detail.isLastAdminOfType()).isTrue();
    }
}
