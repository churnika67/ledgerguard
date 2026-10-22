package com.ledgerguard.ledger.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Micrometer counters/timers exposed at /actuator/prometheus for the PESSIMISTIC-vs-OPTIMISTIC benchmark. */
@Component
class TransferMetrics {

    private final MeterRegistry registry;

    TransferMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void recordRetry(String strategy) {
        Counter.builder("ledger.transfer.retries")
                .tag("strategy", strategy)
                .register(registry)
                .increment();
    }

    void recordConflict(String strategy) {
        Counter.builder("ledger.transfer.conflicts")
                .tag("strategy", strategy)
                .register(registry)
                .increment();
    }

    void recordLatency(String strategy, Duration duration) {
        Timer.builder("ledger.transfer.latency")
                .tag("strategy", strategy)
                .register(registry)
                .record(duration);
    }
}
