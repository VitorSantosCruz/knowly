package br.com.conectabyte.knowly.audit;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Deliberately narrow: no update/delete methods are exposed. Audit events are append-only. */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent auditEvent);

    List<AuditEvent> findByTenantIdOrderByOccurredAtDesc(Long tenantId);

    List<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(Long actorUserId);

    /**
     * specify/features/paginated-audit-trail/PLAN.md: replaces the former defensive {@code Top500}
     * cap with genuine {@code Pageable}-bounded pagination (max {@code size=100} per page, enforced
     * in {@code StaffService}). Offset/limit is pushed into the generated SQL, backed by the
     * existing {@code ix_audit_events_actor_time (actor_user_id, occurred_at)} composite index
     * (backward index scan, no new migration needed).
     */
    Page<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(Long actorUserId, Pageable pageable);

    List<AuditEvent> findByActionAndResourceIdOrderByOccurredAtDesc(
            String action, String resourceId);
}
