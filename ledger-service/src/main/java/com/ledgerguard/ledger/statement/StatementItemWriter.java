package com.ledgerguard.ledger.statement;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
public class StatementItemWriter implements ItemWriter<StatementFile> {

    private final StatementStorage storage;

    public StatementItemWriter(StatementStorage storage) {
        this.storage = storage;
    }

    @Override
    public void write(Chunk<? extends StatementFile> chunk) {
        for (StatementFile file : chunk) {
            storage.store(file.s3Key(), file.content());
        }
    }
}
