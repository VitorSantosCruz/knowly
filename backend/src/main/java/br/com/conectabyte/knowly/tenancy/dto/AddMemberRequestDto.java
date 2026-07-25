package br.com.conectabyte.knowly.tenancy.dto;

import br.com.conectabyte.knowly.tenancy.MembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddMemberRequestDto(@Email @NotBlank String email, @NotNull MembershipRole role) {}
