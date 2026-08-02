package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * Global-scope (staff) batch permission update request -- REQ-12: the full desired set of direct
 * grants, not an add/remove diff (see PLAN.md). {@code word} is required only when the submitted
 * set differs from the current one (REQ-14).
 */
public record BatchPermissionUpdateRequestDto(
        @NotNull Set<GlobalPermission> permissions, String word) {}
