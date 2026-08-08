package br.com.conectabyte.knowly.softdelete;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
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

    public SoftDeleteFilterTestSupportService(
            UserRepository userRepository, ConversationRepository conversationRepository) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
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
}
