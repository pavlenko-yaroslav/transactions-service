package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.ExceededTransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.mappers.TransactionMapper;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Limit;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Transaction;
import ru.yaroslav_pavlenko.TransactionsRestApi.repositories.jpa.TransactionRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    private static final Long ACCOUNT = 123L;
    private static final OffsetDateTime JANUARY_2ND =
            OffsetDateTime.of(2022, 1, 2, 10, 0, 0, 0, ZoneOffset.ofHours(6));

    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private LimitService limitService;
    @Mock
    private CurrencyConverter currencyConverter;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(transactionRepository, limitService, currencyConverter,
                new TransactionMapper());
    }

    @Test
    @DisplayName("An operation within the limit is stored without the flag")
    void savesTransactionWithinLimit() {
        givenRate("100.00", "EUR", "108.50");
        givenLimit("1000.00");
        givenSpentThisMonth("0.00");
        givenRepositoryEchoesSavedEntity();

        TransactionResponse response = transactionService.register(request("100.00", "EUR"));

        assertThat(response.limitExceeded()).isFalse();
        assertThat(response.sumUsd()).isEqualByComparingTo("108.50");
    }

    @Test
    @DisplayName("An operation pushing the monthly spend past the limit is flagged")
    void marksTransactionExceedingLimit() {
        givenRate("600.00", "USD", "600.00");
        givenLimit("1000.00");
        givenSpentThisMonth("500.00");
        givenRepositoryEchoesSavedEntity();

        TransactionResponse response = transactionService.register(request("600.00", "USD"));

        assertThat(response.limitExceeded()).isTrue();
    }

    @Test
    @DisplayName("The spend window is the calendar month of the operation")
    void aggregatesSpendOverCalendarMonthOfTransaction() {
        givenRate("100.00", "USD", "100.00");
        givenLimit("1000.00");
        givenSpentThisMonth("0.00");
        givenRepositoryEchoesSavedEntity();

        transactionService.register(request("100.00", "USD"));

        ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
        org.mockito.Mockito.verify(transactionRepository)
                .sumSpentUsd(eq(ACCOUNT), eq(ExpenseCategory.product), from.capture(), to.capture());

        assertThat(from.getValue())
                .isEqualTo(OffsetDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(6)));
        assertThat(to.getValue())
                .isEqualTo(OffsetDateTime.of(2022, 2, 1, 0, 0, 0, 0, ZoneOffset.ofHours(6)));
    }

    @Test
    @DisplayName("The default limit is not persisted and leaves an empty reference")
    void doesNotLinkUnsavedDefaultLimit() {
        givenRate("100.00", "USD", "100.00");
        givenLimit("1000.00"); // a limit without an id is the default one
        givenSpentThisMonth("0.00");
        givenRepositoryEchoesSavedEntity();

        transactionService.register(request("100.00", "USD"));

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        org.mockito.Mockito.verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getAppliedLimit()).isNull();
    }

    @Test
    @DisplayName("An established limit is linked to the transaction")
    void linksStoredLimit() {
        givenRate("100.00", "USD", "100.00");
        Limit stored = limit("1000.00");
        stored.setId(7L);
        when(limitService.effectiveLimit(eq(ACCOUNT), eq(ExpenseCategory.product), any())).thenReturn(stored);
        givenSpentThisMonth("0.00");
        givenRepositoryEchoesSavedEntity();

        transactionService.register(request("100.00", "USD"));

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        org.mockito.Mockito.verify(transactionRepository).save(saved.capture());
        assertThat(saved.getValue().getAppliedLimit()).isSameAs(stored);
    }

    @Test
    @DisplayName("The exceedance list carries the parameters of the exceeded limit")
    void returnsExceededTransactionsWithLimitDetails() {
        Limit applied = limit("1000.00");
        applied.setId(7L);
        Transaction transaction = Transaction.builder()
                .id(1L)
                .accountFrom(ACCOUNT)
                .accountTo(9_999_999_999L)
                .currencyShortname("EUR")
                .sum(new BigDecimal("1000.00"))
                .sumUsd(new BigDecimal("1050.00"))
                .expenseCategory(ExpenseCategory.product)
                .datetime(JANUARY_2ND)
                .limitExceeded(true)
                .appliedLimit(applied)
                .build();
        when(transactionRepository.findExceededByAccount(ACCOUNT)).thenReturn(List.of(transaction));

        List<ExceededTransactionResponse> exceeded = transactionService.findExceeded(ACCOUNT, null);

        assertThat(exceeded).hasSize(1);
        assertThat(exceeded.get(0).limitSum()).isEqualByComparingTo("1000.00");
        assertThat(exceeded.get(0).limitCurrencyShortname()).isEqualTo("USD");
        assertThat(exceeded.get(0).limitDatetime()).isEqualTo(applied.getLimitDatetime());
    }

    @Test
    @DisplayName("For a default-limit transaction the parameters come from configuration")
    void fallsBackToDefaultLimitInExceededList() {
        Transaction transaction = Transaction.builder()
                .id(1L)
                .accountFrom(ACCOUNT)
                .accountTo(9_999_999_999L)
                .currencyShortname("USD")
                .sum(new BigDecimal("1100.00"))
                .sumUsd(new BigDecimal("1100.00"))
                .expenseCategory(ExpenseCategory.service)
                .datetime(JANUARY_2ND)
                .limitExceeded(true)
                .build();
        when(transactionRepository.findExceededByAccountAndCategory(ACCOUNT, ExpenseCategory.service))
                .thenReturn(List.of(transaction));
        when(limitService.defaultLimit(ACCOUNT, ExpenseCategory.service, JANUARY_2ND))
                .thenReturn(limit("1000.00"));

        List<ExceededTransactionResponse> exceeded =
                transactionService.findExceeded(ACCOUNT, ExpenseCategory.service);

        assertThat(exceeded).singleElement()
                .satisfies(response -> {
                    assertThat(response.limitSum()).isEqualByComparingTo("1000.00");
                    assertThat(response.limitCurrencyShortname()).isEqualTo("USD");
                });
    }

    private TransactionRequest request(String sum, String currency) {
        return new TransactionRequest(ACCOUNT, 9_999_999_999L, currency, new BigDecimal(sum),
                ExpenseCategory.product, JANUARY_2ND);
    }

    private Limit limit(String sum) {
        return Limit.builder()
                .accountNumber(ACCOUNT)
                .limitSum(new BigDecimal(sum))
                .limitCurrencyShortname("USD")
                .expenseCategory(ExpenseCategory.product)
                .limitDatetime(OffsetDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(6)))
                .build();
    }

    private void givenRate(String sum, String currency, String usd) {
        when(currencyConverter.toBaseCurrency(new BigDecimal(sum), currency, JANUARY_2ND))
                .thenReturn(new BigDecimal(usd));
    }

    private void givenLimit(String sum) {
        when(limitService.effectiveLimit(eq(ACCOUNT), eq(ExpenseCategory.product), any()))
                .thenReturn(limit(sum));
    }

    private void givenSpentThisMonth(String spent) {
        when(transactionRepository.sumSpentUsd(eq(ACCOUNT), eq(ExpenseCategory.product), any(), any()))
                .thenReturn(new BigDecimal(spent));
    }

    private void givenRepositoryEchoesSavedEntity() {
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }
}
