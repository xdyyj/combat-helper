package com.xdyyj.autoattacker;

import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientEventsScoreCandidateTest {

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

    private LivingEntity createMockEntity(float health) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getHealth()).thenReturn(health);
        return entity;
    }

    @Nested
    @DisplayName("SwitchPriority.FOV Tests")
    class FovPriorityTests {

        @ParameterizedTest(name = "alignment={0}, distSqr={1}, range={2} -> expectedScore={3}")
        @CsvSource({
                "1.0, 100.0, 20.0, 0.925",     // 1.0 - 0.15 * (10.0 / 20.0) = 0.925
                "0.0, 100.0, 20.0, -0.075",    // 0.0 - 0.15 * (10.0 / 20.0) = -0.075
                "-1.0, 100.0, 20.0, -1.075",   // -1.0 - 0.15 * (10.0 / 20.0) = -1.075
                "0.8, 0.0, 64.0, 0.8",         // 0.8 - 0.15 * (0.0 / 64.0) = 0.8
                "0.5, 400.0, 64.0, 0.453125"   // 0.5 - 0.15 * (20.0 / 64.0) = 0.453125
        })
        void testFovScoringFormula(double alignment, double distSqr, double range, double expectedScore) {
            LivingEntity entity = createMockEntity(20.0f);
            ClientEvents.Candidate candidate = new ClientEvents.Candidate(entity, 0.0, distSqr, alignment);

            double score = ClientEvents.scoreCandidate(candidate, range, AutoAttackerConfig.SwitchPriority.FOV);

            assertEquals(expectedScore, score, EPSILON);
        }

        @Test
        @DisplayName("Higher aligned target scores higher than lower aligned target in FOV mode")
        void testFovRanking() {
            LivingEntity entity1 = createMockEntity(20.0f);
            LivingEntity entity2 = createMockEntity(20.0f);

            // Candidate A: Alignment 0.95, Distance 10 (distSqr = 100)
            ClientEvents.Candidate candidateA = new ClientEvents.Candidate(entity1, 5.0, 100.0, 0.95);
            // Candidate B: Alignment 0.50, Distance 4 (distSqr = 16)
            ClientEvents.Candidate candidateB = new ClientEvents.Candidate(entity2, 30.0, 16.0, 0.50);

            double range = 64.0;
            double scoreA = ClientEvents.scoreCandidate(candidateA, range, AutoAttackerConfig.SwitchPriority.FOV);
            double scoreB = ClientEvents.scoreCandidate(candidateB, range, AutoAttackerConfig.SwitchPriority.FOV);

            assertTrue(scoreA > scoreB, "Candidate with higher alignment (0.95) should score higher than lower alignment (0.50)");
        }
    }

    @Nested
    @DisplayName("SwitchPriority.DISTANCE Tests")
    class DistancePriorityTests {

        @ParameterizedTest(name = "distSqr={0} -> expectedScore={1}")
        @CsvSource({
                "0.0, 0.0",
                "25.0, -5.0",
                "100.0, -10.0",
                "2500.0, -50.0"
        })
        void testDistanceScoringFormula(double distSqr, double expectedScore) {
            LivingEntity entity = createMockEntity(20.0f);
            ClientEvents.Candidate candidate = new ClientEvents.Candidate(entity, 0.0, distSqr, 0.0);

            double score = ClientEvents.scoreCandidate(candidate, 64.0, AutoAttackerConfig.SwitchPriority.DISTANCE);

            assertEquals(expectedScore, score, EPSILON);
        }

        @Test
        @DisplayName("Closer target scores higher than farther target in DISTANCE mode")
        void testDistanceRanking() {
            LivingEntity entity1 = createMockEntity(20.0f);
            LivingEntity entity2 = createMockEntity(20.0f);

            // Candidate Near: distSqr = 25 (dist = 5)
            ClientEvents.Candidate candidateNear = new ClientEvents.Candidate(entity1, 10.0, 25.0, 0.8);
            // Candidate Far: distSqr = 100 (dist = 10)
            ClientEvents.Candidate candidateFar = new ClientEvents.Candidate(entity2, 2.0, 100.0, 0.99);

            double range = 64.0;
            double scoreNear = ClientEvents.scoreCandidate(candidateNear, range, AutoAttackerConfig.SwitchPriority.DISTANCE);
            double scoreFar = ClientEvents.scoreCandidate(candidateFar, range, AutoAttackerConfig.SwitchPriority.DISTANCE);

            assertTrue(scoreNear > scoreFar, "Closer candidate (-5.0) should score higher than farther candidate (-10.0)");
        }
    }

    @Nested
    @DisplayName("SwitchPriority.HEALTH Tests")
    class HealthPriorityTests {

        @ParameterizedTest(name = "health={0} -> expectedScore={1}")
        @CsvSource({
                "20.0, -20.0",
                "10.0, -10.0",
                "1.0, -1.0",
                "0.5, -0.5"
        })
        void testHealthScoringFormula(float health, double expectedScore) {
            LivingEntity entity = createMockEntity(health);
            ClientEvents.Candidate candidate = new ClientEvents.Candidate(entity, 0.0, 100.0, 0.8);

            double score = ClientEvents.scoreCandidate(candidate, 64.0, AutoAttackerConfig.SwitchPriority.HEALTH);

            assertEquals(expectedScore, score, EPSILON);
        }

        @Test
        @DisplayName("Lower health target scores higher than higher health target in HEALTH mode")
        void testHealthRanking() {
            LivingEntity lowHealthEntity = createMockEntity(2.0f);
            LivingEntity fullHealthEntity = createMockEntity(20.0f);

            ClientEvents.Candidate candidateLowHealth = new ClientEvents.Candidate(lowHealthEntity, 15.0, 100.0, 0.7);
            ClientEvents.Candidate candidateFullHealth = new ClientEvents.Candidate(fullHealthEntity, 2.0, 16.0, 0.98);

            double range = 64.0;
            double scoreLow = ClientEvents.scoreCandidate(candidateLowHealth, range, AutoAttackerConfig.SwitchPriority.HEALTH);
            double scoreFull = ClientEvents.scoreCandidate(candidateFullHealth, range, AutoAttackerConfig.SwitchPriority.HEALTH);

            assertTrue(scoreLow > scoreFull, "Lower health candidate (-2.0) should score higher than full health candidate (-20.0)");
        }
    }
}
