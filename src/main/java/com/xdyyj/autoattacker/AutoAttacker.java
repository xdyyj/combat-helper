package com.xdyyj.autoattacker;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mod("autoattacker")
public class AutoAttacker {

    public static final String MODID = "autoattacker";
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public AutoAttacker(FMLJavaModLoadingContext context) {
        // 获取 Mod 事件总线
        IEventBus modEventBus = context.getModEventBus();
        
        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AutoAttackerConfig.SPEC, AutoAttacker.MODID + "-client.toml");
        
        // 注册配置类监听器 (仅 mod 总线; TagsUpdatedEvent 由 TagsUpdatedHandler 独立处理)
        modEventBus.register(AutoAttackerConfig.class);

        // 注册通用事件监听器（原CommonEvents已被移除）

        // 注册客户端专用事件监听器与全屏配置抽屉工厂
        DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            AutoBallisticsTracker.init();
            MinecraftForge.EVENT_BUS.register(new ClientEvents());
            ModLoadingContext.get().registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                    (mc, parent) -> new com.xdyyj.autoattacker.ui.TacticalConsoleScreen(parent)
                )
            );
        });

        LOGGER.info(">>> 自动攻击工具 (AutoAttacker) 加载成功！已进入就绪状态。");
    }
}