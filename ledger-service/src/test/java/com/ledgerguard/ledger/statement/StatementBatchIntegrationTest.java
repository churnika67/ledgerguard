package com.ledgerguard.ledger.statement;

import com.ledgerguard.ledger.PostgresIntegrationTest;
import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.service.AccountService;
import com.ledgerguard.ledger.service.TransferRequest;
import com.ledgerguard.ledger.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real monthly statement job (real Spring Batch job, real reader/processor/writer)
 * against a LocalStack S3 instead of real AWS, proving an account's statement for the current
 * month ends up in the bucket with the right opening/closing balances once a transfer is posted.
 */
class StatementBatchIntegrationTest extends PostgresIntegrationTest {

    private static final String BUCKET = "ledgerguard-statements-test";

    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.8"))
                    .withServices(LocalStackContainer.Service.S3);

    static {
        LOCALSTACK.start();
    }

    @DynamicPropertySource
    static void localstackProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.region.static", LOCALSTACK::getRegion);
        registry.add("spring.cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
        registry.add("spring.cloud.aws.s3.endpoint",
                () -> LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        registry.add("spring.cloud.aws.s3.path-style-access-enabled", () -> "true");
        registry.add("ledger.statements.bucket", () -> BUCKET);
    }

    @Autowired
    private AccountService accountService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private StatementRunService statementRunService;
    @Autowired
    private StatementStorage statementStorage;
    @Autowired
    private S3Client s3Client;

    @Test
    void generatesAStatementForEachAccountAndUploadsItToS3() {
        ensureBucketExists();

        Account from = accountService.open("statement-owner-1", "USD", new BigDecimal("100.00"));
        Account to = accountService.open("statement-owner-2", "USD", new BigDecimal("0.00"));

        transferService.submit(new TransferRequest(from.getId(), to.getId(), new BigDecimal("30.00"), "USD"),
                "statement-test-" + UUID.randomUUID());

        var execution = statementRunService.run(YearMonth.now());
        assertThat(execution.getExitStatus().getExitCode()).isEqualTo("COMPLETED");

        String key = StatementStorage.keyFor(from.getId(), new StatementPeriod(YearMonth.now()));
        var content = statementStorage.fetch(key);

        assertThat(content).isPresent();
        assertThat(content.get()).contains("statement-owner-1");
        assertThat(content.get()).contains("Opening balance: 100.0000 USD");
        assertThat(content.get()).contains("DEBIT");
        assertThat(content.get()).contains("Closing balance: 70.0000 USD");
    }

    private void ensureBucketExists() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (BucketAlreadyOwnedByYouException ignored) {
            // already created by an earlier test in this class
        }
    }
}
