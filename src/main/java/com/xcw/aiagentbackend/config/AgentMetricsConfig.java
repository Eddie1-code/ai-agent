package com.xcw.aiagentbackend.config;

import com.xcw.aiagentbackend.agent.AgentMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class AgentMetricsConfig {

    private final MeterRegistry meterRegistry;

    public AgentMetricsConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        AgentMetrics.setMeterRegistry(meterRegistry);
    }
}
