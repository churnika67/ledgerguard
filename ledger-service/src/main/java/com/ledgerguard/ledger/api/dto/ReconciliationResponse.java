package com.ledgerguard.ledger.api.dto;

import com.ledgerguard.ledger.service.ReconciliationReport;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReconciliationResponse(boolean healthy, BigDecimal totalBalance, BigDecimal totalOpeningBalance,
                                      List<UUID> accountsWithMismatchedBalance,
                                      List<UUID> transfersNotNettingToZero) {

    public static ReconciliationResponse from(ReconciliationReport report) {
        return new ReconciliationResponse(report.healthy(), report.totalBalance(), report.totalOpeningBalance(),
                report.accountsWithMismatchedBalance(), report.transfersNotNettingToZero());
    }
}
