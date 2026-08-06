package com.payroll.web.sync;

import com.payroll.core.entity.CloudDayLevelOTConfig;
import com.payroll.web.repository.CloudDayLevelOTConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Applies a full-push batch of desktop day-level OT configs to the cloud store, scoped to
 * the company resolved from the caller's API key. Upserts by (companyId, configDate) — a
 * day's config can legitimately change later (e.g. a holiday marked retroactively), so an
 * existing date is updated in place rather than rejected. Each record is validated and
 * persisted independently via {@link CloudDayLevelOTConfigRepository} (individually
 * transactional, default Spring Data JPA behaviour), so one bad record cannot block the
 * rest of the batch.
 */
@Service
public class DayLevelOtSyncService {

    private static final Logger log = LoggerFactory.getLogger(DayLevelOtSyncService.class);

    private final CloudDayLevelOTConfigRepository repository;

    public DayLevelOtSyncService(CloudDayLevelOTConfigRepository repository) {
        this.repository = repository;
    }

    public List<DayLevelOtSyncResult> processBatch(Long companyId, List<DayLevelOtSyncRequest> requests) {
        List<DayLevelOtSyncResult> results = new ArrayList<>(requests.size());
        for (DayLevelOtSyncRequest request : requests) {
            results.add(processOne(companyId, request));
        }
        return results;
    }

    private DayLevelOtSyncResult processOne(Long companyId, DayLevelOtSyncRequest request) {
        try {
            Optional<CloudDayLevelOTConfig> existing =
                    repository.findByCompanyIdAndConfigDate(companyId, request.configDate());

            if (existing.isEmpty()) {
                CloudDayLevelOTConfig config = new CloudDayLevelOTConfig();
                config.setCompanyId(companyId);
                config.setConfigDate(request.configDate());
                applyIncoming(config, request);
                repository.save(config);
                return DayLevelOtSyncResult.accepted(request.configDate());
            }

            CloudDayLevelOTConfig current = existing.get();
            applyIncoming(current, request);
            repository.save(current);
            return DayLevelOtSyncResult.updated(request.configDate());
        } catch (Exception e) {
            log.warn("Rejecting day-level OT sync record configDate={}: {}", request.configDate(), e.getMessage());
            return DayLevelOtSyncResult.rejected(request.configDate(), "Processing error: " + e.getMessage());
        }
    }

    private void applyIncoming(CloudDayLevelOTConfig config, DayLevelOtSyncRequest request) {
        config.setAllStaffOt(request.isAllStaffOt());
        config.setDayType(request.dayType());
        config.setSetBy(request.setBy());
        config.setSetAt(request.setAt());
    }
}
