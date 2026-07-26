package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.identity.ProfileEditRequest;
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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * An in-app, pollable notification (see specify/features/tenant-membership-acceptance/PLAN.md).
 * Deliberately not {@code @Audited}/Envers — this is ephemeral, user-facing state; the {@code
 * TenantMembership} state transitions it surfaces are already fully audited via {@code @AuditLog}.
 * Deliberately not tenant-scoped ({@code @Filter}) either — its own authorization is "is this row's
 * recipient the caller," not tenant membership.
 */
@Entity
@Table(name = "notifications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @ManyToOne(optional = true)
    @JoinColumn(name = "tenant_membership_id", nullable = true)
    private TenantMembership tenantMembership;

    /**
     * REQ-16 (identity-profile-model): the pending-profile-edit-request payload this notification
     * surfaces, for {@code type == PROFILE_EDIT_REQUEST_PENDING} rows. Exactly one of {@code
     * tenantMembership}/{@code profileEditRequest} is ever set (DB-enforced via a CHECK constraint,
     * see V17) -- a {@code Notification} row is always anchored to exactly one of the two
     * originating mechanisms.
     */
    @ManyToOne(optional = true)
    @JoinColumn(name = "profile_edit_request_id", nullable = true)
    private ProfileEditRequest profileEditRequest;

    @Column(nullable = false)
    private boolean resolved = false;

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

    public Notification(User recipient, NotificationType type, TenantMembership tenantMembership) {
        this.recipient = recipient;
        this.type = type;
        this.tenantMembership = tenantMembership;
    }

    public Notification(
            User recipient, NotificationType type, ProfileEditRequest profileEditRequest) {
        this.recipient = recipient;
        this.type = type;
        this.profileEditRequest = profileEditRequest;
    }
}
