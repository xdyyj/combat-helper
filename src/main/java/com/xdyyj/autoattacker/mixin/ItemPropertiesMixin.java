package com.xdyyj.autoattacker.mixin;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ItemProperties.class)
public abstract class ItemPropertiesMixin {

    @Shadow private static Map<Item, Map<ResourceLocation, ItemPropertyFunction>> PROPERTIES;

    /**
     * 线程安全加固：解决多弓弩 Mod 在 FMLClientSetupEvent 并发调用 ItemProperties.register 时
     * 导致的 HashMap ConcurrentModificationException 崩溃问题
     */
    @Inject(
        method = "register(Lnet/minecraft/world/item/Item;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/item/ClampedItemPropertyFunction;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void autoattacker$threadSafeRegister(Item item, ResourceLocation name, ClampedItemPropertyFunction property, CallbackInfo ci) {
        synchronized (PROPERTIES) {
            PROPERTIES.computeIfAbsent(item, k -> new ConcurrentHashMap<>()).put(name, property);
        }
        ci.cancel();
    }
}
