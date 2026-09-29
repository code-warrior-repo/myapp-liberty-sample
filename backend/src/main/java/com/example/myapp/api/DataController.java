package com.example.myapp.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/data")
public class DataController {
    @GetMapping
    public Map<String, Object> get() {
        return Map.of("message", "Data returned by Spring Boot", "serverTime", Instant.now().toString(), "items",
                java.util.List.of(Map.of("id", 1, "name", "Alpha"), Map.of("id", 2, "name", "Beta")));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER')")
    public Map<String, Object> post(@RequestBody Map<String, Object> body) {
        Object value = body.get("value");
        if (!(value instanceof String)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "value must be a string");
        }
        return Map.of("saved", true, "value", value, "serverTime", Instant.now().toString());
    }
}
