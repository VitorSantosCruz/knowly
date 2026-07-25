package br.com.conectabyte.knowly.article;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    List<Article> findByTenantIdAndActiveTrue(Long tenantId);

    long countByTenantIdAndActiveTrue(Long tenantId);
}
