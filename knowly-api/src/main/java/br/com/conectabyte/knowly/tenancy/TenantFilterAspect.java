package br.com.conectabyte.knowly.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enables the tenantFilter for the caller's active tenant at the start of every
 * {@code @Transactional} service method, so tenant-scoped entities never leak across tenants
 * regardless of which query loads them within that transaction.
 *
 * <p>This must target {@code @Transactional}-annotated *service* methods only, never Spring Data
 * repository proxies: {@code SimpleJpaRepository}'s own internal {@code @Transactional} methods
 * (e.g. {@code findById}/{@code save}) satisfy the same {@code @annotation(Transactional)}
 * pointcut, since Spring's repository proxies are still ordinary Spring AOP proxies subject to the
 * application's other registered aspects. Without the {@code !within(Repository+)} exclusion below,
 * any plain repository call made with no active tenant in context -- e.g. from a
 * {@code @RabbitListener} background consumer, which is deliberately not itself
 * {@code @Transactional} (see {@code ArticleExtractionListener}) -- gets the Hibernate tenant
 * filter force-enabled with the fail-closed sentinel ({@link
 * TenantFilter#NO_ACTIVE_TENANT_SENTINEL}), silently hiding rows the caller had every right to see
 * by explicit id (see DECISIONS.md, "TenantFilterAspect pointcut too broad" for the incident this
 * caused). {@code Repository} is a marker interface only Spring Data proxies implement, and every
 * repository in this codebase is a plain {@code interface X extends JpaRepository<...>} with no
 * custom impl classes, so this exclusion cannot accidentally let a real tenant-scoped service
 * method skip filtering.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TenantFilterAspect {

    @PersistenceContext private EntityManager entityManager;

    private final TenantContext tenantContext;
    private final TenantRepository tenantRepository;

    public TenantFilterAspect(TenantContext tenantContext, TenantRepository tenantRepository) {
        this.tenantContext = tenantContext;
        this.tenantRepository = tenantRepository;
    }

    @Around(
            "@annotation(org.springframework.transaction.annotation.Transactional) &&"
                    + " !within(org.springframework.data.repository.Repository+)")
    public Object enableTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        var activeTenantId = tenantContext.getActiveTenantId();
        Session session = entityManager.unwrap(Session.class);
        boolean bypassForOversight =
                ((MethodSignature) joinPoint.getSignature())
                                .getMethod()
                                .getAnnotation(BypassTenantFilterForOversight.class)
                        != null;

        if (bypassForOversight || (tenantContext.isStaff() && activeTenantId.isEmpty())) {
            session.disableFilter(TenantFilter.NAME);
        } else {
            session.enableFilter(TenantFilter.NAME)
                    .setParameter(TenantFilter.PARAMETER, resolveEffectiveTenantId(activeTenantId));
        }

        return joinPoint.proceed();
    }

    /**
     * tenant-crud REQ-11 (AppSec correction, see PLAN.md "Architectural decisions"): a session
     * whose active tenant was soft-deleted *after* the session picked it must not keep
     * tenant-scoped access for the rest of that session -- {@code TenantContextFilter} re-derives
     * the active tenant id from the session attribute on every request with no DB lookup, so this
     * aspect (the true single chokepoint every {@code @Transactional} service method runs through)
     * is where that gap is closed: an active tenant id that no longer resolves to a live
     * (non-soft-deleted) tenant falls back to the same fail-closed sentinel already used for
     * "staff, no active tenant."
     */
    private long resolveEffectiveTenantId(Optional<Long> activeTenantId) {
        if (activeTenantId.isEmpty()) {
            return TenantFilter.NO_ACTIVE_TENANT_SENTINEL;
        }

        return tenantRepository
                .findById(activeTenantId.get())
                .filter(tenant -> tenant.getDeletedAt() == null)
                .map(tenant -> activeTenantId.get())
                .orElse(TenantFilter.NO_ACTIVE_TENANT_SENTINEL);
    }
}
