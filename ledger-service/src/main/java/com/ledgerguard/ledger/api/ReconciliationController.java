package com.ledgerguard.ledger.api;

import com.ledgerguard.ledger.api.dto.ReconciliationResponse;
import com.ledgerguard.ledger.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reconciliation")
@Tag(name = "Reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping
    @Operation(summary = "Check the ledger's money-conservation invariants (ADMIN only)")
    public ReconciliationResponse reconcile() {
        return ReconciliationResponse.from(reconciliationService.check());
    }
}
