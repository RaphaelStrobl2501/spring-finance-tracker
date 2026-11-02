package com.example.finance.tracker.finance_tracker.repository;

import com.example.finance.tracker.finance_tracker.entity.RecurringTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RecurringTransactionRepository
        extends JpaRepository<RecurringTransactionEntity, Integer> {

    List<RecurringTransactionEntity>
    findByActiveTrueAndNextExecutionDateLessThanEqual(
            LocalDate date
    );

    List<RecurringTransactionEntity> findByUser_Id(Integer userId);
}
