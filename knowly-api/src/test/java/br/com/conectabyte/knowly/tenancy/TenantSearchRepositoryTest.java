package br.com.conectabyte.knowly.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.conectabyte.knowly.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

/**
 * specify/features/tenant-pagination-search/SPEC.md REQ-2/5/6/7/9: {@code
 * TenantRepository.search(String, Pageable)} backs {@code GET /api/tenants}'s DB-level pagination
 * and cross-field search.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class TenantSearchRepositoryTest {

    @Autowired private TenantRepository tenantRepository;

    private Tenant tenant(String name, String taxId, String legalName) {
        Tenant tenant = new Tenant(name);
        if (taxId != null) {
            tenant.setTaxId(taxId);
        }
        if (legalName != null) {
            tenant.setLegalName(legalName);
        }
        return tenantRepository.saveAndFlush(tenant);
    }

    @Test
    void unfilteredSearchReturnsAPageAlphabeticalByName() {
        tenant("Zeta Co " + System.nanoTime(), null, null);
        tenant("Alpha Co " + System.nanoTime(), null, null);

        Pageable pageable = PageRequest.of(0, 100, Sort.by("name").ascending());
        Page<Tenant> page = tenantRepository.search(null, pageable);

        List<String> names = page.getContent().stream().map(Tenant::getName).toList();
        List<String> sortedNames = names.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        assertThat(names).isEqualTo(sortedNames);
    }

    @Test
    void searchMatchesOnlyByNameCaseInsensitively() {
        String marker = "Uniq" + System.nanoTime();
        Tenant tenant = tenant("Findme" + marker + "Corp", "00000000000000", "Some Razao Social");

        Page<Tenant> page =
                tenantRepository.search(
                        marker.toLowerCase(), PageRequest.of(0, 20, Sort.by("name").ascending()));

        assertThat(page.getContent()).extracting(Tenant::getId).contains(tenant.getId());
    }

    @Test
    void searchMatchesOnlyByTaxIdCaseInsensitively() {
        String marker = "TAXID" + System.nanoTime();
        Tenant tenant = tenant("Ordinary Co " + System.nanoTime(), marker, "Some Legal Name");

        Page<Tenant> page =
                tenantRepository.search(
                        marker.toLowerCase(), PageRequest.of(0, 20, Sort.by("name").ascending()));

        assertThat(page.getContent()).extracting(Tenant::getId).contains(tenant.getId());
    }

    @Test
    void searchMatchesOnlyByLegalNameCaseInsensitively() {
        String marker = "LEGALNAME" + System.nanoTime();
        Tenant tenant =
                tenant("Ordinary Co " + System.nanoTime(), "11111111111111", "Something " + marker);

        Page<Tenant> page =
                tenantRepository.search(
                        marker.toLowerCase(), PageRequest.of(0, 20, Sort.by("name").ascending()));

        assertThat(page.getContent()).extracting(Tenant::getId).contains(tenant.getId());
    }

    @Test
    void pageBeyondTheLastPageReturnsEmptyContentWithCorrectTotals() {
        String marker = "PastEnd" + System.nanoTime();
        tenant(marker, null, null);

        Page<Tenant> page =
                tenantRepository.search(
                        marker.toLowerCase(), PageRequest.of(50, 20, Sort.by("name").ascending()));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void filteredResultNeverExceedsRequestedPageSizeAtTheRepositoryLevel() {
        String marker = "Bulk" + System.nanoTime();
        for (int i = 0; i < 15; i++) {
            tenant(marker + i, null, null);
        }

        Page<Tenant> page =
                tenantRepository.search(
                        marker.toLowerCase(), PageRequest.of(0, 5, Sort.by("name").ascending()));

        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(15);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }
}
