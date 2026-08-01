package br.com.conectabyte.knowly.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Generates a per-request correlation id, makes it available to every log line for that request via
 * MDC, and echoes it back as a W3C-shaped {@code traceparent} response header (no real distributed
 * tracing backend is wired up -- there's no {@code micrometer-tracing} bridge on the classpath --
 * this is a lightweight, self-contained id, not a real trace/span propagated across services). The
 * frontend's {@code metric-fetcher.ts}/dashboard error states already read this exact header to
 * show a "Trace id:" a user can hand to support, so its shape ({@code 00-<32 hex>-<16 hex>-01})
 * matches what that existing client-side code expects, even though nothing upstream generated it
 * via real tracing.
 *
 * <p>Registered as a plain {@code @Component} filter (not wired into {@code SecurityConfig}'s
 * chain) with {@code HIGHEST_PRECEDENCE} so it also covers responses Spring Security's filter chain
 * rejects before reaching any controller (401s, etc.) -- those need a correlatable id just as much
 * as a 200/403 from inside a controller.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    static final String TRACE_ID_MDC_KEY = "traceId";
    static final String TRACEPARENT_HEADER = "traceparent";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = randomHex(32);
        String spanId = randomHex(16);

        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACEPARENT_HEADER, "00-" + traceId + "-" + spanId + "-01");

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private static String randomHex(int length) {
        StringBuilder hex = new StringBuilder(length);
        while (hex.length() < length) {
            hex.append(Integer.toHexString(RANDOM.nextInt(16)));
        }
        return hex.toString();
    }
}
