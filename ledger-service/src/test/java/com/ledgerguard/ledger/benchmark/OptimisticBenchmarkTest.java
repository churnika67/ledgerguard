package com.ledgerguard.ledger.benchmark;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("benchmark")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ledger.locking=OPTIMISTIC", "ledger.kafka.enabled=false"})
class OptimisticBenchmarkTest extends BenchmarkSupport {

    @Test
    void measureThroughputAndLatency() throws Exception {
        BenchmarkResult result = runBenchmark("OPTIMISTIC");
        BenchmarkReport.write(result);
    }
}
