package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionIntegrationTest extends IntegrationTestSupport {
    @Test
    void shouldCreateAndPersistTransactionForAuthenticatedUser() throws Exception {
        int id = createTransaction(ALICE, "12.34", DATE, "FOOD", "EXPENSE");
        var saved = transactions.findByIdAndUser_Username(id, ALICE).orElseThrow();
        assertThat(saved.getAmount()).isEqualByComparingTo("12.34");
        assertThat(saved.getDate()).isEqualTo(DATE);
        assertThat(saved.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(transactions.findByIdAndUser_Username(id, BOB)).isEmpty();
        mvc.perform(get("/transactions/{id}", id).with(auth(ALICE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.amount").value(12.34))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.date").value(DATE.toString()));
    }

    @Test
    void shouldCreateIncomeTransaction() throws Exception {
        int id = createTransaction(ALICE, "2000.00", DATE, "SALARY", "INCOME");
        assertThat(transactions.findById(id).orElseThrow().getTransactionType()).isEqualTo(TransactionType.INCOME);
    }

    @Test
    void shouldListOnlyOwnedTransactions() throws Exception {
        var first = seedExpense(ALICE, "10", DATE);
        var second = seedExpense(ALICE, "20", DATE.minusDays(1));
        seedExpense(BOB, "999", DATE);
        mvc.perform(get("/transactions").with(auth(ALICE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(first.getId(), second.getId())));
    }

    @Test
    void shouldReturnEmptyListForUserWithoutTransactions() throws Exception {
        seedExpense(BOB, "999", DATE);
        mvc.perform(get("/transactions").with(auth(ALICE)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void shouldFilterByCategoryAndDateRangeWithoutLeakingOtherUsers() throws Exception {
        var match = seedExpense(ALICE, "10", DATE);
        seedExpense(ALICE, "20", DATE.minusMonths(1));
        seedExpense(ALICE, "20", DATE.plusMonths(1));
        seedTransaction(ALICE, "Bus", "5", DATE, "TRANSPORT", TransactionType.EXPENSE);
        seedExpense(BOB, "999", DATE);
        mvc.perform(get("/transactions").with(auth(ALICE)).param("category", "FOOD")
                        .param("startDate", "2024-11-01").param("endDate", "2024-11-30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(match.getId()));
    }

    @Test
    void shouldIncludeBothDateFilterBoundaries() throws Exception {
        // Explicit API contract: startDate <= date <= endDate for transaction lists.
        var first = seedExpense(ALICE, "10", LocalDate.of(2024, 11, 1));
        var last = seedExpense(ALICE, "20", LocalDate.of(2024, 11, 30));
        seedExpense(ALICE, "999", LocalDate.of(2024, 10, 31));
        seedExpense(ALICE, "999", LocalDate.of(2024, 12, 1));
        mvc.perform(get("/transactions").with(auth(ALICE))
                        .param("startDate", "2024-11-01").param("endDate", "2024-11-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(first.getId(), last.getId())));
    }

    @Test
    void shouldSupportOnlyStartDate() throws Exception {
        var match = seedExpense(ALICE, "10", DATE);
        seedExpense(ALICE, "20", DATE.minusDays(1));
        mvc.perform(get("/transactions").with(auth(ALICE)).param("startDate", DATE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(match.getId()));
    }

    @Test
    void shouldSupportOnlyEndDate() throws Exception {
        var match = seedExpense(ALICE, "10", DATE);
        seedExpense(ALICE, "20", DATE.plusDays(1));
        mvc.perform(get("/transactions").with(auth(ALICE)).param("endDate", DATE.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(match.getId()));
    }

    @Test
    void shouldRejectReversedDateRange() throws Exception {
        mvc.perform(get("/transactions").with(auth(ALICE))
                        .param("startDate", "2024-12-01").param("endDate", "2024-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectMalformedFilterValues() throws Exception {
        mvc.perform(get("/transactions").with(auth(ALICE)).param("category", "NOT_A_CATEGORY"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/transactions").with(auth(ALICE)).param("startDate", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenReadingAnotherUsersTransaction() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        mvc.perform(get("/transactions/{id}", saved.getId()).with(auth(BOB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldPartiallyUpdateWithoutChangingOtherFields() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        mvc.perform(patch("/transactions/{id}", saved.getId()).with(auth(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Updated\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("Updated"))
                .andExpect(jsonPath("$.amount").value(10)).andExpect(jsonPath("$.date").value(DATE.toString()));
        var reloaded = transactions.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getDescription()).isEqualTo("Updated");
        assertThat(reloaded.getAmount()).isEqualByComparingTo("10");
        assertThat(reloaded.getTransactionType()).isEqualTo(TransactionType.EXPENSE);
    }

    @Test
    void shouldUpdateAmountCategoryAndDate() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        mvc.perform(patch("/transactions/{id}", saved.getId()).with(auth(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("amount", new BigDecimal("42.75"),
                                "category", "TRANSPORT", "date", "2024-10-31"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(42.75))
                .andExpect(jsonPath("$.category").value("TRANSPORT"))
                .andExpect(jsonPath("$.date").value("2024-10-31"));
        mvc.perform(get("/transactions/{id}", saved.getId()).with(auth(ALICE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category").value("TRANSPORT"));
    }

    @Test
    void shouldNotUpdateAnotherUsersTransaction() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        mvc.perform(patch("/transactions/{id}", saved.getId()).with(auth(BOB))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":99}"))
                .andExpect(status().isNotFound());
        assertThat(transactions.findById(saved.getId()).orElseThrow().getAmount()).isEqualByComparingTo("10");
    }

    @Test
    void shouldDeleteOwnedTransactionAndPreserveOtherUsersData() throws Exception {
        var mine = seedExpense(ALICE, "10", DATE);
        var theirs = seedExpense(BOB, "20", DATE);
        mvc.perform(delete("/transactions/{id}", mine.getId()).with(auth(ALICE)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(transactions.findById(mine.getId())).isEmpty();
        assertThat(transactions.findById(theirs.getId())).isPresent();
    }

    @Test
    void shouldNotDeleteAnotherUsersTransaction() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        mvc.perform(delete("/transactions/{id}", saved.getId()).with(auth(BOB)))
                .andExpect(status().isNotFound());
        assertThat(transactions.findById(saved.getId())).isPresent();
    }

    @Test
    void shouldReturn404ForMissingTransactionOnReadUpdateAndDelete() throws Exception {
        var deleted = seedExpense(ALICE, "10", DATE);
        transactions.deleteById(deleted.getId());
        mvc.perform(get("/transactions/{id}", deleted.getId()).with(auth(ALICE))).andExpect(status().isNotFound());
        mvc.perform(patch("/transactions/{id}", deleted.getId()).with(auth(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":20}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/transactions/{id}", deleted.getId()).with(auth(ALICE))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"description", "amount", "date", "category", "type"})
    void shouldRejectMissingRequiredCreationFields(String missing) throws Exception {
        var body = new HashMap<>(transactionBody("Test", "10", DATE, "FOOD", "EXPENSE"));
        body.remove(missing);
        mvc.perform(post("/transactions").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
        assertThat(transactions.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01"})
    void shouldRejectNonPositiveAmountsOnCreateAndUpdate(String amount) throws Exception {
        mvc.perform(post("/transactions").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(transactionBody("Test", amount, DATE, "FOOD", "EXPENSE"))))
                .andExpect(status().isBadRequest());
        assertThat(transactions.count()).isZero();
        var saved = seedExpense(ALICE, "10", DATE);
        mvc.perform(patch("/transactions/{id}", saved.getId()).with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", new BigDecimal(amount)))))
                .andExpect(status().isBadRequest());
        assertThat(transactions.findById(saved.getId()).orElseThrow().getAmount()).isEqualByComparingTo("10");
    }

    @Test
    void shouldRejectBlankDescription() throws Exception {
        mvc.perform(post("/transactions").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(transactionBody("   ", "10", DATE, "FOOD", "EXPENSE"))))
                .andExpect(status().isBadRequest());
        assertThat(transactions.count()).isZero();
    }

    @Test
    void shouldAcceptFutureDateUnderCurrentCreateRequestContract() throws Exception {
        // CreateTransactionRequest has @NotNull, but no @PastOrPresent on date.
        // CSV import deliberately has a different check in the supplied service.
        LocalDate future = LocalDate.now().plusYears(1);
        int id = createTransaction(ALICE, "10", future, "FOOD", "EXPENSE");
        assertThat(transactions.findById(id).orElseThrow().getDate()).isEqualTo(future);
    }

    @Test
    void shouldTreatNullPatchFieldsAsUnchanged() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        mvc.perform(patch("/transactions/{id}", saved.getId()).with(auth(ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":null,\"amount\":null,\"date\":null,\"category\":null}"))
                .andExpect(status().isOk());
        var found = transactions.findById(saved.getId()).orElseThrow();
        assertThat(found.getDescription()).isEqualTo(saved.getDescription());
        assertThat(found.getAmount()).isEqualByComparingTo("10");
        assertThat(found.getDate()).isEqualTo(DATE);
    }

    @Test
    void shouldRequireAuthenticationForMutationAndImport() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        mvc.perform(patch("/transactions/{id}", saved.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":20}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/transactions/{id}", saved.getId())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/transactions/import").file(new MockMultipartFile("file", "x.csv", "text/csv",
                        "description,amount,date,category,type\nTest,10,2024-11-15,FOOD,EXPENSE\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());
        assertThat(transactions.findById(saved.getId()).orElseThrow().getAmount()).isEqualByComparingTo("10");
    }
}
