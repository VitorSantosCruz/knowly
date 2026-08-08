package br.com.conectabyte.knowly.softdelete;

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
 * Enables {@link SoftDeleteFilter#NAME} unconditionally at the start of every
 * {@code @Transactional} service method, so soft-deleted rows never leak into a standard
 * entity-load-time query regardless of which repository method loads them within that transaction
 * -- see specify/features/soft-delete-default-filter/SPEC.md requirements 1/2/3.
 *
 * <p>Same pointcut and {@code @Order} as {@link
 * br.com.conectabyte.knowly.tenancy.TenantFilterAspect} and for the same documented reason: {@code
 * !within(Repository+)} excludes Spring Data's own internal {@code @Transactional} repository-proxy
 * methods (e.g. {@code findById}/{@code save}), since a plain repository call made from a
 * non-{@code @Transactional} context (e.g. a {@code @RabbitListener} background consumer) must not
 * get this filter force-enabled either -- unlike {@code TenantFilterAspect}, this aspect has no
 * "ambiguous caller" branch to fail closed on, since the condition needs no request-scoped context
 * to decide (SPEC requirement 2).
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SoftDeleteFilterAspect {

    @PersistenceContext private EntityManager entityManager;

    @Around(
            "@annotation(org.springframework.transaction.annotation.Transactional) &&"
                    + " !within(org.springframework.data.repository.Repository+)")
    public Object enableSoftDeleteFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(SoftDeleteFilter.NAME);

        return joinPoint.proceed();
    }
}
