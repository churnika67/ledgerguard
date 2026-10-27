package com.ledgerguard.ledger.graphql;

import com.ledgerguard.ledger.api.dto.TransferResponseDto;
import com.ledgerguard.ledger.security.OwnershipGuard;
import com.ledgerguard.ledger.service.AccountService;
import com.ledgerguard.ledger.service.TransferRequest;
import com.ledgerguard.ledger.service.TransferResult;
import com.ledgerguard.ledger.service.TransferService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
public class TransferGraphQlController {

    private final TransferService transferService;
    private final AccountService accountService;
    private final OwnershipGuard ownershipGuard;

    public TransferGraphQlController(TransferService transferService, AccountService accountService,
                                      OwnershipGuard ownershipGuard) {
        this.transferService = transferService;
        this.accountService = accountService;
        this.ownershipGuard = ownershipGuard;
    }

    @MutationMapping
    public TransferResponseDto transfer(@Argument TransferInput input, Authentication authentication) {
        ownershipGuard.requireOwnerOrAdmin(authentication, accountService.get(input.fromAccountId()));
        TransferResult result = transferService.submit(
                new TransferRequest(input.fromAccountId(), input.toAccountId(), input.amount(), input.currency()),
                input.idempotencyKey());
        return TransferResponseDto.from(result);
    }
}
