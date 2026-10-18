package com.example.finance.tracker.finance_tracker.controller;

import com.example.finance.tracker.finance_tracker.Type.Category;
import com.example.finance.tracker.finance_tracker.dto.CreateTransactionRequest;
import com.example.finance.tracker.finance_tracker.dto.UpdateTransactionRequest;
import com.example.finance.tracker.finance_tracker.model.Transaction;
import com.example.finance.tracker.finance_tracker.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final TransactionService transactionService;


    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping()
    @ResponseStatus(HttpStatus.CREATED)
    public Transaction createTransaction(@Valid @RequestBody CreateTransactionRequest createTransactionRequest, Principal principal) {
        String username = principal.getName();
        return transactionService.createTransaction(createTransactionRequest, username);
    }

    @GetMapping()
    public List<Transaction> getAllTransactions(@RequestParam(required = false) Category category,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                LocalDate startDate,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                LocalDate endDate,
                                                Principal principal) {
        String name = principal.getName();
        return transactionService.getAllTransactions(category, startDate, endDate, name);
    }

    @GetMapping("/{id}")
    public Transaction getTransaction(@PathVariable int id, Principal principal) {
        String name = principal.getName();
        return transactionService.getTransaction(id, name);
    }

    @PatchMapping("/{id}")
    public Transaction updateTransaction(@PathVariable int id, @Valid @RequestBody UpdateTransactionRequest updateTransactionRequest, Principal principal) {
        String name = principal.getName();
        return transactionService.updateTransaction(id, updateTransactionRequest, name);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransaction(@PathVariable int id, Principal principal) {
        String name = principal.getName();
        transactionService.deleteTransaction(id, name);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(NoSuchElementException exception) {
        return Map.of("error", exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleNotFound(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }
}
