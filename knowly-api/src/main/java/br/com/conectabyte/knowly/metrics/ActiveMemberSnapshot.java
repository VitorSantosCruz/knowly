package br.com.conectabyte.knowly.metrics;

import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * specify/features/active-members-trend/PLAN.md: one row per (tenant, UTC calendar day), written by
 * the daily {@link ActiveMemberSnapshotScheduler} upsert, not through normal JPA persistence --
 * this entity mapping exists so any future JPQL/{@code findBy...} usage against it still fails
 * closed via the tenant {@link Filter}, per this codebase's standing tenancy convention.
 */
@Entity
@Table(
        name = "active_member_snapshots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "snapshot_date"}))
@EntityListeners(AuditingEntityListener.class)
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :" + TenantFilter.PARAMETER)
@Getter
@Setter
@NoArgsConstructor
public class ActiveMemberSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "active_count", nullable = false)
    private long activeCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    public ActiveMemberSnapshot(Tenant tenant, LocalDate snapshotDate, long activeCount) {
        this.tenant = tenant;
        this.snapshotDate = snapshotDate;
        this.activeCount = activeCount;
    }
}
