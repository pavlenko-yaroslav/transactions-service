package ru.yaroslav_pavlenko.TransactionsRestApi.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.ExceededTransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.services.LimitService;
import ru.yaroslav_pavlenko.TransactionsRestApi.services.TransactionService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
class ClientControllerTest {

    private static final OffsetDateTime JANUARY_10TH =
            OffsetDateTime.of(2022, 1, 10, 0, 0, 0, 0, ZoneOffset.ofHours(6));

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LimitService limitService;
    @MockBean
    private TransactionService transactionService;

    @Test
    @DisplayName("A new limit is created and the service stamps its date")
    void createsLimit() throws Exception {
        when(limitService.create(any(LimitRequest.class))).thenReturn(LimitResponse.builder()
                .id(1L)
                .accountNumber(123L)
                .limitSum(new BigDecimal("1000.00"))
                .limitCurrencyShortname("USD")
                .expenseCategory(ExpenseCategory.product)
                .limitDatetime(JANUARY_10TH)
                .build());

        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account_number": 123, "limit_sum": 1000.00, "expense_category": "product"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limit_sum").value(1000.00))
                .andExpect(jsonPath("$.limit_currency_shortname").value("USD"))
                .andExpect(jsonPath("$.limit_datetime").value("2022-01-10T00:00:00+06:00"));
    }

    @Test
    @DisplayName("An establishment date supplied by the client is ignored")
    void ignoresClientSuppliedDatetime() throws Exception {
        when(limitService.create(any(LimitRequest.class))).thenReturn(LimitResponse.builder()
                .id(1L)
                .accountNumber(123L)
                .limitSum(new BigDecimal("1000.00"))
                .limitCurrencyShortname("USD")
                .expenseCategory(ExpenseCategory.product)
                .limitDatetime(JANUARY_10TH)
                .build());

        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account_number": 123, "limit_sum": 1000.00, "expense_category": "product",
                                 "limit_datetime": "2030-01-01T00:00:00+06:00"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limit_datetime").value("2022-01-10T00:00:00+06:00"));
    }

    @Test
    @DisplayName("A zero limit is rejected")
    void rejectsNonPositiveLimit() throws Exception {
        mockMvc.perform(post("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account_number": 123, "limit_sum": 0, "expense_category": "product"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("limit_sum")));
    }

    @Test
    @DisplayName("Updating an existing limit is not supported")
    void doesNotExposeLimitUpdate() throws Exception {
        mockMvc.perform(put("/api/v1/limits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"limit_sum": 5000.00}"""))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("The exceedance list carries the three parameters of the exceeded limit")
    void returnsExceededTransactions() throws Exception {
        when(transactionService.findExceeded(eq(123L), isNull())).thenReturn(List.of(
                ExceededTransactionResponse.builder()
                        .id(1L)
                        .accountFrom(123L)
                        .accountTo(9_999_999_999L)
                        .currencyShortname("EUR")
                        .sum(new BigDecimal("1000.00"))
                        .sumUsd(new BigDecimal("1050.00"))
                        .expenseCategory(ExpenseCategory.product)
                        .datetime(JANUARY_10TH)
                        .limitSum(new BigDecimal("1000.00"))
                        .limitDatetime(JANUARY_10TH)
                        .limitCurrencyShortname("USD")
                        .build()));

        mockMvc.perform(get("/api/v1/transactions/exceeded").param("accountNumber", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].limit_sum").value(1000.00))
                .andExpect(jsonPath("$[0].limit_datetime").value("2022-01-10T00:00:00+06:00"))
                .andExpect(jsonPath("$[0].limit_currency_shortname").value("USD"));
    }

    @Test
    @DisplayName("The exceedance list is filtered by expense category")
    void filtersExceededByCategory() throws Exception {
        when(transactionService.findExceeded(123L, ExpenseCategory.service)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/transactions/exceeded")
                        .param("accountNumber", "123")
                        .param("expenseCategory", "service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("A request without an account number is rejected")
    void requiresAccountNumber() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/exceeded"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("The client limit list is serialised in snake_case")
    void returnsLimits() throws Exception {
        when(limitService.findAll(eq(123L), isNull())).thenReturn(List.of(LimitResponse.builder()
                .id(1L)
                .accountNumber(123L)
                .limitSum(new BigDecimal("1000.00"))
                .limitCurrencyShortname("USD")
                .expenseCategory(ExpenseCategory.product)
                .limitDatetime(JANUARY_10TH)
                .build()));

        mockMvc.perform(get("/api/v1/limits").param("accountNumber", "123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].account_number").value(123))
                .andExpect(jsonPath("$[0].expense_category").value("product"));
    }
}
