package com.xdyyj.autoattacker.ui;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class TacticalDebugPanelSetStatusTest {

    private Field statusMessageField;
    private Field statusMessageExpiryField;
    private Field lastConfigSaveMsField;
    private Field configSavePendingField;

    @BeforeAll
    static void setupBootstrap() {
        SharedConstants.tryDetectVersion();
        try {
            Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            field.setBoolean(null, true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set bootstrapped flag", e);
        }
    }

    @BeforeEach
    void setUpReflectionFields() throws Exception {
        statusMessageField = TacticalDebugPanel.class.getDeclaredField("statusMessage");
        statusMessageField.setAccessible(true);

        statusMessageExpiryField = TacticalDebugPanel.class.getDeclaredField("statusMessageExpiry");
        statusMessageExpiryField.setAccessible(true);

        lastConfigSaveMsField = TacticalDebugPanel.class.getDeclaredField("lastConfigSaveMs");
        lastConfigSaveMsField.setAccessible(true);

        configSavePendingField = TacticalDebugPanel.class.getDeclaredField("configSavePending");
        configSavePendingField.setAccessible(true);

        // Reset static fields before each test
        statusMessageField.set(null, null);
        statusMessageExpiryField.setLong(null, 0L);
        lastConfigSaveMsField.setLong(null, 0L);
        configSavePendingField.setBoolean(null, false);
    }

    private String getStatusMessage() throws Exception {
        return (String) statusMessageField.get(null);
    }

    private long getStatusMessageExpiry() throws Exception {
        return statusMessageExpiryField.getLong(null);
    }

    private long getLastConfigSaveMs() throws Exception {
        return lastConfigSaveMsField.getLong(null);
    }

    private boolean isConfigSavePending() throws Exception {
        return configSavePendingField.getBoolean(null);
    }

    @Test
    @DisplayName("setStatus updates statusMessage and statusMessageExpiry (~2500ms in future)")
    void testSetStatus_BasicMessage() throws Exception {
        long beforeCall = System.currentTimeMillis();
        TacticalDebugPanel.setStatus("Test Status Message");
        long afterCall = System.currentTimeMillis();

        assertEquals("Test Status Message", getStatusMessage());

        long expiry = getStatusMessageExpiry();
        assertTrue(expiry >= beforeCall + 2500L, "Expiry should be at least 2500ms from start time");
        assertTrue(expiry <= afterCall + 2500L, "Expiry should be at most 2500ms from end time");
    }

    @Test
    @DisplayName("setStatus overwrites an existing status message and updates expiry")
    void testSetStatus_OverwriteExisting() throws Exception {
        TacticalDebugPanel.setStatus("First Message");
        assertEquals("First Message", getStatusMessage());
        long firstExpiry = getStatusMessageExpiry();

        Thread.sleep(10); // Ensure timestamp advances slightly

        TacticalDebugPanel.setStatus("Second Message");
        assertEquals("Second Message", getStatusMessage());
        long secondExpiry = getStatusMessageExpiry();

        assertTrue(secondExpiry >= firstExpiry, "Second expiry should be greater than or equal to first expiry");
    }

    @Test
    @DisplayName("setStatus handles null and empty string status messages")
    void testSetStatus_NullAndEmpty() throws Exception {
        TacticalDebugPanel.setStatus(null);
        assertNull(getStatusMessage());

        TacticalDebugPanel.setStatus("");
        assertEquals("", getStatusMessage());
    }

    @Test
    @DisplayName("setStatus triggers config save throttling logic via requestConfigSave")
    void testSetStatus_ConfigSaveThrottling() throws Exception {
        long beforeCall = System.currentTimeMillis();
        TacticalDebugPanel.setStatus("First Trigger");

        long lastSaveMs = getLastConfigSaveMs();
        assertTrue(lastSaveMs >= beforeCall, "lastConfigSaveMs should be updated on initial call");
        assertFalse(isConfigSavePending(), "configSavePending should be false on initial call (immediate save)");

        // Call setStatus immediately after (within 800ms window)
        TacticalDebugPanel.setStatus("Rapid Second Trigger");

        assertEquals(lastSaveMs, getLastConfigSaveMs(), "lastConfigSaveMs should not change within 800ms window");
        assertTrue(isConfigSavePending(), "configSavePending should be set to true when throttled");
    }
}
