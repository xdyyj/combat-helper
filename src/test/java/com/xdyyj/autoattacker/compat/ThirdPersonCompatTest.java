package com.xdyyj.autoattacker.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThirdPersonCompatTest {

    @Test
    @DisplayName("ThirdPersonCompat should gracefully handle uninstalled state without throwing")
    void testUninstalledState() {
        assertDoesNotThrow(ThirdPersonCompat::init);
        assertDoesNotThrow(ThirdPersonCompat::neutralizeLegacyShoulderSurfingIntegrations);

        // When neither mod is loaded in test environment
        assertFalse(ThirdPersonCompat.isThirdPerson());
        assertFalse(ThirdPersonCompat.isFreeLooking());
        assertFalse(ThirdPersonCompat.isShoulderSurfingActive());
        assertFalse(ThirdPersonCompat.isLeawindActive());

        // Camera calls should return safe fallbacks without throwing
        assertDoesNotThrow(() -> ThirdPersonCompat.setCameraRotation(45.0f, 15.0f));
        assertDoesNotThrow(() -> ThirdPersonCompat.syncPlayerRotation(45.0f, 15.0f));
        assertDoesNotThrow(ThirdPersonCompat::suppressLeawindInteractionRotation);
        assertDoesNotThrow(ThirdPersonCompat::restoreLeawindInteractionRotation);
        assertDoesNotThrow(() -> ThirdPersonCompat.setAiming(true));
        assertDoesNotThrow(ThirdPersonCompat::resetAiming);
        assertDoesNotThrow(ThirdPersonCompat::getCameraPosition);
        assertDoesNotThrow(ThirdPersonCompat::getCameraLookVector);
    }

    @Test
    @DisplayName("ShoulderSurfingCompat facade should correctly forward to ThirdPersonCompat")
    void testShoulderSurfingCompatFacade() {
        assertDoesNotThrow(ShoulderSurfingCompat::neutralizeLegacyShoulderSurfingIntegrations);
        assertEquals(ThirdPersonCompat.isInstalled(), ShoulderSurfingCompat.isInstalled());
        assertEquals(ThirdPersonCompat.isThirdPerson(), ShoulderSurfingCompat.isShoulderSurfing());
        assertEquals(ThirdPersonCompat.isFreeLooking(), ShoulderSurfingCompat.isFreeLooking());
        assertEquals(ThirdPersonCompat.getCameraYaw(), ShoulderSurfingCompat.getCameraYaw());
        assertEquals(ThirdPersonCompat.getCameraPitch(), ShoulderSurfingCompat.getCameraPitch());

        assertDoesNotThrow(() -> ShoulderSurfingCompat.setCameraRotation(90.0f, 0.0f));
        assertDoesNotThrow(() -> ShoulderSurfingCompat.syncPlayerRotation(90.0f, 0.0f));
        assertDoesNotThrow(ShoulderSurfingCompat::suppressLeawindInteractionRotation);
        assertDoesNotThrow(ShoulderSurfingCompat::restoreLeawindInteractionRotation);
        assertNotNull(ShoulderSurfingCompat.getCameraPosition());
        assertNotNull(ShoulderSurfingCompat.getCameraLookVector());
    }
}
