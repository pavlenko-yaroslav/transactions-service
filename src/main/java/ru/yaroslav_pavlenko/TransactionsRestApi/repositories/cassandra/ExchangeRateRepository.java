package ru.yaroslav_pavlenko.TransactionsRestApi.repositories.cassandra;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.cassandra.ExchangeRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateRepository extends CassandraRepository<ExchangeRate, String> {

    /**
     * The latest rate of a pair not after the given date. Rows are clustered by date in
     * descending order, so the first one is either that day's close or the previous close.
     */
    @Query("SELECT * FROM exchange_rates WHERE pair = ?0 AND rate_date <= ?1 LIMIT 1")
    Optional<ExchangeRate> findLatestNotAfter(String pair, LocalDate date);

    @Query("SELECT * FROM exchange_rates WHERE pair = ?0 LIMIT ?1")
    List<ExchangeRate> findLatest(String pair, int limit);
}
