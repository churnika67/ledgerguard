package com.ledgerguard.ledger.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Appends one benchmark run's numbers to a plain-text file under target/, for later collection into docs/benchmark.md. */
final class BenchmarkReport {

    private static final Path OUTPUT = Path.of("target", "benchmark-results.txt");

    private BenchmarkReport() {
    }

    static synchronized void write(BenchmarkSupport.BenchmarkResult result) throws IOException {
        String line = String.format("%s transfers=%d tps=%.1f p95Ms=%d retries=%d conflicts=%d failures=%d%n",
                result.strategy(), result.transferCount(), result.transfersPerSecond(), result.p95LatencyMillis(),
                result.retries(), result.conflicts(), result.failures());
        Files.writeString(OUTPUT, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.print(line);
    }
}
