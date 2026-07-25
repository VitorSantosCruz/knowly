package br.com.conectabyte.knowly.tenancy.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateStaffUserRequestDto(@Email @NotBlank String email) {}
