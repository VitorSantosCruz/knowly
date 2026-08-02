package br.com.conectabyte.knowly.identity;

/**
 * Standard Brazilian CPF mod-11 check-digit validator, per
 * specify/features/identity-profile-model-v2/PLAN.md's 2026-08-02 amendment (REQ-4a). Also
 * explicitly rejects all-repeated-digit CPFs (e.g. {@code 11111111111}), which naive mod-11
 * arithmetic would otherwise incorrectly accept, and any input that isn't exactly 11 digits. Pure
 * function, no Spring machinery needed -- expects an already-normalized (digits-only) input.
 */
final class CpfChecksumValidator {

    private CpfChecksumValidator() {}

    static boolean isValid(String normalizedCpf) {
        if (normalizedCpf == null || !normalizedCpf.matches("\\d{11}")) {
            return false;
        }

        if (normalizedCpf.chars().distinct().count() == 1) {
            return false;
        }

        int[] digits = normalizedCpf.chars().map(c -> c - '0').toArray();

        int firstVerifier = verifierDigit(digits, 9);
        if (firstVerifier != digits[9]) {
            return false;
        }

        int secondVerifier = verifierDigit(digits, 10);
        return secondVerifier == digits[10];
    }

    private static int verifierDigit(int[] digits, int length) {
        int weight = length + 1;
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digits[i] * weight;
            weight--;
        }

        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
