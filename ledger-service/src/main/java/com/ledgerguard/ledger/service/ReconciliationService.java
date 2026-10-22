package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.domain.Direction;
import com.ledgerguard.ledger.repository.AccountRepository;
import com.ledgerguard.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Checks that the ledger still satisfies its three money-conservation invariants. Read-only
 * and safe to run against a live, concurrently-written database: it takes no locks and simply
 * reports what it finds.
 */
@Service
public class ReconciliationService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public ReconciliationService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public ReconciliationReport check() {
        BigDecimal totalBalance = nullToZero(accountRepository.sumBalances());
        BigDecimal totalOpeningBalance = nullToZero(accountRepository.sumOpeningBalances());

        Map<UUID, BigDecimal> credits = new HashMap<>();
        Map<UUID, BigDecimal> debits = new HashMap<>();
        for (Object[] row : ledgerEntryRepository.sumsByAccountAndDirection()) {
            UUID accountId = (UUID) row[0];
            Direction direction = (Direction) row[1];
            BigDecimal sum = (BigDecimal) row[2];
            if (direction == Direction.CREDIT) {
                credits.put(accountId, sum);
            } else {
                debits.put(accountId, sum);
            }
        }

        List<UUID> accountMismatches = new ArrayList<>();
        for (Account account : accountRepository.findAll()) {
            BigDecimal credit = credits.getOrDefault(account.getId(), BigDecimal.ZERO);
            BigDecimal debit = debits.getOrDefault(account.getId(), BigDecimal.ZERO);
            BigDecimal expected = account.getOpeningBalance().add(credit).subtract(debit);
            if (expected.compareTo(account.getBalance()) != 0) {
                accountMismatches.add(account.getId());
            }
        }

        List<UUID> transferMismatches = new ArrayList<>();
        for (Object[] row : ledgerEntryRepository.findTransferIdsThatDoNotNetToZero()) {
            transferMismatches.add((UUID) row[0]);
        }

        boolean totalsMatch = totalBalance.compareTo(totalOpeningBalance) == 0;
        boolean healthy = totalsMatch && accountMismatches.isEmpty() && transferMismatches.isEmpty();

        return new ReconciliationReport(healthy, totalBalance, totalOpeningBalance, accountMismatches,
                transferMismatches);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
