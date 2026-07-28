package br.com.conectabyte.knowly.tenancy;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    long countByCreatedAtGreaterThanEqual(Instant from);

    /**
     * specify/features/tenant-pagination-search/SPEC.md REQ-2/5/6/7/9: DB-level pagination and
     * case-insensitive substring search across {@code name}/{@code cnpj}/{@code razaoSocial}, OR'd
     * together. {@code search == null} short-circuits the {@code WHERE} clause to match every row.
     */
    @Query(
            """
            SELECT t FROM Tenant t
            WHERE CAST(:search AS string) IS NULL
               OR LOWER(t.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(t.cnpj) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(t.razaoSocial) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
            """)
    Page<Tenant> search(@Param("search") String search, Pageable pageable);
}
