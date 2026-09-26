package com.xdyyj.autoattacker.weapon;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FirearmAdapterAmmoBenchmarkTest {

    static class FakeItemRes {
        final String namespace;
        final String path;

        FakeItemRes(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getPath() {
            return path;
        }
    }

    private static boolean baselineMatch(String gunNs, FakeItemRes itemRes) {
        String itemNs = itemRes.getNamespace().toLowerCase();
        String itemPath = itemRes.getPath().toLowerCase();
        return (itemNs.equals(gunNs) || itemNs.equals("minecraft") || itemNs.contains("ammo") || itemNs.contains("bullet")) &&
                (itemPath.contains("ammo") || itemPath.contains("bullet") || itemPath.contains("magazine") ||
                 itemPath.contains("round") || itemPath.contains("powder") || itemPath.contains("arrow") ||
                 itemPath.contains("cartridge") || itemPath.contains("shot") || itemPath.contains("ball"));
    }

    private static boolean containsAny(String str, String[] keywords) {
        for (String kw : keywords) {
            if (str.contains(kw)) return true;
        }
        return false;
    }

    private static final String[] PATH_KEYWORDS = new String[] {
        "ammo", "bullet", "magazine", "round", "powder", "arrow", "cartridge", "shot", "ball"
    };

    private static boolean optimizedMatch(String gunNs, FakeItemRes itemRes) {
        String itemNs = itemRes.getNamespace();
        if (!itemNs.equals(gunNs) && !itemNs.equals("minecraft") && !itemNs.contains("ammo") && !itemNs.contains("bullet")) {
            return false;
        }
        String itemPath = itemRes.getPath();
        return containsAny(itemPath, PATH_KEYWORDS);
    }

    @Test
    public void benchmarkAmmoMatching() {
        List<FakeItemRes> items = new ArrayList<>();
        // Fill inventory items
        items.add(new FakeItemRes("minecraft", "dirt"));
        items.add(new FakeItemRes("minecraft", "cobblestone"));
        items.add(new FakeItemRes("minecraft", "iron_ingot"));
        items.add(new FakeItemRes("minecraft", "golden_apple"));
        items.add(new FakeItemRes("custommod", "some_tool"));
        items.add(new FakeItemRes("custommod", "sword"));
        items.add(new FakeItemRes("custommod", "shield"));
        items.add(new FakeItemRes("mygunmod", "gun_part"));
        items.add(new FakeItemRes("mygunmod", "rifle_stock"));
        items.add(new FakeItemRes("mygunmod", "rifle_barrel"));
        items.add(new FakeItemRes("minecraft", "bow"));
        items.add(new FakeItemRes("minecraft", "arrow")); // match!

        String gunNs = "mygunmod";

        // Verification of correctness
        for (FakeItemRes item : items) {
            assertEquals(baselineMatch(gunNs, item), optimizedMatch(gunNs, item), "Mismatch for " + item.getNamespace() + ":" + item.getPath());
        }

        int iterations = 100_000;

        // Warmup
        for (int i = 0; i < 10_000; i++) {
            for (FakeItemRes item : items) {
                baselineMatch(gunNs, item);
                optimizedMatch(gunNs, item);
            }
        }

        // Baseline benchmark
        long startBaseline = System.nanoTime();
        int countBaseline = 0;
        for (int i = 0; i < iterations; i++) {
            for (FakeItemRes item : items) {
                if (baselineMatch(gunNs, item)) {
                    countBaseline++;
                }
            }
        }
        long durationBaseline = System.nanoTime() - startBaseline;

        // Optimized benchmark
        long startOptimized = System.nanoTime();
        int countOptimized = 0;
        for (int i = 0; i < iterations; i++) {
            for (FakeItemRes item : items) {
                if (optimizedMatch(gunNs, item)) {
                    countOptimized++;
                }
            }
        }
        long durationOptimized = System.nanoTime() - startOptimized;

        assertEquals(countBaseline, countOptimized);

        double baselineMs = durationBaseline / 1_000_000.0;
        double optimizedMs = durationOptimized / 1_000_000.0;
        double speedup = (double) durationBaseline / durationOptimized;

        System.out.printf("[AMMO MATCH BENCHMARK RESULTS] Iterations: %d x %d items%n", iterations, items.size());
        System.out.printf("  Baseline Time:  %.3f ms (%.2f ns/op)%n", baselineMs, (double) durationBaseline / (iterations * items.size()));
        System.out.printf("  Optimized Time: %.3f ms (%.2f ns/op)%n", optimizedMs, (double) durationOptimized / (iterations * items.size()));
        System.out.printf("  Speedup Factor: %.2fx faster%n", speedup);

        assertTrue(durationOptimized < durationBaseline, "Optimized matching should be faster than baseline");
    }
}
