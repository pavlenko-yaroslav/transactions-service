package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionResponse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Batch ingestion with parallel currency conversion.
 * <p>
 * Only the order-independent part runs in parallel: fetching rates and converting amounts to
 * USD, grouped by currency. Raising {@code limit_exceeded} stays sequential and follows
 * chronological order, because the accumulated monthly spend depends on preceding operations.
 */
@Service
@Slf4j
public class TransactionBatchService {

    private final TransactionService transactionService;
    private final CurrencyConverter currencyConverter;
    private final Executor currencyExecutor;

    public TransactionBatchService(TransactionService transactionService,
                                   CurrencyConverter currencyConverter,
                                   @Qualifier("currencyExecutor") Executor currencyExecutor) {
        this.transactionService = transactionService;
        this.currencyConverter = currencyConverter;
        this.currencyExecutor = currencyExecutor;
    }

    public List<TransactionResponse> registerAll(List<TransactionRequest> requests) {
        Map<Integer, BigDecimal> amountsInUsd = convertInParallel(requests);

        List<Integer> chronologically = new ArrayList<>(amountsInUsd.keySet());
        chronologically.sort(Comparator.comparing(index -> requests.get(index).datetime()));

        List<TransactionResponse> responses = new ArrayList<>(requests.size());
        for (Integer index : chronologically) {
            responses.add(transactionService.register(requests.get(index), amountsInUsd.get(index)));
        }
        return responses;
    }

    /**
     * Operations are grouped by currency so each rate is looked up once, and the groups are
     * processed in parallel.
     */
    private Map<Integer, BigDecimal> convertInParallel(List<TransactionRequest> requests) {
        Map<String, List<Integer>> byCurrency = new LinkedHashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            byCurrency.computeIfAbsent(requests.get(i).currencyShortname(), key -> new ArrayList<>()).add(i);
        }

        List<CompletableFuture<Map<Integer, BigDecimal>>> futures = byCurrency.entrySet().stream()
                .map(entry -> CompletableFuture.supplyAsync(
                        () -> convertGroup(entry.getKey(), entry.getValue(), requests), currencyExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<Integer, BigDecimal> convertGroup(String currency, List<Integer> indexes,
                                                  List<TransactionRequest> requests) {
        log.debug("Converting {} transaction(s) in {}", indexes.size(), currency);

        Map<Integer, BigDecimal> converted = new LinkedHashMap<>();
        for (Integer index : indexes) {
            TransactionRequest request = requests.get(index);
            converted.put(index, currencyConverter.toBaseCurrency(request.sum(), currency, request.datetime()));
        }
        return converted;
    }
}
