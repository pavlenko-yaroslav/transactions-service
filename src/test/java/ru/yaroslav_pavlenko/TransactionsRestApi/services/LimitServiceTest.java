package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yaroslav_pavlenko.TransactionsRestApi.config.LimitProperties;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.mappers.TransactionMapper;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Limit;
import ru.yaroslav_pavlenko.TransactionsRestApi.repositories.jpa.LimitRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LimitServiceTest {

    private static final Long ACCOUNT = 123L;
    private static final Instant NOW = Instant.parse("2022-01-10T08:00:00Z");

    @Mock
    private LimitRepository limitRepository;

    private LimitService limitService;

    @BeforeEach
    void setUp() {
        limitService = new LimitService(limitRepository, new TransactionMapper(),
                new LimitProperties(new BigDecimal("1000.00"), "USD"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("The service stamps the establishment date, the client cannot supply it")
    void setsCurrentDatetimeOnCreate() {
        when(limitRepository.save(any(Limit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LimitResponse response = limitService.create(
                new LimitRequest(ACCOUNT, new BigDecimal("2000.00"), ExpenseCategory.product));

        ArgumentCaptor<Limit> saved = ArgumentCaptor.forClass(Limit.class);
        verify(limitRepository).save(saved.capture());

        assertThat(saved.getValue().getLimitDatetime().toInstant()).isEqualTo(NOW);
        assertThat(response.limitDatetime().toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("The limit currency is always USD")
    void alwaysStoresLimitInUsd() {
        when(limitRepository.save(any(Limit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LimitResponse response = limitService.create(
                new LimitRequest(ACCOUNT, new BigDecimal("500.00"), ExpenseCategory.service));

        assertThat(response.limitCurrencyShortname()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Setting a new limit appends a record instead of updating one")
    void createsNewRecordInsteadOfUpdating() {
        when(limitRepository.save(any(Limit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        limitService.create(new LimitRequest(ACCOUNT, new BigDecimal("2000.00"), ExpenseCategory.product));

        ArgumentCaptor<Limit> saved = ArgumentCaptor.forClass(Limit.class);
        verify(limitRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isNull();
    }

    @Test
    @DisplayName("Without an established limit, 1000 USD applies from the start of the month")
    void fallsBackToDefaultLimit() {
        OffsetDateTime at = OffsetDateTime.of(2022, 1, 20, 12, 0, 0, 0, ZoneOffset.ofHours(6));
        when(limitRepository
                .findFirstByAccountNumberAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDescIdDesc(
                        ACCOUNT, ExpenseCategory.product, at))
                .thenReturn(Optional.empty());

        Limit limit = limitService.effectiveLimit(ACCOUNT, ExpenseCategory.product, at);

        assertThat(limit.getId()).isNull();
        assertThat(limit.getLimitSum()).isEqualByComparingTo("1000.00");
        assertThat(limit.getLimitCurrencyShortname()).isEqualTo("USD");
        assertThat(limit.getLimitDatetime())
                .isEqualTo(OffsetDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(6)));
    }

    @Test
    @DisplayName("The limit in force is the latest one established no later than the operation")
    void usesLatestLimitEffectiveAtTransactionTime() {
        OffsetDateTime at = OffsetDateTime.of(2022, 1, 20, 12, 0, 0, 0, ZoneOffset.ofHours(6));
        Limit stored = Limit.builder()
                .id(5L)
                .accountNumber(ACCOUNT)
                .limitSum(new BigDecimal("2000.00"))
                .limitCurrencyShortname("USD")
                .expenseCategory(ExpenseCategory.product)
                .limitDatetime(at.minusDays(10))
                .build();
        when(limitRepository
                .findFirstByAccountNumberAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDescIdDesc(
                        ACCOUNT, ExpenseCategory.product, at))
                .thenReturn(Optional.of(stored));

        assertThat(limitService.effectiveLimit(ACCOUNT, ExpenseCategory.product, at)).isSameAs(stored);
    }
}
