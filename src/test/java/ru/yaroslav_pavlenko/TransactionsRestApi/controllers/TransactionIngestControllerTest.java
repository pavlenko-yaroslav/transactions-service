package ru.yaroslav_pavlenko.TransactionsRestApi.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.exceptions.ExchangeRateNotFoundException;
import ru.yaroslav_pavlenko.TransactionsRestApi.services.TransactionBatchService;
import ru.yaroslav_pavlenko.TransactionsRestApi.services.TransactionService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionIngestController.class)
class TransactionIngestControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TransactionService transactionService;
    @MockBean
    private TransactionBatchService batchService;

    @Test
    @DisplayName("An accepted operation is returned with limit_exceeded in snake_case")
    void acceptsTransaction() throws Exception {
        when(transactionService.register(any(TransactionRequest.class))).thenReturn(TransactionResponse.builder()
                .id(1L)
                .accountFrom(123L)
                .accountTo(9_999_999_999L)
                .currencyShortname("EUR")
                .sum(new BigDecimal("100.00"))
                .sumUsd(new BigDecimal("108.50"))
                .expenseCategory(ExpenseCategory.product)
                .datetime(OffsetDateTime.of(2022, 1, 30, 0, 0, 0, 0, ZoneOffset.ofHours(6)))
                .limitExceeded(true)
                .build());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.account_from").value(123))
                .andExpect(jsonPath("$.currency_shortname").value("EUR"))
                .andExpect(jsonPath("$.sum_usd").value(108.50))
                .andExpect(jsonPath("$.limit_exceeded").value(true));
    }

    @Test
    @DisplayName("A non-positive amount is rejected with an explanation")
    void rejectsNonPositiveSum() throws Exception {
        String request = body().replace("\"sum\": 100.00", "\"sum\": -1");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("sum")));
    }

    @Test
    @DisplayName("An account longer than ten digits is rejected")
    void rejectsTooLongAccount() throws Exception {
        String request = body().replace("\"account_from\": 123", "\"account_from\": 12345678901");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("An unknown expense category is rejected")
    void rejectsUnknownCategory() throws Exception {
        String request = body().replace("\"expense_category\": \"product\"", "\"expense_category\": \"food\"");

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("An unavailable exchange rate yields 422 rather than 500")
    void reportsMissingExchangeRate() throws Exception {
        when(transactionService.register(any(TransactionRequest.class)))
                .thenThrow(new ExchangeRateNotFoundException("EUR/USD", LocalDate.of(2022, 1, 30)));

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Exchange rate unavailable"));
    }

    @Test
    @DisplayName("Batch ingestion returns a result per operation")
    void acceptsBatch() throws Exception {
        when(batchService.registerAll(any())).thenReturn(List.of(
                TransactionResponse.builder().id(1L).limitExceeded(false).build(),
                TransactionResponse.builder().id(2L).limitExceeded(true).build()));

        mockMvc.perform(post("/api/v1/transactions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + body() + "," + body() + "]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].limit_exceeded").value(true));
    }

    private String body() {
        return """
                {
                  "account_from": 123,
                  "account_to": 9999999999,
                  "currency_shortname": "EUR",
                  "sum": 100.00,
                  "expense_category": "product",
                  "datetime": "2022-01-30T00:00:00+06:00"
                }""";
    }
}
