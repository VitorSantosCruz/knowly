package br.com.conectabyte.knowly.identity;

/**
 * Shared normalization for {@code cpf}/{@code rg}/{@code addresses.cep}/phone-type {@code
 * contacts.value}, per specify/features/identity-profile-model-v2/PLAN.md's 2026-08-02 amendment
 * (REQ-4a). Deliberately distinct from {@link BlindIndexService#normalize}, which strips ALL
 * non-digit characters -- this strips only the specific formatting characters REQ-4a names ({@code
 * .}, {@code -}, {@code /}, whitespace, {@code (}/{@code )}), preserving a leading {@code +} on an
 * international-format phone value. Pure function, no Spring machinery needed.
 */
final class IdentityFieldNormalizer {

    private IdentityFieldNormalizer() {}

    /**
     * Strips {@code .}/{@code -}/{@code /}/whitespace/{@code (}/{@code )}; {@code null} unchanged.
     */
    static String stripFormatting(String value) {
        if (value == null) {
            return null;
        }

        return value.replaceAll("[.\\-/\\s()]", "");
    }
}
