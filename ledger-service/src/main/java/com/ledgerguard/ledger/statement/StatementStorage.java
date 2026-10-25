package com.ledgerguard.ledger.statement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Thin wrapper around the S3 client for exactly the two operations statements need. */
@Component
public class StatementStorage {

    private final S3Client s3Client;
    private final String bucket;

    public StatementStorage(S3Client s3Client, @Value("${ledger.statements.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    public void store(String key, String content) {
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType("text/csv").build(),
                RequestBody.fromString(content, StandardCharsets.UTF_8));
    }

    public Optional<String> fetch(String key) {
        try (var response = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            return Optional.of(new String(response.readAllBytes(), StandardCharsets.UTF_8));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String keyFor(UUID accountId, StatementPeriod period) {
        return "statements/%s/%s.csv".formatted(accountId, period.month());
    }
}
