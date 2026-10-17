package com.example.finance.tracker.finance_tracker.model;

import com.example.finance.tracker.finance_tracker.Type.Category;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Transaction(int id, String description, BigDecimal amount, Category category, LocalDate date) {
}
