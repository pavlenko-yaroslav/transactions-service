package ru.yaroslav_pavlenko.TransactionsRestApi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * The default limit: when a client has never established one, a monthly limit
 * of 1000 USD applies.
 */
@ConfigurationProperties(prefix = "app.limit")
public record LimitProperties(BigDecimal defaultSum, String currency) {

    public LimitProperties {
        defaultSum = defaultSum == null ? new BigDecimal("1000.00") : defaultSum;
        currency = currency == null ? "USD" : currency;
    }
}
