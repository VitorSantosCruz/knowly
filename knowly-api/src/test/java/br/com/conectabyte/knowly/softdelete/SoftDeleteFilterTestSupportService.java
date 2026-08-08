package br.com.conectabyte.knowly.softdelete;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.identity.Address;
import br.com.conectabyte.knowly.identity.AddressRepository;
import br.com.conectabyte.knowly.identity.Contact;
import br.com.conectabyte.knowly.identity.ContactRepository;
import br.com.conectabyte.knowly.identity.UserProfile;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-only helper, mirroring {@code ChatOversightConversationLoader}'s role for the tenant
 * filter's own tests: exercises {@link SoftDeleteFilterAspect} through a real
 * {@code @Transactional} Spring proxy (self-invocation from within a test class would silently skip
 * the aspect's advice), with and without {@link AllowDeletedForOversight}, so the tests can prove
 * the filter is on by default and can be deliberately, narrowly disabled.
 */
@Component
public class SoftDeleteFilterTestSupportService {

    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final UserProfileRepository userProfileRepository;
    private final ContactRepository contactRepository;
    private final AddressRepository addressRepository;

    public SoftDeleteFilterTestSupportService(
            UserRepository userRepository,
            ConversationRepository conversationRepository,
            UserProfileRepository userProfileRepository,
            ContactRepository contactRepository,
            AddressRepository addressRepository) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.userProfileRepository = userProfileRepository;
        this.contactRepository = contactRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public Optional<User> findUserById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    @AllowDeletedForOversight
    public Optional<User> findUserByIdIgnoringSoftDelete(Long id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Conversation> findConversationByIdAndOwnerId(Long id, Long ownerId) {
        return conversationRepository.findByIdAndOwnerId(id, ownerId);
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> findUserProfileByUserId(Long userId) {
        return userProfileRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<Contact> findContactsByUser(User user) {
        return contactRepository.findByUser(user);
    }

    @Transactional(readOnly = true)
    public Optional<Address> findAddressByUserId(Long userId) {
        return addressRepository.findByUserId(userId);
    }
}
