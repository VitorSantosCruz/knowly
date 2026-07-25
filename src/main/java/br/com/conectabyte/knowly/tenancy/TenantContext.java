package br.com.conectabyte.knowly.tenancy;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Holds the active tenant for the current request thread. Populated by TenantContextFilter from the
 * session's active-tenant attribute and cleared at the end of each request.
 */
@Component
public class TenantContext {

    private static final ThreadLocal<Long> ACTIVE_TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> STAFF = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> STAFF_ADMIN = new ThreadLocal<>();

    public void setActiveTenantId(Long tenantId) {
        ACTIVE_TENANT_ID.set(tenantId);
    }

    public Optional<Long> getActiveTenantId() {
        return Optional.ofNullable(ACTIVE_TENANT_ID.get());
    }

    public void setStaff(boolean staff) {
        STAFF.set(staff);
    }

    public boolean isStaff() {
        return Boolean.TRUE.equals(STAFF.get());
    }

    /** True only for STAFF_ADMIN — unrestricted staff. A permission-gated STAFF returns false. */
    public void setStaffAdmin(boolean staffAdmin) {
        STAFF_ADMIN.set(staffAdmin);
    }

    public boolean isStaffAdmin() {
        return Boolean.TRUE.equals(STAFF_ADMIN.get());
    }

    public void clear() {
        ACTIVE_TENANT_ID.remove();
        STAFF.remove();
        STAFF_ADMIN.remove();
    }
}
