package br.com.conectabyte.knowly.tenancy;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.auth.UserRepository;
import br.com.conectabyte.knowly.identity.ProfileCompletenessService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * REQ-2/REQ-3/REQ-4/REQ-10: gates only the single bootstrap {@code STAFF_ADMIN} row (identified by
 * {@code spring.flyway.placeholders.bootstrap-staff-email}, the same email {@code
 * staff-bootstrap-user}'s migration seeds it with) -- every non-allowlisted {@code /api/**} request
 * made by that account while its profile is incomplete is rejected with {@code 409
 * PROFILE_COMPLETION_REQUIRED}. Deliberately scoped by identity, not by a bare "is this user's
 * profile incomplete" check: REQ-7/REQ-8/REQ-9 only guarantee completeness for accounts *created
 * after* this feature ships -- a bare completeness check would also gate any already-existing
 * pre-feature account (a real operational hazard, and a direct violation of REQ-10's "no account
 * other than the single bootstrap STAFF_ADMIN row is ever gated" text), which is explicitly out of
 * this feature's scope (SPEC's "no automatic backfill" decision). Registered <em>before</em> {@link
 * TenantContextFilter} (see specify/features/mandatory-complete-profile/PLAN.md's AppSec review
 * note) -- a pending bootstrap account has no tenant concept at all, so this gate must
 * short-circuit first.
 */
public class ProfileCompletionFilter extends OncePerRequestFilter {

    private static final List<String> ALLOWLISTED_EXACT_PATHS =
            List.of("/api/users/me/profile", "/api/users/me/profile/complete");

    private final UserRepository userRepository;
    private final ProfileCompletenessService profileCompletenessService;
    private final String bootstrapStaffEmail;

    public ProfileCompletionFilter(
            UserRepository userRepository,
            ProfileCompletenessService profileCompletenessService,
            String bootstrapStaffEmail) {
        this.userRepository = userRepository;
        this.profileCompletenessService = profileCompletenessService;
        this.bootstrapStaffEmail = bootstrapStaffEmail;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestUri = request.getRequestURI();

        if (!requestUri.startsWith("/api/") || requestUri.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (ALLOWLISTED_EXACT_PATHS.contains(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> user = currentUser();

        if (user.isPresent()
                && bootstrapStaffEmail.equalsIgnoreCase(user.get().getEmail())
                && !profileCompletenessService.isComplete(user.get())) {
            writeProfileCompletionRequired(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Optional<User> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            return Optional.empty();
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName());
    }

    private void writeProfileCompletionRequired(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_CONFLICT);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"PROFILE_COMPLETION_REQUIRED\"}");
    }
}
