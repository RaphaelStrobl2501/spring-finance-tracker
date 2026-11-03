package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.entity.CategoryEntity;
import com.example.finance.tracker.finance_tracker.entity.TransactionEntity;
import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.repository.CategoryRepository;
import com.example.finance.tracker.finance_tracker.repository.TransactionRepository;
import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class TransactionRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void shouldFindTransactionForOwner() {
        UserEntity user = new UserEntity("user", "123");
        userRepository.save(user);

        TransactionType type = TransactionType.INCOME;
        CategoryEntity categoryEntity = new CategoryEntity("FOOD");
        categoryRepository.save(categoryEntity);
        TransactionEntity transactionEntity = new TransactionEntity("description test", BigDecimal.valueOf(10), LocalDate.of(2026, 11, 3), user, categoryEntity, type);
        transactionRepository.save(transactionEntity);

        Optional<TransactionEntity> transaction = transactionRepository.findByIdAndUser_Username(transactionEntity.getId(), "user");

        assertThat(transaction).isPresent();

        assertThat(transaction.get().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10));

        assertThat(transaction.get().getTransactionType()).isEqualTo(type);
    }

    @Test
    void shouldNotFindTransactionForAnotherUser() {
        UserEntity user = new UserEntity("user", "123");
        userRepository.save(user);

        UserEntity user2 = new UserEntity("test", "345");
        userRepository.save(user);

        TransactionType type = TransactionType.INCOME;
        CategoryEntity categoryEntity = new CategoryEntity("FOOD");
        categoryRepository.save(categoryEntity);
        TransactionEntity transactionEntity = new TransactionEntity("description test", BigDecimal.valueOf(10), LocalDate.of(2026, 11, 3), user, categoryEntity, type);
        transactionRepository.save(transactionEntity);

        Optional<TransactionEntity> transaction = transactionRepository.findByIdAndUser_Username(transactionEntity.getId(), "abc");

        assertThat(transaction).isNotPresent();

    }
}