package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
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

/**
 * 1:1 with {@link User}, created eagerly at account creation (REQ-1), per
 * specify/features/identity-profile-model-v2/PLAN.md. {@code taxId} reuses {@link
 * TaxIdEncryptionConverter}/{@link BlindIndexService} unchanged, just relocated from {@code
 * User.cpf}/renamed from {@code UserProfile.cpf} (country-agnostic identity/address model
 * amendment, 2026-08-02). {@code rg}/{@code rgOrgaoEmissor}/{@code birthDate} were removed entirely
 * per the same day's LGPD data-minimization amendments (see V26 migration). No {@code @Filter} --
 * user-owned, not tenant-owned, same as {@link User} itself.
 */
@Entity
@Table(name = "user_profiles")
@Audited
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name")
    private String fullName;

    @Convert(converter = TaxIdEncryptionConverter.class)
    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "tax_id_blind_index", length = 64)
    private String taxIdBlindIndex;

    /** ISO 3166-1 alpha-2, nullable until the user selects a country (REQ-1b). */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "avatar_url")
    private String avatarUrl;

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

    public UserProfile(User user) {
        this.user = user;
    }
}
