package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.domain.RejectReason;
import com.ledgerguard.ledger.domain.TransferStatus;

/** Outcome of one committed attempt: either COMPLETED, or REJECTED for a business reason. */
public record TransferExecutionResult(TransferStatus status, RejectReason rejectReason) {

    public static TransferExecutionResult completed() {
        return new TransferExecutionResult(TransferStatus.COMPLETED, null);
    }

    public static TransferExecutionResult rejected(RejectReason reason) {
        return new TransferExecutionResult(TransferStatus.REJECTED, reason);
    }
}
