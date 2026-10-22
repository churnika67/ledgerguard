package com.ledgerguard.ledger.security;

import com.ledgerguard.ledger.domain.Account;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * In this demo, an account's owner is identified by its {@code ownerName} matching the JWT
 * subject. A plain USER may only act on accounts they own; ADMIN bypasses the check.
 */
@Component
public class OwnershipGuard {

    public void requireOwnerOrAdmin(Authentication authentication, Account account) {
        if (authentication.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"))) {
            return;
        }
        if (!authentication.getName().equals(account.getOwnerName())) {
            throw new AccessDeniedException("You do not own account " + account.getId());
        }
    }
}
