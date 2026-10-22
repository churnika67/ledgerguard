package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.exception.AccountNotFoundException;
import com.ledgerguard.ledger.repository.AccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs exactly one optimistic-locking attempt in its own fresh transaction (REQUIRES_NEW), so a
 * failed attempt never reuses state from a previous one and the caller can retry cleanly. A
 * separate bean is required here: an {@code @Transactional} method only gets Spring's proxy
 * behavior when invoked through the bean, not via self-invocation from {@link OptimisticTransferExecutor}.
 */
@Component
class OptimisticAttemptRunner {

    private final AccountRepository accountRepository;
    private final LedgerMutationService ledgerMutationService;

    OptimisticAttemptRunner(AccountRepository accountRepository, LedgerMutationService ledgerMutationService) {
        this.accountRepository = accountRepository;
        this.ledgerMutationService = ledgerMutationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransferExecutionResult attempt(TransferCommand command) {
        Account from = accountRepository.findById(command.fromAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.fromAccountId()));
        Account to = accountRepository.findById(command.toAccountId())
                .orElseThrow(() -> new AccountNotFoundException(command.toAccountId()));

        TransferExecutionResult result = ledgerMutationService.apply(command, from, to);
        accountRepository.flush();
        return result;
    }
}
