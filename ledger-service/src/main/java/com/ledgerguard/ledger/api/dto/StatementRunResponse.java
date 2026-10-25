package com.ledgerguard.ledger.api.dto;

import org.springframework.batch.core.JobExecution;

public record StatementRunResponse(Long jobExecutionId, String status, String exitCode, long statementsWritten) {

    public static StatementRunResponse from(JobExecution execution) {
        long written = execution.getStepExecutions().stream().mapToLong(se -> se.getWriteCount()).sum();
        return new StatementRunResponse(execution.getId(), execution.getStatus().name(),
                execution.getExitStatus().getExitCode(), written);
    }
}
