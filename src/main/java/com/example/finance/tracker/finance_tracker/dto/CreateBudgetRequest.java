package com.example.finance.tracker.finance_tracker.dto;

import com.example.finance.tracker.finance_tracker.Type.Category;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Month;

public record CreateBudgetRequest(String description, @NotNull Category category, @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount, @NotNull @Min(2000) Integer year, @NotNull
                                  Month month) {
}
