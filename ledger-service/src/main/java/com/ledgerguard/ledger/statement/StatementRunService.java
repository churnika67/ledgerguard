package com.ledgerguard.ledger.statement;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.YearMonth;

@Service
public class StatementRunService {

    private final JobLauncher jobLauncher;
    private final Job monthlyStatementJob;

    public StatementRunService(JobLauncher jobLauncher, Job monthlyStatementJob) {
        this.jobLauncher = jobLauncher;
        this.monthlyStatementJob = monthlyStatementJob;
    }

    public JobExecution run(YearMonth month) {
        try {
            var params = new JobParametersBuilder()
                    .addString("month", month.toString())
                    // Makes each run a distinct job instance even for the same month -- Spring
                    // Batch otherwise refuses to re-run a job instance that already completed.
                    .addLong("runAt", Instant.now().toEpochMilli())
                    .toJobParameters();
            return jobLauncher.run(monthlyStatementJob, params);
        } catch (Exception e) {
            throw new StatementRunFailedException(month, e);
        }
    }

    public static final class StatementRunFailedException extends RuntimeException {
        public StatementRunFailedException(YearMonth month, Throwable cause) {
            super("Failed to run the statement job for " + month, cause);
        }
    }
}
