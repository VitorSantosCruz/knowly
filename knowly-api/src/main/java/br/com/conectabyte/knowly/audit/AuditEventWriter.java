package br.com.conectabyte.knowly.audit;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists {@link AuditEvent}s in their own transaction, independent of the transaction (if any) of
 * the method {@link AuditLogAspect} is intercepting.
 *
 * <p>This matters because several audited methods are {@code @Transactional(readOnly = true)} (e.g.
 * read/detail-view endpoints whose permission denial still needs auditing) — PostgreSQL rejects an
 * INSERT issued inside a read-only transaction. Writing the audit event via {@code REQUIRES_NEW}
 * guarantees the audit record is always persisted regardless of the caller's transactional context
 * (readOnly, absent, or rolled back).
 */
@Component
public class AuditEventWriter {

    private final AuditEventRepository auditEventRepository;

    public AuditEventWriter(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditEvent event) {
        auditEventRepository.save(event);
    }
}
