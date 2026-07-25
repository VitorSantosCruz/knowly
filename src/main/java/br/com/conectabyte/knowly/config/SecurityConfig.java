package br.com.conectabyte.knowly.config;

import br.com.conectabyte.knowly.tenancy.TenantContext;
import br.com.conectabyte.knowly.tenancy.TenantContextFilter;
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
                                csrf.ignoringRequestMatchers(
                                        "/api/auth/login-request",
                                        "/api/auth/login-code/verify",
                                        "/api/auth/login-password/verify",
                                        "/api/tenants/**",
                                        "/api/users/me/onboarding-complete"))
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterAfter(
                        new TenantContextFilter(tenantContext),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
