package com.ledgerguard.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledgerguard.common.event.LedgerEventType;
import com.ledgerguard.common.event.TransferCompletedEvent;
import com.ledgerguard.common.event.TransferRejectedEvent;
import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.domain.Direction;
import com.ledgerguard.ledger.domain.LedgerEntry;
import com.ledgerguard.ledger.domain.RejectReason;
import com.ledgerguard.ledger.domain.Transfer;
import com.ledgerguard.ledger.outbox.OutboxEvent;
import com.ledgerguard.ledger.outbox.OutboxEventRepository;
import com.ledgerguard.ledger.repository.AccountRepository;
import com.ledgerguard.ledger.repository.LedgerEntryRepository;
import com.ledgerguard.ledger.repository.TransferRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single place that turns a validated {@link TransferCommand} plus two already-loaded
 * accounts into a durable outcome. Both locking strategies call this from inside their own
 * transaction so the accounts, the ledger entries, the transfer row and the outbox row are
 * written atomically no matter which strategy is active.
 */
@Component
class LedgerMutationService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    LedgerMutationService(AccountRepository accountRepository, TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository, OutboxEventRepository outboxEventRepository,
                           ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates and applies the transfer against the given accounts, then persists the
     * Transfer, any LedgerEntry rows, and the OutboxEvent. Throws only on serialization
     * failure (never expected) or on the unique-key violation handled by the caller.
     */
    TransferExecutionResult apply(TransferCommand command, Account from, Account to) {
        RejectReason rejectReason = validate(command, from, to);
        if (rejectReason != null) {
            return reject(command, rejectReason);
        }

        try {
            from.debit(command.amount());
        } catch (Account.InsufficientFundsException e) {
            return reject(command, RejectReason.INSUFFICIENT_FUNDS);
        }
        to.credit(command.amount());
        accountRepository.save(from);
        accountRepository.save(to);

        ledgerEntryRepository.save(new LedgerEntry(UUID.randomUUID(), command.transferId(), from.getId(),
                Direction.DEBIT, command.amount(), command.createdAt()));
        ledgerEntryRepository.save(new LedgerEntry(UUID.randomUUID(), command.transferId(), to.getId(),
                Direction.CREDIT, command.amount(), command.createdAt()));

        transferRepository.save(Transfer.completed(command.transferId(), command.idempotencyKey(),
                command.requestHash(), command.fromAccountId(), command.toAccountId(), command.amount(),
                command.currency(), command.createdAt()));

        UUID eventId = UUID.randomUUID();
        TransferCompletedEvent event = new TransferCompletedEvent(eventId, command.transferId(),
                command.fromAccountId(), command.toAccountId(), command.amount(), command.currency(),
                command.createdAt());
        outboxEventRepository.save(new OutboxEvent(eventId, command.transferId(),
                LedgerEventType.TRANSFER_COMPLETED.name(), writeJson(event), command.createdAt()));

        return TransferExecutionResult.completed();
    }

    private RejectReason validate(TransferCommand command, Account from, Account to) {
        if (command.fromAccountId().equals(command.toAccountId())) {
            return RejectReason.SAME_ACCOUNT;
        }
        if (!from.getCurrency().equals(command.currency()) || !to.getCurrency().equals(command.currency())) {
            return RejectReason.CURRENCY_MISMATCH;
        }
        return null;
    }

    private TransferExecutionResult reject(TransferCommand command, RejectReason reason) {
        transferRepository.save(Transfer.rejected(command.transferId(), command.idempotencyKey(),
                command.requestHash(), command.fromAccountId(), command.toAccountId(), command.amount(),
                command.currency(), reason.name(), command.createdAt()));

        UUID eventId = UUID.randomUUID();
        TransferRejectedEvent event = new TransferRejectedEvent(eventId, command.transferId(),
                command.fromAccountId(), command.toAccountId(), command.amount(), command.currency(),
                reason.name(), command.createdAt());
        outboxEventRepository.save(new OutboxEvent(eventId, command.transferId(),
                LedgerEventType.TRANSFER_REJECTED.name(), writeJson(event), command.createdAt()));

        return TransferExecutionResult.rejected(reason);
    }

    private String writeJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
