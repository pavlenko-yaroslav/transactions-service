package ru.yaroslav_pavlenko.TransactionsRestApi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "LimitResponse")
public record LimitResponse(
        Long id,
        Long accountNumber,
        BigDecimal limitSum,
        String limitCurrencyShortname,
        ExpenseCategory expenseCategory,
        OffsetDateTime limitDatetime
) {
}
