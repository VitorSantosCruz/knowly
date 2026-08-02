package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import jakarta.persistence.Column;
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
 * 1:1 with {@link User}, created only once an address is first submitted (REQ-2a), per
 * specify/features/identity-profile-model-v2/PLAN.md's 2026-08-02 country-agnostic identity/address
 * model amendment. Country-agnostic shape ({@code addressLine1}/{@code addressLine2}/{@code
 * city}/{@code stateRegion}/{@code postalCode}/{@code countryCode}) replaces the Brazil-only {@code
 * cep}/{@code logradouro}/{@code numero}/{@code complemento}/{@code bairro}/{@code cidade}/{@code
 * estado}/{@code pais} shape (see V27 migration).
 */
@Entity
@Table(name = "addresses")
@Audited
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class Address {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "address_line1", nullable = false)
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city", nullable = false)
    private String city;

    @Column(name = "state_region")
    private String stateRegion;

    @Column(name = "postal_code", nullable = false, length = 20)
    private String postalCode;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

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

    public Address(User user) {
        this.user = user;
    }
}
