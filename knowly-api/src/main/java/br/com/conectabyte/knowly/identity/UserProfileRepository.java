package br.com.conectabyte.knowly.identity;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    boolean existsByTaxIdBlindIndexAndUserIdNot(String taxIdBlindIndex, Long userId);

    /**
     * Derived (HQL-backed) lookup by primary key, used instead of the inherited {@code findById} in
     * soft-delete-filter-sensitive test coverage: {@code JpaRepository#findById} delegates to
     * {@code EntityManager#find}, which does not honor Hibernate {@code @Filter}s on
     * entity-by-primary-key loads (see {@code UserRepository#findById}'s Javadoc for the full
     * explanation).
     */
    Optional<UserProfile> findByUserId(Long userId);

    /**
     * chat-message-search amendment (2026-08-10): name-prefilter for {@code
     * ChatEligibilityService#searchEligibleDirectCandidates}, pushing the name match into SQL
     * rather than scanning every user like {@code listCandidates} does. {@code UserProfile} carries
     * no tenant column, so no {@code TenantFilterAspect} concern here -- but the {@code deletedAt
     * IS NULL} guard below is required explicitly: unlike {@code listCandidates}, which starts from
     * {@code UserRepository#findAllByDeletedAtIsNull()}, this query starts from {@code UserProfile}
     * directly, so it must restate the guard itself or silently reopen the 2026-08-04 "a
     * soft-deleted user must never surface as a chat candidate" fix for this one new code path.
     */
    @Query(
            "SELECT up FROM UserProfile up WHERE LOWER(up.fullName) LIKE LOWER(:pattern) AND"
                    + " up.deletedAt IS NULL")
    List<UserProfile> searchByFullName(@Param("pattern") String pattern, Pageable pageable);
}
