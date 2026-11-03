package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.Type.Category;
import com.example.finance.tracker.finance_tracker.Type.Frequency;
import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.config.RecurringTransactionScheduler;
import com.example.finance.tracker.finance_tracker.entity.*;
import com.example.finance.tracker.finance_tracker.repository.*;
import com.example.finance.tracker.finance_tracker.service.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.cache.type=redis",
        "spring.cache.redis.time-to-live=10m",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/not_used",
        "spring.datasource.username=not_used",
        "spring.datasource.password=not_used",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "logging.level.org.springframework.security=WARN"
})
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Import(IntegrationContainers.class)
@Execution(ExecutionMode.SAME_THREAD)
abstract class IntegrationTestSupport {
    protected static final String ALICE = "it_alice";
    protected static final String BOB = "it_bob";
    protected static final String PASSWORD = "Integration-test-password-123!";
    protected static final LocalDate DATE = LocalDate.of(2024, 11, 15);

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected UserRepository users;
    @Autowired protected CategoryRepository categories;
    @Autowired protected TransactionRepository transactions;
    @Autowired protected BudgetRepository budgets;
    @Autowired protected RecurringTransactionRepository recurring;
    @Autowired protected UserService userService;
    @Autowired protected DatabaseUserDetailsService userDetailsService;
    @Autowired protected RecurringTransactionService recurringService;
    @Autowired protected AnalyticsService analyticsService;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected CacheManager cacheManager;
    @Autowired protected PlatformTransactionManager transactionManager;

    // Only the timer is mocked. Controllers, services, authentication, repositories,
    // Flyway, PostgreSQL and Redis are real. Tests invoke recurringService explicitly.
    @MockitoBean(enforceOverride = true)
    private RecurringTransactionScheduler recurringTransactionScheduler;

    protected UserEntity alice;
    protected UserEntity bob;

    @BeforeEach
    void prepareDatabase() {
        cleanDatabaseAndCache();
        for (Category category : Category.values()) {
            categories.findByName(category.name()).orElseGet(() ->
                    categories.save(new CategoryEntity(category.name())));
        }
        alice = users.save(new UserEntity(ALICE, passwordEncoder.encode(PASSWORD)));
        bob = users.save(new UserEntity(BOB, passwordEncoder.encode(PASSWORD)));
    }

    @AfterEach
    void cleanDatabaseAndCache() {
        // These repositories are connected to disposable Testcontainers only.
        // No test-level @Transactional: requests really commit, including cache writes.
        new TransactionTemplate(transactionManager).executeWithoutResult(ignored -> {
            transactions.deleteAll();
            recurring.deleteAll();
            budgets.deleteAll();
            users.deleteAll();
        });
        cacheManager.getCacheNames().forEach(name ->
                Objects.requireNonNull(cacheManager.getCache(name)).clear());
    }

    protected RequestPostProcessor auth(String username) {
        return httpBasic(username, PASSWORD);
    }

    protected String json(Object value) {
        return mapper.writeValueAsString(value);
    }

    protected int id(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
    }

    protected CategoryEntity category(String name) {
        return categories.findByName(name).orElseThrow();
    }

    protected TransactionEntity seedTransaction(String username, String description,
            String amount, LocalDate date, String category, TransactionType type) {
        return transactions.save(new TransactionEntity(description, new BigDecimal(amount),
                date, users.findByUsername(username).orElseThrow(), category(category), type));
    }

    protected TransactionEntity seedExpense(String username, String amount, LocalDate date) {
        return seedTransaction(username, "Expense", amount, date, "FOOD", TransactionType.EXPENSE);
    }

    protected List<TransactionEntity> ownedTransactions(String username) {
        return transactions.findByUser_IdOrderByDateDesc(users.findByUsername(username).orElseThrow().getId());
    }

    protected Map<String, Object> transactionBody(String description, String amount,
            LocalDate date, String category, String type) {
        return Map.of("description", description, "amount", new BigDecimal(amount),
                "date", date.toString(), "category", category, "type", type);
    }

    protected int createTransaction(String username, String amount, LocalDate date,
            String category, String type) throws Exception {
        return id(mvc.perform(post("/transactions").with(auth(username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(transactionBody("Created through API", amount, date, category, type))))
                .andExpect(status().isCreated()).andReturn());
    }

    protected Map<String, Object> budgetBody(String category, String amount, int year, Month month) {
        return Map.of("description", "Monthly budget", "category", category,
                "amount", new BigDecimal(amount), "year", year, "month", month.name());
    }

    protected int createBudget(String username, String category, String amount,
            int year, Month month) throws Exception {
        return id(mvc.perform(post("/budget").with(auth(username)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(budgetBody(category, amount, year, month))))
                .andExpect(status().isCreated()).andReturn());
    }

    protected RecurringTransactionEntity seedRecurring(String username, String amount,
            LocalDate nextDate, Frequency frequency, boolean active) {
        RecurringTransactionEntity entity = new RecurringTransactionEntity(
                users.findByUsername(username).orElseThrow(), category("FOOD"),
                "Recurring test expense", new BigDecimal(amount), TransactionType.EXPENSE,
                frequency, nextDate);
        entity.setActive(active);
        return recurring.save(entity);
    }

    protected Map<String, Object> recurringBody(LocalDate nextDate, String frequency) {
        return Map.of("description", "Recurring test expense", "amount", new BigDecimal("25.50"),
                "category", "FOOD", "transactionType", "EXPENSE", "frequency", frequency,
                "nextExecutionDate", nextDate.toString());
    }

    protected ResultActions importCsv(String username, String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "transactions.csv",
                "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return mvc.perform(multipart("/transactions/import").file(file).with(auth(username)));
    }

    protected ResultActions monthly(String username, int year, Month month) throws Exception {
        return mvc.perform(get("/analytics/monthly").with(auth(username))
                .param("year", Integer.toString(year)).param("month", month.name()));
    }
}
