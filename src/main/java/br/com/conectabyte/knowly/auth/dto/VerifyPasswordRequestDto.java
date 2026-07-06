package br.com.conectabyte.knowly.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyPasswordRequestDto(
        @NotBlank @Email String email, @NotBlank String password, String captchaToken) {}
