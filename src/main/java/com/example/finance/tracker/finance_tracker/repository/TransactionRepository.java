package com.example.finance.tracker.finance_tracker.repository;

import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.dto.response.CategoryAnalyticsResponse;
import com.example.finance.tracker.finance_tracker.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Integer> {
    Optional<TransactionEntity> findByIdAndUser_Username(int id, String username);

    @Query("""
    SELECT t FROM TransactionEntity t
    WHERE t.user.id = :userId
    AND t.categoryEntity.name = COALESCE(:categoryName, t.categoryEntity.name)
    AND t.date >= COALESCE(:startDate, t.date)
    AND t.date <= COALESCE(:endDate, t.date)
    """)
    List<TransactionEntity> findFiltered(
            @Param("userId") Integer userId,
            @Param("categoryName") String categoryName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
    SELECT COALESCE(SUM(t.amount), 0)
    FROM TransactionEntity t
    WHERE t.user.id = :userId
      AND t.categoryEntity.id = :categoryId
      AND t.transactionType = :type
      AND t.date >= :startDate
      AND t.date < :endDate
    """)
    BigDecimal sumTransactions(
            @Param("userId") Integer userId,
            @Param("categoryId") Integer categoryId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
    SELECT COALESCE(SUM(t.amount), 0)
    FROM TransactionEntity t
    WHERE t.user.id = :userId
      AND t.transactionType = :type
      AND t.date >= :startDate
      AND t.date < :endDate
    """)
    BigDecimal sumByTypeAndMonth(
            @Param("userId") Integer userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
    SELECT new com.example.finance.tracker.finance_tracker.dto.response.CategoryAnalyticsResponse(
        t.categoryEntity.name,
        SUM(t.amount)
    )
    FROM TransactionEntity t
    WHERE t.user.id = :userId
      AND t.transactionType = :type
      AND t.date >= :startDate
      AND t.date < :endDate
    GROUP BY t.categoryEntity.name
    ORDER BY SUM(t.amount) DESC
    """)
    List<CategoryAnalyticsResponse> sumExpensesByCategory(
            @Param("userId") Integer userId,
            @Param("type") TransactionType type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<TransactionEntity> findByUser_IdOrderByDateDesc(Integer userId);
}
