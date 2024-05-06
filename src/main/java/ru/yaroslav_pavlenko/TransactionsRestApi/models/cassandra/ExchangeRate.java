package ru.yaroslav_pavlenko.TransactionsRestApi.models.cassandra;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Closing rate of a currency pair on a given date, stored in Cassandra.
 * <p>
 * Partitioned by pair and clustered by date in descending order, which makes
 * "the latest rate not after date X" a single-row read — exactly what weekends and
 * holidays need, where the previous close has to be reused.
 * <p>
 * {@code closeValue} is how much of the quote currency (USD) one unit of the base
 * currency costs, so for {@code EUR/USD} it is the price of one euro in dollars.
 */
@Table("exchange_rates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRate {

    @PrimaryKeyColumn(name = "pair", type = PrimaryKeyType.PARTITIONED)
    private String pair;

    @PrimaryKeyColumn(name = "rate_date", ordinal = 0, ordering = Ordering.DESCENDING)
    private LocalDate rateDate;

    @Column("close_value")
    private BigDecimal closeValue;
}
