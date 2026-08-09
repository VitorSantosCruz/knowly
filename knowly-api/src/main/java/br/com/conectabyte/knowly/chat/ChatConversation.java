package br.com.conectabyte.knowly.chat;

import br.com.conectabyte.knowly.auth.User;
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
import org.hibernate.annotations.FilterDef;
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Covers all three chat shapes (see specify/features/internal-team-chat/PLAN.md): PEER_DIRECT/
 * PEER_GROUP (peer-to-peer) and SUPPORT (per-member support channel). {@code tenant} is nullable —
 * a NULL value never matches the {@code tenant_id = :tenantId} filter condition, so staff-only peer
 * conversations are structurally invisible to the tenant-scoped filter regardless of the caller's
 * active tenant (mirrors GlobalAccessGroup's no-filter precedent for genuinely global entities, but
 * keeps the same @Filter mechanism rather than omitting it).
 */
@Entity
@Table(name = "chat_conversations")
@Audited
@EntityListeners(AuditingEntityListener.class)
@Filter(name = TenantFilter.NAME, condition = "tenant_id = :" + TenantFilter.PARAMETER)
@FilterDef(name = SoftDeleteFilter.NAME, defaultCondition = "deleted_at is null")
@Filter(name = SoftDeleteFilter.NAME)
@Getter
@Setter
@NoArgsConstructor
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatConversationKind kind;

    @ManyToOne
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column private String title;

    @ManyToOne
    @JoinColumn(name = "owner_user_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatGroupVisibility visibility = ChatGroupVisibility.PRIVATE;

    @Column(name = "archived_at")
    private Instant archivedAt;

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

    public ChatConversation(ChatConversationKind kind, Tenant tenant, String title, User owner) {
        this.kind = kind;
        this.tenant = tenant;
        this.title = title;
        this.owner = owner;
    }
}
