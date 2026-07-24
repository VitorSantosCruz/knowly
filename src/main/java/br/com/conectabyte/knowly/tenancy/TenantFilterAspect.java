package br.com.conectabyte.knowly.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Enables the tenantFilter for the caller's active tenant at the start of every
 * {@code @Transactional} service method, so tenant-scoped entities never leak across tenants
 * regardless of which query loads them within that transaction.
 *
 * <p>This must target {@code @Transactional}-annotated *service* methods, not repository
 * interfaces: Spring Data repository proxies get their transactional behavior from their own
 * dedicated proxy-creation pipeline (RepositoryFactorySupport), which is a separate layer that
 * always sits *inside* the general Spring AOP auto-proxy chain regardless of {@code @Order} — so an
 * aspect targeting repository executions runs *before* that inner transaction opens, enabling the
 * filter on a throwaway, immediately discarded EntityManager. Targeting {@code @Transactional}
 * service methods instead puts this aspect on the same, single, @Order-controlled proxy as the
 * transaction advisor (see TransactionManagementConfig), so ordering actually holds.
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

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object enableTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        var activeTenantId = tenantContext.getActiveTenantId();
        Session session = entityManager.unwrap(Session.class);

        if (tenantContext.isStaff() && activeTenantId.isEmpty()) {
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
