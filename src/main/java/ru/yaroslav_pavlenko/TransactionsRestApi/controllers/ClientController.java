package ru.yaroslav_pavlenko.TransactionsRestApi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.ExceededTransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.services.LimitService;
import ru.yaroslav_pavlenko.TransactionsRestApi.services.TransactionService;

import java.util.List;

/**
 * Client API: establishing limits, listing limits and listing transactions that exceeded them.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Client API")
public class ClientController {

    private final LimitService limitService;
    private final TransactionService transactionService;

    @PostMapping("/limits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Establish a new monthly limit",
            description = "The service stamps the establishment date with the current time. "
                    + "Previously established limits are never modified — a new record is appended.")
    public LimitResponse createLimit(@RequestBody @Valid LimitRequest request) {
        return limitService.create(request);
    }

    @GetMapping("/limits")
    @Operation(summary = "List all limits of a client")
    public List<LimitResponse> getLimits(
            @Parameter(description = "Client bank account", example = "123")
            @RequestParam Long accountNumber,
            @Parameter(description = "Optional expense category filter")
            @RequestParam(required = false) ExpenseCategory expenseCategory) {
        return limitService.findAll(accountNumber, expenseCategory);
    }

    @GetMapping("/transactions/exceeded")
    @Operation(summary = "List transactions that exceeded the limit",
            description = "Every transaction carries the amount, establishment date and currency "
                    + "of the limit it exceeded.")
    public List<ExceededTransactionResponse> getExceeded(
            @Parameter(description = "Client bank account", example = "123")
            @RequestParam Long accountNumber,
            @Parameter(description = "Optional expense category filter")
            @RequestParam(required = false) ExpenseCategory expenseCategory) {
        return transactionService.findExceeded(accountNumber, expenseCategory);
    }

    @GetMapping("/transactions")
    @Operation(summary = "List all transactions of a client")
    public List<TransactionResponse> getTransactions(
            @Parameter(description = "Client bank account", example = "123")
            @RequestParam Long accountNumber) {
        return transactionService.findByAccount(accountNumber);
    }
}
