package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import com.example.finance.tracker.finance_tracker.dto.response.CategoryAnalyticsResponse;
import com.example.finance.tracker.finance_tracker.dto.response.MonthlyAnalyticsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.json.JsonCompareMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AnalyticsIntegrationTest extends IntegrationTestSupport {
    @Test
    void shouldCalculateMonthlyTotalsAndBalance() throws Exception {
        seedTransaction(ALICE, "Salary", "2000.00", DATE, "SALARY", TransactionType.INCOME);
        seedTransaction(ALICE, "Gift", "100.25", DATE, "GIFTS", TransactionType.INCOME);
        seedExpense(ALICE, "40.10", DATE);
        seedExpense(ALICE, "10.20", DATE);
        expectMonthly(ALICE, 2024, Month.NOVEMBER, "2100.25", "50.30", "2049.95");
    }

    @Test
    void shouldReturnZerosWhenNoTransactionsExist() throws Exception {
        expectMonthly(ALICE, 2024, Month.NOVEMBER, "0", "0", "0");
    }

    @Test
    void shouldHandleExpenseOnlyMonthAndNegativeBalance() throws Exception {
        seedExpense(ALICE, "12.34", DATE);
        expectMonthly(ALICE, 2024, Month.NOVEMBER, "0", "12.34", "-12.34");
    }

    @Test
    void shouldHandleIncomeOnlyMonth() throws Exception {
        seedTransaction(ALICE, "Salary", "100.01", DATE, "SALARY", TransactionType.INCOME);
        expectMonthly(ALICE, 2024, Month.NOVEMBER, "100.01", "0", "100.01");
    }

    @Test
    void shouldExcludeOtherUsersFromTotalsIncludingCachedResponses() throws Exception {
        seedExpense(ALICE, "10", DATE);
        seedExpense(BOB, "999", DATE);
        expectMonthly(ALICE, 2024, Month.NOVEMBER, "0", "10", "-10");
        expectMonthly(BOB, 2024, Month.NOVEMBER, "0", "999", "-999");
        expectMonthly(ALICE, 2024, Month.NOVEMBER, "0", "10", "-10");
    }

    @Test
    void shouldIncludeFirstAndLastDayButExcludeAdjacentMonthsAndOtherYears() throws Exception {
        seedExpense(ALICE, "10", LocalDate.of(2024, 11, 1));
        seedExpense(ALICE, "20", LocalDate.of(2024, 11, 30));
        seedExpense(ALICE, "999", LocalDate.of(2024, 10, 31));
        seedExpense(ALICE, "999", LocalDate.of(2024, 12, 1));
        seedExpense(ALICE, "999", LocalDate.of(2023, 11, 15));
        expectMonthly(ALICE, 2024, Month.NOVEMBER, "0", "30", "-30");
    }

    @Test
    void shouldHandleDecemberToJanuaryBoundary() throws Exception {
        seedExpense(ALICE, "12", LocalDate.of(2024, 12, 31));
        seedExpense(ALICE, "99", LocalDate.of(2025, 1, 1));
        expectMonthly(ALICE, 2024, Month.DECEMBER, "0", "12", "-12");
        expectMonthly(ALICE, 2025, Month.JANUARY, "0", "99", "-99");
    }

    @Test
    void shouldIncludeLeapDayInFebruary() throws Exception {
        seedExpense(ALICE, "29", LocalDate.of(2024, 2, 29));
        seedExpense(ALICE, "999", LocalDate.of(2024, 3, 1));
        expectMonthly(ALICE, 2024, Month.FEBRUARY, "0", "29", "-29");
    }

    @Test
    void shouldAggregateExpensesByCategoryAndExcludeIncomeOtherUsersAndOtherMonths() throws Exception {
        seedExpense(ALICE, "10.10", DATE);
        seedExpense(ALICE, "20.20", DATE.minusDays(1));
        seedTransaction(ALICE, "Bus", "5.50", DATE, "TRANSPORT", TransactionType.EXPENSE);
        seedTransaction(ALICE, "Food reimbursement", "999", DATE, "FOOD", TransactionType.INCOME);
        seedExpense(ALICE, "999", DATE.minusMonths(1));
        seedExpense(ALICE, "999", LocalDate.of(2024, 12, 1));
        seedExpense(BOB, "999", DATE);
        var expected = List.of(new CategoryAnalyticsResponse("FOOD", new BigDecimal("30.30")),
                new CategoryAnalyticsResponse("TRANSPORT", new BigDecimal("5.50")));
        mvc.perform(get("/analytics/categories").with(auth(ALICE)).param("year", "2024").param("month", "NOVEMBER"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(content().json(json(expected), JsonCompareMode.LENIENT));
    }

    @Test
    void shouldReturnEmptyCategoryListWhenThereAreNoExpenses() throws Exception {
        seedTransaction(ALICE, "Salary", "100", DATE, "SALARY", TransactionType.INCOME);
        seedExpense(BOB, "999", DATE);
        mvc.perform(get("/analytics/categories").with(auth(ALICE)).param("year", "2024").param("month", "NOVEMBER"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void shouldRejectMissingOrMalformedParameters() throws Exception {
        for (String path : List.of("/analytics/monthly", "/analytics/categories")) {
            mvc.perform(get(path).with(auth(ALICE))).andExpect(status().isBadRequest());
            mvc.perform(get(path).with(auth(ALICE)).param("year", "2024").param("month", "13"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get(path).with(auth(ALICE)).param("year", "invalid").param("month", "NOVEMBER"))
                    .andExpect(status().isBadRequest());
        }
    }

    private void expectMonthly(String username, int year, Month month,
            String income, String expenses, String balance) throws Exception {
        var expected = new MonthlyAnalyticsResponse(year, month, new BigDecimal(income),
                new BigDecimal(expenses), new BigDecimal(balance));
        monthly(username, year, month).andExpect(status().isOk())
                .andExpect(content().json(json(expected), JsonCompareMode.STRICT));
    }
}
