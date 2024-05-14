package ru.yaroslav_pavlenko.TransactionsRestApi.mappers;

import org.springframework.stereotype.Component;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.ExceededTransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Limit;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Transaction;

@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .accountFrom(transaction.getAccountFrom())
                .accountTo(transaction.getAccountTo())
                .currencyShortname(transaction.getCurrencyShortname())
                .sum(transaction.getSum())
                .sumUsd(transaction.getSumUsd())
                .expenseCategory(transaction.getExpenseCategory())
                .datetime(transaction.getDatetime())
                .limitExceeded(transaction.isLimitExceeded())
                .build();
    }

    /**
     * A transaction together with the parameters of the limit it exceeded. When the
     * default limit applied, the caller passes a transient limit built from configuration.
     */
    public ExceededTransactionResponse toExceededResponse(Transaction transaction, Limit appliedLimit) {
        return ExceededTransactionResponse.builder()
                .id(transaction.getId())
                .accountFrom(transaction.getAccountFrom())
                .accountTo(transaction.getAccountTo())
                .currencyShortname(transaction.getCurrencyShortname())
                .sum(transaction.getSum())
                .sumUsd(transaction.getSumUsd())
                .expenseCategory(transaction.getExpenseCategory())
                .datetime(transaction.getDatetime())
                .limitSum(appliedLimit.getLimitSum())
                .limitDatetime(appliedLimit.getLimitDatetime())
                .limitCurrencyShortname(appliedLimit.getLimitCurrencyShortname())
                .build();
    }

    public LimitResponse toResponse(Limit limit) {
        return LimitResponse.builder()
                .id(limit.getId())
                .accountNumber(limit.getAccountNumber())
                .limitSum(limit.getLimitSum())
                .limitCurrencyShortname(limit.getLimitCurrencyShortname())
                .expenseCategory(limit.getExpenseCategory())
                .limitDatetime(limit.getLimitDatetime())
                .build();
    }
}
