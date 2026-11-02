package com.example.finance.tracker.finance_tracker.controller;

import com.example.finance.tracker.finance_tracker.dto.CreateBudgetRequest;
import com.example.finance.tracker.finance_tracker.dto.response.BudgetProgressResponse;
import com.example.finance.tracker.finance_tracker.dto.response.BudgetResponse;
import com.example.finance.tracker.finance_tracker.entity.BudgetEntity;
import com.example.finance.tracker.finance_tracker.service.BudgetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Month;
import java.util.List;

@RestController
@RequestMapping("/budget")
public class BudgetController {
    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse createBudget(@Valid @RequestBody CreateBudgetRequest createBudgetRequest, Principal principal) {
        BudgetEntity budgetEntity = budgetService.createBudget(createBudgetRequest, principal.getName());

        return new BudgetResponse(
                budgetEntity.getId(),
                budgetEntity.getCategoryEntity().getName(),
                budgetEntity.getDescription(),
                budgetEntity.getAmount(),
                budgetEntity.getYear(),
                budgetEntity.getMonth()
        );
    }

    @GetMapping()
    public List<BudgetResponse> getBudgets(
            @RequestParam Integer year,
            @RequestParam Month month,
            Principal principal
    ) {
        return budgetService.getBudgets(
                year,
                month,
                principal.getName()
        );
    }

    @GetMapping("/progress")
    public List<BudgetProgressResponse> getBudgetProgress(
            @RequestParam Integer year,
            @RequestParam Month month,
            Principal principal
    ) {
        return budgetService.getBudgetProgress(
                year,
                month,
                principal.getName()
        );
    }
}
