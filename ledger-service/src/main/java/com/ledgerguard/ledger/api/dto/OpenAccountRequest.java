package com.ledgerguard.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

@Schema(description = "Request to open a new account with a starting balance")
public record OpenAccountRequest(

        @NotBlank
        @Schema(example = "Ada Lovelace")
        String ownerName,

        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO-4217 code")
        @Schema(example = "USD")
        String currency,

        @NotNull
        @DecimalMin(value = "0.00", message = "openingBalance must not be negative")
        @Schema(example = "1000.00")
        BigDecimal openingBalance
) {
}
