package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired private UserRepository userRepository;

    @Autowired private EntityManager entityManager;

    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void findsUserByEmailIgnoringCase() {
        userRepository.saveAndFlush(new User("Someone@Example.com"));

        Optional<User> found = userRepository.findByEmailIgnoreCase("someone@example.com");

        assertThat(found).isPresent();
    }

    @Test
    void setsAuditFieldsOnCreate() {
        User saved = userRepository.saveAndFlush(new User("audited@example.com"));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getUpdatedBy()).isEqualTo("system");
    }

    @Test
    void recordsEnversRevisionOnChange() {
        User saved = userRepository.saveAndFlush(new User("history@example.com"));

        saved.setOneTimePasswordHash("hash");
        userRepository.saveAndFlush(saved);

        var revisions =
                new TransactionTemplate(transactionManager)
                        .execute(
                                status ->
                                        AuditReaderFactory.get(entityManager)
                                                .getRevisions(User.class, saved.getId()));

        assertThat(revisions).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void findsOnlyUsersWithMatchingGlobalRoles() {
        User staff = new User("staff-role-search@example.com");
        staff.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staff);

        User staffAdmin = new User("staff-admin-role-search@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);

        userRepository.saveAndFlush(new User("plain-role-search@example.com"));

        List<User> found =
                userRepository.findByGlobalRoleIn(
                        List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN));

        assertThat(found)
                .extracting(User::getEmail)
                .contains("staff-role-search@example.com", "staff-admin-role-search@example.com");
        assertThat(found)
                .extracting(User::getEmail)
                .doesNotContain("plain-role-search@example.com");
    }

    @Test
    void findsUsersWithMatchingGlobalRolesAndEmailSubstringCaseInsensitively() {
        User staff = new User("Findme-Substring@example.com");
        staff.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staff);

        User staffOther = new User("other-substring@example.com");
        staffOther.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staffOther);

        List<User> found =
                userRepository.findByGlobalRoleInAndEmailContainingIgnoreCase(
                        List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN), "findme-substring");

        assertThat(found)
                .extracting(User::getEmail)
                .containsExactly("Findme-Substring@example.com");
    }

    @Test
    void returnsEmptyWhenNoUserMatchesGlobalRoleAndEmailSubstring() {
        List<User> found =
                userRepository.findByGlobalRoleInAndEmailContainingIgnoreCase(
                        List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN), "no-such-user-xyz");

        assertThat(found).isEmpty();
    }

    @Test
    void countsOnlyStaffAndStaffAdminRolesAndExcludesUsersWithNoGlobalRole() {
        long baseline =
                userRepository.countByGlobalRoleIn(
                        List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN));

        User staff = new User("staff-count@example.com");
        staff.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staff);

        User staffAdmin = new User("staff-admin-count@example.com");
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);

        userRepository.saveAndFlush(new User("plain-count@example.com"));

        long count =
                userRepository.countByGlobalRoleIn(
                        List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN));

        assertThat(count).isEqualTo(baseline + 2);
    }

    @Test
    void countByGlobalRoleInReturnsZeroForAnEmptyRoleList() {
        long count = userRepository.countByGlobalRoleIn(List.of());

        assertThat(count).isZero();
    }
}
