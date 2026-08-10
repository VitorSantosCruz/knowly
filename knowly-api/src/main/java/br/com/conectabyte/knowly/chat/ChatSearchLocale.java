package br.com.conectabyte.knowly.chat;

/**
 * REQ-13/14/15: the two locales chat-message-search's full-text index is split into --
 * feature-local, deliberately not shared with {@code deletion.DeletionLocale} (see PLAN.md's
 * "Architectural decisions" for the cross-feature-coupling rationale).
 */
public enum ChatSearchLocale {
    PT,
    EN
}
