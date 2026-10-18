package com.example.finance.tracker.finance_tracker.service;

import com.example.finance.tracker.finance_tracker.dto.RegisterRequest;
import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.repository.JpaUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final JpaUserRepository jpaUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(JpaUserRepository jpaUserRepository, PasswordEncoder passwordEncoder) {
        this.jpaUserRepository = jpaUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void registerUser(RegisterRequest registerRequest) {
        if (jpaUserRepository.findByUsername(registerRequest.username()).isPresent()) {
            throw new IllegalArgumentException("Username already registered");
        }
        String hashedPassword = passwordEncoder.encode(registerRequest.password());
        UserEntity userEntity = new UserEntity(registerRequest.username(), hashedPassword);
        jpaUserRepository.save(userEntity);
    }
}
