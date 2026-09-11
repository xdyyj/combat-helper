package com.xdyyj.autoattacker;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class AttributeLookupBenchmarkTest {

    static class DummyAttribute {
        final String path;
        DummyAttribute(String path) {
            this.path = path;
        }
    }

    @Test
    public void benchmarkRegistryIterationVsCachedList() {
        // Simulate a Forge attributes registry with 200 registered attributes (common in large modpacks)
        List<DummyAttribute> simulatedRegistry = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            if (i == 45) {
                simulatedRegistry.add(new DummyAttribute("apotheosis:draw_speed"));
            } else if (i == 120) {
                simulatedRegistry.add(new DummyAttribute("attributeslib:arrow_velocity"));
            } else {
                simulatedRegistry.add(new DummyAttribute("mod" + i + ":attribute_" + i));
            }
        }

        // Unoptimized iteration pattern (simulating searching full registry every call)
        int iterations = 100_000;

        // Warmup
        for (int i = 0; i < 10_000; i++) {
            simulateFullRegistryIteration(simulatedRegistry);
        }

        long startIter = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            simulateFullRegistryIteration(simulatedRegistry);
        }
        long durationIter = System.nanoTime() - startIter;

        // Pre-cached lookup pattern
        List<DummyAttribute> cachedDrawSpeedAttrs = new ArrayList<>();
        for (DummyAttribute attr : simulatedRegistry) {
            if (attr.path.contains("draw_speed")) {
                cachedDrawSpeedAttrs.add(attr);
            }
        }

        // Warmup
        for (int i = 0; i < 10_000; i++) {
            simulateCachedListIteration(cachedDrawSpeedAttrs);
        }

        long startCached = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            simulateCachedListIteration(cachedDrawSpeedAttrs);
        }
        long durationCached = System.nanoTime() - startCached;

        System.out.printf("Full Registry Iteration (%d calls): %.2f ms%n", iterations, durationIter / 1_000_000.0);
        System.out.printf("Cached List Iteration (%d calls): %.2f ms%n", iterations, durationCached / 1_000_000.0);
        System.out.printf("Speedup: %.2fx%n", (double) durationIter / Math.max(1, durationCached));
    }

    private void simulateFullRegistryIteration(List<DummyAttribute> registry) {
        for (DummyAttribute attr : registry) {
            String path = attr.path;
            if (path.equals("draw_speed") || path.contains("draw_speed")) {
                // found
                break;
            }
        }
    }

    private void simulateCachedListIteration(List<DummyAttribute> cachedList) {
        for (DummyAttribute attr : cachedList) {
            // found
            break;
        }
    }
}
