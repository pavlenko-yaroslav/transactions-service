package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.ExceededTransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.TransactionResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.mappers.TransactionMapper;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Limit;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Transaction;
import ru.yaroslav_pavlenko.TransactionsRestApi.repositories.jpa.TransactionRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Ingests spending operations and resolves the {@code limit_exceeded} flag.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LimitService limitService;
    private final CurrencyConverter currencyConverter;
    private final TransactionMapper mapper;

    /**
     * Registers an operation: converts the amount to USD at the rate of the spending day,
     * compares the accumulated monthly spend in that category against the limit in force
     * and stores the outcome.
     * <p>
     * The calculation and the write share one database transaction, so concurrent operations
     * on the same account never observe each other's intermediate state.
     */
    @Transactional
    public TransactionResponse register(TransactionRequest request) {
        return register(request, currencyConverter.toBaseCurrency(request.sum(), request.currencyShortname(),
                request.datetime()));
    }

    /**
     * The same ingestion path with a pre-computed USD amount, used by batch processing which
     * converts currencies in parallel before entering the database transaction.
     */
    @Transactional
    public TransactionResponse register(TransactionRequest request, BigDecimal sumUsd) {
        Limit limit = limitService.effectiveLimit(request.accountFrom(), request.expenseCategory(),
                request.datetime());

        BigDecimal spentBefore = transactionRepository.sumSpentUsd(
                request.accountFrom(),
                request.expenseCategory(),
                LimitEvaluator.monthStart(request.datetime()),
                LimitEvaluator.monthEnd(request.datetime()));

        boolean exceeded = LimitEvaluator.isExceeded(spentBefore, sumUsd, limit.getLimitSum());

        Transaction transaction = Transaction.builder()
                .accountFrom(request.accountFrom())
                .accountTo(request.accountTo())
                .currencyShortname(request.currencyShortname().toUpperCase())
                .sum(request.sum())
                .sumUsd(sumUsd)
                .expenseCategory(request.expenseCategory())
                .datetime(request.datetime())
                .limitExceeded(exceeded)
                // The default limit is not persisted, so the reference stays empty.
                .appliedLimit(limit.getId() == null ? null : limit)
                .build();

        Transaction saved = transactionRepository.save(transaction);

        log.info("Transaction {} for account {} registered: {} {} ({} USD), spent before {} USD, limit {} USD, exceeded={}",
                saved.getId(), saved.getAccountFrom(), saved.getSum(), saved.getCurrencyShortname(), sumUsd,
                spentBefore, limit.getLimitSum(), exceeded);

        return mapper.toResponse(saved);
    }

    public List<TransactionResponse> findByAccount(Long accountNumber) {
        return transactionRepository.findAllByAccountFromOrderByDatetimeAsc(accountNumber).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Transactions that exceeded their limit, together with the parameters of that limit.
     */
    public List<ExceededTransactionResponse> findExceeded(Long accountNumber, ExpenseCategory category) {
        List<Transaction> exceeded = category == null
                ? transactionRepository.findExceededByAccount(accountNumber)
                : transactionRepository.findExceededByAccountAndCategory(accountNumber, category);

        return exceeded.stream()
                .map(transaction -> mapper.toExceededResponse(transaction, limitOf(transaction)))
                .toList();
    }

    /**
     * The limit a transaction exceeded. For operations covered by the default limit it is
     * reconstructed from configuration.
     */
    private Limit limitOf(Transaction transaction) {
        Limit applied = transaction.getAppliedLimit();
        if (applied != null) {
            return applied;
        }
        return limitService.defaultLimit(transaction.getAccountFrom(), transaction.getExpenseCategory(),
                transaction.getDatetime());
    }

    /**
     * Remaining monthly limit for a category at the given moment.
     */
    public BigDecimal remainingLimit(Long accountNumber, ExpenseCategory category, OffsetDateTime at) {
        Limit limit = limitService.effectiveLimit(accountNumber, category, at);
        BigDecimal spent = transactionRepository.sumSpentUsd(accountNumber, category,
                LimitEvaluator.monthStart(at), LimitEvaluator.monthEnd(at));

        return limit.getLimitSum().subtract(spent);
    }
}
