package com.payroll.web.sync;

import com.payroll.core.entity.CloudOtEmployeeAuthorization;
import com.payroll.web.repository.CloudOtEmployeeAuthorizationRepository;
import com.payroll.web.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Applies a full-push batch of desktop OT employee authorizations to the cloud store,
 * scoped to the company resolved from the caller's API key. Upserts by (companyId,
 * employeeCode, authDate) — an authorization can legitimately be flipped later, so an
 * existing (employee, date) pair is updated in place rather than rejected. Each record
 * is validated and persisted independently via {@link CloudOtEmployeeAuthorizationRepository}
 * (individually transactional, default Spring Data JPA behaviour), so one bad record
 * cannot block the rest of the batch.
 */
@Service
public class OtAuthorizationSyncService {

    private static final Logger log = LoggerFactory.getLogger(OtAuthorizationSyncService.class);

    private final CloudOtEmployeeAuthorizationRepository repository;
    private final EmployeeRepository employeeRepository;

    public OtAuthorizationSyncService(CloudOtEmployeeAuthorizationRepository repository,
                                       EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    public List<OtAuthorizationSyncResult> processBatch(Long companyId, List<OtAuthorizationSyncRequest> requests) {
        List<OtAuthorizationSyncResult> results = new ArrayList<>(requests.size());
        for (OtAuthorizationSyncRequest request : requests) {
            results.add(processOne(companyId, request));
        }
        return results;
    }

    private OtAuthorizationSyncResult processOne(Long companyId, OtAuthorizationSyncRequest request) {
        try {
            boolean employeeExists =
                    employeeRepository.findByEmployeeCodeAndCompanyId(request.employeeCode(), companyId).isPresent();
            if (!employeeExists) {
                String reason = "Unknown employeeCode '" + request.employeeCode() + "' for this company";
                log.warn("Rejecting OT authorization sync record employeeCode={} authDate={}: {}",
                        request.employeeCode(), request.authDate(), reason);
                return OtAuthorizationSyncResult.rejected(request.employeeCode(), request.authDate(), reason);
            }

            Optional<CloudOtEmployeeAuthorization> existing = repository.findByCompanyIdAndEmployeeCodeAndAuthDate(
                    companyId, request.employeeCode(), request.authDate());

            if (existing.isEmpty()) {
                CloudOtEmployeeAuthorization auth = new CloudOtEmployeeAuthorization();
                auth.setCompanyId(companyId);
                auth.setEmployeeCode(request.employeeCode());
                auth.setAuthDate(request.authDate());
                applyIncoming(auth, request);
                repository.save(auth);
                return OtAuthorizationSyncResult.accepted(request.employeeCode(), request.authDate());
            }

            CloudOtEmployeeAuthorization current = existing.get();
            applyIncoming(current, request);
            repository.save(current);
            return OtAuthorizationSyncResult.updated(request.employeeCode(), request.authDate());
        } catch (Exception e) {
            log.warn("Rejecting OT authorization sync record employeeCode={} authDate={}: {}",
                    request.employeeCode(), request.authDate(), e.getMessage());
            return OtAuthorizationSyncResult.rejected(request.employeeCode(), request.authDate(),
                    "Processing error: " + e.getMessage());
        }
    }

    private void applyIncoming(CloudOtEmployeeAuthorization auth, OtAuthorizationSyncRequest request) {
        auth.setAuthorized(request.authorized());
        auth.setSetBy(request.setBy());
        auth.setSetAt(request.setAt());
    }
}
