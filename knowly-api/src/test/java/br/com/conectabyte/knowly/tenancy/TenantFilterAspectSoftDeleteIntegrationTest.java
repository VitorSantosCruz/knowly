package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.article.Article;
import br.com.conectabyte.knowly.article.ArticleRepository;
import br.com.conectabyte.knowly.article.ArticleService;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * tenant-crud REQ-11 (AppSec correction, PLAN.md "Architectural decisions"): a member who switched
 * into a tenant *before* it was soft-deleted must lose tenant-scoped access on the very next
 * request within the same still-live session -- {@code TenantContextFilter} only re-derives the
 * active tenant id from the session attribute (no DB lookup), so {@code TenantFilterAspect} is the
 * actual chokepoint that must reject the now-stale active tenant. Exercises a real
 * {@code @Transactional} service method (mirrors {@code
 * TenantFilterAspectPointcutIntegrationTest}'s style) rather than going through the full
 * controller/session stack, since the aspect itself -- not the controller -- is what this
 * correction changed.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TenantFilterAspectSoftDeleteIntegrationTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private ArticleService articleService;
    @Autowired private TenantContext tenantContext;

    @AfterEach
    void clearTenantContext() {
        tenantContext.clear();
    }

    @Test
    void aTenantScopedRequestAgainstAnActiveTenantIdThatWasSoftDeletedMidSessionGetsNoAccess() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Soft Deleted Mid Session Co"));
        articleRepository.saveAndFlush(
                new Article(
                        tenant,
                        "Pre-deletion Article",
                        "tenants/x/articles/1/original",
                        "a.pdf",
                        "application/pdf"));

        // Session already picked this tenant as active (as TenantContextFilter would set it from
        // the session attribute, with no DB lookup).
        tenantContext.setActiveTenantId(tenant.getId());

        // The tenant is soft-deleted *after* the session's active tenant was set -- simulating a
        // staff deletion happening mid-session for another user.
        tenant.setDeletedAt(Instant.now());
        tenantRepository.saveAndFlush(tenant);

        // A further tenant-scoped request within the same still-live session must see no data,
        // not the pre-deletion article -- proving TenantFilterAspect itself now fails closed.
        assertThat(articleService.list(tenant.getId())).isEmpty();
    }

    @Test
    void aTenantScopedRequestAgainstAStillActiveTenantSeesItsData() {
        Tenant tenant = tenantRepository.saveAndFlush(new Tenant("Still Active Mid Session Co"));
        articleRepository.saveAndFlush(
                new Article(
                        tenant,
                        "Still Visible Article",
                        "tenants/y/articles/1/original",
                        "a.pdf",
                        "application/pdf"));
        tenantContext.setActiveTenantId(tenant.getId());

        assertThat(articleService.list(tenant.getId())).isNotEmpty();
    }
}
