package com.xcw.aiagentbackend.service;

import org.springframework.stereotype.Service;
import reactor.core.Disposable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StreamSessionManager {

    private final Map<String, Disposable> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> cancelSignals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    public AtomicBoolean registerSignal(String requestId) {
        AtomicBoolean signal = new AtomicBoolean(false);
        cancelSignals.put(requestId, signal);
        sequences.put(requestId, new AtomicLong(0));
        return signal;
    }

    public void setSubscription(String requestId, Disposable disposable) {
        subscriptions.put(requestId, disposable);
    }

    public long nextSeq(String requestId) {
        return sequences.computeIfAbsent(requestId, key -> new AtomicLong(0)).incrementAndGet();
    }

    public boolean cancel(String requestId) {
        AtomicBoolean signal = cancelSignals.computeIfAbsent(requestId, key -> new AtomicBoolean(false));
        signal.set(true);
        Disposable disposable = subscriptions.remove(requestId);
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            return true;
        }
        return false;
    }

    public boolean hasSession(String requestId) {
        return subscriptions.containsKey(requestId) || cancelSignals.containsKey(requestId);
    }

    public boolean isCancelled(String requestId) {
        AtomicBoolean signal = cancelSignals.get(requestId);
        return signal != null && signal.get();
    }

    public void cleanup(String requestId) {
        subscriptions.remove(requestId);
        cancelSignals.remove(requestId);
        sequences.remove(requestId);
    }
}
