package ru.yaroslav_pavlenko.TransactionsRestApi.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Monthly spending limit of a client for a single expense category.
 * <p>
 * Records are immutable: an already established limit is never updated, setting a new
 * limit appends a row stamped with the current date, and the history is preserved.
 */
@Entity
@Table(name = "limits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Limit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false)
    private Long accountNumber;

    @Column(name = "limit_sum", nullable = false)
    private BigDecimal limitSum;

    @Column(name = "limit_currency_shortname", nullable = false, length = 3)
    private String limitCurrencyShortname;

    @Enumerated(EnumType.STRING)
    @Column(name = "expense_category", nullable = false, length = 16)
    private ExpenseCategory expenseCategory;

    @Column(name = "limit_datetime", nullable = false)
    private OffsetDateTime limitDatetime;
}
