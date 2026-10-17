package com.example.finance.tracker.finance_tracker.repository;

import com.example.finance.tracker.finance_tracker.Type.Category;
import com.example.finance.tracker.finance_tracker.dto.CreateTransactionRequest;
import com.example.finance.tracker.finance_tracker.dto.UpdateTransactionRequest;
import com.example.finance.tracker.finance_tracker.model.Transaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Repository
public class TransactionRepository {
    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Transaction createTransaction(int userId, String description, BigDecimal amount, Category category, LocalDate date) {
        String categorySql = "SELECT id FROM categories WHERE name = ?";
        Integer categoryId = jdbcTemplate.queryForObject(categorySql, Integer.class, category.name());

        String sql = "INSERT INTO transactions (description, amount, category_id, transaction_date, user_id) " +
                     "VALUES (?, ?, ?, ?, ?) " +
                     "RETURNING id";
        Integer id = jdbcTemplate.queryForObject(sql, Integer.class, description, amount, categoryId, date, userId);

        return new Transaction(id, description, amount, category, date);
    }

    public List<Transaction> getAllTransactions() {
        String sql = "SELECT t.id, t.description, t.amount, t.transaction_date, c.name AS category_name " +
                     "FROM transactions t " +
                     "JOIN categories c ON t.category_id = c.id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> new Transaction(
                        rs.getInt("id"),
                        rs.getString("description"),
                        rs.getBigDecimal("amount"),
                        Category.valueOf(rs.getString("category_name")),
                        rs.getDate("transaction_date").toLocalDate())
        );
    }

    public List<Transaction> getTransactionsFiltered(Category category, LocalDate startDate, LocalDate endDate, int userId) {
        StringBuilder sql = new StringBuilder("SELECT t.id, t.description, t.amount, t.transaction_date, c.name AS category_name " +
                     "FROM transactions t " +
                     "JOIN categories c ON t.category_id = c.id " +
                     "WHERE 1 = 1 AND t.user_id = ?");
        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);

        if (category != null) {
            sql.append(" AND c.name = ?");
            parameters.add(category.name());
        }
        if (startDate != null) {
            sql.append(" AND t.transaction_date >= ?");
            parameters.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND t.transaction_date <= ?");
            parameters.add(endDate);
        }

        sql.append(" ORDER BY t.transaction_date DESC, t.id DESC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new Transaction(
                rs.getInt("id"),
                rs.getString("description"),
                rs.getBigDecimal("amount"),
                Category.valueOf(rs.getString("category_name")),
                rs.getDate("transaction_date").toLocalDate()),
                parameters.toArray()
        );
    }

    public Transaction getTransactionById(int id, int userId) {
        String sql = "SELECT t.id, t.description, t.amount, t.transaction_date, c.name AS category_name " +
                     "FROM transactions t " +
                     "JOIN categories c ON t.category_id = c.id " +
                     "WHERE t.id = ? AND t.user_id = ?";
        List<Transaction> transaction = jdbcTemplate.query(sql, (rs, rowNum) -> new Transaction(
                rs.getInt("id"),
                rs.getString("description"),
                rs.getBigDecimal("amount"),
                Category.valueOf(rs.getString("category_name")),
                rs.getDate("transaction_date").toLocalDate()),
                id,
                userId
        );
        return transaction.isEmpty() ? null : transaction.getFirst();
    }

    public boolean updateTransaction(int id, Transaction transaction, int userId) {
        String categorySql = "SELECT id FROM categories WHERE name = ?";
        Integer categoryId = jdbcTemplate.queryForObject(categorySql, Integer.class, transaction.category().name());

        String sql = "UPDATE transactions " +
                     "SET description = ?, amount = ?, category_id = ?, transaction_date = ? " +
                     "WHERE id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, transaction.description(), transaction.amount(), categoryId, transaction.date(), id, userId) > 0;
    }

    public int deleteTransaction(int id, int userId) {
        String sql = "DELETE FROM transactions " +
                     "WHERE id = ? AND user_id = ?";
        return jdbcTemplate.update(sql, id, userId);
    }
}
