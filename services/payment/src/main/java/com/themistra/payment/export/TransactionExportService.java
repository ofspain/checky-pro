package com.themistra.payment.export;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** T22 (R27). Read-only orchestration above {@link TransactionExportRepository}'s own native
 * query - no state change, no outbox, no entity touched. */
@Service
public class TransactionExportService {

    private final TransactionExportRepository repository;

    TransactionExportService(TransactionExportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TransactionExportRow> export(UUID merchantAccountUuid, Instant from, Instant to) {
        return repository.findAttestedTransactions(merchantAccountUuid, from, to);
    }
}
