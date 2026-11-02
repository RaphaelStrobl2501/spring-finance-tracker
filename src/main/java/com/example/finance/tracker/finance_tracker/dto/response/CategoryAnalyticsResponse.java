package com.example.finance.tracker.finance_tracker.dto.response;

import java.math.BigDecimal;

public record CategoryAnalyticsResponse(
        String category,
        BigDecimal totalSpent
) {}