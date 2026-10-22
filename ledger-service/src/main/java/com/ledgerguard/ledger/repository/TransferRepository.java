package com.ledgerguard.ledger.repository;

import com.ledgerguard.ledger.domain.Transfer;
import com.ledgerguard.ledger.domain.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(TransferStatus status);

    List<Transfer> findAllByIdempotencyKeyIn(Collection<String> idempotencyKeys);

    /** Admin-only audit log: every transfer, optionally filtered by status and/or reject reason. */
    @Query("select t from Transfer t where (:status is null or t.status = :status) "
            + "and (:rejectReason is null or t.rejectReason = :rejectReason) order by t.createdAt desc")
    Page<Transfer> search(@Param("status") TransferStatus status, @Param("rejectReason") String rejectReason,
                           Pageable pageable);
}
