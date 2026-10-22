package com.ledgerguard.ledger.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives a bounded, admin-triggered burst of concurrent transfers against fresh demo accounts
 * and reports throughput/latency/invariant results -- the same workload shape as the
 * PESSIMISTIC-vs-OPTIMISTIC chaos test in the test suite, exposed as an ops endpoint so the
 * admin console can run it on demand against whichever strategy is live.
 *
 * <p>Each run executes on its own virtual thread (kicked off from {@link #start}, which returns
 * immediately), and its progress is tracked in an in-memory registry keyed by run id -- runs are
 * a demo/ops feature, not durable business records, so they don't need a database table and are
 * lost on restart, which is an acceptable tradeoff here.
 */
@Service
public class ChaosRunService {

    private static final Logger log = LoggerFactory.getLogger(ChaosRunService.class);
    private static final int MAX_TRANSFER_COUNT = 5000;
    private static final BigDecimal OPENING_BALANCE = new BigDecimal("5000.00");
    private static final String CURRENCY = "USD";

    private final AccountService accountService;
    private final TransferService transferService;
    private final ReconciliationService reconciliationService;
    private final MeterRegistry meterRegistry;
    private final String strategy;
    private final ExecutorService orchestrationExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<UUID, ChaosRunState> runs = new ConcurrentHashMap<>();

    public ChaosRunService(AccountService accountService, TransferService transferService,
                            ReconciliationService reconciliationService, MeterRegistry meterRegistry,
                            @Value("${ledger.locking}") String strategy) {
        this.accountService = accountService;
        this.transferService = transferService;
        this.reconciliationService = reconciliationService;
        this.meterRegistry = meterRegistry;
        this.strategy = strategy;
    }

    public ChaosRunState start(int transferCount, int accountCount, int concurrency) {
        if (transferCount > MAX_TRANSFER_COUNT) {
            throw new IllegalArgumentException("transferCount must not exceed " + MAX_TRANSFER_COUNT);
        }
        UUID id = UUID.randomUUID();
        ChaosRunState initial = ChaosRunState.starting(id, strategy, transferCount, accountCount, concurrency);
        runs.put(id, initial);
        orchestrationExecutor.submit(() -> run(initial, transferCount, accountCount, concurrency));
        return initial;
    }

    public Optional<ChaosRunState> find(UUID id) {
        return Optional.ofNullable(runs.get(id));
    }

    public List<ChaosRunState> listRecent() {
        return runs.values().stream()
                .sorted((a, b) -> b.startedAt().compareTo(a.startedAt()))
                .limit(20)
                .toList();
    }

    private void run(ChaosRunState initial, int transferCount, int accountCount, int concurrency) {
        try {
            long retriesBefore = counterValue("ledger.transfer.retries");
            long conflictsBefore = counterValue("ledger.transfer.conflicts");

            List<UUID> accountIds = new ArrayList<>(accountCount);
            String ownerPrefix = "chaos-" + initial.id().toString().substring(0, 8);
            for (int i = 0; i < accountCount; i++) {
                accountIds.add(accountService.open(ownerPrefix + "-" + i, CURRENCY, OPENING_BALANCE).getId());
            }

            AtomicInteger nextTask = new AtomicInteger(0);
            AtomicLong failures = new AtomicLong(0);
            ConcurrentLinkedQueue<Long> latenciesMillis = new ConcurrentLinkedQueue<>();
            Random random = new Random();

            ExecutorService workExecutor = Executors.newFixedThreadPool(concurrency, Thread.ofVirtual().factory());
            Instant start = Instant.now();
            List<java.util.concurrent.Future<?>> workers = new ArrayList<>();
            for (int w = 0; w < concurrency; w++) {
                workers.add(workExecutor.submit(() -> {
                    while (nextTask.getAndIncrement() < transferCount) {
                        UUID from = accountIds.get(pick(random, accountIds.size()));
                        UUID to;
                        do {
                            to = accountIds.get(pick(random, accountIds.size()));
                        } while (to.equals(from));
                        BigDecimal amount = BigDecimal.valueOf(1 + pick(random, 10_000), 2);
                        Instant callStart = Instant.now();
                        try {
                            transferService.submit(new TransferRequest(from, to, amount, CURRENCY),
                                    "chaos-" + UUID.randomUUID());
                        } catch (Exception e) {
                            failures.incrementAndGet();
                        }
                        latenciesMillis.add(Duration.between(callStart, Instant.now()).toMillis());
                    }
                }));
            }
            workExecutor.shutdown();
            workExecutor.awaitTermination(5, TimeUnit.MINUTES);
            Duration elapsed = Duration.between(start, Instant.now());

            List<Long> sorted = new ArrayList<>(latenciesMillis);
            Collections.sort(sorted);
            long p95 = sorted.isEmpty() ? 0 : sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
            double transfersPerSecond = transferCount / Math.max(elapsed.toMillis() / 1000.0, 0.001);

            long retries = counterValue("ledger.transfer.retries") - retriesBefore;
            long conflicts = counterValue("ledger.transfer.conflicts") - conflictsBefore;

            ReconciliationReport report = reconciliationService.check();
            runs.put(initial.id(),
                    initial.completed(transfersPerSecond, p95, retries, conflicts, failures.get(), report));
        } catch (Exception e) {
            log.error("Chaos run {} failed", initial.id(), e);
            runs.put(initial.id(), initial.failed(e.getMessage()));
        }
    }

    private long counterValue(String name) {
        var counter = meterRegistry.find(name).tag("strategy", strategy).counter();
        return counter == null ? 0 : (long) counter.count();
    }

    /** Non-synchronized java.util.Random is not thread-safe; this run doesn't need it to be exact. */
    private static int pick(Random random, int bound) {
        synchronized (random) {
            return random.nextInt(bound);
        }
    }
}
