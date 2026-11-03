package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.Type.Frequency;
import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.dto.response.RecurringTransactionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecurringTransactionIntegrationTest extends IntegrationTestSupport {
    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Europe/Vienna"));
    }

    @Test
    void shouldCreateRecurringTransactionThroughApi() throws Exception {
        LocalDate nextDate = today().plusDays(5);
        var result = mvc.perform(post("/recurring-transactions").with(auth(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content(json(recurringBody(nextDate, "MONTHLY"))))
                .andExpect(status().isCreated()).andReturn();
        int id = id(result);
        var saved = recurring.findById(id).orElseThrow();
        assertThat(saved.getNextExecutionDate()).isEqualTo(nextDate);
        assertThat(saved.getAmount()).isEqualByComparingTo("25.50");
        assertThat(saved.getFrequency()).isEqualTo(Frequency.MONTHLY);
        assertThat(saved.isActive()).isTrue();
        assertThat(transactions.count()).isZero();
        mvc.perform(get("/recurring-transactions").with(auth(ALICE)))
                .andExpect(status().isOk()).andExpect(content().json(json(List.of(new RecurringTransactionResponse(
                        id, "Recurring test expense", new BigDecimal("25.50"), "FOOD", TransactionType.EXPENSE,
                        Frequency.MONTHLY, nextDate, true))), JsonCompareMode.STRICT));
        mvc.perform(get("/recurring-transactions").with(auth(BOB)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void shouldListOnlyOwnedRecurringTransactions() throws Exception {
        var mine = seedRecurring(ALICE, "10", today().plusDays(1), Frequency.DAILY, true);
        seedRecurring(BOB, "999", today().plusDays(1), Frequency.DAILY, true);
        mvc.perform(get("/recurring-transactions").with(auth(ALICE)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.getId()));
    }

    @Test
    void shouldDeactivateAndReactivateOwnedRecurringTransaction() throws Exception {
        var mine = seedRecurring(ALICE, "10", today(), Frequency.DAILY, true);
        mvc.perform(patch("/recurring-transactions/{id}/active", mine.getId()).with(auth(ALICE)).param("active", "false"))
                .andExpect(status().isOk());
        assertThat(recurring.findById(mine.getId()).orElseThrow().isActive()).isFalse();
        recurringService.processDueTransactions();
        assertThat(ownedTransactions(ALICE)).isEmpty();
        mvc.perform(patch("/recurring-transactions/{id}/active", mine.getId()).with(auth(ALICE)).param("active", "true"))
                .andExpect(status().isOk());
        assertThat(recurring.findById(mine.getId()).orElseThrow().isActive()).isTrue();
        recurringService.processDueTransactions();
        assertThat(ownedTransactions(ALICE)).hasSize(1);
    }

    @Test
    void shouldNotChangeAnotherUsersRecurringTransaction() throws Exception {
        var mine = seedRecurring(ALICE, "10", today(), Frequency.DAILY, true);
        mvc.perform(patch("/recurring-transactions/{id}/active", mine.getId()).with(auth(BOB)).param("active", "false"))
                .andExpect(status().isNotFound());
        assertThat(recurring.findById(mine.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void shouldDeleteOwnedRecurringTransactionButKeepGeneratedHistory() throws Exception {
        var mine = seedRecurring(ALICE, "10", today(), Frequency.DAILY, true);
        recurringService.processDueTransactions();
        var history = ownedTransactions(ALICE);
        assertThat(history).hasSize(1);
        mvc.perform(delete("/recurring-transactions/{id}", mine.getId()).with(auth(ALICE)))
                .andExpect(status().isNoContent());
        assertThat(recurring.findById(mine.getId())).isEmpty();
        recurringService.processDueTransactions();
        assertThat(ownedTransactions(ALICE)).extracting(t -> t.getId()).containsExactly(history.get(0).getId());
    }

    @Test
    void shouldNotDeleteAnotherUsersRecurringTransaction() throws Exception {
        var mine = seedRecurring(ALICE, "10", today(), Frequency.DAILY, true);
        mvc.perform(delete("/recurring-transactions/{id}", mine.getId()).with(auth(BOB)))
                .andExpect(status().isNotFound());
        assertThat(recurring.findById(mine.getId())).isPresent();
    }

    @Test
    void shouldReturn404ForMissingRecurringTransaction() throws Exception {
        var removed = seedRecurring(ALICE, "10", today(), Frequency.DAILY, true);
        recurring.deleteById(removed.getId());
        mvc.perform(patch("/recurring-transactions/{id}/active", removed.getId()).with(auth(ALICE)).param("active", "false"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/recurring-transactions/{id}", removed.getId()).with(auth(ALICE)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRequireAuthenticationForRecurringMutations() throws Exception {
        var mine = seedRecurring(ALICE, "10", today(), Frequency.DAILY, true);
        mvc.perform(patch("/recurring-transactions/{id}/active", mine.getId()).param("active", "false"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/recurring-transactions/{id}", mine.getId())).andExpect(status().isUnauthorized());
        assertThat(recurring.findById(mine.getId()).orElseThrow().isActive()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"description", "amount", "category", "transactionType", "frequency", "nextExecutionDate"})
    void shouldRejectMissingRequiredFields(String missing) throws Exception {
        var body = new HashMap<>(recurringBody(today().plusDays(1), "MONTHLY"));
        body.remove(missing);
        mvc.perform(post("/recurring-transactions").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
        assertThat(recurring.count()).isZero();
    }

    @Test
    void shouldRejectInvalidFrequencyAndNonPositiveAmount() throws Exception {
        mvc.perform(post("/recurring-transactions").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(recurringBody(today().plusDays(1), "INVALID"))))
                .andExpect(status().isBadRequest());
        var body = new HashMap<>(recurringBody(today().plusDays(1), "MONTHLY"));
        body.put("amount", BigDecimal.ZERO);
        mvc.perform(post("/recurring-transactions").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
        assertThat(recurring.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(Frequency.class)
    void shouldGenerateDueOccurrenceAndAdvanceByItsFrequency(Frequency frequency) {
        LocalDate today = today();
        var rule = seedRecurring(ALICE, "12.34", today, frequency, true);
        recurringService.processDueTransactions();
        var generated = ownedTransactions(ALICE);
        assertThat(generated).hasSize(1);
        assertThat(generated.get(0).getDescription()).isEqualTo("Recurring test expense");
        assertThat(generated.get(0).getAmount()).isEqualByComparingTo("12.34");
        assertThat(generated.get(0).getDate()).isEqualTo(today);
        assertThat(generated.get(0).getTransactionType()).isEqualTo(TransactionType.EXPENSE);
        assertThat(ownedTransactions(BOB)).isEmpty();
        LocalDate expected = switch (frequency) {
            case DAILY -> today.plusDays(1);
            case WEEKLY -> today.plusWeeks(1);
            case MONTHLY -> today.plusMonths(1);
        };
        assertThat(recurring.findById(rule.getId()).orElseThrow().getNextExecutionDate()).isEqualTo(expected);
    }

    @Test
    void shouldCatchUpAllMissedDailyOccurrencesAndNotDuplicateOnSecondRun() {
        LocalDate today = today();
        var rule = seedRecurring(ALICE, "10", today.minusDays(2), Frequency.DAILY, true);
        recurringService.processDueTransactions();
        assertThat(ownedTransactions(ALICE)).extracting(t -> t.getDate())
                .containsExactly(today, today.minusDays(1), today.minusDays(2));
        assertThat(recurring.findById(rule.getId()).orElseThrow().getNextExecutionDate()).isEqualTo(today.plusDays(1));
        var firstIds = ownedTransactions(ALICE).stream().map(t -> t.getId()).toList();
        recurringService.processDueTransactions();
        assertThat(ownedTransactions(ALICE)).extracting(t -> t.getId()).containsExactlyElementsOf(firstIds);
    }

    @Test
    void shouldCatchUpMissedWeeklyOccurrences() {
        LocalDate today = today();
        var rule = seedRecurring(ALICE, "10", today.minusWeeks(2), Frequency.WEEKLY, true);
        recurringService.processDueTransactions();
        assertThat(ownedTransactions(ALICE)).extracting(t -> t.getDate())
                .containsExactly(today, today.minusWeeks(1), today.minusWeeks(2));
        assertThat(recurring.findById(rule.getId()).orElseThrow().getNextExecutionDate()).isEqualTo(today.plusWeeks(1));
    }

    @Test
    void shouldUseCurrentPlusMonthsBehaviorAcrossLeapYearAndMonthEnd() {
        // Characterizes the supplied implementation: Jan 31 -> Feb 29 -> Mar 29.
        // It does NOT preserve an original "last day of month" anchor.
        LocalDate today = today();
        LocalDate next = LocalDate.of(2024, 1, 31);
        var rule = seedRecurring(ALICE, "10", next, Frequency.MONTHLY, true);
        List<LocalDate> expectedDates = new ArrayList<>();
        while (!next.isAfter(today)) {
            expectedDates.add(next);
            next = next.plusMonths(1);
        }
        recurringService.processDueTransactions();
        assertThat(ownedTransactions(ALICE)).extracting(t -> t.getDate())
                .containsExactlyInAnyOrderElementsOf(expectedDates)
                .contains(LocalDate.of(2024, 1, 31), LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 29));
        assertThat(recurring.findById(rule.getId()).orElseThrow().getNextExecutionDate()).isEqualTo(next);
    }

    @Test
    void shouldSkipInactiveAndFutureRules() {
        LocalDate today = today();
        var inactive = seedRecurring(ALICE, "10", today.minusDays(5), Frequency.DAILY, false);
        var future = seedRecurring(ALICE, "20", today.plusDays(1), Frequency.DAILY, true);
        recurringService.processDueTransactions();
        assertThat(transactions.count()).isZero();
        assertThat(recurring.findById(inactive.getId()).orElseThrow().getNextExecutionDate()).isEqualTo(today.minusDays(5));
        assertThat(recurring.findById(future.getId()).orElseThrow().getNextExecutionDate()).isEqualTo(today.plusDays(1));
    }

    @Test
    void shouldGenerateTransactionsForEachRulesOwner() {
        seedRecurring(ALICE, "10", today(), Frequency.DAILY, true);
        seedRecurring(BOB, "20", today(), Frequency.DAILY, true);
        recurringService.processDueTransactions();
        assertThat(ownedTransactions(ALICE)).hasSize(1);
        assertThat(ownedTransactions(BOB)).hasSize(1);
        assertThat(ownedTransactions(ALICE).get(0).getAmount()).isEqualByComparingTo("10");
        assertThat(ownedTransactions(BOB).get(0).getAmount()).isEqualByComparingTo("20");
    }
}
