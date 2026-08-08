package br.com.conectabyte.knowly.tenancy;

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
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "global_access_group_permissions")
@Audited
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class GlobalAccessGroupPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "global_access_group_id", nullable = false)
    private GlobalAccessGroup globalAccessGroup;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 100)
    private GlobalPermission permission;

    /**
     * role-permission-revoke REQ-3/REQ-4: set when this permission is revoked from the owning
     * {@code GlobalAccessGroup}; cleared (reactivated) if the same permission is granted again,
     * mirroring {@code AccessGroupPermission#deletedAt}.
     */
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

    public GlobalAccessGroupPermission(
            GlobalAccessGroup globalAccessGroup, GlobalPermission permission) {
        this.globalAccessGroup = globalAccessGroup;
        this.permission = permission;
    }
}
