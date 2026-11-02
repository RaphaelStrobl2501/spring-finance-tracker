package com.example.finance.tracker.finance_tracker.dto;

import com.example.finance.tracker.finance_tracker.Type.Category;
import com.example.finance.tracker.finance_tracker.Type.Frequency;
import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateRecurringTransactionRequest(
        @NotBlank String description,
        @NotNull @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal amount,
        @NotNull Category category,
        @NotNull TransactionType transactionType,
        @NotNull Frequency frequency,
        @NotNull LocalDate nextExecutionDate
) {}
