package ru.yaroslav_pavlenko.TransactionsRestApi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A transaction that exceeded its limit, enriched with the parameters of that limit.
 */
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "ExceededTransactionResponse", description = "Transaction that exceeded the monthly limit")
public record ExceededTransactionResponse(
        Long id,
        Long accountFrom,
        Long accountTo,
        String currencyShortname,
        BigDecimal sum,
        BigDecimal sumUsd,
        ExpenseCategory expenseCategory,
        OffsetDateTime datetime,

        @Schema(example = "1000.00", description = "Amount of the exceeded limit")
        BigDecimal limitSum,
        @Schema(example = "2022-01-10T00:00:00+06:00", description = "Date the exceeded limit was established")
        OffsetDateTime limitDatetime,
        @Schema(example = "USD", description = "Limit currency")
        String limitCurrencyShortname
) {
}
