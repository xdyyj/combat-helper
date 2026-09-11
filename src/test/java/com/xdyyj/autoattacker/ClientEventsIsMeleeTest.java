package com.xdyyj.autoattacker;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientEventsIsMeleeTest {

    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        try {
            Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
            field.setAccessible(true);
            field.set(null, true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    @DisplayName("isMelee should return false when ItemStack is null")
    void testIsMelee_NullStack() {
        assertFalse(ClientEvents.isMelee(null));
    }

    @Test
    @DisplayName("isMelee should return false when ItemStack is empty")
    void testIsMelee_EmptyStack() {
        ItemStack emptyStack = mock(ItemStack.class);
        when(emptyStack.isEmpty()).thenReturn(true);
        assertFalse(ClientEvents.isMelee(emptyStack));
    }

    @Test
    @DisplayName("isMelee should return true for SwordItem")
    void testIsMelee_SwordItem() {
        ItemStack stack = mock(ItemStack.class);
        SwordItem swordItem = mock(SwordItem.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(swordItem);

        assertTrue(ClientEvents.isMelee(stack));
    }

    @Test
    @DisplayName("isMelee should return true for AxeItem")
    void testIsMelee_AxeItem() {
        ItemStack stack = mock(ItemStack.class);
        AxeItem axeItem = mock(AxeItem.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(axeItem);

        assertTrue(ClientEvents.isMelee(stack));
    }

    @Test
    @DisplayName("isMelee should return true for TridentItem")
    void testIsMelee_TridentItem() {
        ItemStack stack = mock(ItemStack.class);
        TridentItem tridentItem = mock(TridentItem.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(tridentItem);

        assertTrue(ClientEvents.isMelee(stack));
    }

    @Test
    @DisplayName("isMelee should return false for non-melee item without sword/axe tags")
    void testIsMelee_NonMeleeItem_NoTags() {
        ItemStack stack = mock(ItemStack.class);
        BowItem bowItem = mock(BowItem.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(bowItem);
        when(stack.is(ItemTags.SWORDS)).thenReturn(false);
        when(stack.is(ItemTags.AXES)).thenReturn(false);

        assertFalse(ClientEvents.isMelee(stack));
    }

    @Test
    @DisplayName("isMelee should return true for custom item tagged with ItemTags.SWORDS")
    void testIsMelee_SwordsTag() {
        ItemStack stack = mock(ItemStack.class);
        Item genericItem = mock(Item.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(genericItem);
        when(stack.is(ItemTags.SWORDS)).thenReturn(true);

        assertTrue(ClientEvents.isMelee(stack));
    }

    @Test
    @DisplayName("isMelee should return true for custom item tagged with ItemTags.AXES")
    void testIsMelee_AxesTag() {
        ItemStack stack = mock(ItemStack.class);
        Item genericItem = mock(Item.class);
        when(stack.isEmpty()).thenReturn(false);
        when(stack.getItem()).thenReturn(genericItem);
        when(stack.is(ItemTags.SWORDS)).thenReturn(false);
        when(stack.is(ItemTags.AXES)).thenReturn(true);

        assertTrue(ClientEvents.isMelee(stack));
    }
}
