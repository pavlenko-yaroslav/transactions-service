package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionBatchServiceTest {

    private static final Long ACCOUNT = 123L;

    @Mock
    private TransactionService transactionService;
    @Mock
    private CurrencyConverter currencyConverter;

    private TransactionBatchService batchService() {
        return new TransactionBatchService(transactionService, currencyConverter,
                Executors.newFixedThreadPool(4));
    }

    @Test
    @DisplayName("A mixed-currency batch is converted and stored in chronological order")
    void processesMixedCurrenciesInChronologicalOrder() {
        TransactionRequest late = request("100.00", "EUR", 5);
        TransactionRequest early = request("200.00", "EUR", 3);
        TransactionRequest middle = request("300.00", "USD", 4);

        when(currencyConverter.toBaseCurrency(any(), any(String.class), any(OffsetDateTime.class)))
                .thenAnswer(invocation -> invocation.<BigDecimal>getArgument(0));
        when(transactionService.register(any(TransactionRequest.class), any(BigDecimal.class)))
                .thenAnswer(invocation -> TransactionResponse.builder()
                        .sum(invocation.<TransactionRequest>getArgument(0).sum())
                        .build());

        List<TransactionResponse> responses = batchService().registerAll(List.of(late, early, middle));

        ArgumentCaptor<TransactionRequest> registered = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(transactionService, org.mockito.Mockito.times(3))
                .register(registered.capture(), any(BigDecimal.class));

        assertThat(registered.getAllValues()).extracting(TransactionRequest::datetime)
                .isSorted();
        assertThat(responses).hasSize(3);
    }

    @Test
    @DisplayName("Every operation is converted in its own currency")
    void convertsEachTransactionInItsOwnCurrency() {
        when(currencyConverter.toBaseCurrency(any(), any(String.class), any(OffsetDateTime.class)))
                .thenAnswer(invocation -> "EUR".equals(invocation.getArgument(1))
                        ? new BigDecimal("108.50")
                        : new BigDecimal("50.00"));
        when(transactionService.register(any(TransactionRequest.class), any(BigDecimal.class)))
                .thenAnswer(invocation -> TransactionResponse.builder()
                        .sumUsd(invocation.getArgument(1))
                        .build());

        List<TransactionResponse> responses = batchService()
                .registerAll(List.of(request("100.00", "EUR", 1), request("50.00", "USD", 2)));

        assertThat(responses).extracting(TransactionResponse::sumUsd)
                .containsExactly(new BigDecimal("108.50"), new BigDecimal("50.00"));
    }

    private TransactionRequest request(String sum, String currency, int dayOfMonth) {
        return new TransactionRequest(ACCOUNT, 9_999_999_999L, currency, new BigDecimal(sum),
                ExpenseCategory.product,
                OffsetDateTime.of(2022, 1, dayOfMonth, 10, 0, 0, 0, ZoneOffset.UTC));
    }
}
