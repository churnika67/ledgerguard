package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.domain.LedgerEntry;
import com.ledgerguard.ledger.exception.AccountNotFoundException;
import com.ledgerguard.ledger.repository.AccountRepository;
import com.ledgerguard.ledger.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Opens accounts and serves read access to balances and recent ledger activity. */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public AccountService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public Account open(String ownerName, String currency, BigDecimal openingBalance) {
        Account account = new Account(UUID.randomUUID(), ownerName, currency, openingBalance, Instant.now());
        return accountRepository.save(account);
    }

    public Account get(UUID accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    public List<LedgerEntry> recentEntries(UUID accountId) {
        return ledgerEntryRepository.findTop20ByAccountIdOrderByCreatedAtDesc(accountId);
    }
}
