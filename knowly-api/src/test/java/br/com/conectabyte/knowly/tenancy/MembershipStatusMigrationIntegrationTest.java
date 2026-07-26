package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V16 migration coverage (see specify/features/tenant-membership-acceptance/PLAN.md's "Data schema"
 * section): {@code tenant_memberships.status} exists, defaults to {@code 'ACTIVE'} for both
 * pre-existing (backfilled) and newly-inserted rows.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class MembershipStatusMigrationIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void freshlyInsertedMembershipRowDefaultsStatusToActiveAtTheColumnLevel() {
        User user = userRepository.saveAndFlush(new User("migration-status@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Migration Status Co"));

        // Insert directly via JDBC, bypassing the entity default, to prove the *column's own*
        // DEFAULT 'ACTIVE' (not just TenantMembership's Java-side field initializer) is in place.
        jdbcTemplate.update(
                "insert into tenant_memberships (user_id, tenant_id, role, active, created_at,"
                        + " created_by, updated_at, updated_by) values (?, ?, 'MEMBER', true, now(),"
                        + " 'test', now(), 'test')",
                user.getId(),
                tenant.getId());

        String status =
                jdbcTemplate.queryForObject(
                        "select status from tenant_memberships where user_id = ? and tenant_id = ?",
                        String.class,
                        user.getId(),
                        tenant.getId());

        assertThat(status).isEqualTo("ACTIVE");
    }

    @Test
    void everyPreExistingRowIsBackfilledToActiveStatus() {
        User user = userRepository.saveAndFlush(new User("migration-backfill@example.com"));
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Migration Backfill Co"));
        tenantMembershipRepository.saveAndFlush(
                new TenantMembership(user, tenant, MembershipRole.MEMBER));

        Long nonActiveCount =
                jdbcTemplate.queryForObject(
                        "select count(*) from tenant_memberships where status is null or status = ''",
                        Long.class);

        assertThat(nonActiveCount).isZero();
    }
}
