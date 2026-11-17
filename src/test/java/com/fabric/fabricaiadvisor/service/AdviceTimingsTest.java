package com.fabric.fabricaiadvisor.service;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class AdviceTimingsTest {
    @SuppressWarnings("unchecked")
    private Map<String, Object> stage(Map<String, Object> snapshot, String name) {
        return (Map<String, Object>) ((Map<?, ?>) snapshot.get("stages")).get(name);
    }

    @Test
    void recordsFailedAttemptAndRetryWithoutDoubleCountingClose() {
        AtomicLong clock = new AtomicLong();
        AdviceTimings timings = new AdviceTimings(clock::get);
        assertThrows(IllegalStateException.class, () -> {
            try (var ignored = timings.start("explanation")) {
                clock.addAndGet(2_500_000);
                throw new IllegalStateException("failed model attempt");
            }
        });
        var retry = timings.start("explanation");
        clock.addAndGet(1_250_000);
        retry.close(); retry.close();
        clock.addAndGet(250_000);
        var result = timings.snapshot();
        assertEquals(3.75, stage(result, "explanation").get("durationMs"));
        assertEquals(2, stage(result, "explanation").get("calls"));
        assertEquals(4.0, result.get("totalMs"));
        assertEquals(0, stage(result, "validation").get("calls"));
    }

    @Test
    void snapshotsAndSeparateRequestsDoNotShareMutableMeasurements() {
        AtomicLong clock = new AtomicLong();
        AdviceTimings first = new AdviceTimings(clock::get);
        AdviceTimings second = new AdviceTimings(clock::get);
        Map<String, Object> before = first.snapshot();
        try (var ignored = first.start("intent")) { clock.set(1_000_000); }
        assertEquals(0, stage(before, "intent").get("calls"));
        assertEquals(1, stage(first.snapshot(), "intent").get("calls"));
        assertEquals(0, stage(second.snapshot(), "intent").get("calls"));
        assertThrows(UnsupportedOperationException.class, () -> stage(before, "intent").put("calls", 5));
        assertThrows(UnsupportedOperationException.class, () -> before.put("totalMs", 0));
        assertThrows(IllegalArgumentException.class, () -> first.start("typo"));
    }
}
