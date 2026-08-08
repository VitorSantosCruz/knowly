package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.softdelete.SoftDeleteFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Uniqueness on {@code (tenant_membership_id, access_group_id)} is enforced by the partial index
 * {@code ux_user_access_groups_membership_group} ({@code WHERE deleted_at IS NULL}, V28) -- not a
 * table-level constraint, so unassign-then-reassign doesn't collide.
 */
@Entity
@Table(name = "user_access_groups")
@Audited
@EntityListeners(AuditingEntityListener.class)
@Filter(name = SoftDeleteFilter.NAME)
@Getter
@Setter
@NoArgsConstructor
public class UserAccessGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_membership_id", nullable = false)
    private TenantMembership tenantMembership;

    @ManyToOne(optional = false)
    @JoinColumn(name = "access_group_id", nullable = false)
    private AccessGroup accessGroup;

    /**
     * Logical delete (2026-08-04 standing decision) -- unassign sets this instead of deleting the
     * row; reassigning the same group reactivates it instead of inserting a duplicate.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreatedDate
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

    public UserAccessGroup(TenantMembership tenantMembership, AccessGroup accessGroup) {
        this.tenantMembership = tenantMembership;
        this.accessGroup = accessGroup;
    }
}
