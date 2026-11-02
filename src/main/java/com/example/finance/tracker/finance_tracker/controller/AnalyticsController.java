package com.example.finance.tracker.finance_tracker.controller;

import com.example.finance.tracker.finance_tracker.dto.response.CategoryAnalyticsResponse;
import com.example.finance.tracker.finance_tracker.dto.response.MonthlyAnalyticsResponse;
import com.example.finance.tracker.finance_tracker.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Month;
import java.util.List;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/monthly")
    public MonthlyAnalyticsResponse getMonthlyAnalytics(
            @RequestParam Integer year,
            @RequestParam Month month,
            Principal principal
    ) {
        return analyticsService.getMonthlyAnalytics(
                year,
                month,
                principal.getName()
        );
    }

    @GetMapping("/categories")
    public List<CategoryAnalyticsResponse> getExpensesByCategory(
            @RequestParam Integer year,
            @RequestParam Month month,
            Principal principal
    ) {
        return analyticsService.getExpensesByCategory(
                year,
                month,
                principal.getName()
        );
    }
}