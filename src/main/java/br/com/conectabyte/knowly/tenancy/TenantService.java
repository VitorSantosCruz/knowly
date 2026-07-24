package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.tenancy.exception.TenantAccessDeniedException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TenantService {

    private final TenantMembershipRepository tenantMembershipRepository;

    public TenantService(TenantMembershipRepository tenantMembershipRepository) {
        this.tenantMembershipRepository = tenantMembershipRepository;
    }

    /**
     * Resolves how a freshly-authenticated user's session should start out, per REQ-3/4/5.
     * Deliberately not {@code @Transactional}: this call must see the user's memberships across
     * every tenant, and — since it's scoped by user identity, not by an arbitrary listing — it's
     * safe to run through Spring Data's own default per-call transaction rather than one where
     * TenantFilterAspect has enabled the tenant filter (which would otherwise filter out every
     * membership before a tenant is even chosen).
     */
    public TenantSessionOutcome resolveSessionOutcome(User user) {
        if (user.getGlobalRole() == GlobalRole.STAFF) {
            return new TenantSessionOutcome.Staff();
        }

        List<TenantMembership> memberships =
                tenantMembershipRepository.findByUserAndActiveTrue(user);

        if (memberships.size() == 1) {
            return new TenantSessionOutcome.AutoSelected(memberships.get(0).getTenant().getId());
        }

        return new TenantSessionOutcome.SelectionPending();
    }

    /**
     * Lists the caller's own active memberships (for the tenant picker / switch-tenant menu). Same
     * reasoning as {@link #resolveSessionOutcome}: intentionally not {@code @Transactional}, this
     * is scoped by the caller's own identity.
     */
    public List<TenantMembership> listOwnMemberships(User user) {
        return tenantMembershipRepository.findByUserAndActiveTrue(user);
    }

    /**
     * Validates that the user actually holds an active membership in the requested tenant (REQ-7).
     * Same reasoning as {@link #resolveSessionOutcome}: intentionally not {@code @Transactional},
     * the lookup is scoped by (user, tenant) explicitly.
     */
    public TenantMembership requireActiveMembership(User user, Long tenantId) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        return tenantMembershipRepository
                .findByUserAndTenant(user, tenant)
                .filter(TenantMembership::isActive)
                .orElseThrow(TenantAccessDeniedException::new);
    }
}
