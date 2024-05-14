package ru.yaroslav_pavlenko.TransactionsRestApi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

@Schema(name = "ApiError", description = "Standard error payload")
public record ApiError(
        int status,
        String error,
        String message,
        List<String> details,
        OffsetDateTime timestamp
) {
    public static ApiError of(int status, String error, String message, List<String> details) {
        return new ApiError(status, error, message, details, OffsetDateTime.now());
    }
}
