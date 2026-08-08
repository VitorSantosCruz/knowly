package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.UserProfile;
import br.com.conectabyte.knowly.identity.UserProfileRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** soft-delete-default-filter SPEC requirements 1/2/3, entity: {@code UserProfile}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterUserProfileIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private UserProfileRepository userProfileRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @Test
    void excludesASoftDeletedUserProfileWithNoPerQueryOptIn() {
        User liveUser =
                userRepository.saveAndFlush(
                        new User("soft-delete-filter-profile-live@example.com"));
        UserProfile liveProfile = userProfileRepository.saveAndFlush(new UserProfile(liveUser));

        User deletedUser =
                userRepository.saveAndFlush(
                        new User("soft-delete-filter-profile-deleted@example.com"));
        UserProfile deletedProfile = new UserProfile(deletedUser);
        deletedProfile.setDeletedAt(Instant.now());
        userProfileRepository.saveAndFlush(deletedProfile);

        assertThat(testSupportService.findUserProfileByUserId(liveProfile.getUserId())).isPresent();
        assertThat(testSupportService.findUserProfileByUserId(deletedUser.getId())).isEmpty();
    }
}
