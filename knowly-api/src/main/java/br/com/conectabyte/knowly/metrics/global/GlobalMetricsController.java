package br.com.conectabyte.knowly.metrics.global;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-only, cross-tenant reporting endpoint — the global counterpart of {@link
 * br.com.conectabyte.knowly.metrics.MetricsController}. The permission check and audit log live on
 * {@link GlobalMetricsService#globalMetrics()}, not duplicated here.
 */
@RestController
@RequestMapping("/api/staff/metrics")
public class GlobalMetricsController {

    private final GlobalMetricsService globalMetricsService;

    public GlobalMetricsController(GlobalMetricsService globalMetricsService) {
        this.globalMetricsService = globalMetricsService;
    }

    @GetMapping("/global")
    public GlobalMetricsDto globalMetrics() {
        return globalMetricsService.globalMetrics();
    }
}
