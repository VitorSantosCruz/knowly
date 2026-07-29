package br.com.conectabyte.knowly.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One proposed add/update/remove of a {@link Contact} row, child of a {@link ProfileEditRequest}
 * (REQ-15). Not {@code @Audited} -- ephemeral request state, same rationale as {@link
 * ProfileEditRequest} itself.
 */
@Entity
@Table(name = "profile_edit_request_contacts")
@Getter
@Setter
@NoArgsConstructor
public class ProfileEditRequestContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "profile_edit_request_id", nullable = false)
    private ProfileEditRequest profileEditRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10)
    private ContactChangeAction action;

    @Column(name = "contact_id")
    private Long contactId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 20)
    private ContactType type;

    @Column(name = "value")
    private String value;

    @Column(name = "label")
    private String label;

    @Column(name = "is_primary")
    private Boolean primary;

    public ProfileEditRequestContact(
            ProfileEditRequest profileEditRequest,
            ContactChangeAction action,
            Long contactId,
            ContactType type,
            String value,
            String label,
            Boolean primary) {
        this.profileEditRequest = profileEditRequest;
        this.action = action;
        this.contactId = contactId;
        this.type = type;
        this.value = value;
        this.label = label;
        this.primary = primary;
    }
}
