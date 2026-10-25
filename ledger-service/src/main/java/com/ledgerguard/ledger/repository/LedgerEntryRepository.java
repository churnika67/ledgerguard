package com.ledgerguard.ledger.repository;

import com.ledgerguard.ledger.domain.Direction;
import com.ledgerguard.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findTop20ByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<LedgerEntry> findByAccountIdAndCreatedAtBetweenOrderByCreatedAtAsc(UUID accountId, Instant start,
                                                                             Instant end);

    /** Net signed effect (credits positive, debits negative) of every entry before a point in time --
     * added to the account's opening balance, this is the balance as of that point. */
    @Query("select coalesce(sum(case when e.direction = 'CREDIT' then e.amount else -e.amount end), 0) "
            + "from LedgerEntry e where e.accountId = :accountId and e.createdAt < :before")
    BigDecimal netAmountBefore(@Param("accountId") UUID accountId, @Param("before") Instant before);

    long countByTransferId(UUID transferId);

    long countByTransferIdIn(java.util.Collection<UUID> transferIds);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e "
            + "where e.accountId = :accountId and e.direction = :direction")
    BigDecimal sumByAccountIdAndDirection(@Param("accountId") UUID accountId, @Param("direction") Direction direction);

    @Query("select e.transferId, sum(case when e.direction = 'DEBIT' then e.amount else -e.amount end) "
            + "from LedgerEntry e group by e.transferId "
            + "having sum(case when e.direction = 'DEBIT' then e.amount else -e.amount end) <> 0")
    List<Object[]> findTransferIdsThatDoNotNetToZero();

    @Query("select e.accountId, e.direction, sum(e.amount) from LedgerEntry e group by e.accountId, e.direction")
    List<Object[]> sumsByAccountAndDirection();
}
