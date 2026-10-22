package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.domain.Transfer;
import com.ledgerguard.ledger.exception.IdempotencyConflictException;
import com.ledgerguard.ledger.repository.TransferRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for moving money between two accounts. Guarantees, regardless of which
 * {@link TransferExecutor} is active: a transfer with a given Idempotency-Key is applied at
 * most once; a replay of the same key and body returns the original outcome with no new ledger
 * entries; and reuse of a key with a different body is rejected as a conflict.
 */
@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final TransferExecutor executor;

    public TransferService(TransferRepository transferRepository, TransferExecutor executor) {
        this.transferRepository = transferRepository;
        this.executor = executor;
    }

    public TransferResult submit(TransferRequest request, String idempotencyKey) {
        String requestHash = RequestHasher.hash(request.fromAccountId(), request.toAccountId(),
                request.amount(), request.currency());

        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return resolveExisting(existing.get(), requestHash);
        }

        TransferCommand command = new TransferCommand(UUID.randomUUID(), idempotencyKey, requestHash,
                request.fromAccountId(), request.toAccountId(), request.amount(), request.currency(),
                Instant.now());

        try {
            TransferExecutionResult result = executor.execute(command);
            return toResult(command, result, false);
        } catch (DataIntegrityViolationException raceOnIdempotencyKey) {
            Transfer raced = transferRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> raceOnIdempotencyKey);
            return resolveExisting(raced, requestHash);
        }
    }

    public Optional<Transfer> findById(UUID transferId) {
        return transferRepository.findById(transferId);
    }

    private TransferResult resolveExisting(Transfer existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(existing.getIdempotencyKey());
        }
        return new TransferResult(existing.getId(), existing.getStatus(), existing.getRejectReason(),
                existing.getFromAccountId(), existing.getToAccountId(), existing.getAmount(),
                existing.getCurrency(), existing.getCreatedAt(), true);
    }

    private TransferResult toResult(TransferCommand command, TransferExecutionResult result, boolean replayed) {
        String reason = result.rejectReason() == null ? null : result.rejectReason().name();
        return new TransferResult(command.transferId(), result.status(), reason, command.fromAccountId(),
                command.toAccountId(), command.amount(), command.currency(), command.createdAt(), replayed);
    }
}
