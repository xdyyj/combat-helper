package com.xdyyj.autoattacker.mixin;

import com.xdyyj.autoattacker.AutoAttackerConfig;
import com.xdyyj.autoattacker.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow public LocalPlayer player;
    @Shadow public net.minecraft.client.Options options;

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void autoattacker$onHandleKeybinds(CallbackInfo ci) {
        if (!AutoAttackerConfig.ENABLE_MOD.get()) return;

        Minecraft mc = (Minecraft) (Object) this;
        if (this.player == null) return;

        if (AutoAttackerConfig.ENABLE_AUTO_ATTACK.get() && ClientEvents.isHoldingWeapon(this.player)) {
            // Check if player is attempting to mine a block (looking at a block with no entity target in melee reach)
            boolean isLookingAtBlock = mc.hitResult != null && mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;
            LivingEntity locked = ClientEvents.getCurrentTarget();
            boolean hasLockedTargetInReach = false;
            if (locked != null && locked.isAlive() && !locked.isRemoved()) {
                double reach = 4.5D;
                if (this.player.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()) != null) {
                    reach = this.player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
                }
                if (this.player.distanceToSqr(locked) <= reach * reach) {
                    hasLockedTargetInReach = true;
                }
            }

            // If pointing at a block and no entity target is in melee reach, preserve vanilla block breaking
            if (isLookingAtBlock && !hasLockedTargetInReach) {
                return;
            }

            // 仅消费点击队列（防止原版重复攻击/挥砍）。
            // 注意：此处【不能】无条件转发 LeftClickEmpty —— 那样会在蓄力未满时也通知
            // 服务端，而服务端对依赖满蓄力的特效(如 Botania 剑气)会因其 attackStrength
            // 不足而丢弃，反而打乱节奏。空挥通知统一由 performAttack 在满蓄力时发出。
            while (this.options.keyAttack.consumeClick()) {
                // Do nothing
            }

            if (this.options.keyAttack.isDown()) {
                // 用 0.0F 与服务端校验基准一致：Botania 的 LeftClickPacket.handle 内部
                // 取的是 getAttackStrengthScale(0.0F)。若客户端用 0.5F 提前半 tick 判定为满，
                // 服务端可能仍未满(高攻速武器周期仅 2 tick 时尤其明显)，依赖满蓄力的特效
                // 会被丢弃，表现为「偶尔掉一发光束」。
                if (this.player.getAttackStrengthScale(0.0F) >= 1.0F) {
                    ClientEvents.performAttack(mc, this.player);
                }
            }
        }
    }

    /**
     * 方案 1: 原版高精 3D 发光轮廓 (Spectral Glow)
     * 当实体被 autoattacker 锁定为当前目标时，赋予原版 3D 发光轮廓，彻底摆脱 2D 贴纸几何框的违和感
     */
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void autoattacker$onShouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (AutoAttackerConfig.ENABLE_MOD.get()) {
            LivingEntity locked = ClientEvents.getCurrentTarget();
            if (entity != null && entity == locked) {
                cir.setReturnValue(true);
            }
        }
    }
}
