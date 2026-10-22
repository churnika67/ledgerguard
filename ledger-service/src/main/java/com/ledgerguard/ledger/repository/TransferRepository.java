package com.ledgerguard.ledger.repository;

import com.ledgerguard.ledger.domain.Transfer;
import com.ledgerguard.ledger.domain.TransferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(TransferStatus status);

    List<Transfer> findAllByIdempotencyKeyIn(Collection<String> idempotencyKeys);
}
