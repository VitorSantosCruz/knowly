package br.com.conectabyte.knowly.config;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
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
 * Makes a per-request correlation id available to every log line for that request via MDC, and
 * echoes it back as a W3C-shaped {@code traceparent} response header. Since {@code
 * spring-boot-starter-opentelemetry} + {@code micrometer-tracing-bridge-otel} are now on the
 * classpath (observability-stack feature), Spring's request instrumentation already opens a real
 * OTel span per request by the time this filter runs -- so this now prefers the real, valid OTel
 * trace id (the same one exported to Tempo), falling back to a locally-generated random id only
 * when no valid span is active (e.g. tracing sampled-out, or a request path this filter's {@code
 * HIGHEST_PRECEDENCE} lets it see before Spring's own instrumentation runs). This is what makes
 * Grafana's Loki-log -> Tempo-trace "trace_id" derived-field link-out actually resolve to a real
 * trace instead of an unrelated random value. The frontend's {@code metric-fetcher.ts}/dashboard
 * error states already read this exact header to show a "Trace id:" a user can hand to support, so
 * its shape ({@code 00-<32 hex>-<16 hex>-01}) is unchanged either way.
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
        SpanContext currentSpan = Span.current().getSpanContext();
        boolean hasRealTrace = currentSpan.isValid();
        String traceId = hasRealTrace ? currentSpan.getTraceId() : randomHex(32);
        String spanId = hasRealTrace ? currentSpan.getSpanId() : randomHex(16);

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
