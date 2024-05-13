package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.yaroslav_pavlenko.TransactionsRestApi.config.ExchangeProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Converts an operation amount into the currency the limits are kept in (USD), using the
 * closing rate of the spending day or the last available close before it.
 */
@Service
@RequiredArgsConstructor
public class CurrencyConverter {

    /** Amounts are rounded to hundredths, matching the transaction data structure. */
    public static final int SCALE = 2;

    private final ExchangeRateService exchangeRateService;
    private final ExchangeProperties properties;

    public BigDecimal toBaseCurrency(BigDecimal amount, String currency, OffsetDateTime datetime) {
        return toBaseCurrency(amount, currency, datetime.toLocalDate());
    }

    public BigDecimal toBaseCurrency(BigDecimal amount, String currency, LocalDate date) {
        if (properties.baseCurrency().equalsIgnoreCase(currency)) {
            return amount.setScale(SCALE, RoundingMode.HALF_UP);
        }

        String pair = currency.toUpperCase() + "/" + properties.baseCurrency();
        BigDecimal rate = exchangeRateService.getRate(pair, date);

        return amount.multiply(rate).setScale(SCALE, RoundingMode.HALF_UP);
    }
}
