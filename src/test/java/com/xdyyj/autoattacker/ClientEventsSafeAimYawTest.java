package com.xdyyj.autoattacker;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientEventsSafeAimYawTest {

    private static final float FLOAT_EPSILON = 1e-3f;

    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        try {
            Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set bootstrapped flag", e);
        }
    }

    @Nested
    @DisplayName("Fallback yaw when horizontal distance < 0.05D")
    class FallbackTests {

        @Test
        @DisplayName("Returns fallbackYaw when dx = 0 and dz = 0")
        void testZeroOffsetReturnsFallback() {
            float fallbackYaw = 45.0f;
            float result = ClientEvents.safeAimYaw(0.0D, 0.0D, fallbackYaw);
            assertEquals(fallbackYaw, result, FLOAT_EPSILON);
        }

        @ParameterizedTest(name = "dx={0}, dz={1}, horiz < 0.05 => fallback={2}")
        @CsvSource({
                "0.01, 0.01, 12.34",
                "-0.02, 0.02, -90.0",
                "0.03, -0.03, 180.0",
                "-0.03, -0.03, -45.0",
                "0.049, 0.0, 0.0",
                "0.0, -0.049, 99.5"
        })
        void testSmallHorizontalDistanceReturnsFallback(double dx, double dz, float fallbackYaw) {
            float result = ClientEvents.safeAimYaw(dx, dz, fallbackYaw);
            assertEquals(fallbackYaw, result, FLOAT_EPSILON);
        }
    }

    @Nested
    @DisplayName("Boundary and direction calculations when horiz >= 0.05D")
    class CalculationTests {

        @Test
        @DisplayName("Boundary at horiz == 0.05D (dx = 0.05, dz = 0)")
        void testBoundaryExactThresholdEast() {
            // horiz = sqrt(0.05^2 + 0^2) = 0.05
            // atan2(0, 0.05) = 0 -> 0 * (180/PI) - 90 = -90.0f
            float result = ClientEvents.safeAimYaw(0.05D, 0.0D, 100.0f);
            assertEquals(-90.0f, result, FLOAT_EPSILON);
        }

        @Test
        @DisplayName("Boundary at horiz == 0.05D (dx = 0, dz = 0.05)")
        void testBoundaryExactThresholdSouth() {
            // horiz = 0.05
            // atan2(0.05, 0) = PI/2 -> 90 - 90 = 0.0f
            float result = ClientEvents.safeAimYaw(0.0D, 0.05D, 100.0f);
            assertEquals(0.0f, result, FLOAT_EPSILON);
        }

        @ParameterizedTest(name = "dx={0}, dz={1} => expectedYaw={2}")
        @CsvSource({
                "0.0, 10.0, 0.0",       // South (+Z): atan2(10, 0) = 90° -> 90 - 90 = 0°
                "-10.0, 0.0, 90.0",      // West (-X): atan2(0, -10) = 180° -> 180 - 90 = 90°
                "0.0, -10.0, -180.0",    // North (-Z): atan2(-10, 0) = -90° -> -90 - 90 = -180°
                "10.0, 0.0, -90.0",      // East (+X): atan2(0, 10) = 0° -> 0 - 90 = -90°
                "10.0, 10.0, -45.0",     // South-East (+X, +Z): atan2(10, 10) = 45° -> 45 - 90 = -45°
                "-10.0, 10.0, 45.0",     // South-West (-X, +Z): atan2(10, -10) = 135° -> 135 - 90 = 45°
                "-10.0, -10.0, -225.0",  // North-West (-X, -Z): atan2(-10, -10) = -135° -> -135 - 90 = -225°
                "10.0, -10.0, -135.0"    // North-East (+X, -Z): atan2(-10, 10) = -45° -> -45 - 90 = -135°
        })
        void testCardinalAndDiagonalDirections(double dx, double dz, float expectedYaw) {
            float fallbackYaw = 999.0f; // Should not be used
            float result = ClientEvents.safeAimYaw(dx, dz, fallbackYaw);
            assertEquals(expectedYaw, result, FLOAT_EPSILON);
        }

        @Test
        @DisplayName("3-4-5 Triangle direction check using Mth.atan2")
        void testPythagoreanTriangleDirection() {
            float expected = (float) (Mth.atan2(4.0, 3.0) * (180.0 / Math.PI)) - 90.0f;
            float result = ClientEvents.safeAimYaw(3.0D, 4.0D, 0.0f);
            assertEquals(expected, result, FLOAT_EPSILON);
        }
    }
}
