package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.entity.CategoryEntity;
import com.example.finance.tracker.finance_tracker.entity.TransactionEntity;
import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.repository.CategoryRepository;
import com.example.finance.tracker.finance_tracker.repository.TransactionRepository;
import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    private EntityManager entityManager;

    private UserEntity owner;
    private UserEntity otherUser;
    private TransactionEntity savedTransaction;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(
                new UserEntity("user", "123")
        );

        otherUser = userRepository.save(
                new UserEntity("test", "345")
        );

        CategoryEntity category = categoryRepository.findByName("FOOD")
                .orElseGet(() ->
                        categoryRepository.save(new CategoryEntity("FOOD"))
                );

        savedTransaction = transactionRepository.save(
                new TransactionEntity(
                        "description test",
                        new BigDecimal("10.00"),
                        LocalDate.of(2026, 11, 3),
                        owner,
                        category,
                        TransactionType.INCOME
                )
        );

        entityManager.flush();
        entityManager.clear();
    }

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
        assertThat(
                transactionRepository.findByIdAndUser_Username(
                        savedTransaction.getId(),
                        "user"
                )
        ).isPresent();

        Optional<TransactionEntity> result =
                transactionRepository.findByIdAndUser_Username(
                        savedTransaction.getId(),
                        "test"
                );

        assertThat(result).isEmpty();
    }
}