package com.example.finance.tracker.finance_tracker.entity;

import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.Type.Frequency;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "recurring_transactions")
@Getter
@Setter
@NoArgsConstructor
public class RecurringTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity categoryEntity;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Column(nullable = false)
    private LocalDate nextExecutionDate;

    @Column(nullable = false)
    private boolean active = true;

    public RecurringTransactionEntity(
            UserEntity user,
            CategoryEntity categoryEntity,
            String description,
            BigDecimal amount,
            TransactionType transactionType,
            Frequency frequency,
            LocalDate nextExecutionDate
    ) {
        this.user = user;
        this.categoryEntity = categoryEntity;
        this.description = description;
        this.amount = amount;
        this.transactionType = transactionType;
        this.frequency = frequency;
        this.nextExecutionDate = nextExecutionDate;
        this.active = true;
    }
}
