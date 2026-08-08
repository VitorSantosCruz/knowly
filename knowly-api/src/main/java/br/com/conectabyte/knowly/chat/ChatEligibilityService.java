package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.dto.CandidateUserDto;
import br.com.conectabyte.knowly.chat.exception.ChatIneligibleParticipantException;
import br.com.conectabyte.knowly.identity.UserProfile;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantMembership;
import br.com.conectabyte.knowly.tenancy.TenantMembershipRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * REQ-3/4/5's single shared eligibility rule, backing both 1:1 and group creation so the rule is
 * enforced by sharing code, not by parallel reimplementation. Always re-derives from a fresh {@link
 * TenantMembershipRepository} lookup -- never trusts a client-supplied "I'm eligible" flag.
 */
@Service
public class ChatEligibilityService {

    private final TenantMembershipRepository tenantMembershipRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    public ChatEligibilityService(
            TenantMembershipRepository tenantMembershipRepository,
            UserRepository userRepository,
            UserProfileRepository userProfileRepository) {
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
    }

    /**
     * @param tenantIdAnchor null for a staff-only conversation (no tenant anchor); a tenant id for
     *     a member-only conversation anchored to that tenant.
     */
    public boolean isEligible(User candidate, Long tenantIdAnchor) {
        if (tenantIdAnchor == null) {
            return isStaffCapable(candidate);
        }

        return hasActiveMembership(candidate, tenantIdAnchor);
    }

    /** Every anchor (null = staff-only, or a tenant id) this user is currently eligible for. */
    public Set<Long> eligibleAnchorsFor(User user) {
        Set<Long> anchors = new HashSet<>();

        if (isStaffCapable(user)) {
            anchors.add(null);
        }

        for (TenantMembership membership :
                tenantMembershipRepository.findByUserAndActiveTrue(user)) {
            anchors.add(membership.getTenant().getId());
        }

        return anchors;
    }

    /**
     * Resolves the shared anchor for a 1:1 peer chat between {@code actor} and {@code target},
     * evaluating capacity-per-conversation exactly like group eligibility (REQ-5). Prefers a
     * concrete tenant anchor over the {@code null} (staff-only) anchor when both are shared, since
     * a 1:1 between two tenant peers should be treated as a member-only conversation.
     */
    public Long resolveDirectAnchor(User actor, User target) {
        Set<Long> actorAnchors = eligibleAnchorsFor(actor);
        Set<Long> targetAnchors = eligibleAnchorsFor(target);
        Set<Long> shared = new HashSet<>(actorAnchors);
        shared.retainAll(targetAnchors);

        if (shared.isEmpty()) {
            throw new ChatIneligibleParticipantException();
        }

        return shared.stream().filter(anchor -> anchor != null).findFirst().orElse(null);
    }

    public List<CandidateUserDto> listCandidates(User actor, String scope, Long tenantId) {
        // logical-delete-everywhere (2026-08-04): a soft-deleted user must never surface as an
        // eligible chat participant candidate.
        List<User> users = userRepository.findAllByDeletedAtIsNull();
        Set<Long> actorAnchors = "direct".equals(scope) ? eligibleAnchorsFor(actor) : null;

        return users.stream()
                .filter(user -> !user.getId().equals(actor.getId()))
                .filter(
                        user -> {
                            switch (scope) {
                                case "direct":
                                    // REQ-3: only candidates who actually share an eligible
                                    // anchor (tenant membership or staff-capability) with the
                                    // caller -- never the full user directory (PII exposure /
                                    // enumeration risk otherwise).
                                    Set<Long> candidateAnchors = eligibleAnchorsFor(user);
                                    return !java.util.Collections.disjoint(
                                            actorAnchors, candidateAnchors);
                                case "group":
                                    return isEligible(user, tenantId);
                                case "group-staff-only":
                                    return isEligible(user, null);
                                default:
                                    return false;
                            }
                        })
                .map(user -> new CandidateUserDto(user.getId(), nicknameOf(user)))
                .toList();
    }

    // Prefers the profile display name over the raw email, mirroring
    // ChatConversationService#nicknameOfUserId -- the candidate picker shouldn't expose an email
    // address when a non-PII display name is already available.
    String nicknameOf(User user) {
        return userProfileRepository
                .findById(user.getId())
                .map(UserProfile::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElseGet(user::getEmail);
    }

    private boolean isStaffCapable(User user) {
        // logical-delete-everywhere (2026-08-04): defense in depth -- correct even if a future
        // caller hands in an already-loaded, unfiltered User.
        if (user.getDeletedAt() != null) {
            return false;
        }

        return user.getGlobalRole() == GlobalRole.STAFF
                || user.getGlobalRole() == GlobalRole.STAFF_ADMIN;
    }

    private boolean hasActiveMembership(User user, Long tenantId) {
        if (user.getDeletedAt() != null) {
            return false;
        }

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        return tenantMembershipRepository
                .findByUserAndTenant(user, tenant)
                .filter(TenantMembership::isActive)
                .isPresent();
    }
}
