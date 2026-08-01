package br.com.conectabyte.knowly.metrics;

public interface TenantActiveCountProjection {
    Long getTenantId();

    Long getCount();
}
