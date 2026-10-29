package com.example.finance.tracker.finance_tracker.repository;

import com.example.finance.tracker.finance_tracker.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}
