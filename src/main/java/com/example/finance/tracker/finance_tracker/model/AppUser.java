package com.example.finance.tracker.finance_tracker.model;

public record AppUser(
        int id,
        String username,
        String passwordHash
) {}