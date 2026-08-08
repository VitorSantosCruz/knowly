package br.com.conectabyte.knowly.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
