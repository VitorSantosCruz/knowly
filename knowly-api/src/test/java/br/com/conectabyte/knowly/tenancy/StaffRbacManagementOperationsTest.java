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
 * batch-update for global-scope ({@code STAFF}/{@code STAFF_ADMIN}) users, and the admin-target
 * rejection on the existing grant/access-group endpoints. Service-level, mirroring {@code
 * StaffServiceTest}'s direct-call style rather than MockMvc, since every case under test is a
 * caller-identity/floor-check concern independent of HTTP/CSRF plumbing.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class StaffRbacManagementOperationsTest {

    @Autowired private UserRepository userRepository;
    @Autowired private StaffService staffService;
    @Autowired private TenantContext tenantContext;
    @Autowired private DirectGlobalPermissionGrantRepository directGlobalPermissionGrantRepository;
    @Autowired private DeletionConfirmationTokenService deletionConfirmationTokenService;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Autowired
    private br.com.conectabyte.knowly.identity.ProfileEditRequestRepository
            profileEditRequestRepository;

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        tenantContext.clear();
    }

    // NB: tenantContext.isStaffAdmin() is what GlobalPermissionAspect/PermissionAspect actually
    // consult -- it must reflect the user being authenticated *as*, not whichever fixture was
    // last created via staffAdmin()/limitedStaff(). authenticateAs(User) re-derives it fresh from
    // the DB every call so call order among fixture-creation helpers never leaks into it.
    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(context);
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        tenantContext.setStaffAdmin(user.getGlobalRole() == GlobalRole.STAFF_ADMIN);
    }

    private void authenticateAs(User user) {
        authenticateAs(user.getEmail());
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
     * This class's {@code @SpringBootTest} context (and its Testcontainers database) is shared
     * across the whole test run, so earlier test classes/methods leave real {@code STAFF_ADMIN}
     * rows behind -- the exact-count assertions below ("exactly N admins exist") need a clean
     * slate. Downgrading (never deleting) avoids the FK issue a bulk delete would hit for any prior
     * admin that already has {@code AuditEvent} rows referencing it as actor.
     */
    private void resetAllStaffAdminsToStaff() {
        userRepository
                .findByGlobalRoleInAndDeletedAtIsNull(List.of(GlobalRole.STAFF_ADMIN))
                .stream()
                .forEach(
                        user -> {
                            user.setGlobalRole(GlobalRole.STAFF);
                            userRepository.saveAndFlush(user);
                        });
    }

    // --- Demotion (REQ-1-6, REQ-21-23) ---

    @Test
    void demoteSucceedsWhenAtLeastTwoStaffAdminsExist() {
        User caller = staffAdmin("demote-caller-1@example.com");
        User target = staffAdmin("demote-target-1@example.com");
        authenticateAs(caller.getEmail());

        staffService.demoteStaffUser(target.getId());

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getGlobalRole()).isEqualTo(GlobalRole.STAFF);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(caller.getId());
        assertThat(events).anyMatch(event -> event.getAction().equals("staff.user.demote"));
    }

    // Note: a single-threaded "demote the last STAFF_ADMIN via a third-party caller" 409 case is
    // unreachable by construction -- requireCallerIsStaffAdmin means the caller is always itself a
    // live STAFF_ADMIN, so the locked count always includes at least the caller as "another admin"
    // unless the caller targets themselves, which is independently blocked by
    // requireNotSelfTarget regardless of admin count (see selfDemotionRejectedRegardlessOfCount
    // below). The floor check's only reachable trigger is therefore the concurrency race below
    // (concurrentDemoteAgainstTwoDifferentStaffAdminsAllowsExactlyOneToSucceed), which is this
    // feature's actual TOCTOU-closing guarantee -- documented here rather than left as a silent
    // gap in coverage.

    @Test
    void selfDemotionRejectedRegardlessOfAdminCount() {
        User caller = staffAdmin("self-demote@example.com");
        staffAdmin("self-demote-other@example.com");
        authenticateAs(caller.getEmail());

        assertThatThrownBy(() -> staffService.demoteStaffUser(caller.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException
                                .class);

        User reloaded = userRepository.findById(caller.getId()).orElseThrow();
        assertThat(reloaded.getGlobalRole()).isEqualTo(GlobalRole.STAFF_ADMIN);
    }

    @Test
    void staffCallerWithUnrelatedGrantIsRejectedFromDemotingAStaffAdmin() {
        User target = staffAdmin("demote-target-4@example.com");
        User staff = limitedStaff("demote-caller-staff@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.STAFF_PERMISSION_MANAGE));
        authenticateAs(staff.getEmail());

        assertThatThrownBy(() -> staffService.demoteStaffUser(target.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException
                                .class);
    }

    @Test
    void concurrentDemoteAgainstTwoDifferentStaffAdminsAllowsExactlyOneToSucceed()
            throws Exception {
        // Exactly two STAFF_ADMINs, each demoting the other -- whichever transaction's lock read
        // wins goes first (leaving one admin); the loser's own lock re-read (post-commit,
        // read-committed) then sees only one admin left and is rejected, regardless of whether
        // that rejection surfaces as LastAdminRemainingException (still an admin when the check
        // ran) or PermissionDeniedException (already demoted by the time the loser's caller-role
        // check ran) -- both are "did not succeed", so only successCount/failureCount matter.
        resetAllStaffAdminsToStaff();
        User admin1 = staffAdmin("concurrent-admin-1@example.com");
        User admin2 = staffAdmin("concurrent-admin-2@example.com");

        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Runnable admin2DemotesAdmin1 =
                () -> {
                    authenticateAs(admin2.getEmail());
                    bothStarted.countDown();
                    try {
                        bothStarted.await();
                        transactionTemplate.executeWithoutResult(
                                status -> staffService.demoteStaffUser(admin1.getId()));
                        successCount.incrementAndGet();
                    } catch (Exception ex) {
                        failureCount.incrementAndGet();
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
        Runnable admin1DemotesAdmin2 =
                () -> {
                    authenticateAs(admin1.getEmail());
                    bothStarted.countDown();
                    try {
                        bothStarted.await();
                        transactionTemplate.executeWithoutResult(
                                status -> staffService.demoteStaffUser(admin2.getId()));
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
    void deletionSucceedsWithAValidToken() {
        User caller = staffAdmin("delete-caller-1@example.com");
        User target = limitedStaff("delete-target-1@example.com");
        authenticateAs(caller.getEmail());
        String word =
                deletionConfirmationTokenService.generate(
                        "staff-user", target.getId().toString(), caller, null);

        staffService.deleteStaffUser(target.getId(), word);

        // Logical delete (2026-08-04): the row stays, marked deletedAt, rather than being removed.
        assertThat(userRepository.findById(target.getId())).isPresent();
        assertThat(userRepository.findById(target.getId()).orElseThrow().getDeletedAt())
                .isNotNull();
    }

    @Test
    void deletionCancelsAnyPendingProfileEditRequestFromTheTarget() {
        // A pending request to edit a profile that no longer effectively exists makes no sense
        // to leave outstanding -- logical-delete-everywhere (2026-08-04).
        User caller = staffAdmin("delete-cancels-request-caller@example.com");
        User target = limitedStaff("delete-cancels-request-target@example.com");
        var request =
                profileEditRequestRepository.saveAndFlush(
                        new br.com.conectabyte.knowly.identity.ProfileEditRequest(target));
        authenticateAs(caller.getEmail());
        String word =
                deletionConfirmationTokenService.generate(
                        "staff-user", target.getId().toString(), caller, null);

        staffService.deleteStaffUser(target.getId(), word);

        var reloaded = profileEditRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus())
                .isEqualTo(br.com.conectabyte.knowly.identity.ProfileEditRequestStatus.CANCELLED);
        assertThat(reloaded.getResolvedAt()).isNotNull();
    }

    @Test
    void deletionRejectedWithoutOrWithAWrongToken() {
        User caller = staffAdmin("delete-caller-2@example.com");
        User target = limitedStaff("delete-target-2@example.com");
        authenticateAs(caller.getEmail());

        assertThatThrownBy(() -> staffService.deleteStaffUser(target.getId(), null))
                .isInstanceOf(DeletionConfirmationInvalidException.class);
        assertThatThrownBy(() -> staffService.deleteStaffUser(target.getId(), "wrong-word"))
                .isInstanceOf(DeletionConfirmationInvalidException.class);
        assertThat(userRepository.findById(target.getId())).isPresent();
    }

    // Note: same reasoning as demote's "unreachable single-threaded 409" comment above applies to
    // delete -- enforceStaffCeiling means only a STAFF_ADMIN caller can ever pass the ceiling
    // against a STAFF_ADMIN target, and that caller is always itself counted as "another admin"
    // unless self-targeting (independently blocked). The floor's only reachable trigger is the
    // concurrency race (concurrentDemoteAgainstTwoDifferentStaffAdminsAllowsExactlyOneToSucceed
    // exercises the identical UserRepository#findByGlobalRoleForUpdate lock both demote and delete
    // share).

    @Test
    void deletingAPlainStaffUserIsNeverBlockedByTheAdminFloor() {
        User caller = staffAdmin("delete-caller-3@example.com");
        User plainStaff = limitedStaff("delete-target-3@example.com");
        authenticateAs(caller.getEmail());

        String wordForStaff =
                deletionConfirmationTokenService.generate(
                        "staff-user", plainStaff.getId().toString(), caller, null);
        staffService.deleteStaffUser(plainStaff.getId(), wordForStaff);

        // Logical delete (2026-08-04): the row stays, marked deletedAt, rather than being removed.
        assertThat(userRepository.findById(plainStaff.getId())).isPresent();
        assertThat(userRepository.findById(plainStaff.getId()).orElseThrow().getDeletedAt())
                .isNotNull();
    }

    @Test
    void selfDeletionRejected() {
        User caller = staffAdmin("delete-self@example.com");
        staffAdmin("delete-self-other@example.com");
        authenticateAs(caller.getEmail());
        String word =
                deletionConfirmationTokenService.generate(
                        "staff-user", caller.getId().toString(), caller, null);

        assertThatThrownBy(() -> staffService.deleteStaffUser(caller.getId(), word))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException
                                .class);
        assertThat(userRepository.findById(caller.getId())).isPresent();
    }

    // --- Admin-target grant/assign rejection (REQ-17-19) ---

    @Test
    void grantPermissionAgainstAStaffAdminTargetIsRejectedAndCreatesNoGrant() {
        User caller = staffAdmin("guard-caller-1@example.com");
        User target = staffAdmin("guard-target-1@example.com");
        authenticateAs(caller.getEmail());

        assertThatThrownBy(
                        () ->
                                staffService.grantPermission(
                                        target.getId(), GlobalPermission.TENANT_CREATE))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException
                                .class);
        assertThat(
                        directGlobalPermissionGrantRepository.findByUserAndPermission(
                                target, GlobalPermission.TENANT_CREATE))
                .isEmpty();
    }

    @Test
    void assignAccessGroupAgainstAStaffAdminTargetIsRejected() {
        User caller = staffAdmin("guard-caller-2@example.com");
        User target = staffAdmin("guard-target-2@example.com");
        authenticateAs(caller.getEmail());
        GlobalAccessGroup group = staffService.createAccessGroup("Guard Group");

        assertThatThrownBy(() -> staffService.assignAccessGroup(target.getId(), group.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException
                                .class);
    }

    // --- Batch permission update (REQ-12-16) ---

    @Test
    void batchUpdateAdditionsOnlyRequiresAndConsumesAValidToken() {
        User caller = staffAdmin("batch-caller-1@example.com");
        User target = limitedStaff("batch-target-1@example.com");
        authenticateAs(caller.getEmail());

        assertThatThrownBy(
                        () ->
                                staffService.batchUpdatePermissions(
                                        target.getId(),
                                        Set.of(GlobalPermission.TENANT_CREATE),
                                        null))
                .isInstanceOf(DeletionConfirmationInvalidException.class);

        String word =
                deletionConfirmationTokenService.generate(
                        "staff-permission-batch", target.getId().toString(), caller, null);
        staffService.batchUpdatePermissions(
                target.getId(), Set.of(GlobalPermission.TENANT_CREATE), word);

        assertThat(
                        directGlobalPermissionGrantRepository.findByUserAndPermission(
                                target, GlobalPermission.TENANT_CREATE))
                .isPresent();
    }

    @Test
    void batchUpdateRemovalsOnlyAlsoRequiresAToken() {
        User caller = staffAdmin("batch-caller-2@example.com");
        User target = limitedStaff("batch-target-2@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(target, GlobalPermission.TENANT_CREATE));
        authenticateAs(caller.getEmail());

        assertThatThrownBy(
                        () -> staffService.batchUpdatePermissions(target.getId(), Set.of(), null))
                .isInstanceOf(DeletionConfirmationInvalidException.class);

        String word =
                deletionConfirmationTokenService.generate(
                        "staff-permission-batch", target.getId().toString(), caller, null);
        staffService.batchUpdatePermissions(target.getId(), Set.of(), word);

        // Logical delete (2026-08-04): revoking sets deletedAt rather than removing the row, so
        // it must be excluded from the not-deleted finder used by permission resolution/listing.
        assertThat(
                        directGlobalPermissionGrantRepository
                                .findByUserAndPermissionAndDeletedAtIsNull(
                                        target, GlobalPermission.TENANT_CREATE))
                .isEmpty();
    }

    @Test
    void noOpBatchSucceedsWithNoTokenRequired() {
        User caller = staffAdmin("batch-caller-3@example.com");
        User target = limitedStaff("batch-target-3@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(target, GlobalPermission.TENANT_CREATE));
        authenticateAs(caller.getEmail());

        staffService.batchUpdatePermissions(
                target.getId(), Set.of(GlobalPermission.TENANT_CREATE), null);

        assertThat(
                        directGlobalPermissionGrantRepository.findByUserAndPermission(
                                target, GlobalPermission.TENANT_CREATE))
                .isPresent();
    }

    @Test
    void batchUpdateEmitsOneAuditEventPerAddedAndRemovedPermission() {
        User caller = staffAdmin("batch-caller-4@example.com");
        User target = limitedStaff("batch-target-4@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(target, GlobalPermission.TENANT_CREATE));
        authenticateAs(caller.getEmail());
        String word =
                deletionConfirmationTokenService.generate(
                        "staff-permission-batch", target.getId().toString(), caller, null);

        staffService.batchUpdatePermissions(
                target.getId(), Set.of(GlobalPermission.TENANT_ACT_AS_ANY), word);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(caller.getId());
        long batchEvents =
                events.stream()
                        .filter(event -> event.getAction().equals("staff.permission.batch_update"))
                        .filter(event -> event.getOutcome() == AuditOutcome.SUCCESS)
                        .count();
        assertThat(batchEvents).isEqualTo(2); // one grant, one revoke
    }

    @Test
    void batchUpdateTargetingAStaffAdminIsRejectedRegardlessOfToken() {
        User caller = staffAdmin("batch-caller-5@example.com");
        User target = staffAdmin("batch-target-5@example.com");
        authenticateAs(caller.getEmail());

        assertThatThrownBy(
                        () ->
                                staffService.batchUpdatePermissions(
                                        target.getId(),
                                        Set.of(GlobalPermission.TENANT_CREATE),
                                        null))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException
                                .class);
    }

    // --- Promotion (REQ-24-30) ---

    @Test
    void promoteSucceedsRegardlessOfExistingAdminCount() {
        User caller = staffAdmin("promote-caller-1@example.com");
        for (int i = 0; i < 5; i++) {
            staffAdmin("promote-existing-admin-" + i + "@example.com");
        }
        User target = limitedStaff("promote-target-1@example.com");
        authenticateAs(caller.getEmail());

        staffService.promoteStaffUser(target.getId());

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getGlobalRole()).isEqualTo(GlobalRole.STAFF_ADMIN);

        List<AuditEvent> events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(caller.getId());
        assertThat(events).anyMatch(event -> event.getAction().equals("staff.user.promote"));
    }

    @Test
    void staffCallerIsRejectedFromPromotingAnyoneToStaffAdmin() {
        User staff = limitedStaff("promote-caller-staff@example.com");
        directGlobalPermissionGrantRepository.saveAndFlush(
                new DirectGlobalPermissionGrant(staff, GlobalPermission.STAFF_PERMISSION_MANAGE));
        User target = limitedStaff("promote-target-2@example.com");
        authenticateAs(staff.getEmail());

        assertThatThrownBy(() -> staffService.promoteStaffUser(target.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException
                                .class);
    }

    @Test
    void selfPromotionRejected() {
        User caller = staffAdmin("promote-self@example.com");
        authenticateAs(caller.getEmail());

        assertThatThrownBy(() -> staffService.promoteStaffUser(caller.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.tenancy.exception.PermissionDeniedException
                                .class);
    }

    // --- isLastAdminOfType detail-DTO amendment ---

    @Test
    void staffUserDetailReportsIsLastAdminOfTypeCorrectly() {
        resetAllStaffAdminsToStaff();
        User sole = staffAdmin("detail-sole-admin@example.com");
        authenticateAs(sole.getEmail());

        var detail = staffService.getStaffUserDetail(sole.getId());
        assertThat(detail.isLastAdminOfType()).isTrue();
        assertThat(detail.globalRole()).isEqualTo(GlobalRole.STAFF_ADMIN);

        staffAdmin("detail-second-admin@example.com");
        var detailAfterSecond = staffService.getStaffUserDetail(sole.getId());
        assertThat(detailAfterSecond.isLastAdminOfType()).isFalse();

        User plainStaff = limitedStaff("detail-plain-staff@example.com");
        var staffDetail = staffService.getStaffUserDetail(plainStaff.getId());
        assertThat(staffDetail.isLastAdminOfType()).isFalse();
    }
}
