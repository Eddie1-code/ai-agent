package com.xcw.aiagentbackend.agent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent执行轨迹收集器，记录每步的思考/行动耗时、工具选择、Token消耗。
 * Agent运行结束后输出JSON格式的执行轨迹，供评估和调试使用。
 */
@Slf4j
public class AgentMetrics {

    private static MeterRegistry meterRegistry;

    public static void setMeterRegistry(MeterRegistry registry) {
        meterRegistry = registry;
    }

    private final String agentName;
    private final Instant startTime;
    private final List<StepRecord> steps = new ArrayList<>();
    private Instant endTime;

    public AgentMetrics(String agentName) {
        this.agentName = agentName;
        this.startTime = Instant.now();
    }

    public StepRecorder step(int stepNumber) {
        return new StepRecorder(stepNumber);
    }

    public void markFinished() {
        this.endTime = Instant.now();
    }

    /**
     * 输出JSON格式的完整执行轨迹。
     * 在Agent.run()的finally块中调用。
     */
    public void logSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("agent", agentName);
        summary.put("start_time", startTime.toString());

        long totalMs = 0;
        if (endTime != null) {
            totalMs = Duration.between(startTime, endTime).toMillis();
        }
        summary.put("total_duration_ms", totalMs);
        summary.put("total_steps", steps.size());

        // 汇总统计
        long totalThinkMs = 0;
        long totalActMs = 0;
        int toolCallCount = 0;
        int errorCount = 0;
        for (StepRecord s : steps) {
            totalThinkMs += s.thinkDurationMs;
            totalActMs += s.actDurationMs;
            toolCallCount += s.toolNames.size();
            if (!s.success) errorCount++;
        }
        summary.put("total_think_ms", totalThinkMs);
        summary.put("total_act_ms", totalActMs);
        summary.put("total_tool_calls", toolCallCount);
        summary.put("error_steps", errorCount);

        // 工具使用频率
        Map<String, Integer> toolFrequency = new LinkedHashMap<>();
        for (StepRecord s : steps) {
            for (String tool : s.toolNames) {
                toolFrequency.merge(tool, 1, Integer::sum);
            }
        }
        summary.put("tool_frequency", toolFrequency);

        // Token汇总
        long totalInputTokens = 0;
        long totalOutputTokens = 0;
        for (StepRecord s : steps) {
            totalInputTokens += s.inputTokens;
            totalOutputTokens += s.outputTokens;
        }
        summary.put("total_input_tokens", totalInputTokens);
        summary.put("total_output_tokens", totalOutputTokens);
        summary.put("total_tokens", totalInputTokens + totalOutputTokens);

        // 逐步详情
        List<Map<String, Object>> stepDetails = new ArrayList<>();
        for (StepRecord s : steps) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("step", s.stepNumber);
            detail.put("think_ms", s.thinkDurationMs);
            detail.put("act_ms", s.actDurationMs);
            detail.put("tools", s.toolNames);
            detail.put("tool_args", s.toolArgs);
            detail.put("input_tokens", s.inputTokens);
            detail.put("output_tokens", s.outputTokens);
            detail.put("success", s.success);
            if (s.errorMessage != null) detail.put("error", s.errorMessage);
            stepDetails.add(detail);
        }
        summary.put("steps", stepDetails);

        // 输出为JSON日志
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
            log.info("AgentMetrics [{}]:\n{}", agentName, json);
        } catch (Exception e) {
            log.info("AgentMetrics [{}]: {}", agentName, summary);
        }

        // 注册为 Micrometer 指标，供 Prometheus + Grafana 采集
        if (meterRegistry != null) {
            try {
                String agentTag = agentName != null ? agentName : "unknown";
                Counter.builder("agent.runs.total")
                        .tag("agent", agentTag)
                        .register(meterRegistry)
                        .increment();
                Counter.builder("agent.steps.total")
                        .tag("agent", agentTag)
                        .register(meterRegistry)
                        .increment(steps.size());
                Timer.builder("agent.duration.seconds")
                        .tag("agent", agentTag)
                        .register(meterRegistry)
                        .record(Duration.ofMillis(totalMs));
                Timer.builder("agent.think.seconds")
                        .tag("agent", agentTag)
                        .register(meterRegistry)
                        .record(Duration.ofMillis(totalThinkMs));
                Timer.builder("agent.act.seconds")
                        .tag("agent", agentTag)
                        .register(meterRegistry)
                        .record(Duration.ofMillis(totalActMs));
                Counter.builder("agent.errors.total")
                        .tag("agent", agentTag)
                        .register(meterRegistry)
                        .increment(errorCount);
                Counter.builder("agent.tokens.input.total")
                        .tag("agent", agentTag)
                        .register(meterRegistry)
                        .increment(totalInputTokens);
                Counter.builder("agent.tokens.output.total")
                        .tag("agent", agentTag)
                        .register(meterRegistry)
                        .increment(totalOutputTokens);
                for (Map.Entry<String, Integer> entry : toolFrequency.entrySet()) {
                    Counter.builder("agent.tool.calls.total")
                            .tag("agent", agentTag)
                            .tag("tool", entry.getKey())
                            .register(meterRegistry)
                            .increment(entry.getValue());
                }
            } catch (Exception e) {
                log.warn("Failed to register Micrometer metrics for agent [{}]", agentName, e);
            }
        }
    }

    @Data
    public class StepRecorder {
        private final int stepNumber;
        private final List<String> toolNames = new ArrayList<>();
        private final List<String> toolArgs = new ArrayList<>();
        private long thinkDurationMs;
        private long actDurationMs;
        private long inputTokens;
        private long outputTokens;
        private boolean success = true;
        private String errorMessage;

        StepRecorder(int stepNumber) {
            this.stepNumber = stepNumber;
        }

        public StepRecorder thinkMs(long ms) { this.thinkDurationMs = ms; return this; }
        public StepRecorder actMs(long ms) { this.actDurationMs = ms; return this; }
        public StepRecorder addTool(String name, String args) {
            this.toolNames.add(name);
            this.toolArgs.add(args);
            return this;
        }
        public StepRecorder tokens(long input, long output) {
            this.inputTokens = input;
            this.outputTokens = output;
            return this;
        }
        public StepRecorder error(String msg) {
            this.success = false;
            this.errorMessage = msg;
            return this;
        }

        public StepRecord done() {
            StepRecord record = new StepRecord();
            record.stepNumber = this.stepNumber;
            record.toolNames.addAll(this.toolNames);
            record.toolArgs.addAll(this.toolArgs);
            record.thinkDurationMs = this.thinkDurationMs;
            record.actDurationMs = this.actDurationMs;
            record.inputTokens = this.inputTokens;
            record.outputTokens = this.outputTokens;
            record.success = this.success;
            record.errorMessage = this.errorMessage;
            steps.add(record);
            return record;
        }
    }

    @Data
    public static class StepRecord {
        int stepNumber;
        List<String> toolNames = new ArrayList<>();
        List<String> toolArgs = new ArrayList<>();
        long thinkDurationMs;
        long actDurationMs;
        long inputTokens;
        long outputTokens;
        boolean success = true;
        String errorMessage;
    }
}
