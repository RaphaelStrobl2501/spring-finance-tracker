package com.example.finance.tracker.finance_tracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(@NotBlank String username, @Size(min = 1) @NotBlank String password) {
}
