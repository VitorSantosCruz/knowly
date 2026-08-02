package br.com.conectabyte.knowly.identity;

import br.com.conectabyte.knowly.auth.User;
import org.springframework.stereotype.Service;

/**
 * Derived (not persisted) completeness check per
 * specify/features/mandatory-complete-profile/SPEC.md's completeness definition and PLAN.md's
 * "derived, not persisted" decision. Reused by {@link
 * br.com.conectabyte.knowly.tenancy.ProfileCompletionFilter}, the login-outcome computation, and
 * the completion endpoint's guard.
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

        if (profile == null || isBlank(profile.getFullName()) || profile.getBirthDate() == null) {
            return false;
        }

        if (isBlank(profile.getCpf())
                || isBlank(profile.getRg())
                || isBlank(profile.getRgOrgaoEmissor())) {
            return false;
        }

        Address address = addressRepository.findById(user.getId()).orElse(null);

        if (address == null
                || isBlank(address.getCep())
                || isBlank(address.getLogradouro())
                || isBlank(address.getBairro())
                || isBlank(address.getCidade())
                || isBlank(address.getEstado())
                || isBlank(address.getPais())) {
            return false;
        }

        return contactRepository.countByUser(user) > 0;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
