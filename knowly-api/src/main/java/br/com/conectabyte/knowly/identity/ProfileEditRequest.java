package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
 * The pending-profile-edit-request business record (REQ-15..21) -- proposed field values plus a
 * resolvable status. Distinct from {@code Notification}, which is only the inbox row referencing
 * this via a new nullable FK (see specify/features/identity-profile-model/PLAN.md's "New
 * ProfileEditRequest entity"). Deliberately not {@code @Audited}/Envers -- ephemeral request state;
 * the resulting {@code User} field change is itself Envers-audited via {@code users_aud}.
 */
@Entity
@Table(name = "profile_edit_requests")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ProfileEditRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private User requester;

    @Column(name = "proposed_full_name")
    private String proposedFullName;

    @Convert(converter = TaxIdEncryptionConverter.class)
    @Column(name = "proposed_tax_id")
    private String proposedTaxId;

    @Column(name = "proposed_country_code", length = 2)
    private String proposedCountryCode;

    @Column(name = "proposed_address_line1")
    private String proposedAddressLine1;

    @Column(name = "proposed_address_line2")
    private String proposedAddressLine2;

    @Column(name = "proposed_city")
    private String proposedCity;

    @Column(name = "proposed_state_region")
    private String proposedStateRegion;

    @Column(name = "proposed_postal_code", length = 20)
    private String proposedPostalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileEditRequestStatus status = ProfileEditRequestStatus.PENDING;

    @ManyToOne(optional = true)
    @JoinColumn(name = "resolved_by_user_id", nullable = true)
    private User resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

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

    public ProfileEditRequest(User requester) {
        this.requester = requester;
    }
}
