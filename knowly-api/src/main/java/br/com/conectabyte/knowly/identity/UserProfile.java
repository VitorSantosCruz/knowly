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
import java.time.LocalDate;
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
 * specify/features/identity-profile-model-v2/PLAN.md. {@code cpf}/{@code rg} reuse {@link
 * CpfRgEncryptionConverter}/{@link BlindIndexService} unchanged, just relocated from {@code
 * User.cpf}/{@code User.rg}. No {@code @Filter} -- user-owned, not tenant-owned, same as {@link
 * User} itself.
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

    @Convert(converter = CpfRgEncryptionConverter.class)
    @Column(name = "cpf")
    private String cpf;

    @Column(name = "cpf_blind_index", length = 64)
    private String cpfBlindIndex;

    @Convert(converter = CpfRgEncryptionConverter.class)
    @Column(name = "rg")
    private String rg;

    @Column(name = "rg_orgao_emissor")
    private String rgOrgaoEmissor;

    @Column(name = "rg_blind_index", length = 64)
    private String rgBlindIndex;

    @Column(name = "birth_date")
    private LocalDate birthDate;

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
