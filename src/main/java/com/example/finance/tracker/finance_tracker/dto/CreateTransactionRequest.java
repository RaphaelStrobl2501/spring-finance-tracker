package com.example.finance.tracker.finance_tracker.dto;

import com.example.finance.tracker.finance_tracker.Type.Category;
import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateTransactionRequest(@NotBlank String description, @NotNull @Positive BigDecimal amount, @NotNull Category category, @NotNull LocalDate date,
                                       @NotNull TransactionType type) {
}
