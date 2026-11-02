package com.example.finance.tracker.finance_tracker.service;

import com.example.finance.tracker.finance_tracker.Type.Category;
import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.dto.CreateTransactionRequest;
import com.example.finance.tracker.finance_tracker.dto.UpdateTransactionRequest;
import com.example.finance.tracker.finance_tracker.entity.CategoryEntity;
import com.example.finance.tracker.finance_tracker.entity.TransactionEntity;
import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.model.AppUser;
import com.example.finance.tracker.finance_tracker.model.Transaction;
import com.example.finance.tracker.finance_tracker.repository.*;
import jakarta.transaction.Transactional;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cglib.core.Local;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.Principal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.*;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository, CategoryRepository categoryRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @CacheEvict(
            value = "monthlyAnalytics",
            allEntries = true
    )
    public Transaction createTransaction(CreateTransactionRequest createTransactionRequest, String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        CategoryEntity category = categoryRepository
                .findByName(createTransactionRequest.category().name())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        TransactionEntity entity = new TransactionEntity(
                createTransactionRequest.description(),
                createTransactionRequest.amount(),
                createTransactionRequest.date(),
                user,
                category,
                createTransactionRequest.type()
        );

        TransactionEntity saved = transactionRepository.save(entity);

        return toTransaction(saved);
    }

    @CacheEvict(
            value = "monthlyAnalytics",
            allEntries = true
    )
    @Transactional
    public void importTransactions(MultipartFile file, String username) {

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"
                ));

        List<TransactionEntity> transactions = new ArrayList<>();

        try (Reader reader = new InputStreamReader(
                file.getInputStream(),
                StandardCharsets.UTF_8
        )) {
            CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .get();

            try (CSVParser parser = format.parse(reader)) {

                for (CSVRecord record : parser) {
                    try {
                        String description = record.get("description");
                        BigDecimal amount = new BigDecimal(record.get("amount"));
                        LocalDate date = LocalDate.parse(record.get("date"));

                        Category category = Category.valueOf(
                                record.get("category")
                        );

                        TransactionType type = TransactionType.valueOf(
                                record.get("type")
                        );

                        if (description.isBlank()
                                || amount.signum() <= 0
                                || date.isAfter(LocalDate.now())) {
                            throw new IllegalArgumentException(
                                    "Invalid transaction values"
                            );
                        }

                        CategoryEntity categoryEntity = categoryRepository
                                .findByName(category.name())
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "Category not found"
                                        )
                                );

                        TransactionEntity transaction = new TransactionEntity(
                                description,
                                amount,
                                date,
                                user,
                                categoryEntity,
                                type
                        );

                        transactions.add(transaction);

                    } catch (IllegalArgumentException | DateTimeException e) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Invalid CSV record at line "
                                        + record.getRecordNumber() + ": "
                                        + e.getMessage(),
                                e
                        );
                    }
                }
            }

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Could not read CSV file",
                    e
            );
        }

        transactionRepository.saveAll(transactions);
    }


    private Transaction toTransaction(TransactionEntity transactionEntity) {
        return new Transaction(transactionEntity.getId(),
                transactionEntity.getDescription(),
                transactionEntity.getAmount(),
                Category.valueOf(transactionEntity.getCategoryEntity().getName()),
                transactionEntity.getDate()
        );
    }

    public List<Transaction> getAllTransactions(Category category, LocalDate startDate, LocalDate endDate, String name) {
        UserEntity user = userRepository.findByUsername(name)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date can not be after end date");
        }
        List<TransactionEntity> entities = transactionRepository.findFiltered(
                user.getId(),
                category == null ? null : category.name(),
                startDate,
                endDate
        );

        return entities.stream()
                .map(this::toTransaction)
                .toList();
    }

    public Transaction getTransaction(int id, String username) {
        Optional<TransactionEntity> transactionEntity = transactionRepository.findByIdAndUser_Username(id, username);
        if (transactionEntity.isEmpty()) {
            throw new NoSuchElementException("No transaction with provided ID found");
        }
        return toTransaction(transactionEntity.get());
    }

    @Transactional
    public String exportTransactions(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<TransactionEntity> transactions =
                transactionRepository.findByUser_IdOrderByDateDesc(user.getId());

        StringBuilder csv = new StringBuilder();

        csv.append("id,description,amount,category,type,date\n");

        for (TransactionEntity transaction : transactions) {
            csv.append(transaction.getId()).append(",");
            csv.append(escapeCsv(transaction.getDescription())).append(",");
            csv.append(transaction.getAmount()).append(",");
            csv.append(escapeCsv(transaction.getCategoryEntity().getName())).append(",");
            csv.append(transaction.getTransactionType()).append(",");
            csv.append(transaction.getDate()).append("\n");
        }

        return csv.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        // Prevent spreadsheet formula injection.
        if (!value.isEmpty() &&
                (value.charAt(0) == '=' ||
                        value.charAt(0) == '+' ||
                        value.charAt(0) == '-' ||
                        value.charAt(0) == '@' ||
                        value.charAt(0) == '\t' ||
                        value.charAt(0) == '\r' ||
                        value.charAt(0) == '\n')) {
            value = "'" + value;
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    @CacheEvict(
            value = "monthlyAnalytics",
            allEntries = true
    )
    @Transactional
    public Transaction updateTransaction(int id, UpdateTransactionRequest request, String username) {
        TransactionEntity entity = transactionRepository
                .findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (request.description() != null) {
            entity.setDescription(request.description());
        }

        if (request.amount() != null) {
            entity.setAmount(request.amount());
        }

        if (request.date() != null) {
            entity.setDate(request.date());
        }

        if (request.category() != null) {
            CategoryEntity category = categoryRepository
                    .findByName(request.category().name())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));

            entity.setCategoryEntity(category);
        }

        return toTransaction(entity);
    }

    @CacheEvict(
            value = "monthlyAnalytics",
            allEntries = true
    )
    @Transactional
    public void deleteTransaction(int id, String username) {
        TransactionEntity entity = transactionRepository
                .findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        transactionRepository.delete(entity);
    }
}
