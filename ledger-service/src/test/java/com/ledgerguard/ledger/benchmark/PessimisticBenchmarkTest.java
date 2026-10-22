package com.ledgerguard.ledger.benchmark;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("benchmark")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ledger.locking=PESSIMISTIC", "ledger.kafka.enabled=false"})
class PessimisticBenchmarkTest extends BenchmarkSupport {

    @Test
    void measureThroughputAndLatency() throws Exception {
        BenchmarkResult result = runBenchmark("PESSIMISTIC");
        BenchmarkReport.write(result);
    }
}
