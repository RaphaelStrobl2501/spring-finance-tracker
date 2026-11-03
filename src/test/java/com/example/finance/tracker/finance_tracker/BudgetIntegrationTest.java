package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.dto.response.BudgetProgressResponse;
import com.example.finance.tracker.finance_tracker.dto.response.BudgetResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BudgetIntegrationTest extends IntegrationTestSupport {
    @Test
    void shouldCreateAndReadBudgetForAuthenticatedUser() throws Exception {
        int id = createBudget(ALICE, "FOOD", "200.00", 2024, Month.NOVEMBER);
        assertThat(budgets.findById(id)).isPresent();
        mvc.perform(get("/budget").with(auth(ALICE)).param("year", "2024").param("month", "NOVEMBER"))
                .andExpect(status().isOk()).andExpect(content().json(json(List.of(new BudgetResponse(
                        id, "FOOD", "Monthly budget", new BigDecimal("200.00"), 2024, Month.NOVEMBER))), JsonCompareMode.STRICT));
        mvc.perform(get("/budget").with(auth(BOB)).param("year", "2024").param("month", "NOVEMBER"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void shouldRejectDuplicateCategoryBudgetForSameUserMonthAndYear() throws Exception {
        createBudget(ALICE, "FOOD", "200", 2024, Month.NOVEMBER);
        mvc.perform(post("/budget").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(budgetBody("FOOD", "300", 2024, Month.NOVEMBER))))
                .andExpect(status().isConflict());
        assertThat(budgets.count()).isEqualTo(1);
    }

    @Test
    void shouldAllowSameCategoryForDifferentUsersMonthsAndYears() throws Exception {
        createBudget(ALICE, "FOOD", "100", 2024, Month.NOVEMBER);
        createBudget(BOB, "FOOD", "200", 2024, Month.NOVEMBER);
        createBudget(ALICE, "FOOD", "300", 2024, Month.DECEMBER);
        createBudget(ALICE, "FOOD", "400", 2025, Month.NOVEMBER);
        createBudget(ALICE, "TRANSPORT", "50", 2024, Month.NOVEMBER);
        assertThat(budgets.count()).isEqualTo(5);
        mvc.perform(get("/budget").with(auth(ALICE)).param("year", "2024").param("month", "NOVEMBER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldCalculateProgressUsingOnlyOwnedCategoryExpensesWithinMonth() throws Exception {
        createBudget(ALICE, "FOOD", "200", 2024, Month.NOVEMBER);
        seedExpense(ALICE, "25", LocalDate.of(2024, 11, 1));
        seedExpense(ALICE, "50", LocalDate.of(2024, 11, 30));
        seedExpense(ALICE, "999", LocalDate.of(2024, 10, 31));
        seedExpense(ALICE, "999", LocalDate.of(2024, 12, 1));
        seedExpense(ALICE, "999", LocalDate.of(2023, 11, 15));
        seedExpense(BOB, "999", DATE);
        seedTransaction(ALICE, "Income in same category", "999", DATE, "FOOD", TransactionType.INCOME);
        seedTransaction(ALICE, "Different category", "999", DATE, "TRANSPORT", TransactionType.EXPENSE);
        expectProgress("FOOD", "200", "75", "125", "37.50");
    }

    @Test
    void shouldReturnZeroSpentWhenBudgetHasNoExpenses() throws Exception {
        createBudget(ALICE, "FOOD", "100", 2024, Month.NOVEMBER);
        expectProgress("FOOD", "100", "0", "100", "0");
    }

    @Test
    void shouldReportOverspendingWithNegativeRemainingAndPercentageOver100() throws Exception {
        createBudget(ALICE, "FOOD", "100", 2024, Month.NOVEMBER);
        seedExpense(ALICE, "125", DATE);
        expectProgress("FOOD", "100", "125", "-25", "125");
    }

    @Test
    void shouldRoundPercentageHalfUpToTwoDecimalPlaces() throws Exception {
        createBudget(ALICE, "FOOD", "32", 2024, Month.NOVEMBER);
        seedExpense(ALICE, "1", DATE);
        expectProgress("FOOD", "32", "1", "31", "3.13");
    }

    @Test
    void shouldReturnNoProgressForOtherUsersBudgetOrAnotherMonth() throws Exception {
        createBudget(BOB, "FOOD", "100", 2024, Month.NOVEMBER);
        createBudget(ALICE, "FOOD", "100", 2024, Month.DECEMBER);
        mvc.perform(get("/budget/progress").with(auth(ALICE)).param("year", "2024").param("month", "NOVEMBER"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void shouldRejectNonPositiveBudgetToPreventInvalidProgressCalculation(String amount) throws Exception {
        mvc.perform(post("/budget").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(budgetBody("FOOD", amount, 2024, Month.NOVEMBER))))
                .andExpect(status().isBadRequest());
        assertThat(budgets.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"category", "amount", "year", "month"})
    void shouldRejectMissingRequiredBudgetFields(String missing) throws Exception {
        var body = new HashMap<>(budgetBody("FOOD", "100", 2024, Month.NOVEMBER));
        body.remove(missing);
        mvc.perform(post("/budget").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());
        assertThat(budgets.count()).isZero();
    }

    @Test
    void shouldRequireValidYearAndMonthParameters() throws Exception {
        mvc.perform(get("/budget").with(auth(ALICE))).andExpect(status().isBadRequest());
        mvc.perform(get("/budget/progress").with(auth(ALICE)).param("year", "2024").param("month", "INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectBudgetYearBefore2000() throws Exception {
        mvc.perform(post("/budget").with(auth(ALICE)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(budgetBody("FOOD", "100", 1999, Month.NOVEMBER))))
                .andExpect(status().isBadRequest());
        assertThat(budgets.count()).isZero();
    }

    private void expectProgress(String category, String limit, String spent, String remaining, String percentage) throws Exception {
        var expected = new BudgetProgressResponse(category, new BigDecimal(limit), new BigDecimal(spent),
                new BigDecimal(remaining), new BigDecimal(percentage));
        mvc.perform(get("/budget/progress").with(auth(ALICE)).param("year", "2024").param("month", "NOVEMBER"))
                .andExpect(status().isOk()).andExpect(content().json(json(List.of(expected)), JsonCompareMode.STRICT));
    }
}
