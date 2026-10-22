package com.ledgerguard.ledger.benchmark;

import com.ledgerguard.ledger.PostgresIntegrationTest;
import com.ledgerguard.ledger.domain.TransferStatus;
import com.ledgerguard.ledger.service.AccountService;
import com.ledgerguard.ledger.service.TransferRequest;
import com.ledgerguard.ledger.service.TransferResult;
import com.ledgerguard.ledger.service.TransferService;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Measures raw transfer throughput and latency for whichever locking strategy the concrete
 * subclass's Spring context is wired with. Uses generously-funded accounts and fresh
 * Idempotency-Keys throughout, so the numbers reflect steady-state transfer cost rather than
 * idempotency-replay or rejection-path overhead (those are already covered by the chaos test).
 */
abstract class BenchmarkSupport extends PostgresIntegrationTest {

    private static final int ACCOUNT_COUNT = 20;
    private static final int TRANSFER_COUNT = 3000;
    private static final int CONCURRENCY = 50;
    private static final BigDecimal OPENING_BALANCE = new BigDecimal("1000000.00");
    private static final String CURRENCY = "USD";

    @Autowired
    private AccountService accountService;
    @Autowired
    private TransferService transferService;

    protected record BenchmarkResult(String strategy, long transferCount, double transfersPerSecond,
                                      long p95LatencyMillis, long retries, long conflicts, long failures) {
    }

    protected BenchmarkResult runBenchmark(String strategyLabel) throws InterruptedException {
        List<UUID> accountIds = new ArrayList<>(ACCOUNT_COUNT);
        for (int i = 0; i < ACCOUNT_COUNT; i++) {
            accountIds.add(accountService.open("bench-owner-" + i, CURRENCY, OPENING_BALANCE).getId());
        }

        AtomicInteger nextTask = new AtomicInteger(0);
        AtomicLong failures = new AtomicLong(0);
        ConcurrentLinkedQueue<Long> latenciesMillis = new ConcurrentLinkedQueue<>();
        java.util.Random random = new java.util.Random(7);

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY, Thread.ofVirtual().factory());
        List<Future<?>> workers = new ArrayList<>();
        Instant start = Instant.now();
        for (int w = 0; w < CONCURRENCY; w++) {
            workers.add(executor.submit(() -> {
                int index;
                while ((index = nextTask.getAndIncrement()) < TRANSFER_COUNT) {
                    UUID from = accountIds.get(ThreadLocalIndex.pick(random, accountIds.size()));
                    UUID to;
                    do {
                        to = accountIds.get(ThreadLocalIndex.pick(random, accountIds.size()));
                    } while (to.equals(from));
                    Instant callStart = Instant.now();
                    try {
                        TransferResult result = transferService.submit(
                                new TransferRequest(from, to, new BigDecimal("1.00"), CURRENCY),
                                "bench-" + UUID.randomUUID());
                        if (result.status() != TransferStatus.COMPLETED) {
                            failures.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    }
                    latenciesMillis.add(Duration.between(callStart, Instant.now()).toMillis());
                }
            }));
        }
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);
        Duration elapsed = Duration.between(start, Instant.now());

        List<Long> sorted = new ArrayList<>(latenciesMillis);
        Collections.sort(sorted);
        long p95 = sorted.isEmpty() ? 0 : sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
        double transfersPerSecond = TRANSFER_COUNT / Math.max(elapsed.toMillis() / 1000.0, 0.001);

        return new BenchmarkResult(strategyLabel, TRANSFER_COUNT, transfersPerSecond, p95,
                counterValue("ledger.transfer.retries", strategyLabel),
                counterValue("ledger.transfer.conflicts", strategyLabel),
                failures.get());
    }

    @Autowired
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    private long counterValue(String name, String strategy) {
        var counter = meterRegistry.find(name).tag("strategy", strategy).counter();
        return counter == null ? 0 : (long) counter.count();
    }

    /** Small helper so each worker gets an independent-ish pseudo-random pick without extra state. */
    private static final class ThreadLocalIndex {
        static int pick(java.util.Random random, int bound) {
            synchronized (random) {
                return random.nextInt(bound);
            }
        }
    }
}
