package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.exception.ChatIneligibleParticipantException;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatEligibilityServiceTest {

    @Mock private TenantMembershipRepository tenantMembershipRepository;
    @Mock private UserRepository userRepository;

    private ChatEligibilityService service;

    @BeforeEach
    void setUp() {
        service = new ChatEligibilityService(tenantMembershipRepository, userRepository);
    }

    private User staffUser() {
        User user = new User("staff@example.com");
        user.setId(1L);
        user.setGlobalRole(GlobalRole.STAFF);
        return user;
    }

    private User plainMember() {
        User user = new User("member@example.com");
        user.setId(2L);
        return user;
    }

    private Tenant tenant(Long id) {
        Tenant tenant = new Tenant("Tenant " + id);
        tenant.setId(id);
        return tenant;
    }

    private TenantMembership activeMembership(User user, Tenant tenant) {
        return new TenantMembership(user, tenant, MembershipRole.MEMBER);
    }

    @Test
    void staffWithNoMembershipInTenantIsIneligibleForThatTenantsMemberOnlyGroup() {
        User staff = staffUser();
        when(tenantMembershipRepository.findByUserAndTenant(
                        org.mockito.ArgumentMatchers.eq(staff), argThatTenant(10L)))
                .thenReturn(Optional.empty());

        assertThat(service.isEligible(staff, 10L)).isFalse();
    }

    @Test
    void staffWithActiveMembershipInTenantIsEligibleForThatTenantsMemberOnlyGroup() {
        User staff = staffUser();
        Tenant tenant = tenant(10L);
        when(tenantMembershipRepository.findByUserAndTenant(
                        org.mockito.ArgumentMatchers.eq(staff), argThatTenant(10L)))
                .thenReturn(Optional.of(activeMembership(staff, tenant)));

        assertThat(service.isEligible(staff, 10L)).isTrue();
    }

    @Test
    void plainMemberIsIneligibleForAStaffOnlyGroup() {
        User member = plainMember();

        assertThat(service.isEligible(member, null)).isFalse();
    }

    @Test
    void staffCapableUserIsEligibleForAStaffOnlyGroup() {
        User staff = staffUser();

        assertThat(service.isEligible(staff, null)).isTrue();
    }

    @Test
    void sameStaffUserIsEligibleForOneTenantAndIneligibleForAnotherInTheSameRun() {
        User staff = staffUser();
        Tenant tenantT = tenant(10L);
        when(tenantMembershipRepository.findByUserAndTenant(
                        org.mockito.ArgumentMatchers.eq(staff), argThatTenant(10L)))
                .thenReturn(Optional.of(activeMembership(staff, tenantT)));
        when(tenantMembershipRepository.findByUserAndTenant(
                        org.mockito.ArgumentMatchers.eq(staff), argThatTenant(20L)))
                .thenReturn(Optional.empty());

        assertThat(service.isEligible(staff, 10L)).isTrue();
        assertThat(service.isEligible(staff, 20L)).isFalse();
    }

    private Tenant argThatTenant(Long id) {
        return org.mockito.ArgumentMatchers.argThat(t -> t != null && id.equals(t.getId()));
    }

    @Test
    void resolveDirectAnchorRejectsAStaffUserWithNoMembershipTargetingAMember() {
        User staff = staffUser();
        User member = plainMember();
        Tenant tenant = tenant(10L);
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantMembershipRepository.findByUserAndActiveTrue(member))
                .thenReturn(List.of(activeMembership(member, tenant)));

        assertThatThrownBy(() -> service.resolveDirectAnchor(staff, member))
                .isInstanceOf(ChatIneligibleParticipantException.class);
    }

    @Test
    void resolveDirectAnchorAcceptsAStaffUserWithMembershipTargetingAMemberOfTheSameTenant() {
        User staff = staffUser();
        User member = plainMember();
        Tenant tenant = tenant(10L);
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff))
                .thenReturn(List.of(activeMembership(staff, tenant)));
        when(tenantMembershipRepository.findByUserAndActiveTrue(member))
                .thenReturn(List.of(activeMembership(member, tenant)));

        assertThat(service.resolveDirectAnchor(staff, member)).isEqualTo(10L);
    }

    @Test
    void listCandidatesForGroupScopeOnlyReturnsEligibleUsers() {
        User staff = staffUser();
        User member = plainMember();
        Tenant tenant = tenant(10L);
        when(userRepository.findAll()).thenReturn(List.of(staff, member));
        when(tenantMembershipRepository.findByUserAndTenant(
                        org.mockito.ArgumentMatchers.eq(staff), argThatTenant(10L)))
                .thenReturn(Optional.empty());
        when(tenantMembershipRepository.findByUserAndTenant(
                        org.mockito.ArgumentMatchers.eq(member), argThatTenant(10L)))
                .thenReturn(Optional.of(activeMembership(member, tenant)));

        var candidates = service.listCandidates("group", 10L);

        assertThat(candidates).extracting("userId").containsExactly(2L);
    }

    @Test
    void listCandidatesForGroupStaffOnlyScopeOnlyReturnsStaffCapableUsers() {
        User staff = staffUser();
        User member = plainMember();
        when(userRepository.findAll()).thenReturn(List.of(staff, member));

        var candidates = service.listCandidates("group-staff-only", null);

        assertThat(candidates).extracting("userId").containsExactly(1L);
    }

    @Test
    void listCandidatesForDirectScopeReturnsEveryone() {
        User staff = staffUser();
        User member = plainMember();
        when(userRepository.findAll()).thenReturn(List.of(staff, member));

        var candidates = service.listCandidates("direct", null);

        assertThat(candidates).hasSize(2);
    }
}
