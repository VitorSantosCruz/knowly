package br.com.conectabyte.knowly.audit;

import java.util.List;
import org.springframework.data.repository.Repository;

/** Deliberately narrow: no update/delete methods are exposed. Audit events are append-only. */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    AuditEvent save(AuditEvent auditEvent);

    List<AuditEvent> findByTenantIdOrderByOccurredAtDesc(Long tenantId);

    List<AuditEvent> findByActorUserIdOrderByOccurredAtDesc(Long actorUserId);
}
