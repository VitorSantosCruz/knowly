package br.com.conectabyte.knowly.identity.exception;

/**
 * Thrown when {@code CPF_RG_ENCRYPTION_KEY}/{@code CPF_RG_HMAC_KEY} (see {@code
 * IdentityCryptoProperties}) isn't valid base64 -- a deployment/environment misconfiguration, never
 * something a client can trigger. Previously this surfaced as a raw, unmapped {@code
 * IllegalArgumentException} ("Illegal base64 character ...") straight out of {@code
 * Base64.getDecoder().decode(...)}, which bubbled past every {@code @ExceptionHandler} in the app
 * and produced an empty/malformed response body -- see the bug report for {@code
 * UserProfileController#completeOwnProfile} on 2026-08-02. This exception carries a clear,
 * non-secret-leaking message (the misconfigured property's name, never its value) and is mapped to
 * a structured 500 by {@code IdentityExceptionHandler}.
 */
public class IdentityCryptoConfigurationException extends IllegalStateException {

    public IdentityCryptoConfigurationException(String propertyName, Throwable cause) {
        super(
                propertyName + " is not valid base64 -- check this environment variable's value",
                cause);
    }
}
