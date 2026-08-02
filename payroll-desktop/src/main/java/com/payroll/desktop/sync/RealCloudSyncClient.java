package com.payroll.desktop.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payroll.core.entity.AttendanceRecord;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Production {@link CloudSyncClient}: posts a single-record batch to the web
 * backend's POST /api/sync/attendance endpoint, authenticated via X-API-Key.
 * Always sends a batch of exactly one record so {@link SyncService}'s existing
 * one-record-at-a-time loop needs no changes — the "batching" this endpoint
 * supports is used at this client boundary only.
 */
public class RealCloudSyncClient implements CloudSyncClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(3);

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;

    public RealCloudSyncClient() {
        this(CloudSyncConfig.BASE_URL, CloudSyncConfig.API_KEY);
    }

    public RealCloudSyncClient(String baseUrl, String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    @Override
    public SyncPushResult pushRecord(AttendanceRecord record) throws SyncException {
        CloudAttendanceRecordDto dto = new CloudAttendanceRecordDto(
                record.getSyncUuid(),
                record.getEmployee().getEmployeeCode(),
                record.getScanDatetime(),
                record.getScanType(),
                record.getOriginalScanDatetime(),
                record.getCorrectionNote(),
                record.getCorrectedBy());

        try {
            String body = MAPPER.writeValueAsString(List.of(dto));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/sync/attendance"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("X-API-Key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new SyncException("Cloud sync HTTP " + response.statusCode() + ": " + response.body());
            }

            List<CloudSyncResultDto> results =
                    MAPPER.readValue(response.body(), MAPPER.getTypeFactory()
                            .constructCollectionType(List.class, CloudSyncResultDto.class));

            if (results.isEmpty()) {
                throw new SyncException("Cloud sync returned an empty result list");
            }
            CloudSyncResultDto result = results.get(0);

            if ("REJECTED".equals(result.status())) {
                throw new SyncException("Cloud rejected record: " + result.reason());
            }

            boolean alreadyExisted = "UPDATED".equals(result.status()) || "NO_OP".equals(result.status());
            return new SyncPushResult(result.cloudRecordId(), alreadyExisted);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SyncException("Cloud sync request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isCloudReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/health"))
                    .timeout(HEALTH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
