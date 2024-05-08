package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import ru.yaroslav_pavlenko.TransactionsRestApi.config.ExchangeProperties;
import ru.yaroslav_pavlenko.TransactionsRestApi.exceptions.ExchangeRateNotFoundException;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.cassandra.ExchangeRate;
import ru.yaroslav_pavlenko.TransactionsRestApi.repositories.cassandra.ExchangeRateRepository;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Fetches and serves exchange rates.
 * <p>
 * Rates are pulled on a daily interval and stored in Cassandra under the pair name from the
 * configuration, for example {@code EUR/USD}. The stored value is the price of one unit of
 * the base currency in the quote currency, so for {@code EUR/USD} it is the price of one euro
 * in dollars.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final ExchangeRateRepository rateRepository;
    private final RestTemplate exchangeRestTemplate;
    private final ExchangeProperties properties;

    /**
     * The rate on the operation date, falling back to the last available close before it when
     * the market was closed that day — a weekend or a public holiday.
     */
    public BigDecimal getRate(String pair, LocalDate date) {
        return rateRepository.findLatestNotAfter(pair, date)
                .map(ExchangeRate::getCloseValue)
                .orElseThrow(() -> new ExchangeRateNotFoundException(pair, date));
    }

    public Optional<ExchangeRate> findLatest(String pair) {
        return rateRepository.findLatest(pair, 1).stream().findFirst();
    }

    /**
     * Daily rate refresh at midnight. Without an API key the provider is not called at all and
     * the service keeps running on previously loaded rates.
     */
    @Scheduled(cron = "${app.exchange.cron:0 0 0 * * *}")
    public void refreshRates() {
        if (!properties.fetchEnabled() || !properties.hasApiKey()) {
            log.debug("Exchange rate refresh is disabled or API key is not set, skipping");
            return;
        }

        for (String pair : properties.pairs()) {
            try {
                BigDecimal close = fetchClose(pair);
                rateRepository.save(ExchangeRate.builder()
                        .pair(pair)
                        .rateDate(LocalDate.now())
                        .closeValue(close)
                        .build());
                log.info("Saved exchange rate {} for pair {}", close, pair);
            } catch (Exception exception) {
                log.error("Failed to refresh exchange rate for pair {}: {}", pair, exception.getMessage());
            }
        }
    }

    /**
     * Demo rates that let the service start without a provider key. They are written once and
     * only into pairs that hold no data, so real rates are never overwritten.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedRatesIfEmpty() {
        properties.seedRates().forEach((pair, value) -> {
            if (findLatest(pair).isPresent()) {
                return;
            }
            rateRepository.save(ExchangeRate.builder()
                    .pair(pair)
                    .rateDate(LocalDate.now())
                    .closeValue(value)
                    .build());
            log.warn("Seeded demo exchange rate {} for pair {} — replace it with real data from the provider",
                    value, pair);
        });
    }

    /**
     * Providers do not quote every direction, so if {@code EUR/USD} is rejected the reverse
     * {@code USD/EUR} is requested instead and its rate is inverted.
     */
    private BigDecimal fetchClose(String pair) {
        try {
            return requestClose(pair);
        } catch (IllegalStateException exception) {
            String inverted = invert(pair);
            log.info("Pair {} is unavailable ({}), falling back to {}", pair, exception.getMessage(), inverted);
            return BigDecimal.ONE.divide(requestClose(inverted), MathContext.DECIMAL64);
        }
    }

    private BigDecimal requestClose(String symbol) {
        String url = "%s/time_series?symbol=%s&interval=%s&outputsize=2&apikey=%s"
                .formatted(properties.apiUrl(), symbol, properties.interval(), properties.apiKey());

        JsonNode response = exchangeRestTemplate.getForObject(url, JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("empty response");
        }
        if (response.hasNonNull("status") && "error".equals(response.get("status").asText())) {
            throw new IllegalStateException(response.path("message").asText("provider error"));
        }

        JsonNode values = response.path("values");
        if (!values.isArray() || values.isEmpty()) {
            throw new IllegalStateException("no values in response");
        }

        // Today's close, or the previous one when it has not been published yet.
        return closeOf(values.get(0))
                .or(() -> values.size() > 1 ? closeOf(values.get(1)) : Optional.<BigDecimal>empty())
                .orElseThrow(() -> new IllegalStateException("no close value in response"));
    }

    private Optional<BigDecimal> closeOf(JsonNode value) {
        String close = value.path("close").asText(null);
        if (close == null || close.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(close));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String invert(String pair) {
        List<String> parts = List.of(pair.split("/"));
        if (parts.size() != 2) {
            throw new IllegalArgumentException("Unexpected currency pair format: " + pair);
        }
        return parts.get(1) + "/" + parts.get(0);
    }
}
