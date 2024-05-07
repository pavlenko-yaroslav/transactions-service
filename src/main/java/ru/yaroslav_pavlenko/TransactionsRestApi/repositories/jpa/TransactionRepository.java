package ru.yaroslav_pavlenko.TransactionsRestApi.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findAllByAccountFromOrderByDatetimeAsc(Long accountFrom);

    /**
     * Total spend of a client in USD for a category over an interval — the accumulated
     * monthly spend that the limit is compared against when raising {@code limit_exceeded}.
     */
    @Query("""
            select coalesce(sum(t.sumUsd), 0)
            from Transaction t
            where t.accountFrom = :accountFrom
              and t.expenseCategory = :category
              and t.datetime >= :from
              and t.datetime < :to
            """)
    BigDecimal sumSpentUsd(@Param("accountFrom") Long accountFrom,
                           @Param("category") ExpenseCategory category,
                           @Param("from") OffsetDateTime from,
                           @Param("to") OffsetDateTime to);

    /**
     * Transactions that exceeded their limit, joined with the applied limit up front
     * so that building the response does not turn into an N+1 query.
     */
    @Query("""
            select t from Transaction t
            left join fetch t.appliedLimit
            where t.accountFrom = :accountFrom
              and t.limitExceeded = true
            order by t.datetime asc
            """)
    List<Transaction> findExceededByAccount(@Param("accountFrom") Long accountFrom);

    @Query("""
            select t from Transaction t
            left join fetch t.appliedLimit
            where t.accountFrom = :accountFrom
              and t.expenseCategory = :category
              and t.limitExceeded = true
            order by t.datetime asc
            """)
    List<Transaction> findExceededByAccountAndCategory(@Param("accountFrom") Long accountFrom,
                                                       @Param("category") ExpenseCategory category);
}
