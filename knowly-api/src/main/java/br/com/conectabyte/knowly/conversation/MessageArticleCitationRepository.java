package br.com.conectabyte.knowly.conversation;

import br.com.conectabyte.knowly.metrics.ArticleUsageDto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageArticleCitationRepository
        extends JpaRepository<MessageArticleCitation, Long> {

    @Query(
            """
            select new br.com.conectabyte.knowly.metrics.ArticleUsageDto(a.id, a.title, count(c))
            from MessageArticleCitation c join c.article a
            where a.tenant.id = :tenantId and a.active = true
            group by a.id, a.title
            order by count(c) desc
            """)
    List<ArticleUsageDto> usageByTenant(@Param("tenantId") Long tenantId);
}
