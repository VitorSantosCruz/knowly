package br.com.conectabyte.knowly.tenancy.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * specify/features/tenant-pagination-search/SPEC.md REQ-6: generic paginated-list response
 * envelope. Placed in {@code tenancy.dto} for now rather than a new top-level shared package — see
 * PLAN.md's "Package/file structure" section for why.
 */
public record PageResponseDto<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponseDto<T> from(Page<T> page) {
        return new PageResponseDto<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
