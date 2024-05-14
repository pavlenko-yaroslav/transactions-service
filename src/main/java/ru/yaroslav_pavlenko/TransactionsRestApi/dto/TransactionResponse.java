package ru.yaroslav_pavlenko.TransactionsRestApi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A stored transaction with the limit_exceeded flag already resolved.
 */
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "TransactionResponse")
public record TransactionResponse(
        Long id,
        Long accountFrom,
        Long accountTo,
        String currencyShortname,
        BigDecimal sum,
        @Schema(description = "Operation amount in USD at the closing rate of the operation date")
        BigDecimal sumUsd,
        ExpenseCategory expenseCategory,
        OffsetDateTime datetime,
        @Schema(description = "Technical flag marking that the monthly limit was exceeded")
        boolean limitExceeded
) {
}
