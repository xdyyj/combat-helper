package com.xdyyj.autoattacker.compat.shouldersurfing;

import com.github.exopandora.shouldersurfing.api.event.IEventBus;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingPlugin;
import com.github.exopandora.shouldersurfing.api.plugin.IShoulderSurfingRegistrar;
import com.xdyyj.autoattacker.ClientEvents;
import com.xdyyj.autoattacker.weapon.FirearmAdapter;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 越肩视角 (Shoulder Surfing Reloaded) 原生扩展插件
 * 
 * 作用：
 * 1. 强制相机耦合 (Camera Coupling)：在自瞄锁定目标或使用枪械瞄准期间，强制使玩家身体与相机方向耦合。
 *    彻底根除非 TAC 枪械在越肩视角下左右移动 (按 A/D 键) 时因身体转向 90 度导致的镜头剧烈晃动及子弹偏斜。
 * 2. 强制原生玩家输入 (Force Vanilla Player Input)：锁定期间保持身体正对目标，禁用 ShoulderSurfing 的侧向走位翻转。
 * 3. 自适应武器声明 (Adaptive Item)：将所有模组枪械、弓箭、弩声明为自适应瞄准武器，启用 ShoulderSurfing 动态准星物理射线追踪。
 */
public class AutoAttackerShoulderSurfingPlugin implements IShoulderSurfingPlugin {

    /**
     * ShoulderSurfing 5.x 现代事件总线注册入口
     */
    @Override
    public void register(IEventBus bus) {
        // 1. 相机耦合：当锁定目标或机瞄时，强制相机与身体朝向绑定
        bus.register((com.github.exopandora.shouldersurfing.api.client.event.ComputeCameraCouplingEvent event) -> {
            if (ClientEvents.isForcingCameraCoupling()) {
                event.setResult(true);
            }
        });

        // 2. 玩家输入控制：锁定期间保持身体正对目标，禁用 ShoulderSurfing 的侧向走位翻转
        bus.register((com.github.exopandora.shouldersurfing.api.client.event.ForceVanillaPlayerInputEvent event) -> {
            if (ClientEvents.isForcingCameraCoupling()) {
                event.setResult(true);
            }
        });

        // 3. 自适应瞄准武器声明：所有模组枪械与远程武器
        bus.register((com.github.exopandora.shouldersurfing.api.client.event.ComputePlayerAimStateEvent event) -> {
            LivingEntity entity = event.getEntity();
            if (entity != null) {
                ItemStack mainHand = entity.getMainHandItem();
                if (FirearmAdapter.isGun(mainHand) || ClientEvents.isRangedWeapon(mainHand)) {
                    event.setResult(true);
                }
            }
        });
    }

    /**
     * ShoulderSurfing 旧版兼容模式入口 (向后兼容)
     */
    public void register(IShoulderSurfingRegistrar registrar) {
        try {
            registrar.registerCameraCouplingCallback(mc -> ClientEvents.isForcingCameraCoupling());
            registrar.registerAdaptiveItemCallback((mc, entity) -> {
                if (entity != null) {
                    ItemStack mainHand = entity.getMainHandItem();
                    return FirearmAdapter.isGun(mainHand) || ClientEvents.isRangedWeapon(mainHand);
                }
                return false;
            });
        } catch (Throwable ignored) {}
    }
}
