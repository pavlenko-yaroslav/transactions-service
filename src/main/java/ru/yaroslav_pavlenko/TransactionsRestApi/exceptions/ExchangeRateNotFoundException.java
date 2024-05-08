package ru.yaroslav_pavlenko.TransactionsRestApi.exceptions;

import java.time.LocalDate;

/**
 * No rate is available for the pair on the operation date or before it, so the amount
 * cannot be converted to USD and the transaction cannot be accepted.
 */
public class ExchangeRateNotFoundException extends RuntimeException {

    public ExchangeRateNotFoundException(String pair, LocalDate date) {
        super("No exchange rate found for pair %s on %s or earlier".formatted(pair, date));
    }
}
