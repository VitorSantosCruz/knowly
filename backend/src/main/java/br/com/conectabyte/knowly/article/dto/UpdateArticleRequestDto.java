package br.com.conectabyte.knowly.article.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateArticleRequestDto(@NotBlank String title, String text) {}
