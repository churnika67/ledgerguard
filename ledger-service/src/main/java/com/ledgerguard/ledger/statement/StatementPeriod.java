package com.ledgerguard.ledger.statement;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;

/** A calendar month, in UTC, as the half-open interval [start, end) a statement covers. */
public record StatementPeriod(YearMonth month) {

    public Instant start() {
        return month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public Instant end() {
        return month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public static StatementPeriod parse(String yearMonth) {
        return new StatementPeriod(YearMonth.parse(yearMonth));
    }
}
