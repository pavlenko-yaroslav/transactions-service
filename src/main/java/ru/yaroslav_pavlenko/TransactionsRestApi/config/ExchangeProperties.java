package ru.yaroslav_pavlenko.TransactionsRestApi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Settings of the exchange rate feed.
 *
 * @param baseCurrency currency the limits are denominated in
 * @param pairs        pairs whose rates the service pulls from the provider
 * @param apiKey       twelvedata key; an empty key disables the scheduled refresh
 * @param apiUrl       provider base URL
 * @param interval     quote interval, daily
 * @param fetchEnabled enables the rate refresh scheduler
 * @param seedRates    demo rates written at startup for pairs that have no data yet
 */
@ConfigurationProperties(prefix = "app.exchange")
public record ExchangeProperties(
        String baseCurrency,
        List<String> pairs,
        String apiKey,
        String apiUrl,
        String interval,
        boolean fetchEnabled,
        Map<String, BigDecimal> seedRates
) {

    public ExchangeProperties {
        baseCurrency = baseCurrency == null ? "USD" : baseCurrency;
        pairs = pairs == null ? List.of("EUR/USD") : pairs;
        apiUrl = apiUrl == null ? "https://api.twelvedata.com" : apiUrl;
        interval = interval == null ? "1day" : interval;
        seedRates = seedRates == null ? Map.of() : seedRates;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
