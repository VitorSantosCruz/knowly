package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.auth.User;
import br.com.conectabyte.knowly.tenancy.GlobalRole;

public record StaffUserSummaryDto(Long id, String email, GlobalRole globalRole) {

    public static StaffUserSummaryDto from(User user) {
        return new StaffUserSummaryDto(user.getId(), user.getEmail(), user.getGlobalRole());
    }
}
