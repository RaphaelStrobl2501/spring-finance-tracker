package com.example.finance.tracker.finance_tracker.config;

import com.example.finance.tracker.finance_tracker.service.RecurringTransactionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurringTransactionScheduler {

    private final RecurringTransactionService recurringTransactionService;

    public RecurringTransactionScheduler(
            RecurringTransactionService recurringTransactionService
    ) {
        this.recurringTransactionService = recurringTransactionService;
    }

    // @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Vienna")
    @Scheduled(fixedRate = 10000)
    public void processRecurringTransactions() {
        recurringTransactionService.processDueTransactions();
    }
}
