package br.com.conectabyte.knowly.article;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import br.com.conectabyte.knowly.tenancy.Tenant;
import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import({TestcontainersConfiguration.class, ArticleRepositoryTest.Config.class})
@SpringBootTest
@ActiveProfiles("test")
class ArticleRepositoryTest {

    @Autowired private TenantRepository tenantRepository;
    @Autowired private ArticleRepository articleRepository;
    @Autowired private TenantContext tenantContext;
    @Autowired private ArticleQueryService articleQueryService;

    @AfterEach
    void cleanUp() {
        tenantContext.clear();
    }

    @Test
    void articlesRoundTripAndAreIsolatedByTenant() {
        Tenant tenantA = tenantRepository.saveAndFlush(new Tenant("Tenant A"));
        Tenant tenantB = tenantRepository.saveAndFlush(new Tenant("Tenant B"));
        articleRepository.saveAndFlush(
                new Article(tenantA, "A-only article", "key-a", "a.pdf", "application/pdf"));
        articleRepository.saveAndFlush(
                new Article(tenantB, "B-only article", "key-b", "b.pdf", "application/pdf"));

        tenantContext.setActiveTenantId(tenantA.getId());

        assertThat(articleQueryService.findAllForActiveTenant(tenantA.getId()))
                .extracting(Article::getTitle)
                .containsExactly("A-only article");
    }

    static class ArticleQueryService {
        private final ArticleRepository articleRepository;

        ArticleQueryService(ArticleRepository articleRepository) {
            this.articleRepository = articleRepository;
        }

        @Transactional(readOnly = true)
        List<Article> findAllForActiveTenant(Long tenantId) {
            return articleRepository.findByTenantIdAndActiveTrue(tenantId);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        ArticleQueryService articleQueryService(ArticleRepository articleRepository) {
            return new ArticleQueryService(articleRepository);
        }
    }
}
