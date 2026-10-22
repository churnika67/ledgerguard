package com.ledgerguard.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Request to move money from one account to another")
public record TransferRequestDto(

        @NotNull UUID fromAccountId,

        @NotNull UUID toAccountId,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount must be positive")
        BigDecimal amount,

        @NotNull
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO-4217 code")
        String currency
) {
}
