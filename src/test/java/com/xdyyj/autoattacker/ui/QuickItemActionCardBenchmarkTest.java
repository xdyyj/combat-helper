package com.xdyyj.autoattacker.ui;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class QuickItemActionCardBenchmarkTest {

    @Test
    public void runListContainsBenchmark() {
        List<String> configList = List.of(
                "minecraft:diamond_sword",
                "minecraft:bow",
                "extrabotany:failnaught",
                "tconstruct:cleaver",
                "alexsmobs:shield"
        );
        String targetItem = "extrabotany:failnaught";

        int iterations = 1_000_000;

        // Warmup
        for (int i = 0; i < 50_000; i++) {
            boolean dummy1 = new ArrayList<>(configList).contains(targetItem);
            boolean dummy2 = configList.contains(targetItem);
        }

        // Baseline measurement (redundant ArrayList instantiation)
        long startBaseline = System.nanoTime();
        int countBaseline = 0;
        for (int i = 0; i < iterations; i++) {
            List<String> curList = new ArrayList<>(configList);
            if (curList.contains(targetItem)) {
                countBaseline++;
            }
        }
        long durationBaseline = System.nanoTime() - startBaseline;

        // Optimized measurement (direct contains check on underlying list)
        long startOptimized = System.nanoTime();
        int countOptimized = 0;
        for (int i = 0; i < iterations; i++) {
            if (configList.contains(targetItem)) {
                countOptimized++;
            }
        }
        long durationOptimized = System.nanoTime() - startOptimized;

        assertEquals(countBaseline, countOptimized);

        double baselineMs = durationBaseline / 1_000_000.0;
        double optimizedMs = durationOptimized / 1_000_000.0;
        double speedup = (double) durationBaseline / durationOptimized;

        System.out.printf("[BENCHMARK RESULTS] Iterations: %d%n", iterations);
        System.out.printf("  Baseline Time:  %.3f ms (%.2f ns/op)%n", baselineMs, (double) durationBaseline / iterations);
        System.out.printf("  Optimized Time: %.3f ms (%.2f ns/op)%n", optimizedMs, (double) durationOptimized / iterations);
        System.out.printf("  Speedup Factor: %.2fx faster%n", speedup);

        assertTrue(durationOptimized <= durationBaseline, "Optimized version should be as fast as or faster than baseline");
    }
}
