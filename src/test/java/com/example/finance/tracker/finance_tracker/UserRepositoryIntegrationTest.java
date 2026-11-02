package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.repository.UserRepository;
import org.apache.catalina.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18");

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveAndFindUser() {
        UserEntity userEntity = new UserEntity("test", "password");
        userRepository.saveAndFlush(userEntity);

        UserEntity user = userRepository.findByUsername("test").orElseThrow();
        assertThat(user.getUsername()).isEqualTo("test");

        assertThat(user.getId()).isNotNull();
    }
}