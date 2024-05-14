package ru.yaroslav_pavlenko.TransactionsRestApi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * A spending operation as it arrives from the banking system.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(name = "TransactionRequest", description = "Spending operation submitted by the banking system")
public record TransactionRequest(

        @NotNull(message = "account_from is required")
        @Min(value = 0, message = "account_from must be a 10-digit number")
        @Max(value = 9_999_999_999L, message = "account_from must be a 10-digit number")
        @Schema(example = "123", description = "Client bank account, integer of up to 10 digits")
        Long accountFrom,

        @NotNull(message = "account_to is required")
        @Min(value = 0, message = "account_to must be a 10-digit number")
        @Max(value = 9_999_999_999L, message = "account_to must be a 10-digit number")
        @Schema(example = "9999999999", description = "Counterparty bank account, integer of up to 10 digits")
        Long accountTo,

        @NotNull(message = "currency_shortname is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency_shortname must be a 3-letter ISO-4217 code")
        @Schema(example = "EUR", description = "Account currency, ISO-4217 code")
        String currencyShortname,

        @NotNull(message = "sum is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "sum must be greater than 0")
        @Digits(integer = 17, fraction = 2, message = "sum must have at most 2 decimal places")
        @Schema(example = "10000.45", description = "Operation amount, rounded to hundredths")
        BigDecimal sum,

        @NotNull(message = "expense_category is required")
        @Schema(example = "product", description = "Expense category: product or service")
        ExpenseCategory expenseCategory,

        @NotNull(message = "datetime is required")
        @Schema(example = "2022-01-30T00:00:00+06:00", description = "Operation timestamp with time zone")
        OffsetDateTime datetime
) {
}
