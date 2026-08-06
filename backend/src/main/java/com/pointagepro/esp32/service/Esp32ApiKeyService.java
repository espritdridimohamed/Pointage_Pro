package com.pointagepro.esp32.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Constant-time comparison of the shared ESP32 API key (app.esp32.api-key) against the
 * X-API-Key request header. The key is shared by all devices in v1; per-device credentials
 * (terminals.api_key_hash) are a module-6 concern.
 */
@Service
public class Esp32ApiKeyService {

    private final String expectedKey;

    public Esp32ApiKeyService(@Value("${app.esp32.api-key}") String apiKey) {
        this.expectedKey = apiKey == null ? "" : apiKey;
    }

    public boolean isValid(String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expectedKey.getBytes(StandardCharsets.UTF_8));
    }
}
