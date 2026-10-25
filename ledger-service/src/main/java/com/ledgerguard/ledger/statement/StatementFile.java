package com.ledgerguard.ledger.statement;

import java.util.UUID;

/** One generated statement, ready to be uploaded -- the item type passed from processor to writer. */
public record StatementFile(UUID accountId, String s3Key, String content) {
}
