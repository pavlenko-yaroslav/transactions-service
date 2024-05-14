package ru.yaroslav_pavlenko.TransactionsRestApi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;

import java.math.BigDecimal;

/**
 * A request to establish a new monthly limit.
 * <p>
 * The client does not supply the establishment date: the service stamps it with the current
 * time, so neither a past nor a future date can be forced. The limit currency is always USD.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "LimitRequest", description = "New monthly limit denominated in USD")
public record LimitRequest(

        @NotNull(message = "account_number is required")
        @Min(value = 0, message = "account_number must be a 10-digit number")
        @Max(value = 9_999_999_999L, message = "account_number must be a 10-digit number")
        @Schema(example = "123")
        Long accountNumber,

        @NotNull(message = "limit_sum is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "limit_sum must be greater than 0")
        @Digits(integer = 17, fraction = 2, message = "limit_sum must have at most 2 decimal places")
        @Schema(example = "1000.00")
        BigDecimal limitSum,

        @NotNull(message = "expense_category is required")
        @Schema(example = "product")
        ExpenseCategory expenseCategory
) {
}
