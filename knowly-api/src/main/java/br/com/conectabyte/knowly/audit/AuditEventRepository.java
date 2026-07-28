package br.com.conectabyte.knowly.audit;

import java.util.List;
import org.springframework.data.repository.Repository;

/** Deliberately narrow: no update/delete methods are exposed. Audit events are append-only. */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent auditEvent);

    List<AuditEvent> findByTenantIdOrderByOccurredAtDesc(Long tenantId);

    List<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(Long actorUserId);

    /**
     * Defensive cap (specify/features/staff-audit-trail-view/PLAN.md): the {@code LIMIT} is pushed
     * into the generated SQL via Spring Data's {@code Top500} keyword, so the DB — not the JVM —
     * enforces the bound, backed by the existing {@code ix_audit_events_actor_time (actor_user_id,
     * occurred_at)} composite index (backward index scan, no new migration needed).
     */
    List<AuditEvent> findTop500ByActorUserIdOrderByOccurredAtDesc(Long actorUserId);

    List<AuditEvent> findByActionAndResourceIdOrderByOccurredAtDesc(
            String action, String resourceId);
}
