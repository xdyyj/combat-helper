package com.xdyyj.autoattacker;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.tags.ItemTags;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientEventsTest {

    private ClientEvents clientEvents;

    @BeforeAll
    static void initMinecraft() throws Exception {
        SharedConstants.tryDetectVersion();
        Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
        field.setAccessible(true);
        field.set(null, true);
    }

    @BeforeEach
    void setUp() {
        clientEvents = new ClientEvents();
        clientEvents.clearSessionState();
        AutoAttackerConfig.blacklistItems.clear();
        AutoAttackerConfig.blacklistTags.clear();
        AutoAttackerConfig.whitelistItems.clear();
        AutoAttackerConfig.whitelistTags.clear();
    }

    @Test
    @DisplayName("isHoldingWeapon returns false when player is null")
    void testIsHoldingWeapon_NullPlayer() {
        assertFalse(ClientEvents.isHoldingWeapon(null));
    }

    @Test
    @DisplayName("isHoldingWeapon returns false when main hand item is empty")
    void testIsHoldingWeapon_EmptyStack() {
        Player player = mock(Player.class);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);

        assertFalse(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns true for SwordItem")
    void testIsHoldingWeapon_Sword() {
        Player player = mock(Player.class);
        ItemStack swordStack = mock(ItemStack.class);
        Item swordItem = mock(SwordItem.class);

        when(player.getMainHandItem()).thenReturn(swordStack);
        when(swordStack.isEmpty()).thenReturn(false);
        when(swordStack.getItem()).thenReturn(swordItem);
        when(swordStack.is(ItemTags.SWORDS)).thenReturn(true);

        assertTrue(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns true for AxeItem")
    void testIsHoldingWeapon_Axe() {
        Player player = mock(Player.class);
        ItemStack axeStack = mock(ItemStack.class);
        Item axeItem = mock(AxeItem.class);

        when(player.getMainHandItem()).thenReturn(axeStack);
        when(axeStack.isEmpty()).thenReturn(false);
        when(axeStack.getItem()).thenReturn(axeItem);
        when(axeStack.is(ItemTags.AXES)).thenReturn(true);

        assertTrue(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns true for TridentItem instance")
    void testIsHoldingWeapon_Trident() {
        Player player = mock(Player.class);
        ItemStack tridentStack = mock(ItemStack.class);
        TridentItem tridentItem = mock(TridentItem.class);

        when(player.getMainHandItem()).thenReturn(tridentStack);
        when(tridentStack.isEmpty()).thenReturn(false);
        when(tridentStack.getItem()).thenReturn(tridentItem);

        assertTrue(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns false for PickaxeItem with attack damage")
    void testIsHoldingWeapon_Pickaxe() {
        Player player = mock(Player.class);
        ItemStack pickaxeStack = mock(ItemStack.class);
        Item pickaxeItem = mock(PickaxeItem.class);

        Multimap<Attribute, AttributeModifier> modifiers = MultimapBuilder.hashKeys().arrayListValues().build();
        modifiers.put(Attributes.ATTACK_DAMAGE, mock(AttributeModifier.class));

        when(player.getMainHandItem()).thenReturn(pickaxeStack);
        when(pickaxeStack.isEmpty()).thenReturn(false);
        when(pickaxeStack.getItem()).thenReturn(pickaxeItem);
        when(pickaxeStack.is(ItemTags.PICKAXES)).thenReturn(true);
        when(pickaxeStack.getAttributeModifiers(EquipmentSlot.MAINHAND)).thenReturn(modifiers);

        assertFalse(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns false for ShovelItem with attack damage")
    void testIsHoldingWeapon_Shovel() {
        Player player = mock(Player.class);
        ItemStack shovelStack = mock(ItemStack.class);
        Item shovelItem = mock(ShovelItem.class);

        Multimap<Attribute, AttributeModifier> modifiers = MultimapBuilder.hashKeys().arrayListValues().build();
        modifiers.put(Attributes.ATTACK_DAMAGE, mock(AttributeModifier.class));

        when(player.getMainHandItem()).thenReturn(shovelStack);
        when(shovelStack.isEmpty()).thenReturn(false);
        when(shovelStack.getItem()).thenReturn(shovelItem);
        when(shovelStack.is(ItemTags.SHOVELS)).thenReturn(true);
        when(shovelStack.getAttributeModifiers(EquipmentSlot.MAINHAND)).thenReturn(modifiers);

        assertFalse(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns false for HoeItem with attack damage")
    void testIsHoldingWeapon_Hoe() {
        Player player = mock(Player.class);
        ItemStack hoeStack = mock(ItemStack.class);
        Item hoeItem = mock(HoeItem.class);

        Multimap<Attribute, AttributeModifier> modifiers = MultimapBuilder.hashKeys().arrayListValues().build();
        modifiers.put(Attributes.ATTACK_DAMAGE, mock(AttributeModifier.class));

        when(player.getMainHandItem()).thenReturn(hoeStack);
        when(hoeStack.isEmpty()).thenReturn(false);
        when(hoeStack.getItem()).thenReturn(hoeItem);
        when(hoeStack.is(ItemTags.HOES)).thenReturn(true);
        when(hoeStack.getAttributeModifiers(EquipmentSlot.MAINHAND)).thenReturn(modifiers);

        assertFalse(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns true for custom weapon item with attack damage modifier")
    void testIsHoldingWeapon_CustomWeaponWithAttackDamage() {
        Player player = mock(Player.class);
        ItemStack customStack = mock(ItemStack.class);
        Item customItem = mock(Item.class);

        Multimap<Attribute, AttributeModifier> modifiers = MultimapBuilder.hashKeys().arrayListValues().build();
        modifiers.put(Attributes.ATTACK_DAMAGE, mock(AttributeModifier.class));

        when(player.getMainHandItem()).thenReturn(customStack);
        when(customStack.isEmpty()).thenReturn(false);
        when(customStack.getItem()).thenReturn(customItem);
        when(customStack.getAttributeModifiers(EquipmentSlot.MAINHAND)).thenReturn(modifiers);

        assertTrue(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns false for generic item without attack damage")
    void testIsHoldingWeapon_GenericItemWithoutAttackDamage() {
        Player player = mock(Player.class);
        ItemStack genericStack = mock(ItemStack.class);
        Item genericItem = mock(Item.class);

        Multimap<Attribute, AttributeModifier> modifiers = MultimapBuilder.hashKeys().arrayListValues().build();

        when(player.getMainHandItem()).thenReturn(genericStack);
        when(genericStack.isEmpty()).thenReturn(false);
        when(genericStack.getItem()).thenReturn(genericItem);
        when(genericStack.getAttributeModifiers(EquipmentSlot.MAINHAND)).thenReturn(modifiers);

        assertFalse(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns false when item is blacklisted")
    void testIsHoldingWeapon_BlacklistedItem() {
        Player player = mock(Player.class);
        ItemStack swordStack = new ItemStack(Items.DIAMOND_SWORD);
        AutoAttackerConfig.blacklistItems.add(Items.DIAMOND_SWORD);

        when(player.getMainHandItem()).thenReturn(swordStack);

        assertFalse(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon returns true when item is whitelisted even if otherwise non-weapon")
    void testIsHoldingWeapon_WhitelistedItem() {
        Player player = mock(Player.class);
        ItemStack stickStack = new ItemStack(Items.STICK);
        AutoAttackerConfig.whitelistItems.add(Items.STICK);

        when(player.getMainHandItem()).thenReturn(stickStack);

        assertTrue(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon caches result for identical item stack")
    void testIsHoldingWeapon_Caching() {
        Player player = mock(Player.class);
        ItemStack swordStack = new ItemStack(Items.DIAMOND_SWORD);

        when(player.getMainHandItem()).thenReturn(swordStack);

        // First call evaluates weapon check and caches result
        assertTrue(ClientEvents.isHoldingWeapon(player));

        // Add to blacklist after first call - cache should still return true because item is cached
        AutoAttackerConfig.blacklistItems.add(Items.DIAMOND_SWORD);

        assertTrue(ClientEvents.isHoldingWeapon(player));
    }

    @Test
    @DisplayName("isHoldingWeapon recalculates after session state is cleared")
    void testIsHoldingWeapon_CacheClearedOnSessionReset() {
        Player player = mock(Player.class);
        ItemStack swordStack = new ItemStack(Items.DIAMOND_SWORD);

        when(player.getMainHandItem()).thenReturn(swordStack);

        // First call evaluates weapon check and caches true
        assertTrue(ClientEvents.isHoldingWeapon(player));

        // Add to blacklist
        AutoAttackerConfig.blacklistItems.add(Items.DIAMOND_SWORD);

        // Clear session state (which clears cached item)
        clientEvents.clearSessionState();

        // Second call re-evaluates weapon check with blacklist in place, returning false
        assertFalse(ClientEvents.isHoldingWeapon(player));
    }
}
