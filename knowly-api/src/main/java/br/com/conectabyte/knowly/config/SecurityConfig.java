package br.com.conectabyte.knowly.config;

import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * This app never authenticates via username/password (sessions are established directly on
     * SecurityContextHolder after code/OTP verification — see AuthController), so an empty user
     * store is correct here — its only purpose is satisfying
     * UserDetailsServiceAutoConfiguration's @ConditionalOnMissingBean(UserDetailsService.class),
     * which otherwise auto-generates an in-memory user with a random password on every startup
     * (unused, but noisy and misleading in logs, and a red flag during a security review).
     */
    @Bean
    UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

    /**
     * A plain 401 entry point for endpoints other than /api/auth/**: it ensures they respond 401
     * (REST convention) instead of the default Http403ForbiddenEntryPoint's 403. Should be replaced
     * by the real session-aware mechanism once there are protected endpoints to guard.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, TenantContext tenantContext)
            throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/api/auth/logout")
                                        .authenticated()
                                        .requestMatchers("/actuator/health", "/api/auth/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                                        .csrfTokenRequestHandler(
                                                new CsrfTokenRequestAttributeHandler())
                                        .ignoringRequestMatchers(
                                                // Pre-authentication only. Do not widen this back
                                                // to "/api/tenants/**" -- that previously exempted
                                                // every authenticated, state-changing endpoint
                                                // nested under this prefix (TenantController's own
                                                // member/permission mutations,
                                                // ConversationController,
                                                // and any future controller such as
                                                // SupportChannelController) from CSRF protection.
                                                // "/api/tenants/active" is the one endpoint in this
                                                // flow that runs immediately after login, in the
                                                // same request sequence as the exempted
                                                // /api/auth/** endpoints, to select the active
                                                // tenant before a full session is established --
                                                // see
                                                // DECISIONS.md (2026-07-31) for the incident this
                                                // fixes.
                                                "/api/auth/login-request",
                                                "/api/auth/login-code/verify",
                                                "/api/auth/login-password/verify",
                                                "/api/tenants/active",
                                                "/api/notifications/**",
                                                "/api/users/me/onboarding-complete",
                                                "/api/users/**",
                                                "/api/profile-edit-requests/**"))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(
                        new TenantContextFilter(tenantContext),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Spring Security's CSRF token is resolved lazily by default; unless something actually reads
     * it during the request, the CookieCsrfTokenRepository never writes the XSRF-TOKEN cookie.
     * Angular's HttpClientXsrfModule expects that cookie to be present so it can be echoed back as
     * the X-XSRF-TOKEN header on every state-changing request — this filter forces the token to
     * resolve on every request so the cookie is always set.
     */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute("_csrf");

            if (csrfToken != null) {
                csrfToken.getToken();
            }

            filterChain.doFilter(request, response);
        }
    }
}
