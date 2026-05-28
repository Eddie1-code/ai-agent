package com.xcw.aiagentbackend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaStore {

    private static final long TTL_MS = 3 * 60 * 1000; // 3 minutes

    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();

    public record CaptchaEntry(String code, long expireAt) {}

    public String put(String code) {
        String key = UUID.randomUUID().toString();
        store.put(key, new CaptchaEntry(code, System.currentTimeMillis() + TTL_MS));
        return key;
    }

    public boolean verify(String key, String code) {
        CaptchaEntry entry = store.get(key);
        if (entry == null) {
            return false;
        }
        store.remove(key); // one-time use
        if (System.currentTimeMillis() > entry.expireAt) {
            return false;
        }
        return entry.code.equalsIgnoreCase(code);
    }

    @Scheduled(fixedRate = 60_000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> now > e.getValue().expireAt);
    }
}
