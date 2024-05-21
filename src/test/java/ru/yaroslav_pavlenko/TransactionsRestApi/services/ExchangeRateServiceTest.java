package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import ru.yaroslav_pavlenko.TransactionsRestApi.config.ExchangeProperties;
import ru.yaroslav_pavlenko.TransactionsRestApi.exceptions.ExchangeRateNotFoundException;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.cassandra.ExchangeRate;
import ru.yaroslav_pavlenko.TransactionsRestApi.repositories.cassandra.ExchangeRateRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private ExchangeRateRepository rateRepository;
    @Mock
    private RestTemplate restTemplate;

    private ExchangeRateService service(String apiKey, boolean fetchEnabled, Map<String, BigDecimal> seed) {
        return new ExchangeRateService(rateRepository, restTemplate,
                new ExchangeProperties("USD", List.of("EUR/USD"), apiKey,
                        "https://api.twelvedata.com", "1day", fetchEnabled, seed));
    }

    private JsonNode json(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    @DisplayName("On a non-trading day the last available close is used")
    void returnsPreviousCloseWhenDateHasNoQuotes() {
        LocalDate saturday = LocalDate.of(2022, 1, 8);
        when(rateRepository.findLatestNotAfter("EUR/USD", saturday))
                .thenReturn(Optional.of(ExchangeRate.builder()
                        .pair("EUR/USD")
                        .rateDate(LocalDate.of(2022, 1, 7))
                        .closeValue(new BigDecimal("1.0850"))
                        .build()));

        assertThat(service("key", true, Map.of()).getRate("EUR/USD", saturday))
                .isEqualByComparingTo("1.0850");
    }

    @Test
    @DisplayName("A completely missing rate fails instead of falling back to a guess")
    void failsWhenNoRateStored() {
        LocalDate date = LocalDate.of(2022, 1, 8);
        when(rateRepository.findLatestNotAfter("EUR/USD", date)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service("key", true, Map.of()).getRate("EUR/USD", date))
                .isInstanceOf(ExchangeRateNotFoundException.class)
                .hasMessageContaining("EUR/USD");
    }

    @Test
    @DisplayName("The refresh stores the close of the daily interval")
    void savesDailyCloseValue() {
        when(restTemplate.getForObject(contains("symbol=EUR/USD"), eq(JsonNode.class)))
                .thenReturn(json("""
                        {"values":[{"datetime":"2022-01-10","close":"1.0850"},
                                   {"datetime":"2022-01-07","close":"1.0830"}],
                         "status":"ok"}"""));

        service("key", true, Map.of()).refreshRates();

        ArgumentCaptor<ExchangeRate> saved = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(rateRepository).save(saved.capture());
        assertThat(saved.getValue().getPair()).isEqualTo("EUR/USD");
        assertThat(saved.getValue().getCloseValue()).isEqualByComparingTo("1.0850");
    }

    @Test
    @DisplayName("An empty close for today falls back to the previous one")
    void fallsBackToPreviousCloseInResponse() {
        when(restTemplate.getForObject(any(String.class), eq(JsonNode.class)))
                .thenReturn(json("""
                        {"values":[{"datetime":"2022-01-10","close":""},
                                   {"datetime":"2022-01-07","close":"1.0830"}],
                         "status":"ok"}"""));

        service("key", true, Map.of()).refreshRates();

        ArgumentCaptor<ExchangeRate> saved = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(rateRepository).save(saved.capture());
        assertThat(saved.getValue().getCloseValue()).isEqualByComparingTo("1.0830");
    }

    @Test
    @DisplayName("When the provider does not quote the pair, the reverse one is inverted")
    void invertsReversePairWhenDirectIsUnavailable() {
        when(restTemplate.getForObject(contains("symbol=EUR/USD"), eq(JsonNode.class)))
                .thenReturn(json("""
                        {"status":"error","message":"symbol not found"}"""));
        when(restTemplate.getForObject(contains("symbol=USD/EUR"), eq(JsonNode.class)))
                .thenReturn(json("""
                        {"status":"ok","values":[{"close":"0.8"}]}"""));

        service("key", true, Map.of()).refreshRates();

        ArgumentCaptor<ExchangeRate> saved = ArgumentCaptor.forClass(ExchangeRate.class);
        verify(rateRepository).save(saved.capture());
        assertThat(saved.getValue().getPair()).isEqualTo("EUR/USD");
        assertThat(saved.getValue().getCloseValue()).isEqualByComparingTo("1.25");
    }

    @Test
    @DisplayName("Without an API key the provider is not called at all")
    void skipsRefreshWithoutApiKey() {
        service("", true, Map.of()).refreshRates();

        verify(restTemplate, never()).getForObject(any(String.class), eq(JsonNode.class));
        verify(rateRepository, never()).save(any(ExchangeRate.class));
    }

    @Test
    @DisplayName("A demo rate is written only into a pair that has no data")
    void seedsOnlyEmptyPairs() {
        when(rateRepository.findLatest("EUR/USD", 1)).thenReturn(List.of());

        service("", false, Map.of("EUR/USD", new BigDecimal("1.0850"))).seedRatesIfEmpty();

        verify(rateRepository).save(any(ExchangeRate.class));
    }

    @Test
    @DisplayName("Existing rates are never overwritten by demo data")
    void keepsExistingRates() {
        when(rateRepository.findLatest("EUR/USD", 1))
                .thenReturn(List.of(ExchangeRate.builder()
                        .pair("EUR/USD")
                        .rateDate(LocalDate.of(2022, 1, 10))
                        .closeValue(new BigDecimal("1.0830"))
                        .build()));

        service("", false, Map.of("EUR/USD", new BigDecimal("1.0850"))).seedRatesIfEmpty();

        verify(rateRepository, never()).save(any(ExchangeRate.class));
    }
}
