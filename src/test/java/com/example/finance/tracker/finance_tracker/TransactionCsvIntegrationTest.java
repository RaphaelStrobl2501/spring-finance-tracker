package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.Type.TransactionType;
import org.apache.commons.csv.CSVFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TransactionCsvIntegrationTest extends IntegrationTestSupport {
    private static final String HEADER = "description,amount,date,category,type\n";

    @Test
    void shouldImportIncomeAndExpensesForAuthenticatedUser() throws Exception {
        importCsv(ALICE, HEADER + "Groceries,12.34,2024-11-15,FOOD,EXPENSE\n"
                + "Salary,2000.00,2024-11-01,SALARY,INCOME\n").andExpect(status().isOk());
        var owned = ownedTransactions(ALICE);
        assertThat(owned).hasSize(2);
        assertThat(owned).extracting(t -> t.getDescription()).containsExactlyInAnyOrder("Groceries", "Salary");
        var income = owned.stream().filter(t -> t.getTransactionType() == TransactionType.INCOME).findFirst().orElseThrow();
        assertThat(income.getAmount()).isEqualByComparingTo("2000.00");
        assertThat(ownedTransactions(BOB)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Invalid,-1,2024-11-15,FOOD,EXPENSE",
            "Invalid,0,2024-11-15,FOOD,EXPENSE",
            "Invalid,not-a-number,2024-11-15,FOOD,EXPENSE",
            "Invalid,10,not-a-date,FOOD,EXPENSE",
            "Invalid,10,2024-11-15,UNKNOWN,EXPENSE",
            "Invalid,10,2024-11-15,FOOD,UNKNOWN",
            "   ,10,2024-11-15,FOOD,EXPENSE"
    })
    void shouldRejectInvalidRowWithoutImportingEarlierValidRows(String invalidRow) throws Exception {
        var existing = seedExpense(ALICE, "7.50", DATE);
        importCsv(ALICE, HEADER + "Valid,10,2024-11-15,FOOD,EXPENSE\n" + invalidRow + "\n")
                .andExpect(status().isBadRequest());
        assertThat(ownedTransactions(ALICE)).extracting(t -> t.getId()).containsExactly(existing.getId());
        assertThat(transactions.findById(existing.getId()).orElseThrow().getAmount()).isEqualByComparingTo("7.50");
    }

    @Test
    void shouldRejectFutureDatedImport() throws Exception {
        importCsv(ALICE, HEADER + "Future,10," + java.time.LocalDate.now().plusYears(1) + ",FOOD,EXPENSE\n")
                .andExpect(status().isBadRequest());
        assertThat(transactions.count()).isZero();
    }

    @Test
    void shouldRejectEmptyFileAndMissingColumns() throws Exception {
        importCsv(ALICE, "").andExpect(status().isBadRequest());
        importCsv(ALICE, "description,amount\nTest,10\n").andExpect(status().isBadRequest());
        assertThat(transactions.count()).isZero();
    }

    @Test
    void shouldAcceptHeaderOnlyFileWithoutCreatingTransactions() throws Exception {
        importCsv(ALICE, HEADER).andExpect(status().isOk());
        assertThat(transactions.count()).isZero();
    }

    @Test
    void shouldExportOnlyOwnedTransactionsInDescendingDateOrder() throws Exception {
        var earlier = seedExpense(ALICE, "10", DATE.minusDays(1));
        var later = seedExpense(ALICE, "20", DATE);
        seedTransaction(BOB, "Private BOB data", "999", DATE, "FOOD", TransactionType.EXPENSE);
        String csv = mvc.perform(get("/transactions/export").with(auth(ALICE)))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"transactions.csv\""))
                .andReturn().getResponse().getContentAsString();
        try (var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader(csv))) {
            assertThat(parser.getHeaderNames()).containsExactly("id", "description", "amount", "category", "type", "date");
            var records = parser.getRecords();
            assertThat(records).hasSize(2);
            assertThat(records.get(0).get("id")).isEqualTo(String.valueOf(later.getId()));
            assertThat(records.get(1).get("id")).isEqualTo(String.valueOf(earlier.getId()));
        }
        assertThat(csv).doesNotContain("Private BOB data");
    }

    @Test
    void shouldRoundTripQuotedCommasQuotesAndNewlines() throws Exception {
        String description = "Food, \"special\"\nsecond line";
        seedTransaction(ALICE, description, "12.34", DATE, "FOOD", TransactionType.EXPENSE);
        String csv = mvc.perform(get("/transactions/export").with(auth(ALICE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        importCsv(BOB, csv).andExpect(status().isOk());
        var imported = ownedTransactions(BOB);
        assertThat(imported).hasSize(1);
        assertThat(imported.get(0).getDescription()).isEqualTo(description);
        assertThat(imported.get(0).getAmount()).isEqualByComparingTo("12.34");
        assertThat(imported.get(0).getDate()).isEqualTo(DATE);
        assertThat(imported.get(0).getTransactionType()).isEqualTo(TransactionType.EXPENSE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1+1", "-1+1", "@SUM(A1:A2)", "\tformula", "\rformula", "\nformula"})
    void shouldPrefixFormulaLikeDescriptionsOnExport(String description) throws Exception {
        seedTransaction(ALICE, description, "10", DATE, "FOOD", TransactionType.EXPENSE);
        String csv = mvc.perform(get("/transactions/export").with(auth(ALICE)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        try (var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(new StringReader(csv))) {
            assertThat(parser.getRecords().get(0).get("description")).isEqualTo("'" + description);
        }
    }
}
