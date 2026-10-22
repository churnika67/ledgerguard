package com.ledgerguard.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Demo credentials. Try alice/password123 (USER) or admin/admin123 (ADMIN).")
public record AuthRequest(
        @NotBlank @Schema(example = "alice") String username,
        @NotBlank @Schema(example = "password123") String password
) {
}
