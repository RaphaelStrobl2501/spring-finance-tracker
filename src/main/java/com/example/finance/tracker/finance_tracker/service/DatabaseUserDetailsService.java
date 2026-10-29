package com.example.finance.tracker.finance_tracker.service;

import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

   public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
   }

   @Override
   public @NonNull UserDetails loadUserByUsername(@NonNull String username) {
       UserEntity user = userRepository.findByUsername(username)
               .orElseThrow(() -> new UsernameNotFoundException("User not found"));

       return User.withUsername(user.getUsername()).password(user.getPasswordHash()).roles("USER").build();
   }

}
