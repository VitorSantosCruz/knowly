package br.com.conectabyte.knowly.softdelete;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.conversation.Conversation;
import br.com.conectabyte.knowly.conversation.ConversationRepository;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * soft-delete-default-filter SPEC requirement 4: on {@code Conversation}, which already carries the
 * tenant {@code @Filter}, a row must be returned only when it is both in-tenant and not
 * soft-deleted -- neither filter substitutes for the other, and both apply simultaneously.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SoftDeleteFilterConversationIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private SoftDeleteFilterTestSupportService testSupportService;

    @AfterEach
    void clearTenantContext() {
        tenantContext.clear();
    }

    @Test
    void aRowIsReturnedOnlyWhenBothInTenantAndNotSoftDeleted() {
        Tenant rightTenant = tenantRepository.saveAndFlush(new Tenant("Right Tenant Co"));
        Tenant wrongTenant = tenantRepository.saveAndFlush(new Tenant("Wrong Tenant Co"));
        User owner = userRepository.saveAndFlush(new User("softdelete-conversation@example.com"));

        Conversation rightTenantLive =
                conversationRepository.saveAndFlush(new Conversation(rightTenant, owner));

        Conversation rightTenantDeleted =
                conversationRepository.saveAndFlush(new Conversation(rightTenant, owner));
        rightTenantDeleted.setDeletedAt(Instant.now());
        conversationRepository.saveAndFlush(rightTenantDeleted);

        Conversation wrongTenantLive =
                conversationRepository.saveAndFlush(new Conversation(wrongTenant, owner));

        tenantContext.setActiveTenantId(rightTenant.getId());

        assertThat(
                        testSupportService.findConversationByIdAndOwnerId(
                                rightTenantLive.getId(), owner.getId()))
                .isPresent();
        assertThat(
                        testSupportService.findConversationByIdAndOwnerId(
                                rightTenantDeleted.getId(), owner.getId()))
                .isEmpty();
        assertThat(
                        testSupportService.findConversationByIdAndOwnerId(
                                wrongTenantLive.getId(), owner.getId()))
                .isEmpty();
    }
}
