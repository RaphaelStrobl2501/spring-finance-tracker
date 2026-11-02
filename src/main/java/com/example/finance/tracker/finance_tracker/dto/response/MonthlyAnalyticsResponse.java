package com.example.finance.tracker.finance_tracker.dto.response;

import java.math.BigDecimal;
import java.time.Month;

public record MonthlyAnalyticsResponse(
        Integer year,
        Month month,
        BigDecimal totalIncome,
        BigDecimal totalExpenses,
        BigDecimal balance
) {}