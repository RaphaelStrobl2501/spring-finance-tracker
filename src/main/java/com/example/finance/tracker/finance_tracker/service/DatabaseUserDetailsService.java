package com.example.finance.tracker.finance_tracker.service;

import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import com.example.finance.tracker.finance_tracker.model.AppUser;
import com.example.finance.tracker.finance_tracker.repository.JpaUserRepository;
import com.example.finance.tracker.finance_tracker.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final JpaUserRepository jpaUserRepository;

   public DatabaseUserDetailsService(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
   }

   @Override
   public UserDetails loadUserByUsername(String username) {
       Optional<UserEntity> user = jpaUserRepository.findByUsername(username);
       if (!user.isPresent()) {
           throw new UsernameNotFoundException("User not found");
       }

       return User.withUsername(user.get().getUsername()).password(user.get().getPasswordHash()).roles("USER").build();
   }

}
