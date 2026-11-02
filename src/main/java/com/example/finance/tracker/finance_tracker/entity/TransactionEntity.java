package com.example.finance.tracker.finance_tracker.entity;

import com.example.finance.tracker.finance_tracker.Type.TransactionType;
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
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false) // name means column in transaction table where user id is stored and referencedColumnName means the name of the column of the primary key in the users table
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private CategoryEntity categoryEntity;

    @Column(name = "transaction_date")
    private LocalDate date;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TransactionType transactionType;

    protected TransactionEntity() {
    }

    public TransactionEntity(String description, BigDecimal amount,
                             LocalDate date, UserEntity user,
                             CategoryEntity categoryEntity,
                             TransactionType transactionType) {
        this.description = description;
        this.amount = amount;
        this.date = date;
        this.user = user;
        this.categoryEntity = categoryEntity;
        this.transactionType = transactionType;
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

    public TransactionType getTransactionType() {
        return transactionType;
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
