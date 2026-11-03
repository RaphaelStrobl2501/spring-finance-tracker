package com.example.finance.tracker.finance_tracker.service;

import com.example.finance.tracker.finance_tracker.Type.Frequency;
import com.example.finance.tracker.finance_tracker.dto.CreateRecurringTransactionRequest;
import com.example.finance.tracker.finance_tracker.dto.response.RecurringTransactionResponse;
import com.example.finance.tracker.finance_tracker.entity.CategoryEntity;
import com.example.finance.tracker.finance_tracker.entity.RecurringTransactionEntity;
import com.example.finance.tracker.finance_tracker.entity.TransactionEntity;
import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.repository.CategoryRepository;
import com.example.finance.tracker.finance_tracker.repository.RecurringTransactionRepository;
import com.example.finance.tracker.finance_tracker.repository.TransactionRepository;

import com.example.finance.tracker.finance_tracker.repository.UserRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public RecurringTransactionService(
            RecurringTransactionRepository recurringRepository,
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            CategoryRepository categoryRepository
    ) {
        this.recurringRepository = recurringRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
    }

    @CacheEvict(
            value = "monthlyAnalytics",
            allEntries = true
    )
    @Transactional
    public void processDueTransactions() {

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Vienna"));

        List<RecurringTransactionEntity> recurringTransactions =
                recurringRepository
                        .findByActiveTrueAndNextExecutionDateLessThanEqual(today);

        for (RecurringTransactionEntity recurring : recurringTransactions) {

            // Process every occurrence that was missed
            while (!recurring.getNextExecutionDate().isAfter(today)) {

                LocalDate executionDate = recurring.getNextExecutionDate();

                TransactionEntity transaction = new TransactionEntity(
                        recurring.getDescription(),
                        recurring.getAmount(),
                        executionDate,
                        recurring.getUser(),
                        recurring.getCategoryEntity(),
                        recurring.getTransactionType()
                );

                transactionRepository.save(transaction);

                LocalDate nextDate = calculateNextDate(
                        executionDate,
                        recurring.getFrequency()
                );

                recurring.setNextExecutionDate(nextDate);
            }

            recurringRepository.save(recurring);
        }
    }

    private LocalDate calculateNextDate(
            LocalDate currentDate,
            Frequency frequency
    ) {
        return switch (frequency) {
            case DAILY -> currentDate.plusDays(1);
            case WEEKLY -> currentDate.plusWeeks(1);
            case MONTHLY -> currentDate.plusMonths(1);
        };
    }

    @Transactional
    public RecurringTransactionResponse create(
            CreateRecurringTransactionRequest request,
            String username
    ) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        CategoryEntity category = categoryRepository
                .findByName(request.category().name())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category not found"));

        RecurringTransactionEntity entity = new RecurringTransactionEntity(
                user,
                category,
                request.description(),
                request.amount(),
                request.transactionType(),
                request.frequency(),
                request.nextExecutionDate()
        );

        return toResponse(recurringRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> getAll(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));

        return recurringRepository.findByUser_Id(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RecurringTransactionResponse setActive(
            Integer id,
            boolean active,
            String username
    ) {
        RecurringTransactionEntity entity = findOwned(id, username);
        entity.setActive(active);
        return toResponse(entity);
    }

    @Transactional
    public void delete(Integer id, String username) {
        RecurringTransactionEntity entity = findOwned(id, username);
        recurringRepository.delete(entity);
    }

    private RecurringTransactionEntity findOwned(
            Integer id,
            String username
    ) {
        RecurringTransactionEntity entity = recurringRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Recurring transaction not found"));

        if (!entity.getUser().getUsername().equals(username)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Recurring transaction not found");
        }

        return entity;
    }

    private RecurringTransactionResponse toResponse(
            RecurringTransactionEntity entity
    ) {
        return new RecurringTransactionResponse(
                entity.getId(),
                entity.getDescription(),
                entity.getAmount(),
                entity.getCategoryEntity().getName(),
                entity.getTransactionType(),
                entity.getFrequency(),
                entity.getNextExecutionDate(),
                entity.isActive()
        );
    }

}

