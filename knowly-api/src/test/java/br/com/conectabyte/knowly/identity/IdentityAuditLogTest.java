package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditEventRepository;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.dto.ProfileFieldsDto;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
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
 * Confirms {@code @AuditLog} fires for direct edit/submit/approve/reject (SPEC's "Observability"
 * NFR) and that no event's {@code metadata} ever contains a raw cpf/rg value. See this task's final
 * report for a documented deviation: {@code AuditLogAspect} today only ever populates {@code
 * metadata} for {@code captureSourceIp}, so it is always {@code null} for every event this feature
 * writes -- trivially satisfying "never contains a raw cpf/rg value," but not yet implementing the
 * PLAN's "metadata carries changed field names" richer behavior (that would require extending the
 * shared aspect, out of this feature's declared file list).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class IdentityAuditLogTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private UserProfileService userProfileService;
    @Autowired private ProfileEditRequestService profileEditRequestService;

    private User user(String email) {
        return userRepository.saveAndFlush(new User(email));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    @Test
    void directEditEmitsAnAuditEventWithNoRawCpfOrRgInMetadata() {
        User staffAdmin = user("audit-direct-edit@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        authenticateAs("audit-direct-edit@example.com");

        userProfileService.directEdit(
                staffAdmin,
                staffAdmin.getId(),
                new ProfileFieldsDto("Audited Name", null, null, "12345678900", null));

        var events =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(staffAdmin.getId());
        assertThat(events).extracting(AuditEvent::getAction).contains("identity.profile.edit");
        assertThat(events)
                .allSatisfy(event -> assertThat(safeMetadata(event)).doesNotContain("12345678900"));
    }

    @Test
    void submitApproveAndRejectEachEmitTheirOwnAuditEvent() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Audit Co"));
        User requester = user("audit-requester@example.com");
        User admin = user("audit-admin@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(requester, tenant, MembershipRole.MEMBER));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(admin, tenant, MembershipRole.MEMBER_ADMIN));

        authenticateAs("audit-requester@example.com");
        ProfileEditRequest submitted =
                profileEditRequestService.submitEditRequest(
                        requester, new ProfileFieldsDto("Audited Request", null, null, null, null));
        authenticateAs("audit-admin@example.com");
        profileEditRequestService.approveEditRequest(admin, submitted.getId());

        User secondRequesterUser = user("audit-requester-2@example.com");
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(secondRequesterUser, tenant, MembershipRole.MEMBER));
        authenticateAs("audit-requester-2@example.com");
        ProfileEditRequest secondRequest =
                profileEditRequestService.submitEditRequest(
                        secondRequesterUser,
                        new ProfileFieldsDto("Second", null, null, null, null));
        authenticateAs("audit-admin@example.com");
        profileEditRequestService.rejectEditRequest(admin, secondRequest.getId());

        var requesterEvents =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(requester.getId());
        assertThat(requesterEvents)
                .extracting(AuditEvent::getAction)
                .contains("identity.profile.edit_request.submit");

        var adminEvents =
                auditEventRepository.findByActorUserIdOrderByOccurredAtDesc(admin.getId());
        assertThat(adminEvents)
                .extracting(AuditEvent::getAction)
                .contains(
                        "identity.profile.edit_request.approve",
                        "identity.profile.edit_request.reject");
    }

    private String safeMetadata(AuditEvent event) {
        return event.getMetadata() == null ? "" : event.getMetadata();
    }
}
