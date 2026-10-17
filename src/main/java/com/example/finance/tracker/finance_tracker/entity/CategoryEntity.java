package com.example.finance.tracker.finance_tracker.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id
    private Integer id;

    private String name;

    protected CategoryEntity() {}

    public String getName() {
        return name;
    }
}
