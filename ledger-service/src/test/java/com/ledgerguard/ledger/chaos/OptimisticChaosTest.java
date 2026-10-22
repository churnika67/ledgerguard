package com.ledgerguard.ledger.chaos;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ledger.locking=OPTIMISTIC", "ledger.kafka.enabled=false"})
class OptimisticChaosTest extends ChaosTestSupport {

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void tenThousandConcurrentTransfersPreserveAllInvariantsUnderOptimisticLocking() throws InterruptedException {
        runChaosScenario();
    }
}
