package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.audit.AuditEvent;
import br.com.conectabyte.knowly.audit.AuditOutcome;
import java.time.Instant;

/**
 * specify/features/staff-audit-trail-view/SPEC.md REQ-1: one row of a target user's audit trail, as
 * returned by {@code GET /api/staff/users/{userId}/audit-trail}. {@code tenantId} is nullable
 * (global/staff-level events carry no tenant); {@code metadata} is passed through as already
 * stored, with no additional transformation.
 */
public record AuditEventDto(
        Instant occurredAt,
        String action,
        String resourceType,
        String resourceId,
        Long tenantId,
        AuditOutcome outcome,
        String metadata) {

    public static AuditEventDto from(AuditEvent auditEvent) {
        return new AuditEventDto(
                auditEvent.getOccurredAt(),
                auditEvent.getAction(),
                auditEvent.getResourceType(),
                auditEvent.getResourceId(),
                auditEvent.getTenantId(),
                auditEvent.getOutcome(),
                auditEvent.getMetadata());
    }
}
