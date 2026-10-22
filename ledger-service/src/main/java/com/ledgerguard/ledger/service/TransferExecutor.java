package com.ledgerguard.ledger.service;

/**
 * Moves money for one transfer command and durably records the outcome (Transfer row,
 * LedgerEntry pair when completed, and an OutboxEvent) atomically. Exactly one implementation
 * is active at a time, chosen by {@code ledger.locking}.
 */
public interface TransferExecutor {

    TransferExecutionResult execute(TransferCommand command);
}
