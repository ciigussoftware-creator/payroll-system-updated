package com.payroll.desktop.sync;

import com.payroll.core.entity.AttendanceRecord;
import com.payroll.core.entity.Employee;
import com.payroll.core.entity.EmployeeCategory;
import com.payroll.core.entity.ScanType;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises RealCloudSyncClient's HTTP wiring (request shape, header, response
 * mapping, unreachable handling) against a minimal in-process JDK HttpServer —
 * no Spring context needed. True end-to-end verification against the real
 * payroll-web-backend + Postgres is manual (see task notes).
 */
class RealCloudSyncClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startServerRespondingWith(int statusCode, String responseBody, AtomicReference<String> capturedApiKey)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/sync/attendance", exchange -> {
            if (capturedApiKey != null) {
                capturedApiKey.set(exchange.getRequestHeaders().getFirst("X-API-Key"));
            }
            exchange.getRequestBody().readAllBytes(); // drain
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private AttendanceRecord sampleRecord() {
        Employee employee = new Employee();
        employee.setEmployeeCode("EMP-001");
        employee.setName("Worker 1");
        employee.setCategory(EmployeeCategory.STANDARD);
        employee.setGrossDailySalary(new BigDecimal("1200.00"));

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployee(employee);
        record.setScanDatetime(LocalDateTime.of(2026, 8, 1, 8, 0));
        record.setScanType(ScanType.ENTRY);
        return record;
    }

    @Test
    void acceptedResponseMapsToNewCloudRecord() throws Exception {
        AtomicReference<String> capturedKey = new AtomicReference<>();
        String baseUrl = startServerRespondingWith(200,
                "[{\"syncUuid\":\"u1\",\"status\":\"ACCEPTED\",\"cloudRecordId\":42,\"reason\":null}]",
                capturedKey);
        RealCloudSyncClient client = new RealCloudSyncClient(baseUrl, "my-api-key");

        SyncPushResult result = client.pushRecord(sampleRecord());

        assertThat(result.cloudRecordId()).isEqualTo(42L);
        assertThat(result.alreadyExisted()).isFalse();
        assertThat(capturedKey.get()).isEqualTo("my-api-key");
    }

    @Test
    void noOpResponseMapsToAlreadyExisted() throws Exception {
        String baseUrl = startServerRespondingWith(200,
                "[{\"syncUuid\":\"u1\",\"status\":\"NO_OP\",\"cloudRecordId\":7,\"reason\":null}]",
                null);
        RealCloudSyncClient client = new RealCloudSyncClient(baseUrl, "key");

        SyncPushResult result = client.pushRecord(sampleRecord());

        assertThat(result.cloudRecordId()).isEqualTo(7L);
        assertThat(result.alreadyExisted()).isTrue();
    }

    @Test
    void updatedResponseMapsToAlreadyExisted() throws Exception {
        String baseUrl = startServerRespondingWith(200,
                "[{\"syncUuid\":\"u1\",\"status\":\"UPDATED\",\"cloudRecordId\":7,\"reason\":null}]",
                null);
        RealCloudSyncClient client = new RealCloudSyncClient(baseUrl, "key");

        SyncPushResult result = client.pushRecord(sampleRecord());

        assertThat(result.alreadyExisted()).isTrue();
    }

    @Test
    void rejectedResponseThrowsSyncException() throws Exception {
        String baseUrl = startServerRespondingWith(200,
                "[{\"syncUuid\":\"u1\",\"status\":\"REJECTED\",\"cloudRecordId\":null,\"reason\":\"Unknown employeeCode\"}]",
                null);
        RealCloudSyncClient client = new RealCloudSyncClient(baseUrl, "key");

        assertThatThrownBy(() -> client.pushRecord(sampleRecord()))
                .isInstanceOf(SyncException.class)
                .hasMessageContaining("Unknown employeeCode");
    }

    @Test
    void non200StatusThrowsSyncException() throws Exception {
        String baseUrl = startServerRespondingWith(401, "{\"message\":\"Unauthorized\"}", null);
        RealCloudSyncClient client = new RealCloudSyncClient(baseUrl, "bad-key");

        assertThatThrownBy(() -> client.pushRecord(sampleRecord()))
                .isInstanceOf(SyncException.class);
    }

    @Test
    void isCloudReachableTrueWhenHealthEndpointResponds() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/health", exchange -> {
            byte[] bytes = "{\"status\":\"up\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        String baseUrl = "http://localhost:" + server.getAddress().getPort();
        RealCloudSyncClient client = new RealCloudSyncClient(baseUrl, "key");

        assertThat(client.isCloudReachable()).isTrue();
    }

    @Test
    void isCloudReachableFalseWhenNothingListening() {
        // Port 1 is a reserved low port nothing will be listening on locally.
        RealCloudSyncClient client = new RealCloudSyncClient("http://localhost:1", "key");

        assertThat(client.isCloudReachable()).isFalse();
    }
}
