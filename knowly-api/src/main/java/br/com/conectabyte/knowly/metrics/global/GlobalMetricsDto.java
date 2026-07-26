package br.com.conectabyte.knowly.metrics.global;

public record GlobalMetricsDto(
        long tenantCount, long newTenantsThisMonth, long articlesReadTotal, long staffCount) {}
