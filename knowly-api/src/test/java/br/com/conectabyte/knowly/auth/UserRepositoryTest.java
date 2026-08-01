package br.com.conectabyte.knowly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.metrics.DailyCountProjection;
import br.com.conectabyte.knowly.tenancy.GlobalRole;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
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

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private void backdateUser(User user, Instant createdAt) {
        jdbcTemplate.update(
                "update users set created_at = ? where id = ?",
                Timestamp.from(createdAt),
                user.getId());
    }

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

    // --- specify/features/global-staff-dashboard-trends/SPEC.md REQ-4/11: windowed staff-count
    // comparison for the global trends endpoint ---

    @Test
    void countByGlobalRoleInAndCreatedAtWindowRespectsHalfOpenBoundsAndRoleFilter() {
        Instant windowStart = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant windowEnd = Instant.now().minus(5, ChronoUnit.DAYS);

        User staffInsideWindow =
                userRepository.saveAndFlush(new User("staff-window-in@example.com"));
        staffInsideWindow.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staffInsideWindow);
        backdateUser(staffInsideWindow, windowStart.plus(1, ChronoUnit.HOURS));

        User staffAdminAtLowerBound =
                userRepository.saveAndFlush(new User("staff-admin-window-lower@example.com"));
        staffAdminAtLowerBound.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdminAtLowerBound);
        backdateUser(staffAdminAtLowerBound, windowStart);

        User staffAtUpperBound =
                userRepository.saveAndFlush(new User("staff-window-upper@example.com"));
        staffAtUpperBound.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staffAtUpperBound);
        backdateUser(staffAtUpperBound, windowEnd);

        User staffOutsideWindow =
                userRepository.saveAndFlush(new User("staff-window-out@example.com"));
        staffOutsideWindow.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staffOutsideWindow);
        backdateUser(staffOutsideWindow, windowEnd.plus(1, ChronoUnit.HOURS));

        User plainInsideWindow =
                userRepository.saveAndFlush(new User("plain-window-in@example.com"));
        backdateUser(plainInsideWindow, windowStart.plus(1, ChronoUnit.HOURS));

        long count =
                userRepository.countByGlobalRoleInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        List.of(GlobalRole.STAFF, GlobalRole.STAFF_ADMIN), windowStart, windowEnd);

        assertThat(count).isEqualTo(2);
    }

    // --- specify/features/global-staff-dashboard-sparklines/SPEC.md REQ-1/2: cumulative,
    // day-bucketed running total of internal staff headcount ---

    @Test
    void countCumulativeStaffByDayOnlyCountsStaffAndStaffAdminRoles() {
        long baselineTotal =
                userRepository.countCumulativeStaffByDay().stream()
                        .reduce((first, last) -> last)
                        .map(DailyCountProjection::getCount)
                        .orElse(0L);

        Instant dayOne = Instant.now().truncatedTo(ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS);

        User staff = userRepository.saveAndFlush(new User("cumulative-staff@example.com"));
        staff.setGlobalRole(GlobalRole.STAFF);
        userRepository.saveAndFlush(staff);
        backdateUser(staff, dayOne);

        User staffAdmin =
                userRepository.saveAndFlush(new User("cumulative-staff-admin@example.com"));
        staffAdmin.setGlobalRole(GlobalRole.STAFF_ADMIN);
        userRepository.saveAndFlush(staffAdmin);
        backdateUser(staffAdmin, dayOne.plus(1, ChronoUnit.HOURS));

        User plain = userRepository.saveAndFlush(new User("cumulative-plain@example.com"));
        backdateUser(plain, dayOne.plus(2, ChronoUnit.HOURS));

        List<DailyCountProjection> rows = userRepository.countCumulativeStaffByDay();

        assertThat(rows).isSortedAccordingTo(Comparator.comparing(DailyCountProjection::getDay));

        var dayOneRow =
                rows.stream()
                        .filter(r -> r.getDay().equals(LocalDate.ofInstant(dayOne, ZoneOffset.UTC)))
                        .findFirst()
                        .orElseThrow();

        // Two STAFF/STAFF_ADMIN rows bump the total; the plain (no GlobalRole) row is excluded.
        assertThat(dayOneRow.getCount()).isEqualTo(baselineTotal + 2);

        long previous = Long.MIN_VALUE;
        for (DailyCountProjection row : rows) {
            assertThat(row.getCount()).isGreaterThanOrEqualTo(previous);
            previous = row.getCount();
        }
    }
}
