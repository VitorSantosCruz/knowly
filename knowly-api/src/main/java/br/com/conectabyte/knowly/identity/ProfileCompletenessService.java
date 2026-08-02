package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import org.springframework.stereotype.Service;

/**
 * Derived (not persisted) completeness check per
 * specify/features/mandatory-complete-profile/SPEC.md's completeness definition and PLAN.md's
 * "derived, not persisted" decision. Reused by {@link
 * br.com.conectabyte.knowly.tenancy.ProfileCompletionFilter}, the login-outcome computation, and
 * the completion endpoint's guard.
 *
 * <p>{@code countryCode} is required non-null as of mandatory-complete-profile/SPEC.md's 2026-08-02
 * fourth amendment -- this closes an appsec-flagged gap where a null {@code countryCode} would
 * otherwise let a Brazilian {@code taxId} skip {@link CpfChecksumValidator}'s checksum via the
 * completeness path (the checksum only runs when {@code countryCode == "BR"}). {@code rg}/{@code
 * rgOrgaoEmissor}/{@code birthDate} were removed entirely, 2026-08-02, and are no longer part of
 * this check.
 */
@Service
public class ProfileCompletenessService {

    private final UserProfileRepository userProfileRepository;
    private final AddressRepository addressRepository;
    private final ContactRepository contactRepository;

    public ProfileCompletenessService(
            UserProfileRepository userProfileRepository,
            AddressRepository addressRepository,
            ContactRepository contactRepository) {
        this.userProfileRepository = userProfileRepository;
        this.addressRepository = addressRepository;
        this.contactRepository = contactRepository;
    }

    public boolean isComplete(User user) {
        UserProfile profile = userProfileRepository.findById(user.getId()).orElse(null);

        if (profile == null || isBlank(profile.getFullName())) {
            return false;
        }

        if (isBlank(profile.getTaxId()) || isBlank(profile.getCountryCode())) {
            return false;
        }

        Address address = addressRepository.findById(user.getId()).orElse(null);

        if (address == null
                || isBlank(address.getAddressLine1())
                || isBlank(address.getCity())
                || isBlank(address.getPostalCode())
                || isBlank(address.getCountryCode())) {
            return false;
        }

        return contactRepository.countByUser(user) > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
