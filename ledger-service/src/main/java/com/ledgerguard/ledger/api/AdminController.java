package com.ledgerguard.ledger.api;

import com.ledgerguard.ledger.api.dto.AccountResponse;
import com.ledgerguard.ledger.api.dto.ChaosRunRequest;
import com.ledgerguard.ledger.api.dto.ChaosRunResponse;
import com.ledgerguard.ledger.api.dto.PageResponse;
import com.ledgerguard.ledger.api.dto.StatementRunRequest;
import com.ledgerguard.ledger.api.dto.StatementRunResponse;
import com.ledgerguard.ledger.api.dto.TransferResponseDto;
import com.ledgerguard.ledger.domain.RejectReason;
import com.ledgerguard.ledger.domain.TransferStatus;
import com.ledgerguard.ledger.repository.AccountRepository;
import com.ledgerguard.ledger.repository.TransferRepository;
import com.ledgerguard.ledger.service.ChaosRunService;
import com.ledgerguard.ledger.service.ChaosRunState;
import com.ledgerguard.ledger.statement.StatementPeriod;
import com.ledgerguard.ledger.statement.StatementRunService;
import com.ledgerguard.ledger.statement.StatementStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Back-office endpoints for the Angular admin console: a searchable/sortable accounts table, a
 * filterable transfer audit log, and the chaos-test panel. Every endpoint here is ADMIN-only.
 */
@RestController
@RequestMapping("/admin")
@Tag(name = "Admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final ChaosRunService chaosRunService;
    private final StatementRunService statementRunService;
    private final StatementStorage statementStorage;

    public AdminController(AccountRepository accountRepository, TransferRepository transferRepository,
                            ChaosRunService chaosRunService, StatementRunService statementRunService,
                            StatementStorage statementStorage) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.chaosRunService = chaosRunService;
        this.statementRunService = statementRunService;
        this.statementStorage = statementStorage;
    }

    @GetMapping("/accounts")
    @Operation(summary = "All accounts, sorted/paginated/searched by owner name (ADMIN only)")
    public PageResponse<AccountResponse> accounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort,
            @RequestParam(required = false) String q) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), parseSort(sort));
        var result = (q == null || q.isBlank())
                ? accountRepository.findAll(pageable)
                : accountRepository.findByOwnerNameContainingIgnoreCase(q, pageable);
        return PageResponse.from(result, AccountResponse::from);
    }

    @GetMapping("/transfers")
    @Operation(summary = "Transfer audit log, filterable by status and/or reject reason (ADMIN only)")
    public PageResponse<TransferResponseDto> transfers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) RejectReason rejectReason) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        String rejectReasonName = rejectReason == null ? null : rejectReason.name();
        var result = transferRepository.search(status, rejectReasonName, pageable);
        return PageResponse.from(result, TransferResponseDto::from);
    }

    @PostMapping("/chaos-runs")
    @Operation(summary = "Start a bounded concurrent chaos run (max 5000 transfers) against fresh "
            + "demo accounts, on the currently active locking strategy (ADMIN only)")
    public ResponseEntity<ChaosRunResponse> startChaosRun(@Valid @RequestBody ChaosRunRequest request) {
        ChaosRunState state = chaosRunService.start(request.transferCount(), request.accountCount(),
                request.concurrency());
        return ResponseEntity.accepted().body(ChaosRunResponse.from(state));
    }

    @GetMapping("/chaos-runs/{id}")
    @Operation(summary = "Poll a chaos run's status and, once COMPLETED, its results (ADMIN only)")
    public ChaosRunResponse getChaosRun(@PathVariable UUID id) {
        return chaosRunService.find(id).map(ChaosRunResponse::from)
                .orElseThrow(() -> new ChaosRunNotFoundException(id));
    }

    @GetMapping("/chaos-runs")
    @Operation(summary = "The most recent chaos runs, newest first (ADMIN only)")
    public List<ChaosRunResponse> listChaosRuns() {
        return chaosRunService.listRecent().stream().map(ChaosRunResponse::from).toList();
    }

    @PostMapping("/statements/run")
    @Operation(summary = "Generate every account's statement for a given month and upload each to S3 "
            + "(ADMIN only)")
    public ResponseEntity<StatementRunResponse> runStatements(@Valid @RequestBody StatementRunRequest request) {
        var execution = statementRunService.run(YearMonth.parse(request.month()));
        return ResponseEntity.accepted().body(StatementRunResponse.from(execution));
    }

    @GetMapping("/statements/{accountId}")
    @Operation(summary = "Fetch one account's already-generated statement for a given month, as CSV "
            + "(ADMIN only)")
    public ResponseEntity<String> getStatement(@PathVariable UUID accountId, @RequestParam String month) {
        String key = StatementStorage.keyFor(accountId, StatementPeriod.parse(month));
        return statementStorage.fetch(key)
                .map(content -> ResponseEntity.ok().contentType(MediaType.valueOf("text/csv")).body(content))
                .orElseThrow(() -> new StatementNotFoundException(accountId, month));
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = parts.length > 0 ? parts[0] : "createdAt";
        Sort.Direction direction = parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, property);
    }

    public static final class ChaosRunNotFoundException extends RuntimeException {
        public ChaosRunNotFoundException(UUID id) {
            super("No chaos run found with id " + id);
        }
    }

    public static final class StatementNotFoundException extends RuntimeException {
        public StatementNotFoundException(UUID accountId, String month) {
            super("No statement found for account " + accountId + " for " + month);
        }
    }
}
