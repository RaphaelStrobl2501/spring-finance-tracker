package com.example.finance.tracker.finance_tracker.service;

import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.dto.response.CategoryAnalyticsResponse;
import com.example.finance.tracker.finance_tracker.dto.response.MonthlyAnalyticsResponse;
import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.repository.TransactionRepository;
import com.example.finance.tracker.finance_tracker.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

@Service
public class AnalyticsService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public AnalyticsService(
            TransactionRepository transactionRepository,
            UserRepository userRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Cacheable(
            value = "monthlyAnalytics",
            key = "#username + ':' + #year + ':' + #month"
    )
    @Transactional(readOnly = true)
    public MonthlyAnalyticsResponse getMonthlyAnalytics(
            Integer year,
            Month month,
            String username
    ) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1);

        BigDecimal income = transactionRepository.sumByTypeAndMonth(
                user.getId(),
                TransactionType.INCOME,
                startDate,
                endDate
        );

        BigDecimal expenses = transactionRepository.sumByTypeAndMonth(
                user.getId(),
                TransactionType.EXPENSE,
                startDate,
                endDate
        );

        BigDecimal balance = income.subtract(expenses);

        return new MonthlyAnalyticsResponse(
                year,
                month,
                income,
                expenses,
                balance
        );
    }

    @Transactional(readOnly = true)
    public List<CategoryAnalyticsResponse> getExpensesByCategory(
            Integer year,
            Month month,
            String username
    ) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.plusMonths(1);

        return transactionRepository.sumExpensesByCategory(
                user.getId(),
                TransactionType.EXPENSE,
                startDate,
                endDate
        );
    }
}