package com.fabric.fabricaiadvisor.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** One advice request only. Durations include failed attempts and retries. */
public final class AdviceTimings {
    private static final List<String> STAGES = List.of(
            "intent", "database", "ranking", "evidence", "explanation", "validation");
    private final LongSupplier clock;
    private final long started;
    private final Map<String, Long> nanos = new LinkedHashMap<>();
    private final Map<String, Integer> calls = new LinkedHashMap<>();

    public AdviceTimings() {
        this(System::nanoTime);
    }

    AdviceTimings(LongSupplier clock) {
        this.clock = clock;
        this.started = clock.getAsLong();
        STAGES.forEach(stage -> { nanos.put(stage, 0L); calls.put(stage, 0); });
    }

    public Stage start(String stage) {
        if (!nanos.containsKey(stage)) throw new IllegalArgumentException("Unknown stage: " + stage);
        return new Stage(stage, clock.getAsLong());
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> stages = new LinkedHashMap<>();
        STAGES.forEach(stage -> stages.put(stage, Map.of(
                "durationMs", millis(nanos.get(stage)), "calls", calls.get(stage))));
        return Map.of("scope", "advice_service", "unit", "ms",
                "totalMs", millis(clock.getAsLong() - started), "stages", Map.copyOf(stages));
    }

    private static double millis(long nanos) {
        return Math.round(nanos / 1_000.0) / 1_000.0;
    }

    public final class Stage implements AutoCloseable {
        private final String stage;
        private final long began;
        private boolean closed;

        private Stage(String stage, long began) {
            this.stage = stage;
            this.began = began;
        }

        @Override
        public void close() {
            if (closed) return;
            nanos.merge(stage, clock.getAsLong() - began, Long::sum);
            calls.merge(stage, 1, Integer::sum);
            closed = true;
        }
    }
}
