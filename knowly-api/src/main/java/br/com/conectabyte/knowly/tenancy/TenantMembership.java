package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.softdelete.SoftDeleteFilter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Uniqueness on {@code (user_id, tenant_id)} is enforced by the partial index {@code
 * ux_tenant_memberships_user_tenant} ({@code WHERE deleted_at IS NULL}, V28) -- not a table-level
 * constraint, so a hard-deleted membership doesn't block the user re-joining the tenant later.
 * {@code deletedAt} is distinct from the existing {@code active} flag: {@code active=false} is an
 * ordinary tenant-side removal (membership row and its history stay, REQ-9); {@code deletedAt} is
 * the stronger hard-delete action (REQ-7/8/10/11), now logical instead of physical (2026-08-04).
 */
@Entity
@Table(name = "tenant_memberships")
@Audited
@EntityListeners(AuditingEntityListener.class)
@FilterDef(
        name = TenantFilter.NAME,
        parameters = @ParamDef(name = TenantFilter.PARAMETER, type = Long.class))
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :" + TenantFilter.PARAMETER)
@Filter(name = SoftDeleteFilter.NAME)
@Getter
@Setter
@NoArgsConstructor
public class TenantMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status = MembershipStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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

    public TenantMembership(User user, Tenant tenant, MembershipRole role) {
        this.user = user;
        this.tenant = tenant;
        this.role = role;
    }
}
