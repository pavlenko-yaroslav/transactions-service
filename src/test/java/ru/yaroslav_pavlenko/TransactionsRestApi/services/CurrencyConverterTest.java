package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yaroslav_pavlenko.TransactionsRestApi.config.ExchangeProperties;
import ru.yaroslav_pavlenko.TransactionsRestApi.exceptions.ExchangeRateNotFoundException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConverterTest {

    @Mock
    private ExchangeRateService exchangeRateService;

    private CurrencyConverter converter;

    @BeforeEach
    void setUp() {
        ExchangeProperties properties = new ExchangeProperties("USD", List.of("EUR/USD"),
                "key", "https://api.twelvedata.com", "1day", true, Map.of());
        converter = new CurrencyConverter(exchangeRateService, properties);
    }

    @Test
    @DisplayName("A euro amount is converted at the EUR/USD rate of the operation date")
    void convertsUsingRateOfTransactionDate() {
        OffsetDateTime datetime = OffsetDateTime.of(2022, 1, 30, 0, 0, 0, 0, ZoneOffset.ofHours(6));
        when(exchangeRateService.getRate("EUR/USD", LocalDate.of(2022, 1, 30)))
                .thenReturn(new BigDecimal("1.0850"));

        BigDecimal result = converter.toBaseCurrency(new BigDecimal("100.00"), "EUR", datetime);

        assertThat(result).isEqualByComparingTo("108.50");
    }

    @Test
    @DisplayName("The result is rounded to hundredths")
    void roundsToTwoDecimals() {
        when(exchangeRateService.getRate(any(), any())).thenReturn(new BigDecimal("1.087654"));

        BigDecimal result = converter.toBaseCurrency(new BigDecimal("999.99"), "EUR", LocalDate.of(2022, 3, 1));

        assertThat(result).isEqualByComparingTo("1087.64");
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("No rate is looked up for an operation already in the base currency")
    void skipsLookupForBaseCurrency() {
        BigDecimal result = converter.toBaseCurrency(new BigDecimal("150.5"), "USD", LocalDate.of(2022, 1, 1));

        assertThat(result).isEqualByComparingTo("150.50");
        verify(exchangeRateService, never()).getRate(any(), any());
    }

    @Test
    @DisplayName("A missing rate is not silently replaced by a default")
    void propagatesMissingRate() {
        LocalDate date = LocalDate.of(2022, 1, 1);
        when(exchangeRateService.getRate("EUR/USD", date))
                .thenThrow(new ExchangeRateNotFoundException("EUR/USD", date));

        assertThatThrownBy(() -> converter.toBaseCurrency(BigDecimal.TEN, "EUR", date))
                .isInstanceOf(ExchangeRateNotFoundException.class);
    }
}
