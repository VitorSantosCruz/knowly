package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * tenant-access-group-bulk-and-delete REQ-13: the new bulk soft-delete method used by {@code
 * TenantService#deleteAccessGroup}'s cascade.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class UserAccessGroupRepositoryTest {

    @Autowired private UserAccessGroupRepository userAccessGroupRepository;
    @Autowired private AccessGroupRepository accessGroupRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private TenantMembershipRepository tenantMembershipRepository;
    @Autowired private br.com.conectabyte.knowly.auth.UserRepository userRepository;

    @Test
    @Transactional
    void softDeleteByAccessGroupIdSetsDeletedAtOnlyOnLiveRowsForThatGroup() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Bulk Softdelete UAG Co"));
        AccessGroup targetGroup =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Target"));
        AccessGroup otherGroup =
                accessGroupRepository.saveAndFlush(new AccessGroup(tenant, "Other"));
        var user =
                userRepository.saveAndFlush(
                        new br.com.conectabyte.knowly.auth.User(
                                "uag-softdelete-" + System.nanoTime() + "@example.com"));
        TenantMembership membership =
                tenantMembershipRepository.saveAndFlush(
                        new TenantMembership(user, tenant, MembershipRole.MEMBER));
        UserAccessGroup targetLive =
                userAccessGroupRepository.saveAndFlush(
                        new UserAccessGroup(membership, targetGroup));
        UserAccessGroup otherGroupRow =
                userAccessGroupRepository.saveAndFlush(new UserAccessGroup(membership, otherGroup));
        Instant deletedAt = Instant.now();

        userAccessGroupRepository.softDeleteByAccessGroupId(targetGroup.getId(), deletedAt);

        assertThat(userAccessGroupRepository.findById(targetLive.getId()))
                .hasValueSatisfying(row -> assertThat(row.getDeletedAt()).isNotNull());
        assertThat(userAccessGroupRepository.findById(otherGroupRow.getId()))
                .hasValueSatisfying(row -> assertThat(row.getDeletedAt()).isNull());
    }
}
