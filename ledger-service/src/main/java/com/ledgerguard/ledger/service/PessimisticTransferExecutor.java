package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.exception.AccountNotFoundException;
import com.ledgerguard.ledger.repository.AccountRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Locks both accounts with {@code SELECT ... FOR UPDATE} inside one transaction, always taking
 * the locks in ascending account-id order regardless of which account is "from" or "to" so two
 * transfers between the same pair of accounts can never deadlock on each other.
 */
@Component
@ConditionalOnProperty(prefix = "ledger", name = "locking", havingValue = "PESSIMISTIC")
class PessimisticTransferExecutor implements TransferExecutor {

    private static final String STRATEGY = "PESSIMISTIC";

    private final AccountRepository accountRepository;
    private final LedgerMutationService ledgerMutationService;
    private final TransferMetrics metrics;

    PessimisticTransferExecutor(AccountRepository accountRepository, LedgerMutationService ledgerMutationService,
                                 TransferMetrics metrics) {
        this.accountRepository = accountRepository;
        this.ledgerMutationService = ledgerMutationService;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public TransferExecutionResult execute(TransferCommand command) {
        Instant start = Instant.now();
        UUID first = command.fromAccountId().compareTo(command.toAccountId()) <= 0
                ? command.fromAccountId() : command.toAccountId();
        UUID second = first.equals(command.fromAccountId()) ? command.toAccountId() : command.fromAccountId();

        Account lockedFirst = accountRepository.findByIdForUpdate(first)
                .orElseThrow(() -> new AccountNotFoundException(first));
        Account lockedSecond = accountRepository.findByIdForUpdate(second)
                .orElseThrow(() -> new AccountNotFoundException(second));

        Account from = lockedFirst.getId().equals(command.fromAccountId()) ? lockedFirst : lockedSecond;
        Account to = lockedFirst.getId().equals(command.toAccountId()) ? lockedFirst : lockedSecond;

        TransferExecutionResult result = ledgerMutationService.apply(command, from, to);
        metrics.recordLatency(STRATEGY, Duration.between(start, Instant.now()));
        return result;
    }
}
