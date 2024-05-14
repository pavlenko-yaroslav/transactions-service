package ru.yaroslav_pavlenko.TransactionsRestApi.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yaroslav_pavlenko.TransactionsRestApi.config.LimitProperties;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitRequest;
import ru.yaroslav_pavlenko.TransactionsRestApi.dto.LimitResponse;
import ru.yaroslav_pavlenko.TransactionsRestApi.enums.ExpenseCategory;
import ru.yaroslav_pavlenko.TransactionsRestApi.mappers.TransactionMapper;
import ru.yaroslav_pavlenko.TransactionsRestApi.models.Limit;
import ru.yaroslav_pavlenko.TransactionsRestApi.repositories.jpa.LimitRepository;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class LimitService {

    private final LimitRepository limitRepository;
    private final TransactionMapper mapper;
    private final LimitProperties properties;
    private final Clock clock;

    /**
     * Establishes a new limit. The establishment date is always the current time: the client
     * cannot supply it, and existing limits are never updated — every call appends a row, so
     * the limit history stays intact.
     */
    @Transactional
    public LimitResponse create(LimitRequest request) {
        Limit limit = Limit.builder()
                .accountNumber(request.accountNumber())
                .limitSum(request.limitSum())
                .limitCurrencyShortname(properties.currency())
                .expenseCategory(request.expenseCategory())
                .limitDatetime(OffsetDateTime.now(clock))
                .build();

        Limit saved = limitRepository.save(limit);
        log.info("Limit {} {} set for account {} category {}",
                saved.getLimitSum(), saved.getLimitCurrencyShortname(),
                saved.getAccountNumber(), saved.getExpenseCategory());

        return mapper.toResponse(saved);
    }

    public List<LimitResponse> findAll(Long accountNumber, ExpenseCategory category) {
        List<Limit> limits = category == null
                ? limitRepository.findAllByAccountNumberOrderByLimitDatetimeDesc(accountNumber)
                : limitRepository.findAllByAccountNumberAndExpenseCategoryOrderByLimitDatetimeDesc(accountNumber, category);

        return limits.stream().map(mapper::toResponse).toList();
    }

    /**
     * The limit in force at the moment of the operation. When the client never established one,
     * a transient default limit is returned — 1000 USD, dated at the start of the operation month.
     */
    public Limit effectiveLimit(Long accountNumber, ExpenseCategory category, OffsetDateTime at) {
        Optional<Limit> stored = limitRepository
                .findFirstByAccountNumberAndExpenseCategoryAndLimitDatetimeLessThanEqualOrderByLimitDatetimeDescIdDesc(
                        accountNumber, category, at);

        return stored.orElseGet(() -> defaultLimit(accountNumber, category, at));
    }

    public Limit defaultLimit(Long accountNumber, ExpenseCategory category, OffsetDateTime at) {
        return Limit.builder()
                .accountNumber(accountNumber)
                .limitSum(properties.defaultSum())
                .limitCurrencyShortname(properties.currency())
                .expenseCategory(category)
                .limitDatetime(LimitEvaluator.monthStart(at))
                .build();
    }
}
