package com.example.finance.tracker.finance_tracker.controller;

import com.example.finance.tracker.finance_tracker.dto.CreateRecurringTransactionRequest;
import com.example.finance.tracker.finance_tracker.dto.response.RecurringTransactionResponse;
import com.example.finance.tracker.finance_tracker.service.RecurringTransactionService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/recurring-transactions")
public class RecurringTransactionController {

    private final RecurringTransactionService service;

    public RecurringTransactionController(
            RecurringTransactionService service
    ) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecurringTransactionResponse create(
            @Valid @RequestBody CreateRecurringTransactionRequest request,
            Principal principal
    ) {
        return service.create(request, principal.getName());
    }

    @GetMapping
    public List<RecurringTransactionResponse> getAll(
            Principal principal
    ) {
        return service.getAll(principal.getName());
    }

    @PatchMapping("/{id}/active")
    public RecurringTransactionResponse setActive(
            @PathVariable Integer id,
            @RequestParam boolean active,
            Principal principal
    ) {
        return service.setActive(id, active, principal.getName());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Integer id,
            Principal principal
    ) {
        service.delete(id, principal.getName());
    }
}
