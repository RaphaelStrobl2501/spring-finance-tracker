package com.example.finance.tracker.finance_tracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Month;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"user_id", "category_id", "year", "month"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class BudgetEntity {
    public BudgetEntity(
            String description,
            UserEntity user,
            CategoryEntity categoryEntity,
            BigDecimal amount,
            Integer year,
            Month month
    ) {
        this.description = description;
        this.user = user;
        this.categoryEntity = categoryEntity;
        this.amount = amount;
        this.year = year;
        this.month = month;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) // name means column in transaction table where user id is stored and referencedColumnName means the name of the column of the primary key in the users table
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private CategoryEntity categoryEntity;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Month month;
}
