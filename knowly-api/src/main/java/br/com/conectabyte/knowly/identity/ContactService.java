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
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{10,13}$");

    private final ContactRepository contactRepository;
    private final Validator validator;

    public ContactService(ContactRepository contactRepository, Validator validator) {
        this.contactRepository = contactRepository;
        this.validator = validator;
    }

    /** REQ-3/3a/3b: add a new contact, enforcing the cap, format, and primary-per-type clearing. */
    public Contact addContact(
            User user, ContactType type, String value, String label, boolean primary) {
        validateFormat(type, value);

        if (contactRepository.countByUser(user) >= MAX_CONTACTS_PER_USER) {
            throw new ContactCapExceededException();
        }

        if (primary) {
            clearExistingPrimary(user, type);
        }

        return contactRepository.save(new Contact(user, type, value, label, primary));
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
            contact.setValue(value);
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
                String normalized = value.replaceAll("[^0-9+]", "");
                if (!PHONE_PATTERN.matcher(normalized).matches()) {
                    throw new InvalidContactFormatException();
                }
            }
            case OTHER -> {
                // deliberately unconstrained
            }
            default -> throw new InvalidContactFormatException();
        }
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
