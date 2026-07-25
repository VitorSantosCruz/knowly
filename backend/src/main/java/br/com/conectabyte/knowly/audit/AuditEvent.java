package br.com.conectabyte.knowly.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An append-only record of a read or write action, independent of Envers (which only tracks entity
 * state changes, not reads). Never updated or deleted once written.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 150)
    private String action;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    public AuditEvent(
            Long actorUserId,
            Long tenantId,
            String action,
            String resourceType,
            String resourceId,
            AuditOutcome outcome) {
        this.occurredAt = Instant.now();
        this.actorUserId = actorUserId;
        this.tenantId = tenantId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
    }
}
