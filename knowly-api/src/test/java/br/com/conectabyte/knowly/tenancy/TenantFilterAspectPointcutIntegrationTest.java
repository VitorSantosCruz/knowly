package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.chat.ChatConversation;
import br.com.conectabyte.knowly.chat.ChatConversationKind;
import br.com.conectabyte.knowly.chat.ChatOversightConversationLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Regression coverage for the {@code TenantFilterAspect} pointcut bug: the aspect's
 * {@code @Around("@annotation(Transactional)")} pointcut used to also match Spring Data JPA's own
 * internal {@code @Transactional} on {@code SimpleJpaRepository.findById}/{@code save}, so any
 * plain repository call made with no active tenant in context (e.g. from a {@code @RabbitListener}
 * background consumer, which deliberately isn't {@code @Transactional} itself -- see {@code
 * ArticleExtractionListener}) got the Hibernate tenant filter force-enabled with the fail-closed
 * sentinel, silently hiding rows the caller had every right to see by explicit id.
 *
 * <p>The fix narrows the pointcut with {@code && !within(Repository+)} so it only fires for
 * {@code @Transactional} *service* methods, never for Spring Data's repository proxy itself.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TenantFilterAspectPointcutIntegrationTest {

    @Autowired private ArticleRepository articleRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ChatOversightConversationLoader oversightConversationLoader;
    @Autowired private TenantContext tenantContext;

    @AfterEach
    void clearTenantContext() {
        tenantContext.clear();
    }

    @Test
    void repositoryFindByIdIsNotSubjectToTenantFilterAspectWithNoActiveTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Pointcut Fix Tenant"));
        Article article =
                articleRepository.saveAndFlush(
                        new Article(
                                tenant,
                                "ISO PDF",
                                "tenants/1/articles/1/original",
                                "iso.pdf",
                                "application/pdf"));

        // Simulate ArticleExtractionListener's call pattern: no active tenant, not staff -- a
        // plain background-thread repository call, exactly as it runs from a @RabbitListener.
        tenantContext.clear();

        var found = articleRepository.findById(article.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("ISO PDF");
    }

    @Test
    void genuineTransactionalServiceMethodStaysTenantFilteredWithNoActiveTenant() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Pointcut Regression Tenant"));

        tenantContext.setStaff(true);
        tenantContext.setStaffAdmin(true);
        ChatConversation conversation =
                oversightConversationLoader.save(
                        new ChatConversation(
                                ChatConversationKind.PEER_GROUP,
                                tenant,
                                "Tenant Scoped Conversation",
                                null));

        // Now call the real @Transactional *service*-layer method with no active tenant and no
        // staff bypass -- TenantFilterAspect must still enable the filter with the fail-closed
        // sentinel here, proving the pointcut narrowing only excludes repository proxies, not
        // services.
        tenantContext.clear();

        var found = oversightConversationLoader.loadRespectingTenantFilter(conversation.getId());

        assertThat(found).isEmpty();
    }
}
