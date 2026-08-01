package br.com.conectabyte.knowly.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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

    public TenantFilterAspect(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
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
                    .setParameter(
                            TenantFilter.PARAMETER,
                            activeTenantId.orElse(TenantFilter.NO_ACTIVE_TENANT_SENTINEL));
        }

        return joinPoint.proceed();
    }
}
