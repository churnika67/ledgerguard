package com.ledgerguard.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.domain.RejectReason;
import com.ledgerguard.ledger.domain.TransferStatus;
import com.ledgerguard.ledger.outbox.OutboxEventRepository;
import com.ledgerguard.ledger.repository.AccountRepository;
import com.ledgerguard.ledger.repository.LedgerEntryRepository;
import com.ledgerguard.ledger.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerMutationServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransferRepository transferRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private LedgerMutationService ledgerMutationService;

    @BeforeEach
    void setUp() {
        ledgerMutationService = new LedgerMutationService(accountRepository, transferRepository,
                ledgerEntryRepository, outboxEventRepository, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private Account account(BigDecimal balance, String currency) {
        return new Account(UUID.randomUUID(), "owner", currency, balance, Instant.now());
    }

    private TransferCommand commandFor(Account from, Account to, BigDecimal amount, String currency) {
        return new TransferCommand(UUID.randomUUID(), "key-" + UUID.randomUUID(), "hash", from.getId(),
                to.getId(), amount, currency, Instant.now());
    }

    @Test
    void happyPathDebitsOneAccountAndCreditsTheOtherForTheSameAmount() {
        when(accountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Account from = account(new BigDecimal("100.00"), "USD");
        Account to = account(new BigDecimal("50.00"), "USD");
        TransferCommand command = commandFor(from, to, new BigDecimal("30.00"), "USD");

        TransferExecutionResult result = ledgerMutationService.apply(command, from, to);

        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(from.getBalance()).isEqualTo(new BigDecimal("70.00"));
        assertThat(to.getBalance()).isEqualTo(new BigDecimal("80.00"));
        verify(ledgerEntryRepository, times(2)).save(any());
        verify(transferRepository, times(1)).save(any());
        verify(outboxEventRepository, times(1)).save(any());
    }

    @Test
    void rejectsSameAccountTransferWithNoEntriesWritten() {
        Account only = account(new BigDecimal("100.00"), "USD");
        TransferCommand command = new TransferCommand(UUID.randomUUID(), "key", "hash", only.getId(),
                only.getId(), new BigDecimal("10.00"), "USD", Instant.now());

        TransferExecutionResult result = ledgerMutationService.apply(command, only, only);

        assertThat(result.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(result.rejectReason()).isEqualTo(RejectReason.SAME_ACCOUNT);
        assertThat(only.getBalance()).isEqualTo(new BigDecimal("100.00"));
        verify(ledgerEntryRepository, never()).save(any());
        verify(transferRepository, times(1)).save(any());
    }

    @Test
    void rejectsCurrencyMismatchWithNoEntriesWritten() {
        Account from = account(new BigDecimal("100.00"), "USD");
        Account to = account(new BigDecimal("50.00"), "EUR");
        TransferCommand command = commandFor(from, to, new BigDecimal("10.00"), "USD");

        TransferExecutionResult result = ledgerMutationService.apply(command, from, to);

        assertThat(result.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(result.rejectReason()).isEqualTo(RejectReason.CURRENCY_MISMATCH);
        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    void rejectsInsufficientFundsWithNoEntriesWrittenAndBalanceUnchanged() {
        Account from = account(new BigDecimal("10.00"), "USD");
        Account to = account(new BigDecimal("50.00"), "USD");
        TransferCommand command = commandFor(from, to, new BigDecimal("30.00"), "USD");

        TransferExecutionResult result = ledgerMutationService.apply(command, from, to);

        assertThat(result.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(result.rejectReason()).isEqualTo(RejectReason.INSUFFICIENT_FUNDS);
        assertThat(from.getBalance()).isEqualTo(new BigDecimal("10.00"));
        assertThat(to.getBalance()).isEqualTo(new BigDecimal("50.00"));
        verify(ledgerEntryRepository, never()).save(any());
    }
}
