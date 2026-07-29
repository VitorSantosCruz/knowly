package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.ContactChangeDto;
import br.com.conectabyte.knowly.identity.dto.ProfileEditRequestFieldsDto;
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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Covers {@link ProfileEditRequestService}'s submit/approve/reject flow, per
 * specify/features/identity-profile-model-v2/SPEC.md REQ-14..22.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ProfileEditRequestServiceTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private ProfileEditRequestRepository profileEditRequestRepository;
    @Autowired private ProfileEditRequestService profileEditRequestService;
    @Autowired private BlindIndexService blindIndexService;
    @Autowired private UserProfileService userProfileService;

    private User user(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    private static ProfileFieldsDto fields(String fullName) {
        return new ProfileFieldsDto(fullName, null, null, null, null, null, null);
    }

    private static ProfileFieldsDto fieldsWithCpf(String cpf) {
        return new ProfileFieldsDto(null, cpf, null, null, null, null, null);
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
                        requester, new ProfileEditRequestFieldsDto(fields("New Name"), List.of()));

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
                requester, new ProfileEditRequestFieldsDto(fields("First"), List.of()));

        assertThatThrownBy(
                        () ->
                                profileEditRequestService.submitEditRequest(
                                        requester,
                                        new ProfileEditRequestFieldsDto(
                                                fields("Second"), List.of())))
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
                        requester,
                        new ProfileEditRequestFieldsDto(fields("Approved Name"), List.of()));

        profileEditRequestService.approveEditRequest(admin, request.getId());

        UserProfile reloaded = userProfileRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Approved Name");
        ProfileEditRequest reloadedRequest =
                profileEditRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloadedRequest.getStatus()).isEqualTo(ProfileEditRequestStatus.APPROVED);
        assertThat(reloadedRequest.getResolvedBy().getId()).isEqualTo(admin.getId());
    }

    @Test
    void approvingAppliesContactChangesAtomicallyAlongsideFlattenedFields() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Contact Approve Co"));
        User requester = user("contact-approve-requester@example.com");
        User admin = user("contact-approve-admin@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        Contact existing =
                contactRepository.save(
                        new Contact(requester, ContactType.OTHER, "old-value", null, false));

        List<ContactChangeDto> contactChanges =
                List.of(
                        new ContactChangeDto(
                                ContactChangeAction.ADD,
                                null,
                                ContactType.PHONE,
                                "+5511999990000",
                                null,
                                true),
                        new ContactChangeDto(
                                ContactChangeAction.REMOVE,
                                existing.getId(),
                                null,
                                null,
                                null,
                                null));

        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(
                        requester,
                        new ProfileEditRequestFieldsDto(
                                fields("Contact Approved Name"), contactChanges));

        profileEditRequestService.approveEditRequest(admin, request.getId());

        UserProfile reloaded = userProfileRepository.findById(requester.getId()).orElseThrow();
        assertThat(reloaded.getFullName()).isEqualTo("Contact Approved Name");
        List<Contact> contacts = contactRepository.findByUser(requester);
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getType()).isEqualTo(ContactType.PHONE);
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
                        requester,
                        new ProfileEditRequestFieldsDto(fields("Rejected Name"), List.of()));

        profileEditRequestService.rejectEditRequest(admin, request.getId());

        assertThat(userProfileRepository.findById(requester.getId()).map(UserProfile::getFullName))
                .isNotEqualTo(java.util.Optional.of("Rejected Name"));
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
                        requester, new ProfileEditRequestFieldsDto(fields("Name"), List.of()));

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
    void theRequesterCannotResolveTheirOwnRequestEvenIfTheyOtherwiseHoldTheRight() {
        User requester = user("self-approve-requester@example.com");
        requester.setGlobalRole(br.com.conectabyte.knowly.tenancy.GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(requester);
        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(
                        requester, new ProfileEditRequestFieldsDto(fields("Name"), List.of()));

        assertThatThrownBy(
                        () ->
                                profileEditRequestService.approveEditRequest(
                                        requester, request.getId()))
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
        UserProfile otherProfile = userProfileService.requireUserProfile(other);
        otherProfile.setCpf("11122233344");
        otherProfile.setCpfBlindIndex(blindIndexService.hmac("11122233344"));
        userProfileRepository.saveAndFlush(otherProfile);

        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(
                        requester,
                        new ProfileEditRequestFieldsDto(fieldsWithCpf("11122233344"), List.of()));

        assertThatThrownBy(
                        () -> profileEditRequestService.approveEditRequest(admin, request.getId()))
                .isInstanceOf(ProfileFieldConflictException.class);

        assertThat(userProfileRepository.findById(requester.getId()).map(UserProfile::getCpf))
                .isNotEqualTo(java.util.Optional.of("11122233344"));
        ProfileEditRequest reloadedRequest =
                profileEditRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloadedRequest.getStatus()).isEqualTo(ProfileEditRequestStatus.PENDING);
    }

    @Test
    void aCancelledRequestIsExcludedFromThePendingListAndCannotBeResolved() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Cancelled Co"));
        User requester = user("cancelled-requester@example.com");
        User admin = user("cancelled-admin@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));
        ProfileEditRequest request =
                profileEditRequestService.submitEditRequest(
                        requester, new ProfileEditRequestFieldsDto(fields("Name"), List.of()));
        request.setStatus(ProfileEditRequestStatus.CANCELLED);
        profileEditRequestRepository.saveAndFlush(request);

        assertThat(profileEditRequestService.listPendingForApprover(admin))
                .noneMatch(pending -> pending.getId().equals(request.getId()));
        assertThatThrownBy(
                        () -> profileEditRequestService.approveEditRequest(admin, request.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.identity.exception
                                .ProfileEditRequestAlreadyResolvedException.class);
    }
}
