package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import ru.yaroslav_pavlenko.TransactionsRestApi.config.LimitProperties;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.ExceededTransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.mappers.TransactionMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end coverage of the limit logic against a real database: transactions travel the
 * whole ingestion path, and both the limit_exceeded flags and the contents of the exceedance
 * list are asserted.
 * <p>
 * Exchange rates are stubbed out — they belong to the external provider and are covered
 * separately.
 */
@DataJpaTest
@Import({TransactionService.class, LimitService.class, TransactionMapper.class,
        LimitScenarioIntegrationTest.TestClockConfig.class})
class LimitScenarioIntegrationTest {

    private static final Long ACCOUNT = 123L;

    @Autowired
    private TransactionService transactionService;
    @Autowired
    private LimitService limitService;

    @MockBean
    private CurrencyConverter currencyConverter;

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        LimitProperties limitProperties() {
            return new LimitProperties(new BigDecimal("1000.00"), "USD");
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2022-01-10T00:00:00Z"), ZoneOffset.UTC);
        }
    }

    /**
     * A limit of 1000 USD raised to 2000 USD on January 10th. The operations of January 3rd
     * and 13th are the ones that must end up flagged.
     */
    @Test
    @DisplayName("Limit raised mid-month: the operations of Jan 3rd and 13th are flagged")
    void flagsOperationsThatBreachTheLimitInForce() {
        usdRatesAreIdentity();

        // The default limit of 1000 USD applies from the start of the month.
        assertThat(register("500.00", day(2)).limitExceeded()).isFalse();
        assertThat(register("600.00", day(3)).limitExceeded()).isTrue();

        // On January 10th the client raises the limit to 2000 USD.
        limitService.create(new LimitRequest(ACCOUNT, new BigDecimal("2000.00"), ExpenseCategory.product));
        assertThat(transactionService.remainingLimit(ACCOUNT, ExpenseCategory.product, day(10)))
                .isEqualByComparingTo("900.00");

        assertThat(register("100.00", day(11)).limitExceeded()).isFalse();
        assertThat(register("700.00", day(12)).limitExceeded()).isFalse();
        assertThat(register("100.00", day(13)).limitExceeded()).isFalse();
        assertThat(register("100.00", day(13)).limitExceeded()).isTrue();

        List<ExceededTransactionResponse> exceeded =
                transactionService.findExceeded(ACCOUNT, ExpenseCategory.product);

        assertThat(exceeded).hasSize(2);
        assertThat(exceeded).extracting(response -> response.datetime().getDayOfMonth())
                .containsExactly(3, 13);
    }

    /**
     * A new limit below the amount already spent: every subsequent operation is flagged.
     */
    @Test
    @DisplayName("Limit lowered below the accumulated spend")
    void flagsOperationsAfterLimitIsLowered() {
        usdRatesAreIdentity();

        assertThat(register("500.00", day(2)).limitExceeded()).isFalse();
        assertThat(register("100.00", day(3)).limitExceeded()).isFalse();

        // A new limit of 400 USD against 600 USD already spent.
        limitService.create(new LimitRequest(ACCOUNT, new BigDecimal("400.00"), ExpenseCategory.product));
        assertThat(transactionService.remainingLimit(ACCOUNT, ExpenseCategory.product, day(10)))
                .isEqualByComparingTo("-200.00");

        assertThat(register("100.00", day(11)).limitExceeded()).isTrue();
        assertThat(register("100.00", day(12)).limitExceeded()).isTrue();

        assertThat(transactionService.findExceeded(ACCOUNT, ExpenseCategory.product))
                .extracting(response -> response.datetime().getDayOfMonth())
                .containsExactly(11, 12);
    }

    @Test
    @DisplayName("Spend of the next month starts from zero")
    void resetsSpendOnNewMonth() {
        usdRatesAreIdentity();

        register("900.00", day(20));
        assertThat(register("200.00", day(21)).limitExceeded()).isTrue();

        // On February 1st the accumulated spend resets and the whole limit is available again.
        TransactionResponse february = register("900.00",
                OffsetDateTime.of(2022, 2, 1, 10, 0, 0, 0, ZoneOffset.ofHours(6)));
        assertThat(february.limitExceeded()).isFalse();
    }

    @Test
    @DisplayName("Category limits are independent of each other")
    void keepsCategoriesIndependent() {
        usdRatesAreIdentity();

        register("1100.00", day(2));
        TransactionRequest service = new TransactionRequest(ACCOUNT, 9_999_999_999L, "USD",
                new BigDecimal("100.00"), ExpenseCategory.service, day(3));

        assertThat(transactionService.register(service).limitExceeded()).isFalse();
        assertThat(transactionService.findExceeded(ACCOUNT, null)).hasSize(1);
    }

    @Test
    @DisplayName("The exceedance list reports the limit that was in force at the operation")
    void reportsLimitEffectiveAtTransactionTime() {
        usdRatesAreIdentity();

        limitService.create(new LimitRequest(ACCOUNT, new BigDecimal("300.00"), ExpenseCategory.product));
        register("400.00", day(11));

        assertThat(transactionService.findExceeded(ACCOUNT, ExpenseCategory.product))
                .singleElement()
                .satisfies(response -> {
                    assertThat(response.limitSum()).isEqualByComparingTo("300.00");
                    assertThat(response.limitCurrencyShortname()).isEqualTo("USD");
                    assertThat(response.limitDatetime()).isNotNull();
                });
    }

    private TransactionResponse register(String sum, OffsetDateTime datetime) {
        return transactionService.register(new TransactionRequest(ACCOUNT, 9_999_999_999L, "USD",
                new BigDecimal(sum), ExpenseCategory.product, datetime));
    }

    private void usdRatesAreIdentity() {
        when(currencyConverter.toBaseCurrency(any(BigDecimal.class), anyString(), any(OffsetDateTime.class)))
                .thenAnswer(invocation -> invocation.<BigDecimal>getArgument(0));
    }

    private static OffsetDateTime day(int dayOfMonth) {
        return OffsetDateTime.of(2022, 1, dayOfMonth, 10, 0, 0, 0, ZoneOffset.ofHours(6));
    }
}
