package ru.yaroslav_pavlenko.TransactionsRestApi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A client spending operation.
 * <p>
 * {@code sumUsd} is the amount converted to USD at the closing rate of the operation date,
 * or at the last available close. {@code limitExceeded} is the technical flag raised at
 * ingestion time when the operation pushes the monthly spend past the limit.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_from", nullable = false)
    private Long accountFrom;

    @Column(name = "account_to", nullable = false)
    private Long accountTo;

    @Column(name = "currency_shortname", nullable = false, length = 3)
    private String currencyShortname;

    @Column(name = "sum", nullable = false)
    private BigDecimal sum;

    @Column(name = "sum_usd", nullable = false)
    private BigDecimal sumUsd;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category", nullable = false, length = 16)
    private ExpenseCategory expenseCategory;

    @Column(name = "datetime", nullable = false)
    private OffsetDateTime datetime;

    @Column(name = "limit_exceeded", nullable = false)
    private boolean limitExceeded;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "limit_id")
    private Limit appliedLimit;
}
