package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Pure monthly limit rules, kept apart from persistence so that the {@code limit_exceeded}
 * logic can be unit tested directly.
 */
public final class LimitEvaluator {

    private LimitEvaluator() {
    }

    /**
     * A transaction is marked as exceeding the limit when the monthly spend including it goes
     * past the limit amount. A limit that is exactly used up does not count as exceeded.
     *
     * @param spentBeforeUsd spend in this category since the start of the month, in USD
     * @param transactionUsd amount of the current operation, in USD
     * @param limitSum       limit amount in force, in USD
     */
    public static boolean isExceeded(BigDecimal spentBeforeUsd, BigDecimal transactionUsd, BigDecimal limitSum) {
        return spentBeforeUsd.add(transactionUsd).compareTo(limitSum) > 0;
    }

    /**
     * Remaining limit after the operation.
     */
    public static BigDecimal remainder(BigDecimal spentBeforeUsd, BigDecimal transactionUsd, BigDecimal limitSum) {
        return limitSum.subtract(spentBeforeUsd.add(transactionUsd));
    }

    /**
     * Start of the calendar month the spend is accumulated over. Accumulation always starts
     * on the first day, regardless of when within the month the limit was established.
     */
    public static OffsetDateTime monthStart(OffsetDateTime datetime) {
        return datetime.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1);
    }

    public static OffsetDateTime monthEnd(OffsetDateTime datetime) {
        return monthStart(datetime).plusMonths(1);
    }
}
