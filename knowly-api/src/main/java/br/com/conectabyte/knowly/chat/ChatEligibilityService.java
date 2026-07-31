package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.chat.dto.CandidateUserDto;
import br.com.conectabyte.knowly.chat.exception.ChatIneligibleParticipantException;
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

    public ChatEligibilityService(
            TenantMembershipRepository tenantMembershipRepository, UserRepository userRepository) {
        this.tenantMembershipRepository = tenantMembershipRepository;
        this.userRepository = userRepository;
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

    public List<CandidateUserDto> listCandidates(String scope, Long tenantId) {
        List<User> users = userRepository.findAll();

        return users.stream()
                .filter(
                        user -> {
                            switch (scope) {
                                case "direct":
                                    return true;
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

    String nicknameOf(User user) {
        return user.getEmail();
    }

    private boolean isStaffCapable(User user) {
        return user.getGlobalRole() == GlobalRole.STAFF
                || user.getGlobalRole() == GlobalRole.STAFF_ADMIN;
    }

    private boolean hasActiveMembership(User user, Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        return tenantMembershipRepository
                .findByUserAndTenant(user, tenant)
                .filter(TenantMembership::isActive)
                .isPresent();
    }
}
