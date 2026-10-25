package com.ledgerguard.ledger.statement;

import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.domain.Direction;
import com.ledgerguard.ledger.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds a statement's CSV content from an account and its ledger entries for one month. Kept as
 * a pure function, with no I/O of its own, so it's trivial to unit test independently of S3 or
 * the database.
 */
public final class StatementGenerator {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_INSTANT;

    private StatementGenerator() {
    }

    public static String generate(Account account, StatementPeriod period, BigDecimal openingBalance,
                                   List<LedgerEntry> entries) {
        StringBuilder csv = new StringBuilder();
        csv.append("# Statement for ").append(account.getOwnerName()).append(" (").append(account.getId())
                .append("), ").append(period.month()).append('\n');
        csv.append("# Opening balance: ").append(openingBalance).append(' ').append(account.getCurrency())
                .append('\n');
        csv.append("date,direction,amount,running_balance\n");

        BigDecimal running = openingBalance;
        for (LedgerEntry entry : entries) {
            running = entry.getDirection() == Direction.CREDIT
                    ? running.add(entry.getAmount())
                    : running.subtract(entry.getAmount());
            csv.append(TIMESTAMP.format(entry.getCreatedAt())).append(',')
                    .append(entry.getDirection()).append(',')
                    .append(entry.getAmount()).append(',')
                    .append(running).append('\n');
        }

        csv.append("# Closing balance: ").append(running).append(' ').append(account.getCurrency()).append('\n');
        return csv.toString();
    }
}
