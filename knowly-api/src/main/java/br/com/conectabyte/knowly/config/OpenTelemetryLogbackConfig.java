package br.com.conectabyte.knowly.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Wires {@code logback-spring.xml}'s {@code OTEL} appender to this application's real {@link
 * OpenTelemetry} bean (the same instance {@code spring-boot-starter-opentelemetry} configures for
 * metrics/traces), so log export shares that bean's resource attributes (service.name=knowly, etc.)
 * and OTLP endpoint config instead of falling back to a no-op.
 *
 * <p>Logback itself initializes before the Spring context exists, so the appender can't be handed
 * the real {@code OpenTelemetry} bean at Logback-config time -- {@link
 * OpenTelemetryAppender#install} is the documented way to attach it afterwards, once the bean is
 * available. {@code ContextRefreshedEvent} (fired once the context is fully initialized, before any
 * request is served) is used rather than an eager {@code @PostConstruct} so the {@code
 * OpenTelemetry} bean itself is guaranteed fully built first.
 */
@Component
public class OpenTelemetryLogbackConfig implements ApplicationListener<ContextRefreshedEvent> {

    private final OpenTelemetry openTelemetry;

    public OpenTelemetryLogbackConfig(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
