package ru.yaroslav_pavlenko.TransactionsRestApi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.services.TransactionBatchService;
import ru.yaroslav_pavlenko.TransactionsRestApi.services.TransactionService;

import java.util.List;

/**
 * Integration API: ingestion of spending operations from the banking system.
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Validated
@Tag(name = "Integration API")
public class TransactionIngestController {

    private final TransactionService transactionService;
    private final TransactionBatchService batchService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Accept a spending operation",
            description = "Stores the operation, converts it to USD at the closing rate of the spending date "
                    + "and raises limit_exceeded against the monthly limit in force.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Operation accepted"),
            @ApiResponse(responseCode = "400", description = "Malformed request body", content = @io.swagger.v3.oas.annotations.media.Content()),
            @ApiResponse(responseCode = "422", description = "Exchange rate unavailable", content = @io.swagger.v3.oas.annotations.media.Content())
    })
    public TransactionResponse create(@RequestBody @Valid TransactionRequest request) {
        return transactionService.register(request);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Accept a batch of spending operations",
            description = "Currency conversion runs in parallel across currency groups, while limit_exceeded "
                    + "flags are resolved sequentially in chronological order.")
    public List<TransactionResponse> createBatch(
            @RequestBody @NotEmpty(message = "batch must not be empty")
            @Size(max = 1000, message = "batch must contain at most 1000 transactions")
            List<@Valid TransactionRequest> requests) {
        return batchService.registerAll(requests);
    }
}
