package ru.yaroslav_pavlenko.TransactionsRestApi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties({ExchangeProperties.class, LimitProperties.class})
public class ApplicationConfig {

    /**
     * The time source is a bean so that the limit establishment date is deterministic in tests.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public RestTemplate exchangeRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Pool used to convert a batch of transactions in different currencies in parallel.
     * The work is dominated by lookups in the rate store, so the pool is sized well
     * above the core count.
     */
    @Bean("currencyExecutor")
    public Executor currencyExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("currency-");
        executor.initialize();
        return executor;
    }
}
