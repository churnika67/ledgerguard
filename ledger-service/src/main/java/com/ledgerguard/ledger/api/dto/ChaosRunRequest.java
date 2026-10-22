package com.ledgerguard.ledger.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Parameters for a bounded concurrent chaos run against fresh demo accounts")
public record ChaosRunRequest(

        @NotNull
        @Min(1)
        @Max(5000)
        @Schema(description = "Number of concurrent transfer attempts, capped at 5000", example = "500")
        Integer transferCount,

        @NotNull
        @Min(2)
        @Max(100)
        @Schema(description = "Number of fresh accounts the transfers are spread across", example = "20")
        Integer accountCount,

        @NotNull
        @Min(1)
        @Max(200)
        @Schema(description = "Number of virtual threads driving the workload concurrently", example = "50")
        Integer concurrency
) {
}
