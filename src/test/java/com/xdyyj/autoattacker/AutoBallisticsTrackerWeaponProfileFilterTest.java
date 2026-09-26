package com.xdyyj.autoattacker;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AutoBallisticsTrackerWeaponProfileFilterTest {

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
    @DisplayName("isInvalidBallisticSignature should identify melee weapons and non-weapon items as invalid")
    void testIsInvalidBallisticSignature_MeleeAndInvalidItems() {
        // 召星者 (Starcaller) 及变体指纹
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("botania:star_sword"));
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("botania:star_sword#59dc14d5"));
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("botania:star_sword#59c3278d"));

        // 其它近战武器
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("minecraft:netherite_sword"));
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("mythicbotany:alfsteel_sword|ench:9#57a244f4"));
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("extrabotany:excalibur#e1f8c019"));
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("minecraft:diamond_axe"));

        // 假人与实体刷怪蛋
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("dummmmmmy:target_dummy"));
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("minecraft:iron_golem_spawn_egg"));
        assertTrue(AutoBallisticsTracker.isInvalidBallisticSignature("modern_kinetic_gun#dummy"));
    }

    @Test
    @DisplayName("isInvalidBallisticSignature should keep legitimate ranged weapons valid")
    void testIsInvalidBallisticSignature_LegitimateRangedWeapons() {
        // 原厂基准与经典弓弩
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("minecraft:bow"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("minecraft:crossbow"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("minecraft:trident"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("botania:crystal_bow"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("botania:livingwood_bow"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("extrabotany:failnaught"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("alexsmobs:tendon_bow"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("archers_paradox:diamond_bow"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("twilightforest:triple_bow"));

        // 枪械
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("gun:tacz:hk416d"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("gun:pointblank:m4sopmodii"));
        assertFalse(AutoBallisticsTracker.isInvalidBallisticSignature("gun:scguns:niami"));
    }

    @Test
    @DisplayName("isRangedWeapon should reject pure melee items")
    void testIsRangedWeapon_RejectsMelee() {
        ItemStack swordStack = mock(ItemStack.class);
        SwordItem swordItem = mock(SwordItem.class);
        when(swordStack.isEmpty()).thenReturn(false);
        when(swordStack.getItem()).thenReturn(swordItem);
        assertFalse(AutoBallisticsTracker.isRangedWeapon(swordStack));

        ItemStack axeStack = mock(ItemStack.class);
        AxeItem axeItem = mock(AxeItem.class);
        when(axeStack.isEmpty()).thenReturn(false);
        when(axeStack.getItem()).thenReturn(axeItem);
        assertFalse(AutoBallisticsTracker.isRangedWeapon(axeStack));

        ItemStack eggStack = mock(ItemStack.class);
        SpawnEggItem eggItem = mock(SpawnEggItem.class);
        when(eggStack.isEmpty()).thenReturn(false);
        when(eggStack.getItem()).thenReturn(eggItem);
        assertFalse(AutoBallisticsTracker.isRangedWeapon(eggStack));
    }

    @Test
    @DisplayName("isRangedWeapon should accept bows and crossbows")
    void testIsRangedWeapon_AcceptsBows() {
        ItemStack bowStack = mock(ItemStack.class);
        BowItem bowItem = mock(BowItem.class);
        when(bowStack.isEmpty()).thenReturn(false);
        when(bowStack.getItem()).thenReturn(bowItem);
        assertTrue(AutoBallisticsTracker.isRangedWeapon(bowStack));

        ItemStack crossbowStack = mock(ItemStack.class);
        CrossbowItem crossbowItem = mock(CrossbowItem.class);
        when(crossbowStack.isEmpty()).thenReturn(false);
        when(crossbowStack.getItem()).thenReturn(crossbowItem);
        assertTrue(AutoBallisticsTracker.isRangedWeapon(crossbowStack));
    }
}
