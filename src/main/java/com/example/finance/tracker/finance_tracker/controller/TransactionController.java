package com.example.finance.tracker.finance_tracker.controller;

import com.example.finance.tracker.finance_tracker.Type.Category;
import com.example.finance.tracker.finance_tracker.dto.CreateTransactionRequest;
import com.example.finance.tracker.finance_tracker.dto.UpdateTransactionRequest;
import com.example.finance.tracker.finance_tracker.model.Transaction;
import com.example.finance.tracker.finance_tracker.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    // csv upload
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void importTransactions(@RequestParam("file") MultipartFile file, Principal principal) throws IOException {
        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "CSV file is empty"
            );
        }
        transactionService.importTransactions(file, principal.getName());
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

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportTransactions(Principal principal) {

        String csv = transactionService.exportTransactions(principal.getName());

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"transactions.csv\""
                )
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
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
