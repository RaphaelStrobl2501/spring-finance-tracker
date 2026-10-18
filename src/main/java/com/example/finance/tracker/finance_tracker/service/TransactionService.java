package com.example.finance.tracker.finance_tracker.service;

import com.example.finance.tracker.finance_tracker.Type.Category;
import com.example.finance.tracker.finance_tracker.dto.CreateTransactionRequest;
import com.example.finance.tracker.finance_tracker.dto.UpdateTransactionRequest;
import com.example.finance.tracker.finance_tracker.entity.CategoryEntity;
import com.example.finance.tracker.finance_tracker.entity.TransactionEntity;
import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.model.AppUser;
import com.example.finance.tracker.finance_tracker.model.Transaction;
import com.example.finance.tracker.finance_tracker.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.cglib.core.Local;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class TransactionService {
    private final JpaTransactionRepository jpaTransactionRepository;
    private final JpaUserRepository jpaUserRepository;
    private final JpaCategoryRepository jpaCategoryRepository;

    public TransactionService(JpaTransactionRepository jpaTransactionRepository, JpaUserRepository jpaUserRepository, JpaCategoryRepository jpaCategoryRepository) {
        this.jpaTransactionRepository = jpaTransactionRepository;
        this.jpaUserRepository = jpaUserRepository;
        this.jpaCategoryRepository = jpaCategoryRepository;
    }

    public Transaction createTransaction(CreateTransactionRequest createTransactionRequest, String username) {
        UserEntity user = jpaUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        CategoryEntity category = jpaCategoryRepository
                .findByName(createTransactionRequest.category().name())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));

        TransactionEntity entity = new TransactionEntity(
                createTransactionRequest.description(),
                createTransactionRequest.amount(),
                createTransactionRequest.date(),
                user,
                category
        );

        TransactionEntity saved = jpaTransactionRepository.save(entity);

        return toTransaction(saved);
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
        UserEntity user = jpaUserRepository.findByUsername(name)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date can not be after end date");
        }
        List<TransactionEntity> entities = jpaTransactionRepository.findFiltered(
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
        Optional<TransactionEntity> transactionEntity = jpaTransactionRepository.findByIdAndUser_Username(id, username);
        if (transactionEntity.isEmpty()) {
            throw new NoSuchElementException("No transaction with provided ID found");
        }
        return toTransaction(transactionEntity.get());
    }

    @Transactional
    public Transaction updateTransaction(int id, UpdateTransactionRequest request, String username) {
        TransactionEntity entity = jpaTransactionRepository
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
            CategoryEntity category = jpaCategoryRepository
                    .findByName(request.category().name())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));

            entity.setCategoryEntity(category);
        }

        return toTransaction(entity);
    }

    @Transactional
    public void deleteTransaction(int id, String username) {
        TransactionEntity entity = jpaTransactionRepository
                .findByIdAndUser_Username(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        jpaTransactionRepository.delete(entity);
    }
}
