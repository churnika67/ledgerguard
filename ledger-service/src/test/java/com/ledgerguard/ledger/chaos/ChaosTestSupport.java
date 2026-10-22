package com.ledgerguard.ledger.chaos;

import com.ledgerguard.ledger.PostgresIntegrationTest;
import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.domain.Transfer;
import com.ledgerguard.ledger.domain.TransferStatus;
import com.ledgerguard.ledger.repository.AccountRepository;
import com.ledgerguard.ledger.repository.LedgerEntryRepository;
import com.ledgerguard.ledger.repository.TransferRepository;
import com.ledgerguard.ledger.service.AccountService;
import com.ledgerguard.ledger.service.ReconciliationReport;
import com.ledgerguard.ledger.service.ReconciliationService;
import com.ledgerguard.ledger.service.TransferRequest;
import com.ledgerguard.ledger.service.TransferService;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives 50 accounts through 10,000 concurrent transfers (10% of them replaying an earlier
 * Idempotency-Key) from a pool of 200 virtual threads, then asserts the ledger's three
 * invariants still hold. Shared by {@link PessimisticChaosTest} and {@link OptimisticChaosTest}
 * so the exact same workload exercises both locking strategies.
 */
abstract class ChaosTestSupport extends PostgresIntegrationTest {

    private static final int ACCOUNT_COUNT = 50;
    private static final int TRANSFER_COUNT = 10_000;
    private static final int VIRTUAL_THREAD_COUNT = 200;
    private static final BigDecimal OPENING_BALANCE = new BigDecimal("5000.00");
    private static final String CURRENCY = "USD";

    @Autowired
    private AccountService accountService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private ReconciliationService reconciliationService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransferRepository transferRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private record SubmittedRequest(String idempotencyKey, UUID fromAccountId, UUID toAccountId,
                                      BigDecimal amount, String currency) {
    }

    protected void runChaosScenario() throws InterruptedException {
        List<UUID> accountIds = openAccounts();
        List<SubmittedRequest> tasks = buildWorkload(accountIds);

        AtomicInteger nextTask = new AtomicInteger(0);
        AtomicLong failures = new AtomicLong(0);
        ExecutorService executor = Executors.newFixedThreadPool(VIRTUAL_THREAD_COUNT, Thread.ofVirtual().factory());

        List<Future<?>> workers = new ArrayList<>();
        for (int w = 0; w < VIRTUAL_THREAD_COUNT; w++) {
            workers.add(executor.submit(() -> {
                int index;
                while ((index = nextTask.getAndIncrement()) < tasks.size()) {
                    SubmittedRequest request = tasks.get(index);
                    try {
                        transferService.submit(new TransferRequest(request.fromAccountId(), request.toAccountId(),
                                request.amount(), request.currency()), request.idempotencyKey());
                    } catch (Exception e) {
                        // RetryExhaustedException (optimistic contention) or a conflict from a
                        // racing replay are both acceptable outcomes: nothing was persisted.
                        failures.incrementAndGet();
                    }
                }
            }));
        }
        executor.shutdown();
        boolean finished = executor.awaitTermination(5, TimeUnit.MINUTES);
        assertThat(finished).as("chaos workload finished within timeout").isTrue();
        for (Future<?> f : workers) {
            assertThat(f).succeedsWithin(java.time.Duration.ofSeconds(1));
        }

        // The Postgres container is a JVM-wide singleton shared with other integration test
        // classes, so every count below is scoped to this run's own Idempotency-Keys rather
        // than read table-wide.
        List<String> keysSubmitted = tasks.stream().map(SubmittedRequest::idempotencyKey).distinct().toList();
        List<Transfer> ownTransfers = transferRepository.findAllByIdempotencyKeyIn(keysSubmitted);

        // At most one Transfer row per unique Idempotency-Key: never more (that would mean a
        // replay or a race created an extra charge), but possibly fewer, since an attempt that
        // exhausts its retries under contention legitimately persists nothing at all.
        assertThat(ownTransfers.size())
                .as("no more Transfer rows than unique Idempotency-Keys, i.e. no extras from replays")
                .isLessThanOrEqualTo(keysSubmitted.size());

        List<UUID> ownCompletedTransferIds = ownTransfers.stream()
                .filter(t -> t.getStatus() == TransferStatus.COMPLETED)
                .map(Transfer::getId)
                .toList();
        long ownLedgerEntryCount = ledgerEntryRepository.countByTransferIdIn(ownCompletedTransferIds);
        assertThat(ownLedgerEntryCount)
                .as("exactly two ledger entries per completed transfer, zero extra from replays")
                .isEqualTo(ownCompletedTransferIds.size() * 2L);

        List<Account> accounts = accountRepository.findAllByIdIn(accountIds);
        assertThat(accounts).allSatisfy(account ->
                assertThat(account.getBalance().signum()).as("no account ever goes negative").isGreaterThanOrEqualTo(0));

        ReconciliationReport report = reconciliationService.check();
        assertThat(report.accountsWithMismatchedBalance()).isEmpty();
        assertThat(report.transfersNotNettingToZero()).isEmpty();
        assertThat(report.totalBalance()).isEqualTo(report.totalOpeningBalance());
        assertThat(report.healthy()).isTrue();
    }

    private List<UUID> openAccounts() {
        List<UUID> ids = new ArrayList<>(ACCOUNT_COUNT);
        for (int i = 0; i < ACCOUNT_COUNT; i++) {
            Account account = accountService.open("chaos-owner-" + i, CURRENCY, OPENING_BALANCE);
            ids.add(account.getId());
        }
        return ids;
    }

    private List<SubmittedRequest> buildWorkload(List<UUID> accountIds) {
        Random random = new Random(42);
        List<SubmittedRequest> tasks = new ArrayList<>(TRANSFER_COUNT);
        for (int i = 0; i < TRANSFER_COUNT; i++) {
            boolean replay = i >= 10 && random.nextInt(10) == 0;
            if (replay) {
                tasks.add(tasks.get(random.nextInt(tasks.size())));
                continue;
            }
            UUID from = accountIds.get(random.nextInt(accountIds.size()));
            UUID to;
            do {
                to = accountIds.get(random.nextInt(accountIds.size()));
            } while (to.equals(from));
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(10_000), 2);
            tasks.add(new SubmittedRequest(UUID.randomUUID().toString(), from, to, amount, CURRENCY));
        }
        return tasks;
    }
}
