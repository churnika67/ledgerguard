package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.domain.RejectReason;
import com.ledgerguard.ledger.domain.Transfer;
import com.ledgerguard.ledger.domain.TransferStatus;
import com.ledgerguard.ledger.exception.IdempotencyConflictException;
import com.ledgerguard.ledger.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;
    @Mock
    private TransferExecutor executor;

    private TransferService transferService;

    private final UUID fromId = UUID.randomUUID();
    private final UUID toId = UUID.randomUUID();
    private final BigDecimal amount = new BigDecimal("25.00");

    @BeforeEach
    void setUp() {
        transferService = new TransferService(transferRepository, executor);
    }

    private TransferRequest request() {
        return new TransferRequest(fromId, toId, amount, "USD");
    }

    @Test
    void newRequestDelegatesToExecutorAndReturnsCompletedResult() {
        when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(executor.execute(any())).thenReturn(TransferExecutionResult.completed());

        TransferResult result = transferService.submit(request(), "key-1");

        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(result.replayed()).isFalse();
        verify(executor).execute(any());
    }

    @Test
    void replayWithSameKeyAndSameBodyReturnsOriginalResultWithoutCallingExecutor() {
        String hash = RequestHasher.hash(fromId, toId, amount, "USD");
        Transfer existing = Transfer.completed(UUID.randomUUID(), "key-1", hash, fromId, toId, amount, "USD",
                Instant.now());
        when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        TransferResult result = transferService.submit(request(), "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.transferId()).isEqualTo(existing.getId());
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
        verify(executor, never()).execute(any());
    }

    @Test
    void sameKeyWithDifferentBodyIsRejectedAsConflict() {
        String differentHash = RequestHasher.hash(fromId, toId, new BigDecimal("999.00"), "USD");
        Transfer existing = Transfer.completed(UUID.randomUUID(), "key-1", differentHash, fromId, toId,
                new BigDecimal("999.00"), "USD", Instant.now());
        when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> transferService.submit(request(), "key-1"))
                .isInstanceOf(IdempotencyConflictException.class);
        verify(executor, never()).execute(any());
    }

    @Test
    void concurrentInsertRaceOnIdempotencyKeyResolvesToTheWinningReplayInsteadOfFailing() {
        when(transferRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(Transfer.completed(UUID.randomUUID(), "key-1",
                        RequestHasher.hash(fromId, toId, amount, "USD"), fromId, toId, amount, "USD",
                        Instant.now())));
        when(executor.execute(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        TransferResult result = transferService.submit(request(), "key-1");

        assertThat(result.replayed()).isTrue();
        assertThat(result.status()).isEqualTo(TransferStatus.COMPLETED);
    }

    @Test
    void rejectedResultIsReturnedWithReasonWhenExecutorRejectsTheTransfer() {
        when(transferRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(executor.execute(any())).thenReturn(TransferExecutionResult.rejected(RejectReason.INSUFFICIENT_FUNDS));

        TransferResult result = transferService.submit(request(), "key-1");

        assertThat(result.status()).isEqualTo(TransferStatus.REJECTED);
        assertThat(result.rejectReason()).isEqualTo("INSUFFICIENT_FUNDS");
    }
}
