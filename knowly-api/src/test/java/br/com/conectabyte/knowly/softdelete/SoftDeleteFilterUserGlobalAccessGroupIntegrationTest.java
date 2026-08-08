package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.tenancy.GlobalAccessGroup;
import br.com.conectabyte.knowly.tenancy.GlobalAccessGroupRepository;
import br.com.conectabyte.knowly.tenancy.UserGlobalAccessGroup;
import br.com.conectabyte.knowly.tenancy.UserGlobalAccessGroupRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** soft-delete-default-filter SPEC requirements 1/2/3, entity: {@code UserGlobalAccessGroup}. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterUserGlobalAccessGroupIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private GlobalAccessGroupRepository globalAccessGroupRepository;
    @Autowired private UserGlobalAccessGroupRepository userGlobalAccessGroupRepository;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @Test
    void excludesASoftDeletedUserGlobalAccessGroupWithNoPerQueryOptIn() {
        User user = userRepository.saveAndFlush(new User("soft-delete-filter-ugag@example.com"));
        GlobalAccessGroup group =
                globalAccessGroupRepository.saveAndFlush(
                        new GlobalAccessGroup("Soft Delete Filter UGAG " + System.nanoTime()));

        UserGlobalAccessGroup assignment =
                userGlobalAccessGroupRepository.saveAndFlush(
                        new UserGlobalAccessGroup(user, group));
        assignment.setDeletedAt(Instant.now());
        userGlobalAccessGroupRepository.saveAndFlush(assignment);

        var found = testSupportService.findUserGlobalAccessGroupsByUser(user);

        assertThat(found).isEmpty();
    }
}
