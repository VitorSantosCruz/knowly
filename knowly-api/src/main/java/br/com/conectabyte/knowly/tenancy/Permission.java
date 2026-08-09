package br.com.conectabyte.knowly.tenancy;

import java.util.Optional;

public enum Permission {
    TENANT_MEMBER_MANAGE,
    ARTICLE_VIEW,
    ARTICLE_CREATE,
    ARTICLE_EDIT,
    ARTICLE_DELETE,
    CONVERSATION_USE,
    DASHBOARD_VIEW,
    PROFILE_VIEW,
    PROFILE_EDIT,
    SUPPORT_CHANNEL_VIEW,
    CHAT_GROUP_DELETE;

    /**
     * permission-granularity-model REQ-2: the view/list permission this permission requires the
     * caller to also hold, if any. An explicit switch (not a naming-convention transform) per
     * PLAN.md's "single authoritative place" decision -- an irregular permission like {@code
     * TENANT_MEMBER_MANAGE} or {@code CONVERSATION_USE} would silently misfire under a
     * suffix-stripping derivation. Everything not listed here returns {@link Optional#empty()}, per
     * REQ-3 (view/list and create permissions are always independent).
     */
    public Optional<Permission> viewDependency() {
        return switch (this) {
            case ARTICLE_EDIT, ARTICLE_DELETE -> Optional.of(ARTICLE_VIEW);
            default -> Optional.empty();
        };
    }
}
