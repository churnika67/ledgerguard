package com.ledgerguard.ledger.statement;

import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.repository.AccountRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

/**
 * Wires the monthly statement job: one chunk-oriented step that pages through every account
 * ({@link RepositoryItemReader}, 50 at a time), builds a {@link StatementFile} for each
 * ({@link StatementItemProcessor}), and uploads it to S3 ({@link StatementItemWriter}).
 *
 * {@code spring.batch.job.enabled=false} (see application.yml) stops Spring Boot from
 * auto-running this at startup -- it only runs when {@code StatementRunService} launches it on
 * demand, via the admin endpoint.
 */
@Configuration
public class StatementBatchConfig {

    @Bean
    public RepositoryItemReader<Account> accountReader(AccountRepository accountRepository) {
        return new RepositoryItemReaderBuilder<Account>()
                .name("accountReader")
                .repository(accountRepository)
                .methodName("findAll")
                .pageSize(50)
                .sorts(Map.of("id", Sort.Direction.ASC))
                .build();
    }

    @Bean
    public Step generateStatementsStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                        RepositoryItemReader<Account> accountReader,
                                        StatementItemProcessor statementItemProcessor,
                                        StatementItemWriter statementItemWriter) {
        return new StepBuilder("generateStatementsStep", jobRepository)
                .<Account, StatementFile>chunk(10, transactionManager)
                .reader(accountReader)
                .processor(statementItemProcessor)
                .writer(statementItemWriter)
                .build();
    }

    @Bean
    public Job monthlyStatementJob(JobRepository jobRepository, Step generateStatementsStep) {
        return new JobBuilder("monthlyStatementJob", jobRepository)
                .start(generateStatementsStep)
                .build();
    }
}
