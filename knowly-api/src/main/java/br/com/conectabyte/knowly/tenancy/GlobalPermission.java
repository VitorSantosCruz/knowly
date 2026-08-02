package br.com.conectabyte.knowly.tenancy;

import java.util.Optional;

public enum GlobalPermission {
    TENANT_CREATE,
    TENANT_ACT_AS_ANY,
    STAFF_PERMISSION_MANAGE,
    STAFF_USER_CREATE,
    STAFF_USER_VIEW,
    PROFILE_VIEW,
    PROFILE_EDIT,
    DASHBOARD_VIEW_GLOBAL,
    AUDIT_TRAIL_VIEW,
    STAFF_SUPPORT_HANDLE,
    TENANT_VIEW,
    TENANT_EDIT,
    TENANT_DELETE,
    STAFF_USER_EDIT,
    STAFF_USER_DELETE,
    TENANT_MEMBER_VIEW,
    TENANT_MEMBER_CREATE,
    TENANT_MEMBER_EDIT,
    TENANT_MEMBER_DELETE,
    TENANT_ACCESS_GROUP_VIEW,
    TENANT_ACCESS_GROUP_CREATE,
    TENANT_ACCESS_GROUP_EDIT,
    TENANT_ACCESS_GROUP_DELETE,
    TENANT_PERMISSION_GRANT_VIEW,
    TENANT_PERMISSION_GRANT_CREATE,
    TENANT_PERMISSION_GRANT_DELETE;

    /**
     * permission-granularity-model REQ-2: the view/list permission this permission requires the
     * caller to also hold, if any. See {@link Permission#viewDependency()} for the same reasoning
     * (explicit switch, not a naming-convention transform).
     */
    public Optional<GlobalPermission> viewDependency() {
        return switch (this) {
            case TENANT_EDIT, TENANT_DELETE -> Optional.of(TENANT_VIEW);
            case STAFF_USER_EDIT, STAFF_USER_DELETE -> Optional.of(STAFF_USER_VIEW);
            case TENANT_MEMBER_EDIT, TENANT_MEMBER_DELETE -> Optional.of(TENANT_MEMBER_VIEW);
            case TENANT_ACCESS_GROUP_EDIT, TENANT_ACCESS_GROUP_DELETE ->
                    Optional.of(TENANT_ACCESS_GROUP_VIEW);
            case TENANT_PERMISSION_GRANT_DELETE -> Optional.of(TENANT_PERMISSION_GRANT_VIEW);
            default -> Optional.empty();
        };
    }
}
