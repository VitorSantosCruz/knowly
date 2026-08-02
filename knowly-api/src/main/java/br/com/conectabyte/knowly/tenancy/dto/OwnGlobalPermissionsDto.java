package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.GlobalPermission;
import java.util.List;

public record OwnGlobalPermissionsDto(List<GlobalPermission> permissions, boolean isStaffAccount) {}
