package br.com.conectabyte.knowly.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates TenantContext from the session's active-tenant state for the duration of each request,
 * and rejects tenant-scoped requests made while a multi-membership user hasn't yet picked an active
 * tenant (REQ-5).
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private static final List<String> TENANT_SCOPED_EXEMPT_PATH_PREFIXES =
            List.of(
                    "/api/auth",
                    "/api/tenants/active",
                    "/api/tenants/memberships",
                    "/api/tenants/permissions/any-tenant",
                    "/api/users",
                    "/api/profile-edit-requests",
                    "/api/notifications");

    private final TenantContext tenantContext;

    public TenantContextFilter(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            HttpSession session = request.getSession(false);

            if (session == null) {
                filterChain.doFilter(request, response);
                return;
            }

            boolean staff = Boolean.TRUE.equals(session.getAttribute(TenantSessionKeys.STAFF));
            boolean staffAdmin =
                    Boolean.TRUE.equals(session.getAttribute(TenantSessionKeys.STAFF_ADMIN));
            Long activeTenantId = (Long) session.getAttribute(TenantSessionKeys.ACTIVE_TENANT_ID);
            boolean selectionPending =
                    Boolean.TRUE.equals(session.getAttribute(TenantSessionKeys.SELECTION_PENDING));

            if (selectionPending
                    && activeTenantId == null
                    && isTenantScoped(request.getRequestURI())) {
                writeTenantSelectionRequired(response);
                return;
            }

            tenantContext.setStaff(staff);
            tenantContext.setStaffAdmin(staffAdmin);

            if (activeTenantId != null) {
                tenantContext.setActiveTenantId(activeTenantId);
            }

            filterChain.doFilter(request, response);
        } finally {
            tenantContext.clear();
        }
    }

    /**
     * Only requests under {@code /api/} are business/tenant-scoped data; everything else (actuator,
     * API docs, static assets, etc.) is left alone regardless of tenant-selection state.
     */
    private boolean isTenantScoped(String requestUri) {
        return requestUri.startsWith("/api/")
                && TENANT_SCOPED_EXEMPT_PATH_PREFIXES.stream().noneMatch(requestUri::startsWith);
    }

    private void writeTenantSelectionRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"TENANT_SELECTION_REQUIRED\"}");
    }
}
