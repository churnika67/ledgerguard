package com.ledgerguard.ledger.graphql;

import com.ledgerguard.ledger.service.ReconciliationReport;
import com.ledgerguard.ledger.service.ReconciliationService;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
public class ReconciliationGraphQlController {

    private final ReconciliationService reconciliationService;

    public ReconciliationGraphQlController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @QueryMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ReconciliationReport reconciliation() {
        return reconciliationService.check();
    }
}
