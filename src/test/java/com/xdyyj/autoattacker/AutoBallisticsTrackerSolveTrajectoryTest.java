package com.xdyyj.autoattacker;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class AutoBallisticsTrackerSolveTrajectoryTest {

    private static final double EPSILON = 1e-4;

    @BeforeAll
    static void initMinecraftBootstrap() {
        try {
            Field isBootstrappedField = Bootstrap.class.getDeclaredField("isBootstrapped");
            isBootstrappedField.setAccessible(true);
            isBootstrappedField.setBoolean(null, true);
            Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        } catch (Throwable t) {
            throw new RuntimeException("Failed to initialize Minecraft Bootstrap flag for tests", t);
        }
    }

    @Nested
    @DisplayName("Zero Gravity & Special Short-Distance Scenarios")
    class ZeroGravityAndShortDistanceTests {

        @Test
        @DisplayName("Zero gravity calculates direct pitch angle and flight time")
        void testZeroGravity() {
            Vec3 eye = new Vec3(0, 10, 0);
            Vec3 target = new Vec3(10, 20, 0); // horizDist = 10, targetDy = 10, totalDist = sqrt(200) ~ 14.1421356
            double speed = 3.0;
            double gravity = 0.0;
            double drag = 0.99;

            AutoBallisticsTracker.TrajectorySolution solution =
                    AutoBallisticsTracker.solveTrajectory(eye, target, speed, gravity, drag);

            assertTrue(solution.reachable, "Zero gravity trajectory should be reachable");
            // direct pitch = -atan2(10, 10) * 180 / PI = -45.0 degrees
            assertEquals(-45.0f, solution.pitchDeg, 1e-3f, "Pitch should equal direct angle (-45 degrees)");
            double expectedFlightTime = Math.sqrt(200.0) / 3.0;
            assertEquals(expectedFlightTime, solution.flightTicks, EPSILON, "Flight time should equal totalDist / speed");
        }

        @Test
        @DisplayName("Near-vertical target (horizDist < 0.15) uses direct pitch branch")
        void testNearVerticalDistance() {
            Vec3 eye = new Vec3(0, 10, 0);
            Vec3 targetNearUp = new Vec3(0.05, 20, 0.05); // horizDist ~ 0.0707 < 0.15, targetDy = 10
            double speed = 2.0;
            double gravity = 0.05;

            AutoBallisticsTracker.TrajectorySolution solution =
                    AutoBallisticsTracker.solveTrajectory(eye, targetNearUp, speed, gravity, 0.99);

            assertTrue(solution.reachable, "Near-vertical target should be reachable");
            // directPitch should be nearly -90 degrees (pitching straight up)
            assertTrue(solution.pitchDeg < -88.0f, "Pitch should be near -90 degrees aiming up");
            double expectedFlightTime = eye.distanceTo(targetNearUp) / speed;
            assertEquals(expectedFlightTime, solution.flightTicks, EPSILON);
        }
    }

    @Nested
    @DisplayName("Trajectory Physics & Reachability Scenarios")
    class TrajectoryPhysicsTests {

        @Test
        @DisplayName("Standard reachable trajectory with bow physics")
        void testStandardReachableTrajectory() {
            Vec3 eye = new Vec3(0, 10, 0);
            Vec3 target = new Vec3(20, 10, 0); // Horizontal target at 20 blocks
            double speed = 3.0;   // Vanilla bow arrow speed
            double gravity = 0.05; // Vanilla arrow gravity
            double drag = 0.99;

            AutoBallisticsTracker.TrajectorySolution solution =
                    AutoBallisticsTracker.solveTrajectory(eye, target, speed, gravity, drag);

            assertTrue(solution.reachable, "Standard bow shot at 20m should be reachable");
            // Gravity causes downward curve, so launcher must compensate upward -> pitch < 0
            assertTrue(solution.pitchDeg < 0.0f, "Pitch angle must be elevated (negative pitch) to hit target");
            assertTrue(solution.flightTicks > 0.0, "Flight ticks should be positive");
        }

        @Test
        @DisplayName("Unreachable target far out of range returns reachable = false")
        void testUnreachableTarget() {
            Vec3 eye = new Vec3(0, 0, 0);
            Vec3 targetFar = new Vec3(200, 50, 0); // Very far away
            double speed = 1.0;   // Low speed
            double gravity = 0.05;
            double drag = 0.99;

            AutoBallisticsTracker.TrajectorySolution solution =
                    AutoBallisticsTracker.solveTrajectory(eye, targetFar, speed, gravity, drag);

            assertFalse(solution.reachable, "Target far out of ballistic range should not be reachable");
        }

        @Test
        @DisplayName("Custom drag values influence pitch compensation correctly")
        void testCustomDrag() {
            Vec3 eye = new Vec3(0, 10, 0);
            Vec3 target = new Vec3(25, 10, 0);
            double speed = 3.0;
            double gravity = 0.05;

            AutoBallisticsTracker.TrajectorySolution lowDragSol =
                    AutoBallisticsTracker.solveTrajectory(eye, target, speed, gravity, 0.99); // 1% speed loss per tick (air drag)
            AutoBallisticsTracker.TrajectorySolution highDragSol =
                    AutoBallisticsTracker.solveTrajectory(eye, target, speed, gravity, 0.95); // 5% speed loss per tick

            assertTrue(lowDragSol.reachable, "Low drag shot should be reachable");
            assertTrue(highDragSol.reachable, "High drag shot should be reachable at 25m");
            // Higher drag causes projectile to decelerate faster, requiring a steeper elevation angle (more negative pitch)
            assertTrue(highDragSol.pitchDeg < lowDragSol.pitchDeg,
                    "Higher drag requires higher elevation angle, resulting in more negative pitchDeg. lowDragPitch=" + lowDragSol.pitchDeg + ", highDragPitch=" + highDragSol.pitchDeg);
        }
    }

    @Nested
    @DisplayName("Edge Cases & Method Overload Tests")
    class EdgeCaseAndOverloadTests {

        @ParameterizedTest(name = "Speed={0}")
        @CsvSource({
                "0.0",
                "-1.0",
                "0.1"
        })
        @DisplayName("Low and zero speed values fallback to 0.25 floor to prevent division by zero")
        void testLowAndZeroSpeed(double speed) {
            Vec3 eye = new Vec3(0, 10, 0);
            Vec3 target = new Vec3(0, 20, 0); // Straight up, horizDist = 0 < 0.15
            double gravity = 0.0;

            AutoBallisticsTracker.TrajectorySolution solution =
                    AutoBallisticsTracker.solveTrajectory(eye, target, speed, gravity, 0.99);

            assertTrue(solution.reachable);
            assertFalse(Double.isNaN(solution.flightTicks), "Flight ticks must not be NaN");
            assertFalse(Double.isInfinite(solution.flightTicks), "Flight ticks must not be Infinite");
            // Distance = 10, clamped speed floor = 0.25 -> flightTicks = 10 / 0.25 = 40.0
            assertEquals(40.0, solution.flightTicks, EPSILON);
        }

        @Test
        @DisplayName("4-argument overload solveTrajectory matches 5-argument version with default drag 0.99")
        void testFourArgumentOverload() {
            Vec3 eye = new Vec3(0, 1.62, 0);
            Vec3 target = new Vec3(12, 2.0, 5);
            double speed = 3.15;
            double gravity = 0.05;

            AutoBallisticsTracker.TrajectorySolution sol4 =
                    AutoBallisticsTracker.solveTrajectory(eye, target, speed, gravity);
            AutoBallisticsTracker.TrajectorySolution sol5 =
                    AutoBallisticsTracker.solveTrajectory(eye, target, speed, gravity, 0.99D);

            assertEquals(sol5.pitchDeg, sol4.pitchDeg, 1e-4f);
            assertEquals(sol5.flightTicks, sol4.flightTicks, 1e-4);
            assertEquals(sol5.reachable, sol4.reachable);
        }

        @Test
        @DisplayName("Pitch orientation convention: target above -> negative pitch, target below -> positive pitch")
        void testPitchOrientation() {
            Vec3 eye = new Vec3(0, 10, 0);
            Vec3 targetAbove = new Vec3(10, 20, 0);
            Vec3 targetBelow = new Vec3(10, 0, 0);

            AutoBallisticsTracker.TrajectorySolution solAbove =
                    AutoBallisticsTracker.solveTrajectory(eye, targetAbove, 5.0, 0.0);
            AutoBallisticsTracker.TrajectorySolution solBelow =
                    AutoBallisticsTracker.solveTrajectory(eye, targetBelow, 5.0, 0.0);

            assertTrue(solAbove.pitchDeg < 0.0f, "Target above eye should produce negative pitch (looking up in MC)");
            assertTrue(solBelow.pitchDeg > 0.0f, "Target below eye should produce positive pitch (looking down in MC)");
        }
    }
}
