package com.xdyyj.autoattacker.weapon;

import org.junit.jupiter.api.Test;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class FirearmAdapterAmmoBenchmarkTest {

    static class FakeItem {
        final String namespace;
        final String path;

        FakeItem(String namespace, String path) {
            this.namespace = namespace;
            this.path = path;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getPath() {
            return path;
        }

        public String getKey() {
            return namespace + ":" + path;
        }
    }

    static class FakeItemStack {
        final FakeItem item;
        final int count;

        FakeItemStack(FakeItem item, int count) {
            this.item = item;
            this.count = count;
        }

        public boolean isEmpty() {
            return item == null || count <= 0;
        }

        public FakeItem getItem() {
            return item;
        }

        public int getCount() {
            return count;
        }
    }

    @Test
    public void runBenchmarkWhenNoAmmoIdMatch() {
        // Build simulated inventory with 36 slots
        List<FakeItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            inventory.add(new FakeItemStack(new FakeItem("minecraft", "cobblestone_" + i), 64));
        }
        // Slot 36 has ammo matching by keyword (not ammoId)
        FakeItem ammoItem = new FakeItem("custommod", "custom_bullet");
        inventory.add(new FakeItemStack(ammoItem, 30));

        String ammoIdStr = "custommod:other_ammo"; // non-matching ammoId
        String gunNs = "custommod";

        int iterations = 200_000;

        // Warmup
        for (int i = 0; i < 10_000; i++) {
            baselineHasAmmo(inventory, ammoIdStr, gunNs);
            singlePassHasAmmo(inventory, ammoIdStr, gunNs);
        }

        // Baseline benchmark
        long startBaseline = System.nanoTime();
        int matchesBaseline = 0;
        for (int i = 0; i < iterations; i++) {
            if (baselineHasAmmo(inventory, ammoIdStr, gunNs)) {
                matchesBaseline++;
            }
        }
        long durationBaseline = System.nanoTime() - startBaseline;

        // Single-pass benchmark
        long startOptimized = System.nanoTime();
        int matchesOptimized = 0;
        for (int i = 0; i < iterations; i++) {
            if (singlePassHasAmmo(inventory, ammoIdStr, gunNs)) {
                matchesOptimized++;
            }
        }
        long durationOptimized = System.nanoTime() - startOptimized;

        assertEquals(matchesBaseline, matchesOptimized);

        double baselineMs = durationBaseline / 1_000_000.0;
        double optimizedMs = durationOptimized / 1_000_000.0;
        double speedup = (double) durationBaseline / durationOptimized;

        System.out.printf("[BENCHMARK RESULTS (No ammoId match)] Iterations: %d%n", iterations);
        System.out.printf("  Baseline Time:  %.3f ms (%.2f ns/op)%n", baselineMs, (double) durationBaseline / iterations);
        System.out.printf("  Single-Pass Time: %.3f ms (%.2f ns/op)%n", optimizedMs, (double) durationOptimized / iterations);
        System.out.printf("  Speedup Factor: %.2fx faster%n", speedup);

        assertTrue(matchesOptimized > 0, "Should match ammo in inventory");
    }

    // Baseline code pattern simulating Step 7 in FirearmAdapter
    private boolean baselineHasAmmo(List<FakeItemStack> items, String ammoIdStr, String gunNs) {
        if (ammoIdStr != null && !ammoIdStr.isEmpty()) {
            for (FakeItemStack invStack : items) {
                if (!invStack.isEmpty()) {
                    String itemRes = invStack.getItem().getKey(); // Simulates ForgeRegistries.ITEMS.getKey
                    if (ammoIdStr.equals(itemRes)) {
                        return true;
                    }
                }
            }
        }

        for (FakeItemStack invStack : items) {
            if (!invStack.isEmpty() && invStack.getCount() > 0) {
                FakeItem item = invStack.getItem();
                if (item != null) {
                    String itemRes = item.getKey(); // Simulates repeated ForgeRegistries.ITEMS.getKey
                    String itemNs = item.getNamespace().toLowerCase(Locale.ROOT);
                    String itemPath = item.getPath().toLowerCase(Locale.ROOT);
                    if ((itemNs.equals(gunNs) || itemNs.equals("minecraft") || itemNs.contains("ammo") || itemNs.contains("bullet")) &&
                        (itemPath.contains("ammo") || itemPath.contains("bullet") || itemPath.contains("magazine") ||
                         itemPath.contains("round") || itemPath.contains("powder") || itemPath.contains("arrow") ||
                         itemPath.contains("cartridge") || itemPath.contains("shot") || itemPath.contains("ball"))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // Single pass: only fetches item key / item metadata once per item stack in inventory
    private boolean singlePassHasAmmo(List<FakeItemStack> items, String ammoIdStr, String gunNs) {
        boolean hasAmmoId = ammoIdStr != null && !ammoIdStr.isEmpty();

        for (FakeItemStack invStack : items) {
            if (invStack.isEmpty() || invStack.getCount() <= 0) continue;

            FakeItem item = invStack.getItem();
            if (item == null) continue;

            String itemRes = item.getKey(); // Fetched ONCE per item
            if (hasAmmoId && ammoIdStr.equals(itemRes)) {
                return true;
            }

            String itemNs = item.getNamespace().toLowerCase(Locale.ROOT);
            String itemPath = item.getPath().toLowerCase(Locale.ROOT);
            if ((itemNs.equals(gunNs) || itemNs.equals("minecraft") || itemNs.contains("ammo") || itemNs.contains("bullet")) &&
                (itemPath.contains("ammo") || itemPath.contains("bullet") || itemPath.contains("magazine") ||
                 itemPath.contains("round") || itemPath.contains("powder") || itemPath.contains("arrow") ||
                 itemPath.contains("cartridge") || itemPath.contains("shot") || itemPath.contains("ball"))) {
                return true;
            }
        }

        return false;
    }
}
