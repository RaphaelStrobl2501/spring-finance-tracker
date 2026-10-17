package com.example.finance.tracker.finance_tracker.repository;

import com.example.finance.tracker.finance_tracker.model.AppUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AppUser findByUsername(String username) {
        String sql = "SELECT id, username, password_hash " +
                     "FROM users " +
                     "WHERE username = ?";
        List<AppUser> appUsers = jdbcTemplate.query(sql, (rs, rowNum) -> new AppUser(
                                                                    rs.getInt("id"),
                                                                    rs.getString("username"),
                                                                    rs.getString("password_hash")),
                                                                    username
        );
        return appUsers.isEmpty() ? null : appUsers.getFirst();
    }

    public void registerUser(String username, String hashedPassword) {
        String sql = "INSERT INTO users (username, password_hash) " +
                     "VALUES (?, ?) " +
                     "RETURNING id";
        Integer id = jdbcTemplate.queryForObject(sql, Integer.class, username, hashedPassword);
    }
}
