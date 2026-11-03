package com.example.finance.tracker.finance_tracker;

import com.example.finance.tracker.finance_tracker.dto.RegisterRequest;
import com.example.finance.tracker.finance_tracker.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserIntegrationTest extends IntegrationTestSupport {
    @Test
    void shouldRegisterWithoutAuthenticationAndStoreEncodedPassword() throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "new_user", "password", PASSWORD))))
                .andExpect(status().isCreated());

        UserEntity stored = users.findByUsername("new_user").orElseThrow();
        assertThat(stored.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, stored.getPasswordHash())).isTrue();
        mvc.perform(get("/whoami").with(httpBasic("new_user", PASSWORD)))
                .andExpect(status().isOk()).andExpect(content().string("new_user"));
    }

    @Test
    void shouldRejectDuplicateUsernameInServiceWithoutChangingOriginalPassword() {
        String originalHash = users.findByUsername(ALICE).orElseThrow().getPasswordHash();
        RegisterRequest request = new RegisterRequest(ALICE, "Different-password-123!");
        assertThatThrownBy(() -> userService.registerUser(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Username already registered");
        assertThat(users.count()).isEqualTo(2);
        assertThat(users.findByUsername(ALICE).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
    }

    @Test
    @Tag("contract-gap")
    void shouldReturnConflictForDuplicateRegistration() throws Exception {
        // Desired HTTP contract. AuthController currently has no local handler for
        // UserService's IllegalArgumentException; see README if this test fails.
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", ALICE, "password", PASSWORD))))
                .andExpect(status().isConflict());
        assertThat(users.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"username\":\"\",\"password\":\"Long-password-123!\"}",
            "{\"username\":\"   \",\"password\":\"Long-password-123!\"}",
            "{\"username\":\"new_user\",\"password\":\"\"}",
            "{\"username\":\"new_user\",\"password\":\"   \"}",
            "{}"
    })
    void shouldRejectInvalidRegistration(String body) throws Exception {
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void shouldAuthenticateUsingDatabaseUser() throws Exception {
        mvc.perform(get("/whoami").with(auth(ALICE)))
                .andExpect(status().isOk()).andExpect(content().string(ALICE));
    }

    @Test
    void shouldRejectIncorrectPassword() throws Exception {
        mvc.perform(get("/transactions").with(httpBasic(ALICE, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectUnknownUser() throws Exception {
        mvc.perform(get("/transactions").with(auth("does_not_exist")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotRememberBasicAuthenticationOnTheNextRequest() throws Exception {
        mvc.perform(get("/whoami").with(auth(ALICE))).andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
        mvc.perform(get("/whoami")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLoadUserDetailsAndRejectUnknownUsername() {
        var details = userDetailsService.loadUserByUsername(ALICE);
        assertThat(details.getUsername()).isEqualTo(ALICE);
        assertThat(passwordEncoder.matches(PASSWORD, details.getPassword())).isTrue();
        assertThat(details.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("does_not_exist"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/transactions", "/budget?year=2024&month=NOVEMBER",
            "/budget/progress?year=2024&month=NOVEMBER", "/recurring-transactions",
            "/analytics/monthly?year=2024&month=NOVEMBER",
            "/analytics/categories?year=2024&month=NOVEMBER", "/categories", "/transactions/export"})
    void shouldProtectReadEndpoints(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/transactions", "/budget", "/recurring-transactions"})
    void shouldProtectCreationEndpoints(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        assertThat(transactions.count()).isZero();
        assertThat(budgets.count()).isZero();
        assertThat(recurring.count()).isZero();
    }

    @Test
    void shouldExposeHealthWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
