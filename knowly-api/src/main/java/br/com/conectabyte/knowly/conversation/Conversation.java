package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.icon.IconKey;
import br.com.conectabyte.knowly.softdelete.SoftDeleteFilter;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantFilter;
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
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "conversations")
@Audited
@EntityListeners(AuditingEntityListener.class)
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :" + TenantFilter.PARAMETER)
@Filter(name = SoftDeleteFilter.NAME)
@Getter
@Setter
@NoArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(nullable = false)
    private String title;

    /**
     * Optional, fixed-key icon (see {@link IconKey}) -- nullable at the schema level even though
     * IconKey is a fixed enum: an unset icon keeps the frontend's own default/fallback
     * presentation, a display decision, never a backend-synthesized default.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private IconKey icon;

    /**
     * Set alongside its owning {@link Tenant}'s own deletedAt (2026-08-04 standing decision): a
     * deleted tenant's own conversations no longer make sense to keep live.
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

    /** Test-convenience overload -- defaults {@code title} since it's now a required column. */
    public Conversation(Tenant tenant, User owner) {
        this(tenant, owner, "Untitled conversation");
    }

    public Conversation(Tenant tenant, User owner, String title) {
        this.tenant = tenant;
        this.owner = owner;
        this.title = title;
    }

    public Conversation(Tenant tenant, User owner, String title, IconKey icon) {
        this(tenant, owner, title);
        this.icon = icon;
    }
}
