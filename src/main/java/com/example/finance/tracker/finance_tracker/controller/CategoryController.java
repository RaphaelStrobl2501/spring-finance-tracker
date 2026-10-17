package com.example.finance.tracker.finance_tracker.controller;

import com.example.finance.tracker.finance_tracker.Type.Category;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CategoryController {

    @GetMapping("/categories")
    public Category[] getCategories() {
        return Category.values();
    }
}
