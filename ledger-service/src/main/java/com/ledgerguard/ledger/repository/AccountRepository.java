package com.ledgerguard.ledger.repository;

import com.ledgerguard.ledger.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    /** Takes a row-level SELECT FOR UPDATE lock. Caller is responsible for lock ordering. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    @Query("select sum(a.balance) from Account a")
    java.math.BigDecimal sumBalances();

    @Query("select sum(a.openingBalance) from Account a")
    java.math.BigDecimal sumOpeningBalances();

    List<Account> findAllByIdIn(List<UUID> ids);
}
