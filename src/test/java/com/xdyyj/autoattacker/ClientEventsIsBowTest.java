package com.xdyyj.autoattacker;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientEventsIsBowTest {

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

    @Test
    @DisplayName("isBow returns false when stack is null")
    void testIsBow_NullStack() {
        assertFalse(ClientEvents.isBow(null));
    }

    @Test
    @DisplayName("isBow returns false when stack is empty")
    void testIsBow_EmptyStack() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(true);

        assertFalse(ClientEvents.isBow(stack));
    }

    @Test
    @DisplayName("isBow returns true when item is an instance of BowItem")
    void testIsBow_BowItem() {
        ItemStack stack = mock(ItemStack.class);
        BowItem bowItem = mock(BowItem.class);

        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(bowItem);

        assertTrue(ClientEvents.isBow(stack));
    }

    @Test
    @DisplayName("isBow returns true when item is an instance of CrossbowItem")
    void testIsBow_CrossbowItem() {
        ItemStack stack = mock(ItemStack.class);
        CrossbowItem crossbowItem = mock(CrossbowItem.class);

        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(crossbowItem);

        assertTrue(ClientEvents.isBow(stack));
    }

    @Test
    @DisplayName("isBow returns true when item use animation is UseAnim.BOW")
    void testIsBow_UseAnimBow() {
        ItemStack stack = mock(ItemStack.class);
        Item item = mock(Item.class);

        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(item);
        when(item.getUseAnimation(stack)).thenReturn(UseAnim.BOW);

        assertTrue(ClientEvents.isBow(stack));
    }

    @Test
    @DisplayName("isBow returns true when item use animation is UseAnim.CROSSBOW")
    void testIsBow_UseAnimCrossbow() {
        ItemStack stack = mock(ItemStack.class);
        Item item = mock(Item.class);

        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(item);
        when(item.getUseAnimation(stack)).thenReturn(UseAnim.CROSSBOW);

        assertTrue(ClientEvents.isBow(stack));
    }

    @Test
    @DisplayName("isBow returns true when stack matches forge:tools/bows tag")
    void testIsBow_ForgeBowsTag() {
        ItemStack stack = mock(ItemStack.class);
        Item item = mock(Item.class);

        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(item);
        when(item.getUseAnimation(stack)).thenReturn(UseAnim.NONE);
        when(stack.is(ClientEvents.FORGE_BOWS_TAG)).thenReturn(true);

        assertTrue(ClientEvents.isBow(stack));
    }

    @ParameterizedTest
    @EnumSource(value = UseAnim.class, names = {"NONE", "EAT", "DRINK", "BLOCK", "SPEAR", "SPYGLASS", "TOOT_HORN", "BRUSH"})
    @DisplayName("isBow returns false for non-bow UseAnims")
    void testIsBow_NonBowUseAnim(UseAnim anim) {
        ItemStack stack = mock(ItemStack.class);
        Item item = mock(Item.class);

        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(item);
        when(item.getUseAnimation(stack)).thenReturn(anim);
        when(stack.is(ClientEvents.FORGE_BOWS_TAG)).thenReturn(false);

        assertFalse(ClientEvents.isBow(stack));
    }
}
