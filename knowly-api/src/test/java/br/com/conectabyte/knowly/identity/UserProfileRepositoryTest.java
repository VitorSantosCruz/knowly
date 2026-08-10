package br.com.conectabyte.knowly.identity;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * chat-message-search amendment (2026-08-10): {@link UserProfileRepository#searchByFullName}'s
 * explicit {@code deletedAt IS NULL} guard, backing {@code
 * ChatEligibilityService#searchEligibleDirectCandidates}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class UserProfileRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;

    @Test
    void searchByFullNameNeverMatchesASoftDeletedUserProfile() {
        User liveUser = userRepository.saveAndFlush(new User("live-search@example.com"));
        UserProfile liveProfile = new UserProfile(liveUser);
        liveProfile.setFullName("Searchable Person");
        userProfileRepository.saveAndFlush(liveProfile);

        User deletedUser = userRepository.saveAndFlush(new User("deleted-search@example.com"));
        UserProfile deletedProfile = new UserProfile(deletedUser);
        deletedProfile.setFullName("Searchable Ghost");
        deletedProfile.setDeletedAt(Instant.now());
        userProfileRepository.saveAndFlush(deletedProfile);

        var results = userProfileRepository.searchByFullName("%searchable%", PageRequest.of(0, 20));

        assertThat(results).extracting(UserProfile::getUserId).containsExactly(liveUser.getId());
    }
}
