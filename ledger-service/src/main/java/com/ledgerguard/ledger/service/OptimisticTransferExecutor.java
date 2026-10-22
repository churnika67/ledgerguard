package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.exception.RetryExhaustedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retries a transfer under optimistic locking ({@code @Version}) when two concurrent updates
 * collide on the same account. Each attempt runs in its own transaction via
 * {@link OptimisticAttemptRunner}; on a version conflict, or on a Postgres deadlock from two
 * transfers updating the same pair of accounts in opposite order (optimistic locking takes no
 * explicit locks, so it has no fixed acquisition order to prevent this), we back off with
 * jitter and try again, up to {@code ledger.optimistic-retry.max-attempts} times. Both failure
 * modes are instances of Spring's {@link ConcurrencyFailureException}.
 */
@Component
@ConditionalOnProperty(prefix = "ledger", name = "locking", havingValue = "OPTIMISTIC", matchIfMissing = true)
class OptimisticTransferExecutor implements TransferExecutor {

    private static final String STRATEGY = "OPTIMISTIC";
    private static final Logger log = LoggerFactory.getLogger(OptimisticTransferExecutor.class);

    private final OptimisticAttemptRunner attemptRunner;
    private final TransferMetrics metrics;
    private final int maxAttempts;
    private final long baseDelayMs;
    private final long maxDelayMs;

    OptimisticTransferExecutor(OptimisticAttemptRunner attemptRunner, TransferMetrics metrics,
                                org.springframework.core.env.Environment env) {
        this.attemptRunner = attemptRunner;
        this.metrics = metrics;
        this.maxAttempts = env.getProperty("ledger.optimistic-retry.max-attempts", Integer.class, 10);
        this.baseDelayMs = env.getProperty("ledger.optimistic-retry.base-delay-ms", Long.class, 5L);
        this.maxDelayMs = env.getProperty("ledger.optimistic-retry.max-delay-ms", Long.class, 200L);
    }

    @Override
    public TransferExecutionResult execute(TransferCommand command) {
        Instant start = Instant.now();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                TransferExecutionResult result = attemptRunner.attempt(command);
                metrics.recordLatency(STRATEGY, Duration.between(start, Instant.now()));
                return result;
            } catch (ConcurrencyFailureException e) {
                metrics.recordConflict(STRATEGY);
                if (attempt == maxAttempts) {
                    break;
                }
                metrics.recordRetry(STRATEGY);
                sleepWithBackoff(attempt);
                log.debug("Concurrency conflict on transfer {} ({}), retrying (attempt {}/{})",
                        command.transferId(), e.getClass().getSimpleName(), attempt + 1, maxAttempts);
            }
        }
        throw new RetryExhaustedException(maxAttempts);
    }

    private void sleepWithBackoff(int attempt) {
        long exponential = Math.min(maxDelayMs, baseDelayMs * (1L << Math.min(attempt, 20)));
        long jittered = ThreadLocalRandom.current().nextLong(exponential + 1);
        try {
            Thread.sleep(jittered);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off", e);
        }
    }
}
