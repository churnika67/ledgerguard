package com.ledgerguard.ledger.api;

import com.ledgerguard.ledger.api.dto.TransferRequestDto;
import com.ledgerguard.ledger.api.dto.TransferResponseDto;
import com.ledgerguard.ledger.domain.Transfer;
import com.ledgerguard.ledger.exception.TransferNotFoundException;
import com.ledgerguard.ledger.security.OwnershipGuard;
import com.ledgerguard.ledger.service.AccountService;
import com.ledgerguard.ledger.service.TransferRequest;
import com.ledgerguard.ledger.service.TransferResult;
import com.ledgerguard.ledger.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/transfers")
@Tag(name = "Transfers")
public class TransferController {

    private final TransferService transferService;
    private final AccountService accountService;
    private final OwnershipGuard ownershipGuard;

    public TransferController(TransferService transferService, AccountService accountService,
                               OwnershipGuard ownershipGuard) {
        this.transferService = transferService;
        this.accountService = accountService;
        this.ownershipGuard = ownershipGuard;
    }

    @PostMapping
    @Operation(summary = "Move money between two accounts",
            description = "Requires an Idempotency-Key header. Replaying the same key with the same body "
                    + "returns the original result with no new money movement; the same key with a "
                    + "different body is rejected with 409.")
    public ResponseEntity<TransferResponseDto> transfer(
            @Parameter(description = "Client-generated key that makes retries safe", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequestDto request,
            Authentication authentication) {
        ownershipGuard.requireOwnerOrAdmin(authentication, accountService.get(request.fromAccountId()));
        TransferResult result = transferService.submit(
                new TransferRequest(request.fromAccountId(), request.toAccountId(), request.amount(),
                        request.currency()),
                idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(TransferResponseDto.from(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a transfer by id")
    public TransferResponseDto get(@PathVariable UUID id) {
        Transfer transfer = transferService.findById(id).orElseThrow(() -> new TransferNotFoundException(id));
        return TransferResponseDto.from(transfer);
    }
}
