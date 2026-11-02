package com.example.finance.tracker.finance_tracker.dto.response;

import com.example.finance.tracker.finance_tracker.Type.Frequency;
import com.example.finance.tracker.finance_tracker.Type.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecurringTransactionResponse(
        Integer id,
        String description,
        BigDecimal amount,
        String category,
        TransactionType transactionType,
        Frequency frequency,
        LocalDate nextExecutionDate,
        boolean active
) {}
