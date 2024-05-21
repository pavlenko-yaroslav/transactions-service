package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rules behind the limit_exceeded flag, including two multi-step spending scenarios.
 */
class LimitEvaluatorTest {

    private static BigDecimal usd(String value) {
        return new BigDecimal(value);
    }

    @ParameterizedTest(name = "spent {0} plus operation {1} against limit {2} -> exceeded={3}")
    @CsvSource({
            "0.00,    500.00,  1000.00, false",
            "500.00,  600.00,  1000.00, true",
            "0.00,   1000.00,  1000.00, false",
            "999.99,    0.02,  1000.00, true",
            "1100.00,  100.00, 2000.00, false",
            "1900.00,  100.00, 2000.00, false",
            "2000.00,  100.00, 2000.00, true"
    })
    @DisplayName("Flag is raised only when the monthly spend goes past the limit")
    void marksTransactionExceedingMonthlyLimit(String spentBefore, String amount, String limit, boolean expected) {
        assertThat(LimitEvaluator.isExceeded(usd(spentBefore), usd(amount), usd(limit))).isEqualTo(expected);
    }

    @Test
    @DisplayName("A limit that is exactly used up does not count as exceeded")
    void exactlyReachedLimitIsNotExceeded() {
        assertThat(LimitEvaluator.isExceeded(usd("900.00"), usd("100.00"), usd("1000.00"))).isFalse();
        assertThat(LimitEvaluator.remainder(usd("900.00"), usd("100.00"), usd("1000.00")))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Month boundaries are derived from the operation date and its offset")
    void computesMonthBoundaries() {
        OffsetDateTime datetime = OffsetDateTime.of(2022, 1, 30, 12, 30, 0, 0, ZoneOffset.ofHours(6));

        assertThat(LimitEvaluator.monthStart(datetime))
                .isEqualTo(OffsetDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(6)));
        assertThat(LimitEvaluator.monthEnd(datetime))
                .isEqualTo(OffsetDateTime.of(2022, 2, 1, 0, 0, 0, 0, ZoneOffset.ofHours(6)));
    }

    /**
     * A limit of 1000 USD from January 1st, raised to 2000 USD on January 10th. The spend keeps
     * accumulating from the start of the month, and raising the limit does not reset it.
     */
    @Nested
    @DisplayName("Scenario: limit raised mid-month")
    class LimitRaisedMidMonth {

        @Test
        @DisplayName("Remainders and flags follow the accumulated spend")
        void tracksRemaindersAcrossLimitChange() {
            BigDecimal firstLimit = usd("1000.00");
            BigDecimal secondLimit = usd("2000.00");

            BigDecimal spent = BigDecimal.ZERO;

            // Jan 2nd — spends 500, remainder 500
            assertStep(spent, usd("500.00"), firstLimit, false, "500.00");
            spent = spent.add(usd("500.00"));

            // Jan 3rd — spends 600, remainder -100, flag raised
            assertStep(spent, usd("600.00"), firstLimit, true, "-100.00");
            spent = spent.add(usd("600.00"));

            // Jan 10th the limit is raised to 2000: remainder is 2000 - 1100 = 900
            assertThat(secondLimit.subtract(spent)).isEqualByComparingTo("900.00");

            // Jan 11th — spends 100, remainder 800
            assertStep(spent, usd("100.00"), secondLimit, false, "800.00");
            spent = spent.add(usd("100.00"));

            // Jan 12th — spends 700, remainder 100
            assertStep(spent, usd("700.00"), secondLimit, false, "100.00");
            spent = spent.add(usd("700.00"));

            // Jan 13th — spends 100, remainder 0: the limit is used up but not exceeded
            assertStep(spent, usd("100.00"), secondLimit, false, "0.00");
            spent = spent.add(usd("100.00"));

            // Jan 13th — another 100, remainder -100, flag raised
            assertStep(spent, usd("100.00"), secondLimit, true, "-100.00");
        }
    }

    /**
     * A new limit lower than the amount already spent: subsequent operations are flagged
     * even though each of them is small.
     */
    @Nested
    @DisplayName("Scenario: limit lowered below the accumulated spend")
    class LimitLoweredBelowSpend {

        @Test
        @DisplayName("Every following operation is flagged while the remainder stays negative")
        void flagsEverySubsequentOperation() {
            BigDecimal firstLimit = usd("1000.00");
            BigDecimal reducedLimit = usd("400.00");

            BigDecimal spent = BigDecimal.ZERO;

            assertStep(spent, usd("500.00"), firstLimit, false, "500.00");
            spent = spent.add(usd("500.00"));

            assertStep(spent, usd("100.00"), firstLimit, false, "400.00");
            spent = spent.add(usd("100.00"));

            // A new limit of 400 against 600 already spent leaves a negative remainder of -200
            assertThat(reducedLimit.subtract(spent)).isEqualByComparingTo("-200.00");

            assertStep(spent, usd("100.00"), reducedLimit, true, "-300.00");
            spent = spent.add(usd("100.00"));

            assertStep(spent, usd("100.00"), reducedLimit, true, "-400.00");
        }
    }

    private static void assertStep(BigDecimal spentBefore, BigDecimal amount, BigDecimal limit,
                                   boolean expectedFlag, String expectedRemainder) {
        assertThat(LimitEvaluator.isExceeded(spentBefore, amount, limit)).isEqualTo(expectedFlag);
        assertThat(LimitEvaluator.remainder(spentBefore, amount, limit)).isEqualByComparingTo(expectedRemainder);
    }
}
