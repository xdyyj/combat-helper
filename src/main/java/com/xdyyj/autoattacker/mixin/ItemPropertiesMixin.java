package com.xdyyj.autoattacker.mixin;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.item.ItemPropertyFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ItemProperties.class)
public abstract class ItemPropertiesMixin {

    @Shadow @Final @Mutable
    private static Map<Item, Map<ResourceLocation, ItemPropertyFunction>> PROPERTIES;

    @Shadow @Final @Mutable
    private static Map<ResourceLocation, ItemPropertyFunction> GENERIC_PROPERTIES;

    /**
     * 在 ItemProperties 类加载完成静态初始化后，将静态 HashMap 替换为高并发安全的 ConcurrentHashMap，
     * 彻底解决第三方 Mod (如 nyfsarcheryplus 等) 在 FMLClientSetupEvent 多线程并发直接调用
     * ItemProperties.PROPERTIES.computeIfAbsent 时触发的 ConcurrentModificationException 崩溃。
     */
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void autoattacker$makePropertiesThreadSafe(CallbackInfo ci) {
        Map<Item, Map<ResourceLocation, ItemPropertyFunction>> newMap = new ConcurrentHashMap<>();
        if (PROPERTIES != null) {
            for (Map.Entry<Item, Map<ResourceLocation, ItemPropertyFunction>> entry : PROPERTIES.entrySet()) {
                newMap.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
            }
        }
        PROPERTIES = newMap;

        if (GENERIC_PROPERTIES != null) {
            GENERIC_PROPERTIES = new ConcurrentHashMap<>(GENERIC_PROPERTIES);
        }
    }

    /**
     * 线程安全加固：拦截 ItemProperties.register 方法，确保并发注册时内部 Map 也使用 ConcurrentHashMap
     */
    @Inject(
        method = "register(Lnet/minecraft/world/item/Item;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/renderer/item/ClampedItemPropertyFunction;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void autoattacker$threadSafeRegister(Item item, ResourceLocation name, ClampedItemPropertyFunction property, CallbackInfo ci) {
        if (PROPERTIES != null) {
            PROPERTIES.computeIfAbsent(item, k -> new ConcurrentHashMap<>()).put(name, property);
        }
        ci.cancel();
    }
}
