package com.xdyyj.autoattacker;

import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TagsUpdatedEvent 监听器。
 * 必须与 AutoAttackerConfig 分开注册: AutoAttackerConfig 挂在 mod 总线(处理 ModConfigEvent),
 * 而 TagsUpdatedEvent 属于 Forge 游戏总线, 混在一个类里注册会触发
 * "argument is not a subtype of IModBusEvent" 崩溃。
 */
@Mod.EventBusSubscriber(modid = AutoAttacker.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TagsUpdatedHandler {

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        // 数据包/标签重载后刷新黑/白名单缓存, 避免 Tags 过早解析失效
        AutoAttackerConfig.refreshLists();
    }
}
