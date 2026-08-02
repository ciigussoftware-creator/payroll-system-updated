package com.payroll.web.ping;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PingController {

    @GetMapping("/api/ping")
    public ResponseEntity<Map<String, String>> ping(Authentication authentication) {
        return ResponseEntity.ok(Map.of("message", "pong", "user", authentication.getName()));
    }
}
