package br.com.conectabyte.knowly.tenancy.validation;

/**
 * REQ-6c: real CNPJ checksum validation (mod-11 on both check digits, alphanumeric-adjusted), for
 * Brazil-denoting {@code country} only, applied to an already-normalized (see {@link
 * TaxIdNormalizer}), already-shape-checked (see {@code TaxIdValidator}) 14-character value. Weight
 * sequences: {@code 5,4,3,2,9,8,7,6,5,4,3,2} for the first check digit (12 weights over the 12-
 * character base), {@code 6,5,4,3,2,9,8,7,6,5,4,3,2} for the second (13 weights over the base plus
 * the first check digit) -- the standard Receita Federal mod-11 weight sequences, verified against
 * PLAN.md's own real/published CNPJ fixture table during implementation (see PLAN.md's "Deviations"
 * section: the sequences originally recorded in PLAN.md did not reproduce those fixtures and were
 * corrected here). Remainder-to-digit rule: {@code remainder < 2 -> 0, else 11 - remainder}. Public
 * for the same cross-package reason as {@link TaxIdNormalizer}.
 */
public final class CnpjChecksumValidator {

    private static final int[] FIRST_DIGIT_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_DIGIT_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CnpjChecksumValidator() {}

    public static boolean isValid(String normalizedTaxId) {
        if (normalizedTaxId == null || normalizedTaxId.length() != 14) {
            return false;
        }

        String upper = normalizedTaxId.toUpperCase();
        int expectedFirst = checkDigit(upper.substring(0, 12), FIRST_DIGIT_WEIGHTS);
        if (charValue(upper.charAt(12)) != expectedFirst) {
            return false;
        }

        int expectedSecond =
                checkDigit(upper.substring(0, 12) + expectedFirst, SECOND_DIGIT_WEIGHTS);
        return charValue(upper.charAt(13)) == expectedSecond;
    }

    private static int checkDigit(String base, int[] weights) {
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            sum += charValue(base.charAt(i)) * weights[i];
        }

        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }

    private static int charValue(char c) {
        return Character.toUpperCase(c) - 48;
    }
}
