package com.example.finance.tracker.finance_tracker.repository;

import com.example.finance.tracker.finance_tracker.entity.BudgetEntity;
import com.example.finance.tracker.finance_tracker.entity.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Month;
import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<BudgetEntity, Integer> {
    public Optional<BudgetEntity> findByUser_IdAndCategoryEntityAndYearAndMonth(Integer userId, CategoryEntity categoryEntity, Integer year, Month month);

    public boolean existsByUser_IdAndCategoryEntityAndYearAndMonth(Integer userId, CategoryEntity categoryEntity, Integer year, Month month);

    List<BudgetEntity> findByUser_IdAndYearAndMonth(
            Integer userId,
            Integer year,
            Month month
    );
}
