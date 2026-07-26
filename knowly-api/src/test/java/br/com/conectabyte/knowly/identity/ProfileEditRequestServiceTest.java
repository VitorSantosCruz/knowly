package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.ProfileFieldsDto;
import br.com.conectabyte.knowly.identity.exception.PendingProfileEditRequestExistsException;
import br.com.conectabyte.knowly.identity.exception.ProfileFieldConflictException;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.NotificationRepository;
import br.com.conectabyte.knowly.tenancy.NotificationType;
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
 * Covers {@link ProfileEditRequestService}'s submit/approve/reject flow, per
 * specify/features/identity-profile-model/SPEC.md REQ-15..21.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ProfileEditRequestServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ProfileEditRequestRepository profileEditRequestRepository;
    @Autowired private ProfileEditRequestService profileEditRequestService;
    @Autowired private BlindIndexService blindIndexService;

    private User user(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    @Test
    void submittingCreatesAPendingRequestAndNotifiesEveryMemberAdminDeduplicated() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Submit Co"));
        User requester = user("submit-requester@example.com");
        User admin = user("submit-admin@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));

        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(
                        requester, new ProfileFieldsDto("New Name", null, null, null, null));

        assertThat(request.getStatus()).isEqualTo(ProfileEditRequestStatus.PENDING);
        var adminNotifications = notificationRepository.findByRecipientAndResolvedFalse(admin);
        assertThat(adminNotifications).hasSize(1);
        assertThat(adminNotifications.get(0).getType())
                .isEqualTo(NotificationType.PROFILE_EDIT_REQUEST_PENDING);
        assertThat(adminNotifications.get(0).getProfileEditRequest().getId())
                .isEqualTo(request.getId());
    }

    @Test
    void submittingASecondRequestWhileOneIsPendingIsRejected() {
        User requester = user("submit-double@example.com");
        profileEditRequestService.submitEditRequest(
                requester, new ProfileFieldsDto("First", null, null, null, null));

        assertThatThrownBy(
                        () ->
                                profileEditRequestService.submitEditRequest(
                                        requester,
                                        new ProfileFieldsDto("Second", null, null, null, null)))
                .isInstanceOf(PendingProfileEditRequestExistsException.class);
    }

    @Test
    void approvingAppliesTheProposedFieldsAndResolves() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Approve Co"));
        User requester = user("approve-requester@example.com");
        User admin = user("approve-admin@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(
                        requester, new ProfileFieldsDto("Approved Name", null, null, null, null));

        profileEditRequestService.approveEditRequest(admin, request.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Approved Name");
        ProfileEditRequest reloadedRequest =
                profileEditRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloadedRequest.getStatus()).isEqualTo(ProfileEditRequestStatus.APPROVED);
        assertThat(reloadedRequest.getResolvedBy().getId()).isEqualTo(admin.getId());
    }

    @Test
    void rejectingDiscardsTheProposedFieldsAndResolves() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Reject Co"));
        User requester = user("reject-requester@example.com");
        User admin = user("reject-admin@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(
                        requester, new ProfileFieldsDto("Rejected Name", null, null, null, null));

        profileEditRequestService.rejectEditRequest(admin, request.getId());

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isNotEqualTo("Rejected Name");
        ProfileEditRequest reloadedRequest =
                profileEditRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloadedRequest.getStatus()).isEqualTo(ProfileEditRequestStatus.REJECTED);
    }

    @Test
    void aCallerWithoutTheApplicableRightCannotApproveOrReject() {
        User requester = user("norights-requester@example.com");
        User impostor = user("norights-impostor@example.com");
        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(
                        requester, new ProfileFieldsDto("Name", null, null, null, null));

        assertThatThrownBy(
                        () ->
                                profileEditRequestService.approveEditRequest(
                                        impostor, request.getId()))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(
                        () ->
                                profileEditRequestService.rejectEditRequest(
                                        impostor, request.getId()))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void approvingARequestWhoseProposedCpfCollidesWithAnotherUsersFailsCleanlyWithNoPartialWrite() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Conflict Co"));
        User requester = user("conflict-requester@example.com");
        User admin = user("conflict-admin@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));

        User other = user("conflict-other@example.com");
        other.setCpf("11122233344");
        other.setCpfBlindIndex(blindIndexService.hmac("11122233344"));
        userRepository.saveAndFlush(other);

        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(
                        requester, new ProfileFieldsDto(null, null, null, "11122233344", null));

        assertThatThrownBy(
                        () -> profileEditRequestService.approveEditRequest(admin, request.getId()))
                .isInstanceOf(ProfileFieldConflictException.class);

        User reloaded = userRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getCpf()).isNull();
        ProfileEditRequest reloadedRequest =
                profileEditRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloadedRequest.getStatus()).isEqualTo(ProfileEditRequestStatus.PENDING);
    }
}
