package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.Type.Frequency;
import com.example.finance.tracker.finance_tracker.dto.response.MonthlyAnalyticsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyticsCacheIntegrationTest extends IntegrationTestSupport {
    @Autowired private StringRedisTemplate redis;

    private Cache cache() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
        return Objects.requireNonNull(cacheManager.getCache("monthlyAnalytics"));
    }

    private String key(String username, int year, Month month) {
        return username + ":" + year + ":" + month;
    }

    private MonthlyAnalyticsResponse summary(String username) {
        return analyticsService.getMonthlyAnalytics(2024, Month.NOVEMBER, username);
    }

    @Test
    void shouldSerializeCacheAndReadMonthlyAnalyticsFromRealRedis() {
        seedExpense(ALICE, "10", DATE);
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("10");
        assertThat(cache().get(key(ALICE, 2024, Month.NOVEMBER), MonthlyAnalyticsResponse.class))
                .isNotNull();

        // Deliberately bypass the service's eviction to prove a genuine cache hit.
        seedExpense(ALICE, "5", DATE);
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("10");
        cache().evict(key(ALICE, 2024, Month.NOVEMBER));
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("15");
    }

    @Test
    void shouldApplyConfiguredTenMinuteTtl() {
        summary(ALICE);
        Long ttl = redis.getExpire("monthlyAnalytics::" + key(ALICE, 2024, Month.NOVEMBER), TimeUnit.SECONDS);
        assertThat(ttl).isNotNull().isBetween(1L, 600L);
    }

    @Test
    void shouldSeparateCacheEntriesByUserMonthAndYear() {
        seedExpense(ALICE, "10", DATE);
        seedExpense(BOB, "20", DATE);
        seedExpense(ALICE, "30", DATE.plusMonths(1));
        seedExpense(ALICE, "40", DATE.minusYears(1));
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("10");
        assertThat(summary(BOB).totalExpenses()).isEqualByComparingTo("20");
        assertThat(analyticsService.getMonthlyAnalytics(2024, Month.DECEMBER, ALICE).totalExpenses()).isEqualByComparingTo("30");
        assertThat(analyticsService.getMonthlyAnalytics(2023, Month.NOVEMBER, ALICE).totalExpenses()).isEqualByComparingTo("40");
        for (String cacheKey : java.util.List.of(key(ALICE, 2024, Month.NOVEMBER), key(BOB, 2024, Month.NOVEMBER),
                key(ALICE, 2024, Month.DECEMBER), key(ALICE, 2023, Month.NOVEMBER))) {
            assertThat(cache().get(cacheKey)).isNotNull();
        }
    }

    @Test
    void shouldInvalidateCachedTotalsAfterCreatingTransaction() throws Exception {
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("0");
        createTransaction(ALICE, "12.34", DATE, "FOOD", "EXPENSE");
        assertThat(cache().get(key(ALICE, 2024, Month.NOVEMBER))).isNull();
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("12.34");
    }

    @Test
    void shouldInvalidateCachedTotalsAfterUpdatingAmount() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        summary(ALICE);
        mvc.perform(patch("/transactions/{id}", saved.getId()).with(auth(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":25.50}"))
                .andExpect(status().isOk());
        assertThat(cache().get(key(ALICE, 2024, Month.NOVEMBER))).isNull();
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("25.50");
    }

    @Test
    void shouldInvalidateBothOldAndNewMonthWhenTransactionDateChanges() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        summary(ALICE);
        analyticsService.getMonthlyAnalytics(2024, Month.DECEMBER, ALICE);
        mvc.perform(patch("/transactions/{id}", saved.getId()).with(auth(ALICE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2024-12-15\"}"))
                .andExpect(status().isOk());
        assertThat(cache().get(key(ALICE, 2024, Month.NOVEMBER))).isNull();
        assertThat(cache().get(key(ALICE, 2024, Month.DECEMBER))).isNull();
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("0");
        assertThat(analyticsService.getMonthlyAnalytics(2024, Month.DECEMBER, ALICE).totalExpenses()).isEqualByComparingTo("10");
    }

    @Test
    void shouldInvalidateCachedTotalsAfterDeletingTransaction() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        summary(ALICE);
        mvc.perform(delete("/transactions/{id}", saved.getId()).with(auth(ALICE))).andExpect(status().isNoContent());
        assertThat(cache().get(key(ALICE, 2024, Month.NOVEMBER))).isNull();
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("0");
    }

    @Test
    void shouldInvalidateCachedTotalsAfterCsvImport() throws Exception {
        summary(ALICE);
        importCsv(ALICE, "description,amount,date,category,type\nGroceries,12.34,2024-11-15,FOOD,EXPENSE\n")
                .andExpect(status().isOk());
        assertThat(cache().get(key(ALICE, 2024, Month.NOVEMBER))).isNull();
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("12.34");
    }

    @Test
    void shouldKeepCachedTotalsAndDatabaseUnchangedAfterRejectedCsvImport() throws Exception {
        seedExpense(ALICE, "10", DATE);
        summary(ALICE);
        importCsv(ALICE, "description,amount,date,category,type\nValid,5,2024-11-15,FOOD,EXPENSE\nInvalid,-1,2024-11-15,FOOD,EXPENSE\n")
                .andExpect(status().isBadRequest());
        assertThat(cache().get(key(ALICE, 2024, Month.NOVEMBER))).isNotNull();
        assertThat(ownedTransactions(ALICE)).hasSize(1);
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("10");
    }

    @Test
    void shouldKeepCachedTotalsAfterUnauthorizedDeletionAttempt() throws Exception {
        var saved = seedExpense(ALICE, "10", DATE);
        summary(ALICE);
        mvc.perform(delete("/transactions/{id}", saved.getId()).with(auth(BOB))).andExpect(status().isNotFound());
        assertThat(cache().get(key(ALICE, 2024, Month.NOVEMBER))).isNotNull();
        assertThat(summary(ALICE).totalExpenses()).isEqualByComparingTo("10");
    }

    @Test
    void shouldInvalidateCachedTotalsAfterGeneratingRecurringTransactions() {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Vienna"));
        seedRecurring(ALICE, "12.34", today, Frequency.DAILY, true);
        assertThat(analyticsService.getMonthlyAnalytics(today.getYear(), today.getMonth(), ALICE).totalExpenses())
                .isEqualByComparingTo("0");
        recurringService.processDueTransactions();
        assertThat(cache().get(key(ALICE, today.getYear(), today.getMonth()))).isNull();
        assertThat(analyticsService.getMonthlyAnalytics(today.getYear(), today.getMonth(), ALICE).totalExpenses())
                .isEqualByComparingTo("12.34");
    }
}
