package br.com.conectabyte.knowly.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.exception.ChatIneligibleParticipantException;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.MembershipRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
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
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private TenantContext tenantContext;

    private ChatEligibilityService service;

    @BeforeEach
    void setUp() {
        service =
                new ChatEligibilityService(
                        tenantMembershipRepository,
                        userRepository,
                        userProfileRepository,
                        tenantContext);
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

    // --- Bug fix (2026-08-09): isEligibleAsActor -- a staff actor's own active-session tenant ---
    // --- must be usable for group creation even without a real TenantMembership row there ---

    @Test
    void staffActorWithNoMembershipIsEligibleAsActorForTheirOwnActiveSessionTenant() {
        User staff = staffUser();
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));

        assertThat(service.isEligibleAsActor(staff, 10L)).isTrue();
    }

    @Test
    void staffActorIsNotEligibleAsActorForATenantOtherThanTheirOwnActiveSessionTenant() {
        User staff = staffUser();
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));

        assertThat(service.isEligibleAsActor(staff, 20L)).isFalse();
    }

    @Test
    void plainMemberActorWithNoMembershipIsNotEligibleAsActorForATenant() {
        User member = plainMember();
        when(tenantMembershipRepository.findByUserAndActiveTrue(member)).thenReturn(List.of());

        assertThat(service.isEligibleAsActor(member, 10L)).isFalse();
    }

    @Test
    void plainMemberActorWithARealMembershipIsEligibleAsActorForThatTenant() {
        User member = plainMember();
        Tenant tenant = tenant(10L);
        when(tenantMembershipRepository.findByUserAndActiveTrue(member))
                .thenReturn(List.of(activeMembership(member, tenant)));

        assertThat(service.isEligibleAsActor(member, 10L)).isTrue();
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

    // --- product decision (2026-08-09): a staff actor with an active tenant session must never
    // be able to open/create a DIRECT conversation with another staff member either -- the active
    // tenant REPLACES the staff-only anchor for direct-scope resolution, same exclusivity already
    // applied to listCandidates' "direct" scope via directScopeAnchorsForActor ---

    @Test
    void resolveDirectAnchorRejectsAStaffActorWithAnActiveTenantTargetingAnotherStaffUser() {
        User staff = staffUser();
        User otherStaff = new User("other-staff@example.com");
        otherStaff.setId(3L);
        otherStaff.setGlobalRole(GlobalRole.STAFF);
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantMembershipRepository.findByUserAndActiveTrue(otherStaff)).thenReturn(List.of());
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));

        assertThatThrownBy(() -> service.resolveDirectAnchor(staff, otherStaff))
                .isInstanceOf(ChatIneligibleParticipantException.class);
    }

    @Test
    void resolveDirectAnchorStillWorksForAStaffActorWithNoActiveTenantTargetingAnotherStaffUser() {
        User staff = staffUser();
        User otherStaff = new User("other-staff@example.com");
        otherStaff.setId(3L);
        otherStaff.setGlobalRole(GlobalRole.STAFF);
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantMembershipRepository.findByUserAndActiveTrue(otherStaff)).thenReturn(List.of());
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        assertThat(service.resolveDirectAnchor(staff, otherStaff)).isNull();
    }

    @Test
    void listCandidatesForGroupScopeOnlyReturnsEligibleUsers() {
        User staff = staffUser();
        User member = plainMember();
        Tenant tenant = tenant(10L);
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(staff, member));
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        // The actor (staff) must themselves be anchored to tenant 10 -- via their session's
        // active tenant, same mechanism already used for "direct" scope -- for the security fix
        // below to let this legitimate request through.
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));
        when(tenantMembershipRepository.findByUserAndTenant(
                        org.mockito.ArgumentMatchers.eq(member), argThatTenant(10L)))
                .thenReturn(Optional.of(activeMembership(member, tenant)));

        var candidates = service.listCandidates(staff, "group", 10L);

        assertThat(candidates).extracting("userId").containsExactly(2L);
    }

    // --- cross-tenant PII leak fix (2026-08-09 appsec): "group" must not trust a
    // client-supplied tenantId the actor has no real relationship with ---

    @Test
    void listCandidatesForGroupScopeRejectsAnActorWithNoRelationshipToTheRequestedTenant() {
        User attacker = plainMember();
        User victimTenantMember = new User("victim@example.com");
        victimTenantMember.setId(3L);
        Tenant tenantB = tenant(20L);

        when(userRepository.findAllByDeletedAtIsNull())
                .thenReturn(List.of(attacker, victimTenantMember));
        // The actor (attacker) has no membership at all -- in particular, none in tenant B.
        when(tenantMembershipRepository.findByUserAndActiveTrue(attacker)).thenReturn(List.of());

        assertThatThrownBy(() -> service.listCandidates(attacker, "group", tenantB.getId()))
                .isInstanceOf(
                        br.com.conectabyte.knowly.chat.exception.ChatAccessDeniedException.class);
    }

    @Test
    void listCandidatesForGroupStaffOnlyScopeOnlyReturnsStaffCapableUsers() {
        User staff = staffUser();
        User member = plainMember();
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(staff, member));

        var candidates = service.listCandidates(member, "group-staff-only", null);

        assertThat(candidates).extracting("userId").containsExactly(1L);
    }

    @Test
    void listCandidatesForDirectScopeOnlyReturnsUsersSharingAnEligibleAnchor() {
        User staff = staffUser();
        User member = plainMember();
        User unrelatedStaff = new User("other-staff@example.com");
        unrelatedStaff.setId(3L);
        unrelatedStaff.setGlobalRole(GlobalRole.STAFF);
        when(userRepository.findAllByDeletedAtIsNull())
                .thenReturn(List.of(staff, member, unrelatedStaff));
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantMembershipRepository.findByUserAndActiveTrue(member)).thenReturn(List.of());
        when(tenantMembershipRepository.findByUserAndActiveTrue(unrelatedStaff))
                .thenReturn(List.of());

        // staff and unrelatedStaff both share the null (staff-only) anchor; plainMember has no
        // active membership and isn't staff-capable, so shares nothing with staff.
        var candidates = service.listCandidates(staff, "direct", null);

        assertThat(candidates).extracting("userId").containsExactly(3L);
    }

    @Test
    void listCandidatesForDirectScopeNeverIncludesTheActorThemselves() {
        User staff = staffUser();
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(staff));

        var candidates = service.listCandidates(staff, "direct", null);

        assertThat(candidates).isEmpty();
    }

    // --- logical-delete-everywhere (2026-08-04): a soft-deleted user must not be reachable ---

    @Test
    void listCandidatesNeverIncludesASoftDeletedUserBecauseTheRepositoryQueryExcludesThem() {
        User staff = staffUser();
        User member = plainMember();
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(staff));

        var candidates = service.listCandidates(member, "group-staff-only", null);

        assertThat(candidates).extracting("userId").containsExactly(1L);
    }

    @Test
    void listCandidatesExposesTheCandidateAvatarUrlWhenTheProfileHasOne() {
        User staff = staffUser();
        User member = plainMember();
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(staff, member));
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));
        when(tenantMembershipRepository.findByUserAndTenant(
                        org.mockito.ArgumentMatchers.eq(member), argThatTenant(10L)))
                .thenReturn(Optional.of(activeMembership(member, tenant(10L))));

        var memberProfile = new br.com.conectabyte.knowly.identity.UserProfile();
        memberProfile.setAvatarUrl("https://minio.local/avatars/2");
        when(userProfileRepository.findById(2L)).thenReturn(Optional.of(memberProfile));

        var candidates = service.listCandidates(staff, "group", 10L);

        assertThat(candidates)
                .extracting("avatarUrl")
                .containsExactly("https://minio.local/avatars/2");
    }

    @Test
    void listCandidatesExposesNullAvatarUrlWhenTheCandidateHasNoProfile() {
        User staff = staffUser();
        User member = plainMember();
        when(userRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(staff, member));

        var candidates = service.listCandidates(member, "group-staff-only", null);

        assertThat(candidates).extracting("avatarUrl").containsExactly((Object) null);
    }

    @Test
    void softDeletedStaffUserIsIneligibleForAStaffOnlyGroupEvenIfAlreadyLoaded() {
        User staff = staffUser();
        staff.setDeletedAt(java.time.Instant.now());

        assertThat(service.isEligible(staff, null)).isFalse();
    }

    @Test
    void softDeletedMemberIsIneligibleForAMemberOnlyGroupEvenIfAlreadyLoaded() {
        User member = plainMember();
        member.setDeletedAt(java.time.Instant.now());

        assertThat(service.isEligible(member, 10L)).isFalse();
    }

    // --- tenant-isolation bug fix: a staff session's *active tenant* (server-derived from
    // TenantContext, never a client-supplied tenantId) must anchor direct-scope eligibility ---

    @Test
    void listCandidatesForDirectScopeIncludesTenantMembersOfStaffsActiveSessionTenant() {
        User staff = staffUser();
        User tenantMember = plainMember();
        User unrelatedMember = new User("unrelated-member@example.com");
        unrelatedMember.setId(3L);
        Tenant tenant = tenant(10L);
        Tenant otherTenant = tenant(20L);

        when(userRepository.findAllByDeletedAtIsNull())
                .thenReturn(List.of(staff, tenantMember, unrelatedMember));
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantMembershipRepository.findByUserAndActiveTrue(tenantMember))
                .thenReturn(List.of(activeMembership(tenantMember, tenant)));
        when(tenantMembershipRepository.findByUserAndActiveTrue(unrelatedMember))
                .thenReturn(List.of(activeMembership(unrelatedMember, otherTenant)));
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));

        // Note: no client-supplied tenantId is passed for the "direct" scope -- the active tenant
        // must come from the session, not a request parameter. The plain member of the staff's
        // active tenant (10L) must be included; a member of an unrelated tenant (20L) must not.
        var candidates = service.listCandidates(staff, "direct", null);

        assertThat(candidates).extracting("userId").containsExactly(2L);
    }

    // --- product decision (2026-08-09): a staff member browsing inside an active tenant must see
    // ONLY that tenant's members for "direct" scope -- never other staff colleagues mixed in ---

    @Test
    void listCandidatesForDirectScopeExcludesOtherStaffWhenActorHasAnActiveTenant() {
        User staff = staffUser();
        User tenantMember = plainMember();
        User otherStaff = new User("other-staff@example.com");
        otherStaff.setId(3L);
        otherStaff.setGlobalRole(GlobalRole.STAFF);
        Tenant tenant = tenant(10L);

        when(userRepository.findAllByDeletedAtIsNull())
                .thenReturn(List.of(staff, tenantMember, otherStaff));
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantMembershipRepository.findByUserAndActiveTrue(tenantMember))
                .thenReturn(List.of(activeMembership(tenantMember, tenant)));
        when(tenantMembershipRepository.findByUserAndActiveTrue(otherStaff)).thenReturn(List.of());
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.of(10L));

        var candidates = service.listCandidates(staff, "direct", null);

        assertThat(candidates).extracting("userId").containsExactly(2L);
    }

    @Test
    void aClientSuppliedTenantIdNeverExpandsDirectScopeEligibilityBeyondTheSessionsActiveTenant() {
        User staff = staffUser();
        User memberOfAnotherTenant = plainMember();
        Tenant otherTenant = tenant(99L);

        when(userRepository.findAllByDeletedAtIsNull())
                .thenReturn(List.of(staff, memberOfAnotherTenant));
        when(tenantMembershipRepository.findByUserAndActiveTrue(staff)).thenReturn(List.of());
        when(tenantMembershipRepository.findByUserAndActiveTrue(memberOfAnotherTenant))
                .thenReturn(List.of(activeMembership(memberOfAnotherTenant, otherTenant)));
        // The staff's real session has no active tenant at all.
        when(tenantContext.getActiveTenantId()).thenReturn(Optional.empty());

        // A caller passing an arbitrary tenantId (99L, matching the other tenant's membership)
        // must not be trusted as an authorization input for the "direct" scope.
        var candidates = service.listCandidates(staff, "direct", 99L);

        assertThat(candidates).isEmpty();
    }
}
