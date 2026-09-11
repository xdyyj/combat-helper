package com.xdyyj.autoattacker;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "autoattacker", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    // 默认快捷键：R键 (目标锁定)
    public static final KeyMapping LOCK_ON_KEY = new KeyMapping(
            "key.autoattacker.lock_on",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.autoattacker"
    );

    // 默认快捷键：U键 (战术调试面板控制切换)
    public static final KeyMapping DEBUG_PANEL_KEY = new KeyMapping(
            "key.autoattacker.debug_panel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_U,
            "key.categories.autoattacker"
    );

    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(LOCK_ON_KEY);
        event.register(DEBUG_PANEL_KEY);
    }
}
