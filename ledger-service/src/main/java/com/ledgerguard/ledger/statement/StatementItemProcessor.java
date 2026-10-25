package com.ledgerguard.ledger.statement;

import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.repository.LedgerEntryRepository;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Turns one {@link Account} into a {@link StatementFile} for the month given as this step's
 * "month" job parameter. {@code @StepScope} is what lets {@code @Value("#{jobParameters[...]}")}
 * resolve -- the bean is created fresh per step execution, once the parameters are known, rather
 * than once at application startup before any job has even run.
 */
@Component
@StepScope
public class StatementItemProcessor implements ItemProcessor<Account, StatementFile> {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final StatementPeriod period;

    public StatementItemProcessor(LedgerEntryRepository ledgerEntryRepository,
                                   @Value("#{jobParameters['month']}") String month) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.period = StatementPeriod.parse(month);
    }

    @Override
    public StatementFile process(Account account) {
        BigDecimal openingBalance = account.getOpeningBalance()
                .add(ledgerEntryRepository.netAmountBefore(account.getId(), period.start()));
        List<com.ledgerguard.ledger.domain.LedgerEntry> entries = ledgerEntryRepository
                .findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(account.getId(), period.start(),
                        period.end());

        String content = StatementGenerator.generate(account, period, openingBalance, entries);
        String key = StatementStorage.keyFor(account.getId(), period);
        return new StatementFile(account.getId(), key, content);
    }
}
