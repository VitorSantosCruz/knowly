package br.com.conectabyte.knowly.chat.exception;

/**
 * A client-supplied pagination cursor ({@code ChatCursor#decode}) that fails to decode -- always a
 * bad request, never a server error. Deliberately its own type rather than a raw {@code
 * IllegalArgumentException}: {@link ChatExceptionHandler} used to catch {@code
 * IllegalArgumentException} directly, which -- since {@code @RestControllerAdvice} is
 * application-wide, not scoped to chat endpoints -- silently caught *any* unrelated {@code
 * IllegalArgumentException} thrown anywhere in the app (e.g. {@code
 * IdentityCryptoProperties#encryptionKeyBytes} failing on a misconfigured env var) and mislabeled
 * it as {@code CHAT_INVALID_CURSOR}. See the 2026-08-02 bug report against {@code
 * UserProfileController#completeOwnProfile}.
 */
public class ChatInvalidCursorException extends RuntimeException {

    public ChatInvalidCursorException(Throwable cause) {
        super("Malformed cursor", cause);
    }
}
