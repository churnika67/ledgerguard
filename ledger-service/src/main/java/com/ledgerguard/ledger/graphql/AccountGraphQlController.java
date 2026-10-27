package com.ledgerguard.ledger.graphql;

import com.ledgerguard.ledger.api.dto.TransferResponseDto;
import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.domain.Transfer;
import com.ledgerguard.ledger.repository.TransferRepository;
import com.ledgerguard.ledger.security.OwnershipGuard;
import com.ledgerguard.ledger.service.AccountService;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class AccountGraphQlController {

    private static final int MAX_TRANSFERS_PER_ACCOUNT = 20;

    private final AccountService accountService;
    private final OwnershipGuard ownershipGuard;
    private final TransferRepository transferRepository;

    public AccountGraphQlController(AccountService accountService, OwnershipGuard ownershipGuard,
                                     TransferRepository transferRepository) {
        this.accountService = accountService;
        this.ownershipGuard = ownershipGuard;
        this.transferRepository = transferRepository;
    }

    @QueryMapping
    public List<Account> myAccounts(Authentication authentication) {
        return accountService.findOwnedBy(authentication.getName());
    }

    @QueryMapping
    public Account account(@Argument UUID id, Authentication authentication) {
        Account account = accountService.get(id);
        ownershipGuard.requireOwnerOrAdmin(authentication, account);
        return account;
    }

    /**
     * Resolves every requested account's {@code transfers} field in one query instead of one
     * query per account -- the classic GraphQL N+1 problem, avoided via Spring GraphQL's
     * {@code @BatchMapping}: this method is called once with every {@link Account} being
     * resolved in the current request (e.g. all of {@code myAccounts}), not once per account.
     */
    @BatchMapping
    public Map<Account, List<TransferResponseDto>> transfers(List<Account> accounts) {
        Set<UUID> accountIds = accounts.stream().map(Account::getId).collect(Collectors.toSet());
        List<Transfer> involved = transferRepository.findInvolvingAccounts(accountIds);

        Map<UUID, List<Transfer>> byAccountId = new HashMap<>();
        for (Transfer transfer : involved) {
            addIfRelevant(byAccountId, accountIds, transfer, transfer.getFromAccountId());
            addIfRelevant(byAccountId, accountIds, transfer, transfer.getToAccountId());
        }

        Map<Account, List<TransferResponseDto>> result = new LinkedHashMap<>();
        for (Account account : accounts) {
            List<Transfer> accountTransfers = byAccountId.getOrDefault(account.getId(), List.of());
            result.put(account, accountTransfers.stream().map(TransferResponseDto::from).toList());
        }
        return result;
    }

    private void addIfRelevant(Map<UUID, List<Transfer>> byAccountId, Set<UUID> accountIds, Transfer transfer,
                                UUID candidateAccountId) {
        if (!accountIds.contains(candidateAccountId)) {
            return;
        }
        List<Transfer> list = byAccountId.computeIfAbsent(candidateAccountId, id -> new ArrayList<>());
        if (list.size() < MAX_TRANSFERS_PER_ACCOUNT) {
            list.add(transfer);
        }
    }
}
