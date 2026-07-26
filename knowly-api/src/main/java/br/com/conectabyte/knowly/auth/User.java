package br.com.conectabyte.knowly.auth;

import br.com.conectabyte.knowly.identity.CpfRgEncryptionConverter;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.hibernate.envers.Audited;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * {@code cpfBlindIndex}/{@code rgBlindIndex} are derived, service-internal columns -- they must
 * only ever be written alongside {@code cpf}/{@code rg} by {@code
 * br.com.conectabyte.knowly.identity.UserProfileService#applyFields}, never independently, and are
 * never part of any request DTO (see specify/features/identity-profile-model/SPEC.md's "Resolved"
 * section). Lombok still generates plain setters for them since there's no entity-level enforcement
 * mechanism for this -- the single choke point is the service layer, not the entity.
 */
@Entity
@Table(name = "users")
@Audited
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "one_time_password_hash")
    private String oneTimePasswordHash;

    @Column(name = "one_time_password_issued_at")
    private Instant oneTimePasswordIssuedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "global_role", length = 20)
    private GlobalRole globalRole;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "address")
    private String address;

    @Convert(converter = CpfRgEncryptionConverter.class)
    @Column(name = "rg")
    private String rg;

    @Convert(converter = CpfRgEncryptionConverter.class)
    @Column(name = "cpf")
    private String cpf;

    @Column(name = "phone")
    private String phone;

    @Column(name = "rg_blind_index", length = 64)
    private String rgBlindIndex;

    @Column(name = "cpf_blind_index", length = 64)
    private String cpfBlindIndex;

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

    public User(String email) {
        this.email = email;
    }
}
