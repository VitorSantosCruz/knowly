package br.com.conectabyte.knowly.tenancy.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * tenant-access-group-bulk-and-delete REQ-1/REQ-4: {@code @NotEmpty} covers "empty or missing";
 * {@code @Size(max = 50)} is the AppSec-review cap (PLAN.md) against an unbounded {@code IN (...)}
 * clause/reactivate-or-create loop in one transaction. The duplicate-id check (also REQ-4) isn't
 * expressible as a bean-validation annotation against a plain {@code List<Long>} and is done in
 * {@code TenantService#batchAssignAccessGroups} instead.
 */
public record BatchAccessGroupAssignmentRequestDto(
        @NotEmpty @Size(max = 50) List<Long> accessGroupIds) {}
