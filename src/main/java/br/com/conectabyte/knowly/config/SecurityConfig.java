package br.com.conectabyte.knowly.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * httpBasic is a placeholder for endpoints other than /api/auth/**: it ensures they respond 401
     * (REST convention) instead of the default Http403ForbiddenEntryPoint's 403. It should be
     * replaced by the real session-aware mechanism once there are protected endpoints to guard.
     */
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers("/actuator/health", "/api/auth/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/auth/**"))
                .httpBasic(withDefaults());

        return http.build();
    }
}
