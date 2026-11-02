package com.example.finance.tracker.finance_tracker.service;

import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.dto.CreateBudgetRequest;
import com.example.finance.tracker.finance_tracker.dto.response.BudgetProgressResponse;
import com.example.finance.tracker.finance_tracker.dto.response.BudgetResponse;
import com.example.finance.tracker.finance_tracker.entity.BudgetEntity;
import com.example.finance.tracker.finance_tracker.entity.CategoryEntity;
import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.repository.BudgetRepository;
import com.example.finance.tracker.finance_tracker.repository.CategoryRepository;
import com.example.finance.tracker.finance_tracker.repository.TransactionRepository;
import com.example.finance.tracker.finance_tracker.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

@Service
public class BudgetService {
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    public BudgetService(BudgetRepository budgetRepository, UserRepository userRepository, CategoryRepository categoryRepository, TransactionRepository transactionRepository) {
        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public BudgetEntity createBudget(CreateBudgetRequest createBudgetRequest, String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        CategoryEntity category = categoryRepository
                .findByName(createBudgetRequest.category().name())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        boolean exists = budgetRepository.existsByUser_IdAndCategoryEntityAndYearAndMonth(user.getId(), category, createBudgetRequest.year(), createBudgetRequest.month());
        if (exists) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Budget for this category in this month/year already exists"
            );
        }


        BudgetEntity budgetEntity = new BudgetEntity(
                createBudgetRequest.description(),
                user,
                category,
                createBudgetRequest.amount(),
                createBudgetRequest.year(),
                createBudgetRequest.month()
        );
        return budgetRepository.save(budgetEntity);
    }

    @Transactional
    public List<BudgetResponse> getBudgets(
            Integer year,
            Month month,
            String username
    ) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return budgetRepository
                .findByUser_IdAndYearAndMonth(user.getId(), year, month)
                .stream()
                .map(budget -> new BudgetResponse(
                        budget.getId(),
                        budget.getCategoryEntity().getName(),
                        budget.getDescription(),
                        budget.getAmount(),
                        budget.getYear(),
                        budget.getMonth()
                ))
                .toList();
    }

    @Transactional()
    public List<BudgetProgressResponse> getBudgetProgress(
            Integer year,
            Month month,
            String username
    ) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<BudgetEntity> budgets = budgetRepository
                .findByUser_IdAndYearAndMonth(user.getId(), year, month);

        return budgets.stream().map(budget -> {
            LocalDate startDate = LocalDate.of(
                    budget.getYear(),
                    budget.getMonth(),
                    1
            );

            LocalDate endDate = startDate.plusMonths(1);

            BigDecimal spent = transactionRepository.sumTransactions(
                    user.getId(),
                    budget.getCategoryEntity().getId(),
                    TransactionType.EXPENSE,
                    startDate,
                    endDate
            );

            BigDecimal remaining = budget.getAmount().subtract(spent);

            BigDecimal percentageUsed = spent
                    .multiply(BigDecimal.valueOf(100))
                    .divide(budget.getAmount(), 2, RoundingMode.HALF_UP);

            return new BudgetProgressResponse(
                    budget.getCategoryEntity().getName(),
                    budget.getAmount(),
                    spent,
                    remaining,
                    percentageUsed
            );
        }).toList();
    }
}
