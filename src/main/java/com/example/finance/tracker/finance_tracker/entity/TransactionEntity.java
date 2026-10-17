package com.example.finance.tracker.finance_tracker.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class TransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity categoryEntity;

    @Column(name = "transaction_date")
    private LocalDate date;

    private BigDecimal amount;

    protected TransactionEntity() {
    }

    public TransactionEntity(String description, BigDecimal amount,
                             LocalDate date, UserEntity user,
                             CategoryEntity categoryEntity) {
        this.description = description;
        this.amount = amount;
        this.date = date;
        this.user = user;
        this.categoryEntity = categoryEntity;
    }

    public String getDescription() {
        return description;
    }

    public Integer getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public CategoryEntity getCategoryEntity() {
        return categoryEntity;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public void setCategoryEntity(CategoryEntity categoryEntity) {
        this.categoryEntity = categoryEntity;
    }
}
