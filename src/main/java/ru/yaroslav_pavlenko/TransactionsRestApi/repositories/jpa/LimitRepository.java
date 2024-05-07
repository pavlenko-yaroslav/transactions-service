package ru.yaroslav_pavlenko.TransactionsRestApi.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Limit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LimitRepository extends JpaRepository<Limit, Long> {

    List<Limit> findAllByAccountNumberOrderByLimitDatetimeDesc(Long accountNumber);

    List<Limit> findAllByAccountNumberAndExpenseCategoryOrderByLimitDatetimeDesc(Long accountNumber,
                                                                                ExpenseCategory expenseCategory);

    /**
     * The limit in force at {@code at}: the most recent one established no later than that moment.
     */
    Optional<Limit> findFirstByAccountNumberAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDescIdDesc(
            Long accountNumber, ExpenseCategory expenseCategory, OffsetDateTime at);
}
