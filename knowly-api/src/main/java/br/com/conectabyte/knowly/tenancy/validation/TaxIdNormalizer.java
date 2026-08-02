package br.com.conectabyte.knowly.tenancy.validation;

/**
 * REQ-6a: strips the punctuation characters {@code .}, {@code -}, and {@code /} from a {@code
 * taxId} before any further validation or persistence -- Brazil-specific normalization,
 * deliberately NOT shared with {@code identity}'s own CPF-normalization logic (a small,
 * tenancy-module-scoped duplicate, see PLAN.md's "Architectural decisions" for why). Public (not
 * package-private) because {@code TenantService} (a different Java package, {@code tenancy}, not
 * {@code tenancy.validation}) must call it directly for the REQ-6d ordering fix.
 */
public final class TaxIdNormalizer {

    private TaxIdNormalizer() {}

    public static String normalize(String taxId) {
        if (taxId == null || taxId.isBlank()) {
            return taxId;
        }

        return taxId.replaceAll("[.\\-/]", "");
    }
}
