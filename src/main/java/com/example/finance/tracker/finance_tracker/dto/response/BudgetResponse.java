package com.example.finance.tracker.finance_tracker.dto.response;

import java.math.BigDecimal;
import java.time.Month;

public record BudgetResponse(
        Integer id,
        String category,
        String description,
        BigDecimal amount,
        Integer year,
        Month month
) {}