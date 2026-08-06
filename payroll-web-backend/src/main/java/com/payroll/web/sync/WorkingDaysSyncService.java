package com.payroll.web.sync;

import com.payroll.core.entity.CloudWorkingDaysConfig;
import com.payroll.web.repository.CloudWorkingDaysConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Applies a full-push batch of desktop working-days configs to the cloud store, scoped to
 * the company resolved from the caller's API key. Upserts by (companyId, periodMonth) — a
 * month's working-days count can legitimately be corrected later, so an existing month is
 * updated in place rather than rejected. Each record is validated and persisted
 * independently via {@link CloudWorkingDaysConfigRepository} (individually transactional,
 * default Spring Data JPA behaviour), so one bad record cannot block the rest of the batch.
 */
@Service
public class WorkingDaysSyncService {

    private static final Logger log = LoggerFactory.getLogger(WorkingDaysSyncService.class);

    private final CloudWorkingDaysConfigRepository repository;

    public WorkingDaysSyncService(CloudWorkingDaysConfigRepository repository) {
        this.repository = repository;
    }

    public List<WorkingDaysSyncResult> processBatch(Long companyId, List<WorkingDaysSyncRequest> requests) {
        List<WorkingDaysSyncResult> results = new ArrayList<>(requests.size());
        for (WorkingDaysSyncRequest request : requests) {
            results.add(processOne(companyId, request));
        }
        return results;
    }

    private WorkingDaysSyncResult processOne(Long companyId, WorkingDaysSyncRequest request) {
        try {
            Optional<CloudWorkingDaysConfig> existing =
                    repository.findByCompanyIdAndPeriodMonth(companyId, request.periodMonth());

            if (existing.isEmpty()) {
                CloudWorkingDaysConfig config = new CloudWorkingDaysConfig();
                config.setCompanyId(companyId);
                config.setPeriodMonth(request.periodMonth());
                applyIncoming(config, request);
                repository.save(config);
                return WorkingDaysSyncResult.accepted(request.periodMonth());
            }

            CloudWorkingDaysConfig current = existing.get();
            applyIncoming(current, request);
            repository.save(current);
            return WorkingDaysSyncResult.updated(request.periodMonth());
        } catch (Exception e) {
            log.warn("Rejecting working-days sync record periodMonth={}: {}", request.periodMonth(), e.getMessage());
            return WorkingDaysSyncResult.rejected(request.periodMonth(), "Processing error: " + e.getMessage());
        }
    }

    private void applyIncoming(CloudWorkingDaysConfig config, WorkingDaysSyncRequest request) {
        config.setAvailableWorkingDays(request.availableWorkingDays());
        config.setUpdatedBy(request.updatedBy());
        config.setUpdatedAt(request.updatedAt());
    }
}
