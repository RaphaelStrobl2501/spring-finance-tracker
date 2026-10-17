package com.example.finance.tracker.finance_tracker.dto;

import com.example.finance.tracker.finance_tracker.Type.Category;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateTransactionRequest(String description, @Positive BigDecimal amount, Category category, LocalDate date) {
}
