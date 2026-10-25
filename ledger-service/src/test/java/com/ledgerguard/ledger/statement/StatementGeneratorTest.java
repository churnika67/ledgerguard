package com.ledgerguard.ledger.statement;

import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.domain.Direction;
import com.ledgerguard.ledger.domain.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StatementGeneratorTest {

    @Test
    void runningBalanceReflectsEachEntryInOrder() {
        Account account = new Account(UUID.randomUUID(), "alice", "USD", new BigDecimal("100.00"), Instant.now());
        StatementPeriod period = new StatementPeriod(YearMonth.of(2026, 3));
        List<LedgerEntry> entries = List.of(
                new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), account.getId(), Direction.DEBIT,
                        new BigDecimal("40.00"), Instant.parse("2026-03-05T00:00:00Z")),
                new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), account.getId(), Direction.CREDIT,
                        new BigDecimal("15.00"), Instant.parse("2026-03-10T00:00:00Z")));

        String csv = StatementGenerator.generate(account, period, new BigDecimal("100.00"), entries);

        assertThat(csv).contains("Opening balance: 100.00 USD");
        assertThat(csv).contains("DEBIT,40.00,60.00");
        assertThat(csv).contains("CREDIT,15.00,75.00");
        assertThat(csv).contains("Closing balance: 75.00 USD");
    }

    @Test
    void noEntriesStillProducesAStatementWithUnchangedBalance() {
        Account account = new Account(UUID.randomUUID(), "bob", "USD", new BigDecimal("50.00"), Instant.now());
        StatementPeriod period = new StatementPeriod(YearMonth.of(2026, 3));

        String csv = StatementGenerator.generate(account, period, new BigDecimal("50.00"), List.of());

        assertThat(csv).contains("Opening balance: 50.00 USD");
        assertThat(csv).contains("Closing balance: 50.00 USD");
    }
}
