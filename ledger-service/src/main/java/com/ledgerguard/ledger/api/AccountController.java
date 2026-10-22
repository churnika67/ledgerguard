package com.ledgerguard.ledger.api;

import com.ledgerguard.ledger.api.dto.AccountDetailResponse;
import com.ledgerguard.ledger.api.dto.AccountResponse;
import com.ledgerguard.ledger.api.dto.OpenAccountRequest;
import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.security.OwnershipGuard;
import com.ledgerguard.ledger.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;
    private final OwnershipGuard ownershipGuard;

    public AccountController(AccountService accountService, OwnershipGuard ownershipGuard) {
        this.accountService = accountService;
        this.ownershipGuard = ownershipGuard;
    }

    @GetMapping
    @Operation(summary = "List the caller's own accounts, newest first")
    public List<AccountResponse> listMine(Authentication authentication) {
        return accountService.findOwnedBy(authentication.getName()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Open a new account with a starting balance")
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = accountService.open(request.ownerName(), request.currency(), request.openingBalance());
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an account's current balance and its last 20 ledger entries")
    public AccountDetailResponse get(@PathVariable UUID id, Authentication authentication) {
        Account account = accountService.get(id);
        ownershipGuard.requireOwnerOrAdmin(authentication, account);
        return AccountDetailResponse.from(account, accountService.recentEntries(id));
    }
}
