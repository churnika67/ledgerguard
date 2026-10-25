package com.ledgerguard.ledger.api.dto;

import jakarta.validation.constraints.Pattern;

public record StatementRunRequest(
        @Pattern(regexp = "\\d{4}-\\d{2}", message = "must be a yyyy-MM month, e.g. 2026-10") String month) {
}
