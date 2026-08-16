package com.payroll.web.auditlog;

import com.payroll.core.entity.AuditLogEntry;
import com.payroll.web.repository.AuditLogEntryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Read side of the audit trail (Phase 6E). Reuses {@link AuditLogEntryRepository} as-is —
 * the entity's shape (action/targetRef/oldValue/newValue/reason) was fixed by 6C/6D and
 * this phase only adds a query, not new columns beyond the companyId scoping fix.
 *
 * <p>Filters are built as a {@link Specification} rather than a JPQL query with
 * {@code (:param IS NULL OR ...)} clauses, because Postgres's extended query protocol
 * can't infer a bind parameter's type when it's only ever compared to NULL — a
 * Specification simply omits a predicate when its filter is absent, sidestepping that.
 */
@Service
public class AuditLogService {

    private static final int MAX_ENTRIES = 200;

    private final AuditLogEntryRepository repository;

    public AuditLogService(AuditLogEntryRepository repository) {
        this.repository = repository;
    }

    public List<AuditLogEntryResponse> find(Long companyId, String entityType, LocalDate from, LocalDate to) {
        Specification<AuditLogEntry> spec = (root, query, cb) -> cb.equal(root.get("companyId"), companyId);

        if (entityType != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), entityType));
        }
        if (from != null) {
            Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("entryDatetime"), fromInstant));
        }
        if (to != null) {
            // Exclusive upper bound at the start of the day *after* `to`, so the whole `to` day is included.
            Instant toInstant = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("entryDatetime"), toInstant));
        }

        var pageable = PageRequest.of(0, MAX_ENTRIES, Sort.by(Sort.Direction.DESC, "entryDatetime"));
        return repository.findAll(spec, pageable).stream()
                .map(AuditLogEntryResponse::from)
                .toList();
    }
}
