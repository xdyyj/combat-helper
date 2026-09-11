package com.xdyyj.autoattacker;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AttributeBenchmarkTest {

    // Simulated Attribute class
    static class FakeAttribute {
        final String namespace;
        final String path;

        FakeAttribute(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }

        public String getPath() {
            return path;
        }
    }

    // Simulated Player/LivingEntity AttributeMap
    static class FakePlayer {
        final Set<FakeAttribute> playerAttributes = new HashSet<>();
        final Map<FakeAttribute, Double> values = new HashMap<>();

        void addAttribute(FakeAttribute attr, double value) {
            playerAttributes.add(attr);
            values.put(attr, value);
        }

        // Mirrors LivingEntity.getAttributeValue(Attribute)
        public double getAttributeValue(FakeAttribute attr) {
            if (!playerAttributes.contains(attr)) {
                throw new IllegalArgumentException("Can't find attribute " + attr.path);
            }
            return values.getOrDefault(attr, 0.0);
        }

        // Mirrors LivingEntity.getAttribute(Attribute)
        public Double getAttribute(FakeAttribute attr) {
            if (!playerAttributes.contains(attr)) {
                return null;
            }
            return values.get(attr);
        }
    }

    @Test
    public void runBenchmark() {
        // Setup registry with 100 fake attributes
        List<FakeAttribute> registry = new ArrayList<>();
        FakeAttribute drawSpeedAttr = new FakeAttribute("apotheosis", "draw_speed");
        FakeAttribute arrowVelocityAttr = new FakeAttribute("apotheosis", "arrow_velocity");

        for (int i = 0; i < 98; i++) {
            registry.add(new FakeAttribute("mod" + i, "generic_attribute_" + i));
        }
        registry.add(drawSpeedAttr);
        registry.add(arrowVelocityAttr);

        FakePlayer player = new FakePlayer();
        // Player has arrow_velocity attribute attached
        player.addAttribute(arrowVelocityAttr, 1.25);

        int iterations = 200_000;

        // Warmup
        for (int i = 0; i < 10_000; i++) {
            baselineDetectSpeedMultiplier(registry, player);
            optimizedDetectSpeedMultiplier(registry, player, Collections.singletonList(arrowVelocityAttr));
        }

        // Baseline measurement
        long startBaseline = System.nanoTime();
        float sumBaseline = 0;
        for (int i = 0; i < iterations; i++) {
            sumBaseline += baselineDetectSpeedMultiplier(registry, player);
        }
        long durationBaseline = System.nanoTime() - startBaseline;

        // Cached pre-filtered attribute list
        List<FakeAttribute> cachedCandidates = new ArrayList<>();
        for (FakeAttribute attr : registry) {
            if (attr.getPath().contains("arrow_velocity")) {
                cachedCandidates.add(attr);
            }
        }

        // Optimized measurement
        long startOptimized = System.nanoTime();
        float sumOptimized = 0;
        for (int i = 0; i < iterations; i++) {
            sumOptimized += optimizedDetectSpeedMultiplier(registry, player, cachedCandidates);
        }
        long durationOptimized = System.nanoTime() - startOptimized;

        assertEquals(sumBaseline, sumOptimized, 0.001f);

        double baselineMs = durationBaseline / 1_000_000.0;
        double optimizedMs = durationOptimized / 1_000_000.0;
        double speedup = (double) durationBaseline / durationOptimized;

        System.out.printf("[BENCHMARK RESULTS] Iterations: %d%n", iterations);
        System.out.printf("  Baseline Time:  %.3f ms (%.2f ns/op)%n", baselineMs, (double) durationBaseline / iterations);
        System.out.printf("  Optimized Time: %.3f ms (%.2f ns/op)%n", optimizedMs, (double) durationOptimized / iterations);
        System.out.printf("  Speedup Factor: %.2fx faster%n", speedup);

        assertTrue(durationOptimized < durationBaseline, "Optimized version should be faster than baseline");
    }

    private float baselineDetectSpeedMultiplier(List<FakeAttribute> registry, FakePlayer player) {
        float multiplier = 1.0f;
        if (player != null) {
            for (FakeAttribute attr : registry) {
                String path = attr.getPath();
                if (path.equals("arrow_velocity") || path.contains("arrow_velocity")) {
                    try {
                        double val = player.getAttributeValue(attr);
                        if (val > 0.1) {
                            return (float) val;
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return Math.max(0.2f, multiplier);
    }

    private float optimizedDetectSpeedMultiplier(List<FakeAttribute> registry, FakePlayer player, List<FakeAttribute> cached) {
        float multiplier = 1.0f;
        if (player != null) {
            for (FakeAttribute attr : cached) {
                Double val = player.getAttribute(attr);
                if (val != null && val > 0.1) {
                    return val.floatValue();
                }
            }
        }
        return Math.max(0.2f, multiplier);
    }
}
