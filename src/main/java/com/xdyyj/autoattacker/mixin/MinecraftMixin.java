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

            // Drain the vanilla attack key clicks so vanilla doesn't duplicate attack/swing
            while (this.options.keyAttack.consumeClick()) {
                // Do nothing
            }

            if (this.options.keyAttack.isDown()) {
                // Use 0.5F adjustTicks to align with vanilla server/client attack strength scale calculations
                if (this.player.getAttackStrengthScale(0.5F) >= 1.0F) {
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
