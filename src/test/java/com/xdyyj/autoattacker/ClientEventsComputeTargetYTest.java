package com.xdyyj.autoattacker;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientEventsComputeTargetYTest {

    private static final double EPSILON = 1e-6;

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

    private LivingEntity createMockEntity(double yo, double y, float bbHeight, float eyeHeight, boolean isBaby) {
        LivingEntity entity = mock(LivingEntity.class);
        entity.yo = yo;
        when(entity.getY()).thenReturn(y);
        when(entity.getBbHeight()).thenReturn(bbHeight);
        when(entity.getEyeHeight()).thenReturn(eyeHeight);
        when(entity.isBaby()).thenReturn(isBaby);
        return entity;
    }

    private EnderDragon createMockEnderDragon(double yo, double y, float bbHeight, float eyeHeight) {
        EnderDragon dragon = mock(EnderDragon.class);
        dragon.yo = yo;
        when(dragon.getY()).thenReturn(y);
        when(dragon.getBbHeight()).thenReturn(bbHeight);
        when(dragon.getEyeHeight()).thenReturn(eyeHeight);
        when(dragon.isBaby()).thenReturn(false);
        return dragon;
    }

    @Nested
    @DisplayName("Interpolation (partialTick lerp) tests")
    class LerpTests {

        @ParameterizedTest(name = "partialTick={0}")
        @ValueSource(floats = {0.0f, 0.25f, 0.5f, 0.75f, 1.0f})
        void testPartialTickLerp(float partialTick) {
            LivingEntity target = createMockEntity(10.0, 20.0, 1.8f, 1.62f, false);
            double expectedTargetY = 10.0 + partialTick * (20.0 - 10.0);
            double expectedHeadY = expectedTargetY + 1.62f;

            double result = ClientEvents.computeTargetY(
                    target, partialTick, AutoAttackerConfig.TargetPart.HEAD, false, false, 10.0
            );

            assertEquals(expectedHeadY, result, EPSILON);
        }
    }

    @Nested
    @DisplayName("Entity Type / Height Category tests")
    class EntityCategoryTests {

        @Test
        @DisplayName("Standard adult entity (bbHeight <= 3.0, not baby)")
        void testStandardAdultEntity() {
            LivingEntity target = createMockEntity(10.0, 10.0, 1.8f, 1.62f, false);

            double headY = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, false, false, 10.0);
            double torsoYBow = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.TORSO, false, true, 10.0);
            double torsoYNoBow = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.TORSO, false, false, 10.0);

            // headY = 10.0 + 1.62 = 11.62
            assertEquals(11.62, headY, EPSILON);
            // torsoY = 10.0 + 1.8 * 0.65 = 11.17
            assertEquals(11.17, torsoYBow, EPSILON);
            // bodyY = 10.0 + 1.8 * 0.50 = 10.90
            assertEquals(10.90, torsoYNoBow, EPSILON);
        }

        @Test
        @DisplayName("Baby entity when eyeHeight > bbHeight * 0.82")
        void testBabyEntityEyeHeightDominates() {
            // eyeHeight (0.8) > bbHeight (0.9) * 0.82 = 0.738
            LivingEntity target = createMockEntity(10.0, 10.0, 0.9f, 0.8f, true);

            double headY = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, false, false, 10.0);
            double torsoY = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.TORSO, false, true, 10.0);

            // headY = 10.0 + max(0.8, 0.738) = 10.80
            assertEquals(10.80, headY, EPSILON);
            // torsoY = 10.0 + 0.9 * 0.55 = 10.495
            assertEquals(10.495, torsoY, EPSILON);
        }

        @Test
        @DisplayName("Baby entity when bbHeight * 0.82 > eyeHeight")
        void testBabyEntityScaledHeightDominates() {
            // eyeHeight (0.5) < bbHeight (1.0) * 0.82 = 0.82
            LivingEntity target = createMockEntity(10.0, 10.0, 1.0f, 0.5f, true);

            double headY = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, false, false, 10.0);

            // headY = 10.0 + max(0.5, 0.82) = 10.82
            assertEquals(10.82, headY, EPSILON);
        }

        @Test
        @DisplayName("Tall entity (bbHeight > 3.0D)")
        void testTallEntity() {
            // bbHeight = 4.0, eyeHeight = 3.8; bbHeight * 0.90 = 3.6 (< 3.8)
            LivingEntity target = createMockEntity(20.0, 20.0, 4.0f, 3.8f, false);

            double headY = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, false, false, 10.0);
            double torsoY = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.TORSO, false, true, 10.0);

            // headY = 20.0 + min(3.8, 3.6) = 23.60
            assertEquals(23.60, headY, EPSILON);
            // torsoY = 20.0 + 4.0 * 0.60 = 22.40
            assertEquals(22.40, torsoY, EPSILON);
        }

        @Test
        @DisplayName("Tall entity when eyeHeight < bbHeight * 0.90")
        void testTallEntityEyeHeightSmaller() {
            // bbHeight = 4.0, eyeHeight = 3.0; bbHeight * 0.90 = 3.6 (> 3.0)
            LivingEntity target = createMockEntity(20.0, 20.0, 4.0f, 3.0f, false);

            double headY = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, false, false, 10.0);

            // headY = 20.0 + min(3.0, 3.6) = 23.0
            assertEquals(23.0, headY, EPSILON);
        }

        @Test
        @DisplayName("EnderDragon entity special case")
        void testEnderDragon() {
            EnderDragon dragon = createMockEnderDragon(50.0, 50.0, 4.0f, 3.0f);

            double headY = ClientEvents.computeTargetY(dragon, 1.0f, AutoAttackerConfig.TargetPart.HEAD, false, false, 10.0);
            double torsoY = ClientEvents.computeTargetY(dragon, 1.0f, AutoAttackerConfig.TargetPart.TORSO, false, true, 10.0);

            // headY = 50.0 + 4.0 * 0.5 = 52.0
            assertEquals(52.0, headY, EPSILON);
            // torsoY = 50.0 + 4.0 * 0.4 = 51.6
            assertEquals(51.6, torsoY, EPSILON);
        }
    }

    @Nested
    @DisplayName("TargetPart.HEAD tests")
    class HeadPartTests {

        @Test
        @DisplayName("HEAD with non-gun weapon returns exact headY regardless of distance")
        void testHeadNonGun() {
            LivingEntity target = createMockEntity(10.0, 10.0, 1.8f, 1.62f, false);
            double expectedHeadY = 11.62;

            double resultNear = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, false, false, 10.0);
            double resultFar = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, false, false, 100.0);

            assertEquals(expectedHeadY, resultNear, EPSILON);
            assertEquals(expectedHeadY, resultFar, EPSILON);
        }

        @Test
        @DisplayName("HEAD with gun at targetDistXZ <= 40.0 returns exact headY")
        void testHeadGunShortDistance() {
            LivingEntity target = createMockEntity(10.0, 10.0, 1.8f, 1.62f, false);

            double result = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, true, false, 40.0);

            assertEquals(11.62, result, EPSILON);
        }

        @Test
        @DisplayName("HEAD with gun at targetDistXZ > 40.0 adjusts headY by bbHeight * 0.05 when < 0.12")
        void testHeadGunLongDistanceSmallEntity() {
            // bbHeight = 1.8; 1.8 * 0.05 = 0.09 (< 0.12)
            LivingEntity target = createMockEntity(10.0, 10.0, 1.8f, 1.62f, false);

            double result = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, true, false, 40.1);

            // expected = 11.62 - 0.09 = 11.53
            assertEquals(11.53, result, EPSILON);
        }

        @Test
        @DisplayName("HEAD with gun at targetDistXZ > 40.0 adjusts headY capped at 0.12 when bbHeight * 0.05 >= 0.12")
        void testHeadGunLongDistanceLargeEntity() {
            // bbHeight = 3.0; 3.0 * 0.05 = 0.15 (capped at 0.12)
            LivingEntity target = createMockEntity(10.0, 10.0, 3.0f, 2.7f, false);

            double result = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.HEAD, true, false, 50.0);

            // headY = 10.0 + 2.7 = 12.70; expected = 12.70 - 0.12 = 12.58
            assertEquals(12.58, result, EPSILON);
        }
    }

    @Nested
    @DisplayName("TargetPart.TORSO tests")
    class TorsoPartTests {

        @Test
        @DisplayName("TORSO with isHoldingBow = true returns torsoY")
        void testTorsoHoldingBow() {
            LivingEntity target = createMockEntity(10.0, 10.0, 2.0f, 1.8f, false);

            double result = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.TORSO, false, true, 15.0);

            // torsoY = 10.0 + 2.0 * 0.65 = 11.30
            assertEquals(11.30, result, EPSILON);
        }

        @Test
        @DisplayName("TORSO with isHoldingBow = false returns bodyY")
        void testTorsoNotHoldingBow() {
            LivingEntity target = createMockEntity(10.0, 10.0, 2.0f, 1.8f, false);

            double result = ClientEvents.computeTargetY(target, 1.0f, AutoAttackerConfig.TargetPart.TORSO, false, false, 15.0);

            // bodyY = 10.0 + 2.0 * 0.50 = 11.00
            assertEquals(11.00, result, EPSILON);
        }
    }

    @Nested
    @DisplayName("TargetPart.ADAPTIVE tests")
    class AdaptivePartTests {

        @ParameterizedTest(name = "Gun ADAPTIVE distance={0}")
        @CsvSource({
                "75.0, 11.476",  // > 70.0: headY - min(0.16, 1.8 * 0.08 = 0.144) = 11.62 - 0.144 = 11.476
                "2.5, 11.17",    // < 3.0: torsoY = 10.0 + 1.8 * 0.65 = 11.17
                "3.0, 11.62",    // 3.0 <= dist <= 70.0: headY = 11.62
                "50.0, 11.62",   // 3.0 <= dist <= 70.0: headY = 11.62
                "70.0, 11.62"    // 3.0 <= dist <= 70.0: headY = 11.62
        })
        void testAdaptiveGunDistances(double dist, double expected) {
            LivingEntity target = createMockEntity(10.0, 10.0, 1.8f, 1.62f, false);

            double result = ClientEvents.computeTargetY(
                    target, 1.0f, AutoAttackerConfig.TargetPart.ADAPTIVE, true, false, dist
            );

            assertEquals(expected, result, EPSILON);
        }

        @Test
        @DisplayName("ADAPTIVE for gun at distance > 70.0 with large entity caps offset at 0.16")
        void testAdaptiveGunLongDistanceCapped() {
            // bbHeight = 3.0; 3.0 * 0.08 = 0.24 (capped at 0.16)
            LivingEntity target = createMockEntity(10.0, 10.0, 3.0f, 2.7f, false);

            double result = ClientEvents.computeTargetY(
                    target, 1.0f, AutoAttackerConfig.TargetPart.ADAPTIVE, true, false, 80.0
            );

            // headY = 12.70; expected = 12.70 - 0.16 = 12.54
            assertEquals(12.54, result, EPSILON);
        }

        @Test
        @DisplayName("ADAPTIVE for non-gun at targetDistXZ <= 25.0 returns headY")
        void testAdaptiveNonGunShortDistance() {
            LivingEntity target = createMockEntity(10.0, 10.0, 1.8f, 1.62f, false);

            double result = ClientEvents.computeTargetY(
                    target, 1.0f, AutoAttackerConfig.TargetPart.ADAPTIVE, false, false, 25.0
            );

            assertEquals(11.62, result, EPSILON);
        }

        @Test
        @DisplayName("ADAPTIVE for non-gun at targetDistXZ > 25.0 with bow returns torsoY")
        void testAdaptiveNonGunLongDistanceBow() {
            LivingEntity target = createMockEntity(10.0, 10.0, 1.8f, 1.62f, false);

            double result = ClientEvents.computeTargetY(
                    target, 1.0f, AutoAttackerConfig.TargetPart.ADAPTIVE, false, true, 25.1
            );

            // torsoY = 10.0 + 1.8 * 0.65 = 11.17
            assertEquals(11.17, result, EPSILON);
        }

        @Test
        @DisplayName("ADAPTIVE for non-gun at targetDistXZ > 25.0 without bow returns bodyY")
        void testAdaptiveNonGunLongDistanceNoBow() {
            LivingEntity target = createMockEntity(10.0, 10.0, 1.8f, 1.62f, false);

            double result = ClientEvents.computeTargetY(
                    target, 1.0f, AutoAttackerConfig.TargetPart.ADAPTIVE, false, false, 25.1
            );

            // bodyY = 10.0 + 1.8 * 0.50 = 10.90
            assertEquals(10.90, result, EPSILON);
        }
    }
}
