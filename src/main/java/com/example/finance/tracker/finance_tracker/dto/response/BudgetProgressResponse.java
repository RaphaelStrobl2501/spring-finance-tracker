package com.example.finance.tracker.finance_tracker.dto.response;

import java.math.BigDecimal;

public record BudgetProgressResponse(
        String category,
        BigDecimal budget,
        BigDecimal spent,
        BigDecimal remaining,
        BigDecimal percentageUsed
) {}