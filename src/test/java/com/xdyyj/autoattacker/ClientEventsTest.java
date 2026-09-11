package com.xdyyj.autoattacker;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientEventsTest {

    @BeforeAll
    static void initMinecraft() {
        SharedConstants.tryDetectVersion();
        try {
            Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Throwable ignored) {
        }
        try {
            BuiltInRegistries.bootStrap();
        } catch (Throwable ignored) {
        }
    }

    @Mock
    private Player player;

    @Mock
    private LivingEntity target;

    @Mock
    private EntityType<?> entityType;

    @BeforeEach
    void setUp() {
        AutoAttackerConfig.excludedEntities.clear();
    }

    @AfterEach
    void tearDown() {
        AutoAttackerConfig.excludedEntities.clear();
    }

    private void configureValidTarget(LivingEntity mockTarget) {
        when(mockTarget.isAlive()).thenReturn(true);
        when(mockTarget.isRemoved()).thenReturn(false);
        when(mockTarget.getHealth()).thenReturn(20.0f);
        when(mockTarget.isSpectator()).thenReturn(false);
        when(mockTarget.isPickable()).thenReturn(true);
        doReturn(entityType).when(mockTarget).getType();
    }

    @Test
    @DisplayName("Returns false when target is null")
    void testTargetNull() {
        assertFalse(ClientEvents.isValidTarget(player, null));
    }

    @Test
    @DisplayName("Returns false when target is the player itself")
    void testTargetIsPlayerSelf() {
        assertFalse(ClientEvents.isValidTarget(player, player));
    }

    @Test
    @DisplayName("Returns false when target is not alive")
    void testTargetNotAlive() {
        when(target.isAlive()).thenReturn(false);
        assertFalse(ClientEvents.isValidTarget(player, target));
    }

    @Test
    @DisplayName("Returns false when target is removed")
    void testTargetIsRemoved() {
        when(target.isAlive()).thenReturn(true);
        when(target.isRemoved()).thenReturn(true);
        assertFalse(ClientEvents.isValidTarget(player, target));
    }

    @Test
    @DisplayName("Returns false when target health is zero or negative")
    void testTargetHealthZeroOrNegative() {
        when(target.isAlive()).thenReturn(true);
        when(target.isRemoved()).thenReturn(false);
        when(target.getHealth()).thenReturn(0.0f);
        assertFalse(ClientEvents.isValidTarget(player, target));

        when(target.getHealth()).thenReturn(-5.0f);
        assertFalse(ClientEvents.isValidTarget(player, target));
    }

    @Test
    @DisplayName("Returns false when target is spectator")
    void testTargetIsSpectator() {
        when(target.isAlive()).thenReturn(true);
        when(target.isRemoved()).thenReturn(false);
        when(target.getHealth()).thenReturn(10.0f);
        when(target.isSpectator()).thenReturn(true);
        assertFalse(ClientEvents.isValidTarget(player, target));
    }

    @Test
    @DisplayName("Returns false when target is not pickable")
    void testTargetNotPickable() {
        when(target.isAlive()).thenReturn(true);
        when(target.isRemoved()).thenReturn(false);
        when(target.getHealth()).thenReturn(10.0f);
        when(target.isSpectator()).thenReturn(false);
        when(target.isPickable()).thenReturn(false);
        assertFalse(ClientEvents.isValidTarget(player, target));
    }

    @Test
    @DisplayName("Returns false when target entity type is excluded")
    void testTargetEntityTypeExcluded() {
        configureValidTarget(target);
        AutoAttackerConfig.excludedEntities.add(entityType);

        assertFalse(ClientEvents.isValidTarget(player, target));
    }

    @Test
    @DisplayName("Returns false when player is allied to target")
    void testPlayerAlliedToTarget() {
        configureValidTarget(target);
        when(player.isAlliedTo(target)).thenReturn(true);

        assertFalse(ClientEvents.isValidTarget(player, target));
    }

    @Test
    @DisplayName("Returns true for a standard valid target")
    void testValidTarget() {
        configureValidTarget(target);
        when(player.isAlliedTo(target)).thenReturn(false);

        assertTrue(ClientEvents.isValidTarget(player, target));
    }

    @Test
    @DisplayName("Returns true when player is null but target is valid")
    void testValidTargetNullPlayer() {
        configureValidTarget(target);

        assertTrue(ClientEvents.isValidTarget(null, target));
    }

    @Test
    @DisplayName("TamableAnimal tests: owned by player or allied to player vs untamed / other owner")
    void testTamableAnimalScenarios() {
        TamableAnimal tamable = mock(TamableAnimal.class);
        configureValidTarget(tamable);
        when(player.isAlliedTo(tamable)).thenReturn(false);

        // Case 1: Tamed and owned by player -> false
        when(tamable.isTame()).thenReturn(true);
        when(tamable.isOwnedBy(player)).thenReturn(true);
        assertFalse(ClientEvents.isValidTarget(player, tamable));

        // Case 2: Tamed, not owned by player, but allied to player -> false
        when(tamable.isOwnedBy(player)).thenReturn(false);
        when(player.isAlliedTo(tamable)).thenReturn(true);
        assertFalse(ClientEvents.isValidTarget(player, tamable));

        // Case 3: Tamed, not owned by player, not allied to player -> true
        when(player.isAlliedTo(tamable)).thenReturn(false);
        assertTrue(ClientEvents.isValidTarget(player, tamable));

        // Case 4: Untamed -> true
        when(tamable.isTame()).thenReturn(false);
        assertTrue(ClientEvents.isValidTarget(player, tamable));
    }

    @Test
    @DisplayName("OwnableEntity tests: owned by player vs owned by other player")
    void testOwnableEntityScenarios() {
        LivingEntity ownableLiving = mock(LivingEntity.class, withSettings().extraInterfaces(OwnableEntity.class));
        OwnableEntity ownable = (OwnableEntity) ownableLiving;
        configureValidTarget(ownableLiving);
        when(player.isAlliedTo(ownableLiving)).thenReturn(false);

        // Case 1: Owned by player -> false
        when(ownable.getOwner()).thenReturn(player);
        assertFalse(ClientEvents.isValidTarget(player, ownableLiving));

        // Case 2: Owned by someone else -> true
        Player otherPlayer = mock(Player.class);
        when(ownable.getOwner()).thenReturn(otherPlayer);
        assertTrue(ClientEvents.isValidTarget(player, ownableLiving));
    }
}
