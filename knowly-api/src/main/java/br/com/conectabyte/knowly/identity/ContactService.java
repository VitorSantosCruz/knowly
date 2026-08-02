package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.identity.exception.ContactCapExceededException;
import br.com.conectabyte.knowly.identity.exception.InvalidContactFormatException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Owns the 5-contacts-per-user cap (REQ-3a) and per-{@link ContactType} format validation (REQ-3
 * "Open decision c"), per specify/features/identity-profile-model-v2/PLAN.md. The one-primary-
 * per-type invariant (REQ-3b) is DB-enforced via the partial unique index {@code
 * ux_contacts_primary_per_type} -- when a caller sets a new primary of a type that already has one,
 * this service clears the old primary first so the write never violates that index.
 */
@Service
public class ContactService {

    private static final int MAX_CONTACTS_PER_USER = 5;

    /**
     * E.164 shape (REQ-3c, 2026-08-02 country-agnostic amendment): leading {@code +} is now
     * mandatory (a bare national number is no longer a complete E.164 value), max 15 digits total
     * per ITU E.164 (1 to 9 for the first digit after {@code +}, up to 14 more).
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private final ContactRepository contactRepository;
    private final Validator validator;

    public ContactService(ContactRepository contactRepository, Validator validator) {
        this.contactRepository = contactRepository;
        this.validator = validator;
    }

    /** REQ-3/3a/3b: add a new contact, enforcing the cap, format, and primary-per-type clearing. */
    public Contact addContact(
            User user, ContactType type, String value, String label, boolean primary) {
        String normalizedValue = normalizeIfPhone(type, value);
        validateFormat(type, normalizedValue);

        if (contactRepository.countByUser(user) >= MAX_CONTACTS_PER_USER) {
            throw new ContactCapExceededException();
        }

        if (primary) {
            clearExistingPrimary(user, type);
        }

        return contactRepository.save(new Contact(user, type, normalizedValue, label, primary));
    }

    /**
     * REQ-3/3b: update an existing contact's fields, clearing any other primary of the same type.
     */
    public Contact updateContact(
            Contact contact, ContactType type, String value, String label, Boolean primary) {
        if (type != null) {
            contact.setType(type);
        }
        if (value != null) {
            contact.setValue(normalizeIfPhone(contact.getType(), value));
        }
        validateFormat(contact.getType(), contact.getValue());

        if (label != null) {
            contact.setLabel(label);
        }
        if (Boolean.TRUE.equals(primary) && !contact.isPrimary()) {
            clearExistingPrimary(contact.getUser(), contact.getType());
            contact.setPrimary(true);
        } else if (Boolean.FALSE.equals(primary)) {
            contact.setPrimary(false);
        }

        return contactRepository.save(contact);
    }

    public void removeContact(Contact contact) {
        contactRepository.delete(contact);
    }

    /**
     * REQ-3 "Open decision c": {@code EMAIL} must look like an email, {@code PHONE}/{@code
     * WHATSAPP} must look like a phone number after stripping formatting characters, {@code OTHER}
     * is deliberately unconstrained.
     */
    public void validateFormat(ContactType type, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidContactFormatException();
        }

        switch (type) {
            case EMAIL -> {
                if (!validator.validate(new EmailHolder(value)).isEmpty()) {
                    throw new InvalidContactFormatException();
                }
            }
            case PHONE, WHATSAPP -> {
                if (!PHONE_PATTERN.matcher(value).matches()) {
                    throw new InvalidContactFormatException();
                }
            }
            case OTHER -> {
                // deliberately unconstrained
            }
            default -> throw new InvalidContactFormatException();
        }
    }

    /**
     * REQ-4a: normalizes only {@code PHONE}/{@code WHATSAPP} values -- deliberately narrower than
     * PLAN.md's original "every type is a harmless no-op" assumption, which is factually wrong for
     * {@code EMAIL} (every valid email contains a {@code .}, which {@link
     * IdentityFieldNormalizer#stripFormatting} would strip, corrupting the domain) and for {@code
     * OTHER} free text (which may legitimately contain spaces/dashes). See PLAN.md's "Deviations
     * from this PLAN" section.
     */
    private String normalizeIfPhone(ContactType type, String value) {
        if (type == ContactType.PHONE || type == ContactType.WHATSAPP) {
            return IdentityFieldNormalizer.stripFormatting(value);
        }
        return value;
    }

    private void clearExistingPrimary(User user, ContactType type) {
        contactRepository.findByUserAndType(user, type).stream()
                .filter(Contact::isPrimary)
                .forEach(
                        existing -> {
                            existing.setPrimary(false);
                            contactRepository.save(existing);
                        });
    }

    /** Throwaway record run through the injected {@link Validator} for {@code @Email}'s regex. */
    private record EmailHolder(@Email String value) {}
}
