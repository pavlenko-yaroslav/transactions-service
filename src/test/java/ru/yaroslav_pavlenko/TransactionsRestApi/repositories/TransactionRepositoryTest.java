package ru.yaroslav_pavlenko.TransactionsRestApi.repositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Limit;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Transaction;
import ru.yaroslav_pavlenko.TransactionsRestApi.repositories.jpa.LimitRepository;
import ru.yaroslav_pavlenko.TransactionsRestApi.repositories.jpa.TransactionRepository;
import ru.yaroslav_pavlenko.TransactionsRestApi.services.LimitEvaluator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The queries the limit calculation rests on: accumulated monthly spend, selection of
 * exceedances and lookup of the limit in force.
 */
@DataJpaTest
class TransactionRepositoryTest {

    private static final Long ACCOUNT = 123L;

    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private LimitRepository limitRepository;

    private Limit januaryLimit;

    @BeforeEach
    void setUp() {
        januaryLimit = limitRepository.save(limit("1000.00", at(1, 1)));
    }

    @Test
    @DisplayName("Spend is accumulated per account, category and month")
    void sumsSpendWithinMonthAndCategory() {
        transactionRepository.save(transaction("500.00", ExpenseCategory.product, at(1, 2), false));
        transactionRepository.save(transaction("600.00", ExpenseCategory.product, at(1, 3), true));
        // A different category is out of scope.
        transactionRepository.save(transaction("900.00", ExpenseCategory.service, at(1, 4), false));
        // So is the next month.
        transactionRepository.save(transaction("700.00", ExpenseCategory.product, at(2, 1), false));

        BigDecimal spent = transactionRepository.sumSpentUsd(ACCOUNT, ExpenseCategory.product,
                LimitEvaluator.monthStart(at(1, 15)), LimitEvaluator.monthEnd(at(1, 15)));

        assertThat(spent).isEqualByComparingTo("1100.00");
    }

    @Test
    @DisplayName("With no operations the accumulated spend is zero rather than null")
    void returnsZeroWhenNoTransactions() {
        BigDecimal spent = transactionRepository.sumSpentUsd(ACCOUNT, ExpenseCategory.product,
                LimitEvaluator.monthStart(at(1, 15)), LimitEvaluator.monthEnd(at(1, 15)));

        assertThat(spent).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Only flagged transactions are selected, in chronological order")
    void returnsOnlyExceededTransactions() {
        transactionRepository.save(transaction("500.00", ExpenseCategory.product, at(1, 2), false));
        transactionRepository.save(transaction("600.00", ExpenseCategory.product, at(1, 13), true));
        transactionRepository.save(transaction("100.00", ExpenseCategory.product, at(1, 3), true));

        List<Transaction> exceeded = transactionRepository.findExceededByAccount(ACCOUNT);

        assertThat(exceeded).hasSize(2);
        assertThat(exceeded).extracting(Transaction::getDatetime).isSorted();
        assertThat(exceeded).allSatisfy(transaction ->
                assertThat(transaction.getAppliedLimit().getLimitSum()).isEqualByComparingTo("1000.00"));
    }

    @Test
    @DisplayName("Exceedances are filtered by expense category")
    void filtersExceededByCategory() {
        transactionRepository.save(transaction("600.00", ExpenseCategory.product, at(1, 3), true));
        transactionRepository.save(transaction("600.00", ExpenseCategory.service, at(1, 4), true));

        assertThat(transactionRepository.findExceededByAccountAndCategory(ACCOUNT, ExpenseCategory.service))
                .singleElement()
                .satisfies(transaction ->
                        assertThat(transaction.getExpenseCategory()).isEqualTo(ExpenseCategory.service));
    }

    @Test
    @DisplayName("The limit in force is the latest one established no later than the operation")
    void findsLimitEffectiveAtGivenMoment() {
        limitRepository.save(limit("2000.00", at(1, 10)));

        Optional<Limit> beforeSecondLimit = limitRepository
                .findFirstByAccountNumberAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDescIdDesc(
                        ACCOUNT, ExpenseCategory.product, at(1, 5));
        Optional<Limit> afterSecondLimit = limitRepository
                .findFirstByAccountNumberAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDescIdDesc(
                        ACCOUNT, ExpenseCategory.product, at(1, 11));

        assertThat(beforeSecondLimit).get()
                .satisfies(limit -> assertThat(limit.getLimitSum()).isEqualByComparingTo("1000.00"));
        assertThat(afterSecondLimit).get()
                .satisfies(limit -> assertThat(limit.getLimitSum()).isEqualByComparingTo("2000.00"));
    }

    @Test
    @DisplayName("A limit established after an operation does not apply to it")
    void ignoresLimitsSetAfterTransaction() {
        assertThat(limitRepository
                .findFirstByAccountNumberAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDescIdDesc(
                        ACCOUNT, ExpenseCategory.product, at(1, 1).minusDays(1)))
                .isEmpty();
    }

    @Test
    @DisplayName("Limit history is returned newest first")
    void returnsLimitHistoryNewestFirst() {
        limitRepository.save(limit("2000.00", at(1, 10)));

        assertThat(limitRepository.findAllByAccountNumberOrderByLimitDatetimeDesc(ACCOUNT))
                .extracting(Limit::getLimitSum)
                .containsExactly(new BigDecimal("2000.00"), new BigDecimal("1000.00"));
    }

    private static OffsetDateTime at(int month, int day) {
        return OffsetDateTime.of(2022, month, day, 0, 0, 0, 0, ZoneOffset.ofHours(6));
    }

    private Limit limit(String sum, OffsetDateTime datetime) {
        return Limit.builder()
                .accountNumber(ACCOUNT)
                .limitSum(new BigDecimal(sum))
                .limitCurrencyShortname("USD")
                .expenseCategory(ExpenseCategory.product)
                .limitDatetime(datetime)
                .build();
    }

    private Transaction transaction(String sumUsd, ExpenseCategory category, OffsetDateTime datetime,
                                    boolean exceeded) {
        return Transaction.builder()
                .accountFrom(ACCOUNT)
                .accountTo(9_999_999_999L)
                .currencyShortname("USD")
                .sum(new BigDecimal(sumUsd))
                .sumUsd(new BigDecimal(sumUsd))
                .expenseCategory(category)
                .datetime(datetime)
                .limitExceeded(exceeded)
                .appliedLimit(januaryLimit)
                .build();
    }
}
