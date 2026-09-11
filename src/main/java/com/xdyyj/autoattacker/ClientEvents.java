package com.xdyyj.autoattacker;

// --- Imports ---
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.ClipContext;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import com.xdyyj.autoattacker.ui.TacticalDebugPanel;
import com.xdyyj.autoattacker.render.TrajectoryRenderer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
// ------------------------------------

public class ClientEvents {

    private boolean didSuppressVanillaAttack = false;
    
    // --- 物品识别缓存变量 ---
    private static ItemStack lastCheckedItem = ItemStack.EMPTY;
    private static boolean isLastItemWeapon = false;

    // --- 目标锁定机制核心 ---
    private LivingEntity currentTarget = null;
    private LivingEntity autoLockHoverTarget = null;
    private int autoLockHoverTicks = 0;
    private boolean wasLockKeyDown = false;
    private int lostTargetGraceTicks = 0;
    private int gunSemiFireTimer = 999;
    private int gunReleaseChargeTicks = 0;
    private int gunReleaseCoolTicks = 0;
    private long lastGunShootTime = 0L;
    private static final int MAX_LOST_GRACE_TICKS = 20; // 视线丢失 20 tick (1秒) 宽容期

    // --- 枪械自动换弹节流控制 (Throttled Auto-Reload) ---
    private long lastAutoReloadCheckTime = 0L;
    private long lastAutoReloadTriggerTime = 0L;
    private boolean cachedHasInventoryAmmo = true;
    private ItemStack lastAutoReloadItem = ItemStack.EMPTY;

    // --- 鼠标死区与目标切换机制 (Mouse Deadzone & Flick Switch) ---
    private float mouseDeflectionYaw = 0f;
    private float mouseDeflectionPitch = 0f;
    private long lastSwitchTime = 0L;
    private float lastLockedYaw = 0f;
    private float lastLockedPitch = 0f;
    private boolean wasLockedLastFrame = false;

    // --- 动态相对运动前馈补偿状态 (Dynamic Rotational Kinematic Feedforward) ---
    private LivingEntity lastTrackedTarget = null;
    private float lastDestYaw = Float.NaN;
    private float lastDestPitch = Float.NaN;

    // --- 目标运动追踪与平滑状态 ---
    private LivingEntity lastTarget = null;
    private Vec3 smoothedTargetVelocity = Vec3.ZERO;
    private Vec3 lastMeasuredVelocity = Vec3.ZERO;

    // 速度采样
    private static final int VEL_SAMPLE_CAP = 3;
    private final Vec3[] velSamples = new Vec3[VEL_SAMPLE_CAP];
    private int velSampleIndex = 0;
    private int velSampleCount = 0;

    // --- 供战术调试面板与 3D 轨迹渲染器读取的静态遥测数据 ---
    private static LivingEntity staticCurrentTarget = null;
    private static Vec3 staticSmoothedTargetVelocity = Vec3.ZERO;
    private static PredictedAim staticLastPredictedAim = null;
    private static float staticLastPredictedYaw = 0.0f;

    public static LivingEntity getCurrentTarget() {
        if (staticCurrentTarget != null && staticCurrentTarget.isAlive() && !staticCurrentTarget.isRemoved()) {
            return staticCurrentTarget;
        }
        return null;
    }

    public static double getSmoothedTargetSpeed() {
        return staticSmoothedTargetVelocity != null ? staticSmoothedTargetVelocity.length() : 0.0;
    }

    public static PredictedAim getLastPredictedAim() {
        return staticLastPredictedAim;
    }

    public static float getLastPredictedYaw() {
        return staticLastPredictedYaw;
    }

    public static int getDebugToggleKeyCode() {
        return ClientModEvents.DEBUG_PANEL_KEY.getKey().getValue();
    }

    public static boolean isValidTarget(Player player, LivingEntity target) {
        if (target == null || target == player || !target.isAlive() || target.isRemoved() || target.getHealth() <= 0.0f) {
            return false;
        }
        if (target.isSpectator() || !target.isPickable()) {
            return false;
        }
        if (AutoAttackerConfig.excludedEntities.contains(target.getType())) {
            return false;
        }
        if (player != null) {
            if (player.isAlliedTo(target)) {
                return false;
            }
            if (target instanceof TamableAnimal tamable) {
                if (tamable.isTame() && (tamable.isOwnedBy(player) || player.isAlliedTo(tamable))) {
                    return false;
                }
            } else if (target instanceof OwnableEntity ownable) {
                if (ownable.getOwner() == player) {
                    return false;
                }
            }
        }
        return true;
    }

    private void resetTargetTracking() {
        lastTarget = null;
        smoothedTargetVelocity = Vec3.ZERO;
        lastMeasuredVelocity = Vec3.ZERO;
        velSampleIndex = 0;
        velSampleCount = 0;
        mouseDeflectionYaw = 0f;
        mouseDeflectionPitch = 0f;
        for (int i = 0; i < VEL_SAMPLE_CAP; i++) {
            velSamples[i] = Vec3.ZERO;
        }
    }

    public void clearSessionState() {
        this.currentTarget = null;
        this.autoLockHoverTarget = null;
        this.autoLockHoverTicks = 0;
        this.lastTrackedTarget = null;
        this.lastAutoReloadItem = ItemStack.EMPTY;
        lastCheckedItem = ItemStack.EMPTY;
        staticCurrentTarget = null;
        staticSmoothedTargetVelocity = Vec3.ZERO;
        staticLastPredictedAim = null;
        staticLastPredictedYaw = 0.0f;
        resetTargetTracking();
        AutoBallisticsTracker.clearTransientReferences();
    }

    @SubscribeEvent
    public void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearSessionState();
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clearSessionState();
        }
    }

    @SubscribeEvent
    public void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        AutoBallisticsTracker.onEntityJoinLevel(event);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen == null && ClientModEvents.DEBUG_PANEL_KEY.consumeClick()) {
            TacticalDebugPanel.toggleControl();
        }
    }

    @SubscribeEvent
    public void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getOverlay() == VanillaGuiOverlay.HOTBAR.type() && mc.screen == null) {
            TrajectoryRenderer.renderHudReticle(event.getGuiGraphics(), event.getPartialTick());
            TacticalDebugPanel.render(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public void onRenderLevelStage(RenderLevelStageEvent event) {
        // 对标 camera-lock-on: 严格在 AFTER_ENTITIES 阶段绘制实体战术框与抛物线
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            TrajectoryRenderer.onRenderLevelStage(event);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        var gameMode = mc.gameMode;

        if (player == null || mc.level == null || gameMode == null || mc.screen != null || !AutoAttackerConfig.ENABLE_MOD.get()) {
            this.didSuppressVanillaAttack = false;
            if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isTriggerShooting()) {
                com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
            }
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            while (ClientModEvents.DEBUG_PANEL_KEY.consumeClick()) {
                TacticalDebugPanel.toggleControl();
            }

            AutoBallisticsTracker.clientTick();

            // --- 1. 自动射箭：满蓄力平滑放箭 (消除暴力瞬移视角抽搐) ---
            if (AutoAttackerConfig.ENABLE_AUTO_SHOOT.get() && mc.options.keyUse.isDown() && player.isUsingItem()) {
                ItemStack itemInUse = player.getUseItem();
                if (!isInSet(itemInUse, AutoAttackerConfig.blacklistItems, AutoAttackerConfig.blacklistTags)) {
                    if (isBow(itemInUse)) {
                        HitResult hitResult = mc.hitResult;
                        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
                            Entity target = ((EntityHitResult) hitResult).getEntity();
                            if (AutoAttackerConfig.excludedEntities.contains(target.getType())) {
                                return;
                            }
                        }

                        boolean isFullyCharged = AutoBallisticsTracker.isBowFullyCharged(player, itemInUse);

                        if (isFullyCharged) {
                            InteractionHand hand = player.getUsedItemHand();

                            if (currentTarget != null && currentTarget.isAlive()) {
                                AutoBallisticsTracker.BallisticsProfile profile = AutoBallisticsTracker.getProfile(itemInUse);
                                Vec3 eye = player.getEyePosition();
                                double targetDistXZ = Math.hypot(currentTarget.getX() - eye.x, currentTarget.getZ() - eye.z);
                                double targetY = computeTargetY(currentTarget, 1.0f, AutoAttackerConfig.TARGET_PART.get(), false, true, targetDistXZ);
                                Vec3 baseAimPoint = new Vec3(currentTarget.getX(), targetY, currentTarget.getZ());

                                float sendYaw = player.getYRot();
                                float sendPitch = player.getXRot();

                                if (profile.isHoming) {
                                    double dX = currentTarget.getX() - eye.x;
                                    double dY = targetY - eye.y;
                                    double dZ = currentTarget.getZ() - eye.z;
                                    double horizDist = Math.sqrt(dX * dX + dZ * dZ);
                                    sendYaw = (float) (Mth.atan2(dZ, dX) * (180D / Math.PI)) - 90.0F;
                                    sendPitch = (float) -(Mth.atan2(dY, horizDist) * (180D / Math.PI));
                                } else {
                                    PredictedAim predicted = computePredictedAim(player, currentTarget, profile.speed, profile.gravity, eye, baseAimPoint);
                                    if (predicted != null) {
                                        sendYaw = predicted.targetYaw;
                                        sendPitch = predicted.targetPitch;
                                    }
                                }

                                if (mc.getConnection() != null) {
                                    mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(sendYaw, sendPitch, player.onGround()));
                                }
                            }

                            gameMode.releaseUsingItem(player);

                            boolean isUseKeyPhysicallyDown = com.mojang.blaze3d.platform.InputConstants.isKeyDown(
                                    mc.getWindow().getWindow(), mc.options.keyUse.getKey().getValue());
                            if (isUseKeyPhysicallyDown) {
                                gameMode.useItem(player, hand);
                            }
                        }
                    }
                }
            }

            // --- 1.1 现代枪械自瞄击发 (Firearm Triggerbot) & 智能自动换弹 ---
            ItemStack mainHand = player.getMainHandItem();
            boolean isHoldingGun = com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(mainHand);
            if (isHoldingGun) {
                com.xdyyj.autoattacker.weapon.FirearmAdapter.GunStatus gunStatus = 
                    com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunStatus(mainHand);

                // 智能自动换弹：当枪膛与弹匣彻底打空 (totalAmmo == 0) 时触发
                if (AutoAttackerConfig.ENABLE_GUN_AUTO_RELOAD.get() && gunStatus.totalAmmo == 0 &&
                    !com.xdyyj.autoattacker.weapon.FirearmAdapter.isReloading(player) &&
                    !com.xdyyj.autoattacker.weapon.FirearmAdapter.isBolting(player)) {

                    // 关键修复：一旦打空子弹，立即松开扳机与攻击键！
                    // 枪械模组 (TACZ / Point Blank / JEG 等) 在处于射击按键按下状态时会拦截或拒绝换弹请求
                    if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isTriggerShooting()) {
                        com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
                        gunSemiFireTimer = 0;
                    }

                    long now = System.currentTimeMillis();
                    if (mainHand != lastAutoReloadItem) {
                        lastAutoReloadItem = mainHand;
                        lastAutoReloadTriggerTime = 0L;
                    }

                    // 关键核心防失败机制：打出最后一发子弹后，枪械射击后坐与射击间隔冷却尚未结束（TACZ 服务端 shootCoolDown > 0）
                    // 若在此冷却窗口内发送换弹，服务端会拒收丢弃，导致客户端空播换弹动画但子弹不增加！
                    // 因此必须确保距离最后一发射击至少已过 200ms（冷却彻底清零），方可稳健发起换弹
                    if (now - lastGunShootTime >= 200L) {
                        // 检测背包是否真正有备用子弹 (含 TACZ 弹药箱 / 创造模式)
                        boolean hasAmmo = com.xdyyj.autoattacker.weapon.FirearmAdapter.hasInventoryAmmo(player, mainHand);
                        if (hasAmmo) {
                            // 换弹防重保护：重试防抖间隔 1200ms，杜绝高频重复触发
                            if (now - lastAutoReloadTriggerTime > 1200L) {
                                lastAutoReloadTriggerTime = now;
                                com.xdyyj.autoattacker.weapon.FirearmAdapter.triggerReload(player);
                            }
                        }
                    }
                    // 若背包无备弹，绝不触发换弹操作，彻底消除无弹药时的无限换弹死循环
                } else if (gunStatus.totalAmmo > 0) {
                    // 枪内有子弹时重置触发标记，确保打空当瞬能第一时间就绪换弹
                    lastAutoReloadTriggerTime = 0L;
                }

                if (AutoAttackerConfig.ENABLE_GUN_TRIGGERBOT.get() && currentTarget != null && currentTarget.isAlive() && hasLineOfSightMultiPoint(player, currentTarget)) {
                    // 特殊机制：逐发装填左轮/霰弹枪 (如 Scorched Guns 逐发填装) 遭遇目标打断换弹
                    // 当枪内已有至少 1 发子弹，且正在逐发装填、眼前有锁定的有效敌人时，立即紧急打断装填就绪击发！
                    if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isManualReloading(player, mainHand) && gunStatus.totalAmmo >= 1) {
                        com.xdyyj.autoattacker.weapon.FirearmAdapter.interruptReload(player);
                    }

                    // 修正：使用 totalAmmo (含枪膛上膛子弹)，彻底解决打空弹匣后总残留 1 发上膛子弹不击发的问题
                    boolean canShoot = (gunStatus.totalAmmo > 0 || gunStatus.totalAmmo == -1) && 
                                       !player.getCooldowns().isOnCooldown(mainHand.getItem()) &&
                                       !com.xdyyj.autoattacker.weapon.FirearmAdapter.isReloading(player) && 
                                       !com.xdyyj.autoattacker.weapon.FirearmAdapter.isBolting(player);
                    
                    if (canShoot) {
                        float currentYaw = player.getYRot();
                        float currentPitch = player.getXRot();
                        float targetAimYaw = (staticLastPredictedAim != null) ? staticLastPredictedAim.targetYaw : staticLastPredictedYaw;
                        float targetAimPitch = (staticLastPredictedAim != null) ? staticLastPredictedAim.targetPitch : currentPitch;
                        float yawDiff = Math.abs(Mth.wrapDegrees(targetAimYaw - currentYaw));
                        float pitchDiff = Math.abs(targetAimPitch - currentPitch);
                        
                        // 远距离 (如 68m) 射击时准星容差自适应微缩，提升远距点杀与爆头精度
                        double targetDist = player.distanceTo(currentTarget);
                        float maxDiff = (targetDist > 35.0) ? 9.0f : 14.0f;

                        if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isReleaseFire(gunStatus)) {
                            // 蓄力释放型武器 (如 JEG 复合弓/原始弓 RELEASE_FIRE):
                            // 机制：严格拉满弓 (20 ticks / 1秒)，满蓄力后当准星就位瞬间松开左键释放箭矢！
                            gunSemiFireTimer = 0;
                            if (gunReleaseCoolTicks > 0) {
                                gunReleaseCoolTicks--;
                                com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
                            } else {
                                int currentHold = com.xdyyj.autoattacker.weapon.FirearmAdapter.getJegHoldFire();
                                int maxHold = com.xdyyj.autoattacker.weapon.FirearmAdapter.getMaxHoldFire(mainHand);
                                float progress = com.xdyyj.autoattacker.weapon.FirearmAdapter.getChargeProgress(player, mainHand);
                                int requiredHold = (maxHold > 0) ? maxHold : 20;

                                // 严格判断是否真正完全拉满弓：
                                // 1. JEG 客户端 ShootingHandler.get().getHoldFire() 达到上限 (通常为 20)
                                // 2. 或 ChargeTracker 满蓄力 (1.0f)
                                // 3. 或连续按住拉弦达到 requiredHold + 4 刻保底 (杜绝未满拉弓提前释放)
                                boolean isFullyDrawn = (currentHold >= requiredHold && currentHold > 0) || 
                                                       progress >= 0.99f || 
                                                       (gunReleaseChargeTicks >= requiredHold + 4);

                                if (!isFullyDrawn) {
                                    // 阶段 1：蓄力拉弦阶段。坚决按住左键蓄力，绝不可因准星微小偏移松手！
                                    gunReleaseChargeTicks++;
                                    com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(true, player);
                                } else {
                                    // 阶段 2：弓弦已 100% 彻底拉满！进入瞄准就绪释放判定
                                    if (yawDiff <= maxDiff && pitchDiff <= maxDiff) {
                                        // 准星锁定在目标容差内，瞬间松开左键，满威力击发出箭！
                                        lastGunShootTime = System.currentTimeMillis();
                                        com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
                                        gunReleaseChargeTicks = 0;
                                        gunReleaseCoolTicks = 3; // 留 3 ticks 缓冲确保射击与动画结算
                                    } else {
                                        // 准星尚未对齐，继续稳稳拉满弓不放，等待自瞄对齐瞬间！
                                        com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(true, player);
                                    }
                                }
                            }
                        } else {
                            // 普通全自动 / 半自动枪械
                            gunReleaseChargeTicks = 0;
                            gunReleaseCoolTicks = 0;
                            if (yawDiff <= maxDiff && pitchDiff <= maxDiff) {
                                lastGunShootTime = System.currentTimeMillis();
                                if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isSemiAuto(gunStatus)) {
                                    // 半自动武器 (如 Glock, 沙漠之鹰, SPR-15 DMR, 单发步枪/狙击枪):
                                    // 必须交替扣动与松开扳机 (Tap-Fire) 触发 TACZ 击发并完成扳机重置 (Reset)
                                    int cycleTicks = Math.max(2, Math.round(1200.0f / Math.max(gunStatus.rpm, 120)));
                                    gunSemiFireTimer++;
                                    if (gunSemiFireTimer >= cycleTicks) {
                                        gunSemiFireTimer = 0;
                                    }
                                    if (gunSemiFireTimer == 0) {
                                        com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(true, player);
                                    } else {
                                        com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
                                    }
                                } else {
                                    // 全自动武器 (如 HK416, AUG, M4A1): 持续压住扳机扫射
                                    gunSemiFireTimer = 0;
                                    com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(true, player);
                                }
                            } else {
                                gunSemiFireTimer = 999;
                                com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
                            }
                        }
                    } else {
                        gunSemiFireTimer = 999;
                        gunReleaseChargeTicks = 0;
                        gunReleaseCoolTicks = 0;
                        com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
                    }
                } else {
                    gunSemiFireTimer = 999;
                    gunReleaseChargeTicks = 0;
                    gunReleaseCoolTicks = 0;
                    if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isTriggerShooting()) {
                        com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
                    }
                }
            } else {
                gunSemiFireTimer = 999;
                gunReleaseChargeTicks = 0;
                gunReleaseCoolTicks = 0;
                if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isTriggerShooting()) {
                    com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
                }
            }

            // --- 2. 目标锁定触发逻辑 (3大自动搜索模式与智能自动切靶) ---
            boolean isKeyDown = ClientModEvents.LOCK_ON_KEY.isDown();
            boolean isToggleMode = AutoAttackerConfig.AIM_ASSIST_MODE.get() == AutoAttackerConfig.LockMode.TOGGLE;
            boolean isHoldingBow = isRangedWeapon(player.getMainHandItem()) || isRangedWeapon(player.getOffhandItem());
            boolean isHoldingWeapon = isHoldingBow || 
                    com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(player.getMainHandItem()) ||
                    player.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem ||
                    player.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem ||
                    player.getMainHandItem().getItem() instanceof net.minecraft.world.item.TridentItem;

            double searchRange = (isHoldingBow && AutoAttackerConfig.ENABLE_AIM_PREDICT.get())
                    ? Math.max(AutoAttackerConfig.AIM_ASSIST_RANGE.get(), AutoAttackerConfig.AIM_PREDICT_MAX_DIST.get())
                    : AutoAttackerConfig.AIM_ASSIST_RANGE.get();

            if (AutoAttackerConfig.ENABLE_AIM_ASSIST.get()) {
                AutoAttackerConfig.AutoLockMode lockMode = AutoAttackerConfig.AUTO_LOCK_MODE.get();

                // 2.1 手动快捷键交互 (按键锁定 / 手动解除锁定)
                if (isKeyDown && !wasLockKeyDown) {
                    if (currentTarget != null) {
                        currentTarget = null;
                        autoLockHoverTarget = null;
                        autoLockHoverTicks = 0;
                        lastSwitchTime = System.currentTimeMillis() + 800L; // 手动脱锁赋予 800ms 静默期，避免瞬间重吸
                    } else {
                        currentTarget = getPrioritizedTarget(player, searchRange, 60.0f, AutoAttackerConfig.AUTO_SWITCH_PRIORITY.get(), null);
                    }
                } else if (!isToggleMode && !isKeyDown && lockMode == AutoAttackerConfig.AutoLockMode.OFF) {
                    // 长按模式下松开按键脱锁 (仅当自动索敌关闭时)
                    currentTarget = null;
                }
                wasLockKeyDown = isKeyDown;

                // 2.2 自动索敌三种模式执行
                if (currentTarget == null && System.currentTimeMillis() > lastSwitchTime) {
                    switch (lockMode) {
                        case ALWAYS -> {
                            // 模式 2: 始终自动锁定 (手持武器时生效，视角角度可调)
                            if (isHoldingWeapon) {
                                currentTarget = getPrioritizedTarget(player, searchRange, AutoAttackerConfig.AUTO_LOCK_FOV.get().floatValue(), AutoAttackerConfig.AUTO_SWITCH_PRIORITY.get(), null);
                            }
                            autoLockHoverTarget = null;
                            autoLockHoverTicks = 0;
                        }
                        case HOVER -> {
                            // 模式 1: 准星指向生物 N 秒时自动锁定
                            LivingEntity pointing = getCrosshairPointingTarget(player, searchRange);
                            if (pointing != null) {
                                if (pointing == autoLockHoverTarget) {
                                    autoLockHoverTicks++;
                                    int reqTicks = (int) Math.round(AutoAttackerConfig.AUTO_LOCK_HOVER_TIME.get() * 20.0);
                                    if (reqTicks < 1) reqTicks = 1;
                                    if (autoLockHoverTicks >= reqTicks) {
                                        currentTarget = pointing;
                                        autoLockHoverTarget = null;
                                        autoLockHoverTicks = 0;
                                    }
                                } else {
                                    autoLockHoverTarget = pointing;
                                    autoLockHoverTicks = 1;
                                }
                            } else {
                                autoLockHoverTarget = null;
                                autoLockHoverTicks = 0;
                            }
                        }
                        case OFF -> {
                            // 模式 3: 关闭自动索敌
                            autoLockHoverTarget = null;
                            autoLockHoverTicks = 0;
                        }
                    }
                }

                // 目标丢锁、死亡与自动切换目标 (Auto-Switch Target)
                if (currentTarget != null) {
                    double maxLockDist = (isHoldingBow && AutoAttackerConfig.ENABLE_AIM_PREDICT.get())
                            ? Math.max(64.0, AutoAttackerConfig.AIM_PREDICT_MAX_DIST.get())
                            : 64.0;
                    boolean isInvalid = !isValidTarget(player, currentTarget);
                    boolean isOutOfRange = currentTarget.level() != player.level() || currentTarget.distanceToSqr(player) > maxLockDist * maxLockDist;

                    if (isInvalid || isOutOfRange) {
                        if (AutoAttackerConfig.ENABLE_AUTO_SWITCH_TARGET.get()) {
                            // 目标死亡或超距，毫秒级无缝自动寻觅切换下一位最佳目标！
                            currentTarget = getPrioritizedTarget(player, searchRange, 90.0f, AutoAttackerConfig.AUTO_SWITCH_PRIORITY.get(), currentTarget);
                            lostTargetGraceTicks = 0;
                        } else {
                            currentTarget = null;
                            lostTargetGraceTicks = 0;
                        }
                    } else if (!hasLineOfSightMultiPoint(player, currentTarget)) {
                        lostTargetGraceTicks++;
                        if (lostTargetGraceTicks > MAX_LOST_GRACE_TICKS) {
                            if (AutoAttackerConfig.ENABLE_AUTO_SWITCH_TARGET.get()) {
                                // 目标长时间隐蔽入掩体，自动转火视野内其他暴露目标
                                currentTarget = getPrioritizedTarget(player, searchRange, 90.0f, AutoAttackerConfig.AUTO_SWITCH_PRIORITY.get(), currentTarget);
                                lostTargetGraceTicks = 0;
                            } else {
                                currentTarget = null;
                                lostTargetGraceTicks = 0;
                            }
                        }
                    } else {
                        lostTargetGraceTicks = 0;
                    }
                }
            } else {
                currentTarget = null;
            }

            staticCurrentTarget = currentTarget;

            // 目标运动采样与平滑追踪
            if (currentTarget != null) {
                Entity rootVehicle = currentTarget.getRootVehicle();
                Vec3 measured = new Vec3(
                        rootVehicle.getX() - rootVehicle.xo,
                        rootVehicle.getY() - rootVehicle.yo,
                        rootVehicle.getZ() - rootVehicle.zo
                );
                if (measured.lengthSqr() > 36.0) {
                    measured = Vec3.ZERO;
                }
                if (rootVehicle.onGround() && measured.y < 0) {
                    measured = new Vec3(measured.x, 0, measured.z);
                }

                if (currentTarget != lastTarget) {
                    resetTargetTracking();
                    lastTarget = currentTarget;
                    smoothedTargetVelocity = measured;
                    lastMeasuredVelocity = measured;
                    velSamples[0] = measured;
                    velSampleIndex = 1;
                    velSampleCount = 1;
                } else {
                    velSamples[velSampleIndex] = measured;
                    velSampleIndex = (velSampleIndex + 1) % VEL_SAMPLE_CAP;
                    if (velSampleCount < VEL_SAMPLE_CAP) velSampleCount++;

                    if (velSampleCount == 1) {
                        smoothedTargetVelocity = measured;
                    } else if (velSampleCount == 2) {
                        int p1 = (velSampleIndex - 1 + VEL_SAMPLE_CAP) % VEL_SAMPLE_CAP;
                        int p2 = (velSampleIndex - 2 + VEL_SAMPLE_CAP) % VEL_SAMPLE_CAP;
                        smoothedTargetVelocity = velSamples[p1].scale(0.65).add(velSamples[p2].scale(0.35));
                    } else {
                        int p1 = (velSampleIndex - 1 + VEL_SAMPLE_CAP) % VEL_SAMPLE_CAP;
                        int p2 = (velSampleIndex - 2 + VEL_SAMPLE_CAP) % VEL_SAMPLE_CAP;
                        int p3 = (velSampleIndex - 3 + VEL_SAMPLE_CAP) % VEL_SAMPLE_CAP;
                        smoothedTargetVelocity = velSamples[p1].scale(0.55)
                                .add(velSamples[p2].scale(0.30))
                                .add(velSamples[p3].scale(0.15));
                    }
                    lastMeasuredVelocity = measured;
                }
            } else {
                resetTargetTracking();
            }
        }
    }

    // --------------------------------------------------------
    // 渲染帧级平滑自瞄吸附 (严格配备 Smart Free-Look 鼠标保护与视线检测)
    // --------------------------------------------------------
    private long lastAimFrameNanos = 0L;
    private float smoothedRecoilBoost = 0.0f;

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;

        if (currentTarget == null) {
            lastAimFrameNanos = 0L;
            staticCurrentTarget = null;
            staticLastPredictedAim = null;
            wasLockedLastFrame = false;
            lastTrackedTarget = null;
            lastDestYaw = Float.NaN;
            lastDestPitch = Float.NaN;
            return;
        }

        long now = System.nanoTime();
        float deltaSec = (lastAimFrameNanos == 0L) ? (1.0f / 60.0f) : (now - lastAimFrameNanos) / 1e9f;
        lastAimFrameNanos = now;
        if (deltaSec <= 0f || deltaSec > 0.25f) deltaSec = 1.0f / 60.0f;

        applyAimAssist(player, currentTarget, event.renderTickTime, deltaSec);
    }

    private void applyAimAssist(Player player, LivingEntity target, float partialTick, float deltaSec) {
        // --- 鼠标死区与目标切换机制 (Mouse Deadzone & Switch Logic) ---
        float curYaw = player.getYRot();
        float curPitch = player.getXRot();
        if (wasLockedLastFrame) {
            float mouseDeltaYaw = Mth.wrapDegrees(curYaw - lastLockedYaw);
            float mouseDeltaPitch = curPitch - lastLockedPitch;

            // 累计玩家施加的鼠标偏转位移
            mouseDeflectionYaw += mouseDeltaYaw;
            mouseDeflectionPitch += mouseDeltaPitch;

            // 动态阻尼衰减 (半衰期约 150ms，微小手抖快速归零)
            float decay = (float) Math.exp(-deltaSec * 6.5f);
            mouseDeflectionYaw *= decay;
            mouseDeflectionPitch *= decay;

            double deflection = Math.hypot(mouseDeflectionYaw, mouseDeflectionPitch);
            double deadzoneThreshold = AutoAttackerConfig.LOCK_DEADZONE_THRESHOLD.get();

            // 超过死区阈值：判定为玩家主动甩动准星切换目标
            long now = System.currentTimeMillis();
            if (deflection >= deadzoneThreshold && (now - lastSwitchTime > 220L)) {
                ItemStack heldStack = getHeldBow(player);
                boolean holdingBow = !heldStack.isEmpty();
                double searchDist = (holdingBow && AutoAttackerConfig.ENABLE_AIM_PREDICT.get())
                        ? Math.max(AutoAttackerConfig.AIM_ASSIST_RANGE.get(), AutoAttackerConfig.AIM_PREDICT_MAX_DIST.get())
                        : AutoAttackerConfig.AIM_ASSIST_RANGE.get();

                LivingEntity switchTarget = findSwitchTarget(player, target, searchDist, 75.0f);
                if (switchTarget != null && switchTarget != target) {
                    target = switchTarget;
                    currentTarget = switchTarget;
                    lastSwitchTime = now;
                    mouseDeflectionYaw = 0f;
                    mouseDeflectionPitch = 0f;
                    TacticalDebugPanel.setStatus("切换锁定: " + switchTarget.getType().getDescription().getString());
                } else if (deflection >= deadzoneThreshold * 2.5) {
                    // 若无其他目标且强力甩开视角 (>2.5倍阈值)，解脱锁定并给予静默期
                    currentTarget = null;
                    staticCurrentTarget = null;
                    lastSwitchTime = now + 600L; // 给予 600ms 静默期，防止瞬间重新锁回
                    mouseDeflectionYaw = 0f;
                    mouseDeflectionPitch = 0f;
                    wasLockedLastFrame = false;
                    TacticalDebugPanel.setStatus("甩脱视角: 已解除锁定");
                    return;
                }
            }
        }

        staticCurrentTarget = target;
        staticSmoothedTargetVelocity = smoothedTargetVelocity;

        double px = Mth.lerp((double) partialTick, player.xo, player.getX());
        double py = Mth.lerp((double) partialTick, player.yo, player.getY()) + player.getEyeHeight();
        double pz = Mth.lerp((double) partialTick, player.zo, player.getZ());

        double tx = Mth.lerp((double) partialTick, target.xo, target.getX());
        double tz = Mth.lerp((double) partialTick, target.zo, target.getZ());

        ItemStack bowStack = getHeldBow(player);
        boolean isHoldingBow = !bowStack.isEmpty();
        boolean isGun = com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(bowStack);
        AutoBallisticsTracker.BallisticsProfile profile = isHoldingBow ? AutoBallisticsTracker.getProfile(bowStack) : null;

        double targetDistXZ = Math.hypot(tx - px, tz - pz);
        double baseTargetY = computeTargetY(target, partialTick, AutoAttackerConfig.TARGET_PART.get(), isGun, isHoldingBow, targetDistXZ);

        Vec3 eye = new Vec3(px, py, pz);
        Vec3 baseAimPoint = new Vec3(tx, baseTargetY, tz);

        double dX0 = tx - px;
        double dY0 = baseTargetY - py;
        double dZ0 = tz - pz;
        double horizDist0 = Math.sqrt(dX0 * dX0 + dZ0 * dZ0);
        float directYaw = (float) (Mth.atan2(dZ0, dX0) * (180D / Math.PI)) - 90.0F;
        float directPitch = (float) -(Mth.atan2(dY0, horizDist0) * (180D / Math.PI));

        float destYaw = directYaw;
        float destPitch = directPitch;

        if (isHoldingBow && profile != null && !profile.isHoming && AutoAttackerConfig.ENABLE_AIM_PREDICT.get()) {
            PredictedAim predicted = computePredictedAim(player, target, profile.speed, profile.gravity, eye, baseAimPoint);
            staticLastPredictedAim = predicted;
            if (predicted != null) {
                float leadYawDelta = Mth.wrapDegrees(predicted.targetYaw - directYaw);
                float leadPitchDelta = predicted.targetPitch - directPitch;

                double stability = computeMotionStability(lastMeasuredVelocity, smoothedTargetVelocity);
                double configuredBlend = AutoAttackerConfig.AIM_PREDICT_BLEND.get();
                float strength = (float) Mth.clamp(configuredBlend * stability, 0.0D, 1.0D);

                destYaw = directYaw + leadYawDelta * strength;
                destPitch = directPitch + leadPitchDelta * strength;
                staticLastPredictedYaw = destYaw;
            } else {
                staticLastPredictedYaw = directYaw;
            }
        } else {
            staticLastPredictedAim = null;
            staticLastPredictedYaw = directYaw;
        }

        // 视线丢失宽容期内不强行拉拽视角撞墙
        if (lostTargetGraceTicks > 0) {
            wasLockedLastFrame = false;
            lastTrackedTarget = null;
            lastDestYaw = Float.NaN;
            lastDestPitch = Float.NaN;
            return;
        }

        // ==========================================
        // 极速强锁 (Hard-Lock / Snap Lock): 0-Frame 绝对吸附死锁
        // ==========================================
        if (AutoAttackerConfig.AIM_LOCK_TYPE.get() == AutoAttackerConfig.AimLockType.HARD) {
            float hardYaw = destYaw;
            float hardPitch = Mth.clamp(destPitch, -89.5F, 89.5F);

            player.setYRot(hardYaw);
            player.setXRot(hardPitch);
            player.yRotO = hardYaw;
            player.xRotO = hardPitch;
            player.yHeadRot = hardYaw;
            player.yHeadRotO = hardYaw;

            lastLockedYaw = hardYaw;
            lastLockedPitch = hardPitch;
            wasLockedLastFrame = true;
            lastTrackedTarget = target;
            lastDestYaw = destYaw;
            lastDestPitch = destPitch;
            return;
        }

        // ==========================================
        // 动态相对运动前馈补偿 (Dynamic Rotational Kinematic Feedforward)
        // 当角色自身移动 (例如按 D 往右走位) 时，目标相对玩家视线必然向左转动。
        // 立即前馈补偿该角位移，驱动镜头自动向左拉拽，消除走位滞后，使准星绝对咬死在目标身上！
        // ==========================================
        float kinematicYaw = 0.0f;
        float kinematicPitch = 0.0f;
        if (wasLockedLastFrame && lastTrackedTarget == target && !Float.isNaN(lastDestYaw) && !Float.isNaN(lastDestPitch)) {
            kinematicYaw = Mth.wrapDegrees(destYaw - lastDestYaw);
            kinematicPitch = destPitch - lastDestPitch;
        }
        lastTrackedTarget = target;
        lastDestYaw = destYaw;
        lastDestPitch = destPitch;

        float currentYawAfterKinematic = player.getYRot() + kinematicYaw;
        float currentPitchAfterKinematic = player.getXRot() + kinematicPitch;

        float deltaY = Mth.wrapDegrees(destYaw - currentYawAfterKinematic);
        float deltaX = Mth.wrapDegrees(destPitch - currentPitchAfterKinematic);

        float baseFactor = AutoAttackerConfig.AIM_ASSIST_SPEED.get().floatValue();
        if (isHoldingBow && AutoAttackerConfig.ENABLE_AIM_PREDICT.get()) {
            baseFactor = Math.max(baseFactor, 0.35f);
        }
        // 角色走位/横移时自动增强角速度追踪刚度，防止剧烈横拉时被惯性甩脱
        if (player.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
            baseFactor = Math.max(baseFactor, 0.40f);
        }

        // 枪械后坐力抑制 (平滑连续阻尼 Anti-Recoil，消除高频抖动) 与机瞄感知 (ADS Sensing)
        Minecraft mc = Minecraft.getInstance();
        boolean isRelease = isGun && com.xdyyj.autoattacker.weapon.FirearmAdapter.isReleaseFire(com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunStatus(player.getMainHandItem()));
        if (mc.options.keyAttack.isDown() && !isRelease) {
            lastGunShootTime = System.currentTimeMillis();
        }
        boolean isGunFiring = isGun && (!isRelease 
                ? ((System.currentTimeMillis() - lastGunShootTime < 350L) || mc.options.keyAttack.isDown() || com.xdyyj.autoattacker.weapon.FirearmAdapter.isTriggerShooting())
                : (System.currentTimeMillis() - lastGunShootTime < 250L));
        boolean isGunAiming = isGun && AutoAttackerConfig.ENABLE_ADS_SENSING.get() && com.xdyyj.autoattacker.weapon.FirearmAdapter.isAiming(player);

        float factorPitch = baseFactor;
        float factorYaw = baseFactor;

        if (isGun && AutoAttackerConfig.ENABLE_ANTI_RECOIL.get()) {
            float targetRecoil = isGunFiring ? 1.0f : 0.0f;
            float lerpRate = isGunFiring ? (deltaSec * 18.0f) : (deltaSec * 9.0f);
            smoothedRecoilBoost = Mth.lerp(Mth.clamp(lerpRate, 0.0f, 1.0f), smoothedRecoilBoost, targetRecoil);

            float recoilMult = (float) AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get().doubleValue();

            // 当枪械处于射击状态且准星被后坐力抬高 (deltaX > 0，即玩家视线向上偏离目标，需要下压纠正) 时：
            // 远距离 (如 68m) 角误差对准星偏移极其敏感，必须提供强劲充沛的下压刚度，绝不能被 0.48 封顶卡死！
            if (isGunFiring && deltaX > 0) {
                float distBoost = (float) Mth.clamp(targetDistXZ / 30.0, 1.0, 2.2);
                factorPitch = Math.min(0.92f, baseFactor + 0.38f * recoilMult * smoothedRecoilBoost * distBoost);
            } else {
                factorPitch = Math.min(0.60f, baseFactor + 0.20f * recoilMult * smoothedRecoilBoost);
            }
            factorYaw = Math.min(0.45f, baseFactor + 0.12f * recoilMult * smoothedRecoilBoost);
        } else {
            smoothedRecoilBoost = 0.0f;
        }

        if (isGunAiming) {
            // 机瞄状态下微调抗抖：仅在水平或非后坐力回拉时平滑，下压抗后坐力期间绝不削弱下压刚度！
            factorYaw *= 0.85f;
            if (!isGunFiring || deltaX <= 0) {
                factorPitch *= 0.85f;
            }
        }

        // 微小角距平滑阻尼 (Micro-angle Damping Buffer)，当视角与目标差角极小 (<0.03°) 时渐进衰减拉拽，彻底杜绝镜头高频震荡 (Jitter)
        float absDeltaY = Math.abs(deltaY);
        float absDeltaX = Math.abs(deltaX);
        float dampedDeltaY = deltaY;
        float dampedDeltaX = deltaX;
        if (absDeltaY < 0.03f) {
            dampedDeltaY *= (absDeltaY / 0.03f);
        }
        if (absDeltaX < 0.03f) {
            dampedDeltaX *= (absDeltaX / 0.03f);
        }

        float alphaY = 1.0f - (float) Math.pow(1.0 - factorYaw, deltaSec * 20.0);
        float alphaX = 1.0f - (float) Math.pow(1.0 - factorPitch, deltaSec * 20.0);
        float stepY = kinematicYaw + dampedDeltaY * alphaY;
        float stepX = kinematicPitch + dampedDeltaX * alphaX;

        float newYaw = player.getYRot() + stepY;
        float newPitch = Mth.clamp(player.getXRot() + stepX, -89.5F, 89.5F);

        // 同步端点与头部转向
        player.setYRot(newYaw);
        player.setXRot(newPitch);
        player.yRotO = newYaw;
        player.xRotO = newPitch;
        player.yHeadRot = newYaw;
        player.yHeadRotO = newYaw;

        lastLockedYaw = newYaw;
        lastLockedPitch = newPitch;
        wasLockedLastFrame = true;
    }

    private LivingEntity getClosestTargetInFOV(Player player, double range, float maxAngle) {
        return getPrioritizedTarget(player, range, maxAngle, AutoAttackerConfig.AUTO_SWITCH_PRIORITY.get(), null);
    }

    /**
     * 判定玩家准星是否正在指向特定实体 (线段与 Hitbox 相交判定，或极小角度对齐)
     */
    private LivingEntity getCrosshairPointingTarget(Player player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(range));
        AABB searchBox = player.getBoundingBox().inflate(range);

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> isValidTarget(player, e));

        LivingEntity bestEntity = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            // Hitbox 稍微 inflate 0.15D，让准星手感更舒适自然
            AABB aabb = entity.getBoundingBox().inflate(0.15D);
            Optional<Vec3> hit = aabb.clip(eyePos, endPos);
            if (hit.isPresent()) {
                double distSqr = eyePos.distanceToSqr(hit.get());
                if (distSqr < bestDistSqr && hasLineOfSightMultiPoint(player, entity)) {
                    bestDistSqr = distSqr;
                    bestEntity = entity;
                }
            } else {
                // 如果距离稍远，角度在极小容差内且在视线内也算作指向
                double dX = entity.getX() - eyePos.x;
                double dY = (entity.getY() + entity.getBbHeight() * 0.5D) - eyePos.y;
                double dZ = entity.getZ() - eyePos.z;
                double length = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
                if (length > 0.001D && length <= range) {
                    double dot = (dX * lookVec.x + dY * lookVec.y + dZ * lookVec.z) / length;
                    double angle = Math.acos(Mth.clamp(dot, -1.0, 1.0)) * (180.0 / Math.PI);
                    // 视线张角：距离越远允许的容差越紧致
                    double allowedAngle = Math.max(1.8, Math.min(4.5, 20.0 / length));
                    if (angle <= allowedAngle && hasLineOfSightMultiPoint(player, entity)) {
                        double distSqr = player.distanceToSqr(entity);
                        if (distSqr < bestDistSqr) {
                            bestDistSqr = distSqr;
                            bestEntity = entity;
                        }
                    }
                }
            }
        }
        return bestEntity;
    }

    /**
     * 多策略目标优选搜索器 (支持准星角距优先、物理距离优先、残血斩杀优先)
     */
    private LivingEntity getPrioritizedTarget(Player player, double range, float maxAngle,
                                              AutoAttackerConfig.SwitchPriority priority, LivingEntity excludeTarget) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        AABB searchBox = player.getBoundingBox().inflate(range);

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != excludeTarget && isValidTarget(player, e));

        double rangeSqr = range * range;
        LivingEntity bestEntity = null;
        double bestScore = -Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            double distSqr = player.distanceToSqr(entity);
            if (distSqr > rangeSqr) continue;

            double dX = entity.getX() - eyePos.x;
            double dY = (entity.getY() + entity.getBbHeight() * 0.5D) - eyePos.y;
            double dZ = entity.getZ() - eyePos.z;
            double length = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
            if (length <= 0.0001D) continue;

            double alignment = (dX * lookVec.x + dY * lookVec.y + dZ * lookVec.z) / length;
            double angle = Math.acos(Mth.clamp(alignment, -1.0, 1.0)) * (180.0 / Math.PI);
            if (angle > maxAngle) continue;

            if (!hasLineOfSightMultiPoint(player, entity)) continue;

            double score = 0.0;
            switch (priority) {
                case FOV -> {
                    double normDist = Math.sqrt(distSqr) / range;
                    score = alignment - 0.15D * normDist;
                }
                case DISTANCE -> {
                    score = -Math.sqrt(distSqr);
                }
                case HEALTH -> {
                    score = -entity.getHealth();
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestEntity = entity;
            }
        }

        return bestEntity;
    }

    private LivingEntity findSwitchTarget(Player player, LivingEntity excludeTarget, double range, float maxAngle) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        AABB searchBox = player.getBoundingBox().inflate(range);

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
            e -> e != excludeTarget && isValidTarget(player, e));

        double rangeSqr = range * range;
        LivingEntity bestEntity = null;
        double bestScore = -Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            double distSqr = player.distanceToSqr(entity);
            if (distSqr > rangeSqr) continue;

            double dX = entity.getX() - eyePos.x;
            double dY = (entity.getY() + entity.getBbHeight() * 0.5D) - eyePos.y;
            double dZ = entity.getZ() - eyePos.z;
            double length = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
            if (length <= 0.0001D) continue;

            double alignment = (dX * lookVec.x + dY * lookVec.y + dZ * lookVec.z) / length;
            double angle = Math.acos(Mth.clamp(alignment, -1.0, 1.0)) * (180.0 / Math.PI);
            if (angle > maxAngle) continue;

            if (!hasLineOfSightMultiPoint(player, entity)) continue;

            // 优先选择最接近当前甩动朝向的目标 (对齐度高权重，距离低权重)
            double normDist = Math.sqrt(distSqr) / range;
            double score = alignment * 2.5D - 0.25D * normDist;

            if (score > bestScore) {
                bestScore = score;
                bestEntity = entity;
            }
        }

        return bestEntity;
    }

    private static boolean hasLineOfSightMultiPoint(Player player, LivingEntity target) {
        if (player.hasLineOfSight(target)) return true;

        Vec3 eye = player.getEyePosition();
        AABB bb = target.getBoundingBox();

        // 1. 眼睛部位点
        Vec3 headEyePos = target.getEyePosition();
        if (player.level().clip(new ClipContext(eye, headEyePos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
            return true;
        }

        // 2. Hitbox 中心点
        Vec3 center = bb.getCenter();
        if (player.level().clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
            return true;
        }

        // 3. 头部顶端 (maxY - 0.05)
        Vec3 top = new Vec3(center.x, bb.maxY - 0.05, center.z);
        if (player.level().clip(new ClipContext(eye, top, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
            return true;
        }

        // 4. 躯体腰部 (65% 高度)
        Vec3 waist = new Vec3(center.x, bb.minY + (bb.maxY - bb.minY) * 0.65, center.z);
        return player.level().clip(new ClipContext(eye, waist, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
    }

    public static final TagKey<Item> FORGE_BOWS_TAG = ItemTags.create(ResourceLocation.tryParse("forge:tools/bows"));

    public static boolean isBow(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof BowItem 
            || item instanceof CrossbowItem
            || item.getUseAnimation(stack) == net.minecraft.world.item.UseAnim.BOW 
            || item.getUseAnimation(stack) == net.minecraft.world.item.UseAnim.CROSSBOW
            || stack.is(FORGE_BOWS_TAG);
    }

    public static boolean isRangedWeapon(ItemStack stack) {
        return isBow(stack) || com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(stack);
    }

    public enum WeaponCategory {
        GUN("枪械", 0xFFD29922),
        BOW("弓弩", 0xFF388BFD),
        MELEE("近战", 0xFFF85149),
        OTHER("常规", 0xFF8B949E);

        private final String displayName;
        private final int color;

        WeaponCategory(String displayName, int color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getColor() {
            return color;
        }
    }

    public static boolean isMelee(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof SwordItem 
            || item instanceof AxeItem 
            || item instanceof TridentItem
            || stack.is(ItemTags.SWORDS) 
            || stack.is(ItemTags.AXES);
    }

    public static WeaponCategory getWeaponCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return WeaponCategory.OTHER;
        if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(stack)) {
            return WeaponCategory.GUN;
        }
        if (isBow(stack)) {
            return WeaponCategory.BOW;
        }
        if (isMelee(stack)) {
            return WeaponCategory.MELEE;
        }
        return WeaponCategory.OTHER;
    }

    public static ItemStack getActiveWeapon(Player player) {
        if (player == null) return ItemStack.EMPTY;
        if (player.isUsingItem() && (isRangedWeapon(player.getUseItem()) || isMelee(player.getUseItem()))) {
            return player.getUseItem();
        }
        ItemStack main = player.getMainHandItem();
        if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(main) || isBow(main) || isMelee(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(off) || isBow(off) || isMelee(off)) {
            return off;
        }
        return main.isEmpty() ? off : main;
    }

    public static ItemStack getHeldBow(Player player) {
        if (player.isUsingItem() && isRangedWeapon(player.getUseItem())) {
            return player.getUseItem();
        }
        if (isRangedWeapon(player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        if (isRangedWeapon(player.getOffhandItem())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    public static final class PredictedAim {
        public final Vec3 interceptPos;
        public final float targetYaw;
        public final float targetPitch;
        public final double flightTicks;

        public PredictedAim(Vec3 interceptPos, float targetYaw, float targetPitch, double flightTicks) {
            this.interceptPos = interceptPos;
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            this.flightTicks = flightTicks;
        }
    }

    private PredictedAim computePredictedAim(Player player, LivingEntity target, float speed, double gravity, Vec3 eye, Vec3 baseAimPoint) {
        if (!AutoAttackerConfig.ENABLE_AIM_PREDICT.get()) {
            return null;
        }

        double maxDist = AutoAttackerConfig.AIM_PREDICT_MAX_DIST.get();
        double distSq = eye.distanceToSqr(baseAimPoint);
        if (distSq < 1.0 * 1.0 || distSq > maxDist * maxDist) {
            return null;
        }

        if (speed < 0.25D) return null;

        Vec3 targetVel = smoothedTargetVelocity;
        if (target.hurtTime > 0) {
            // 受击衰减因子平滑过渡，避免硬截断导致准星频繁抖动拉扯
            double hurtDampen = 0.60D + 0.40D * (1.0D - (double) target.hurtTime / 10.0D);
            targetVel = targetVel.scale(Mth.clamp(hurtDampen, 0.50D, 1.0D));
        }
        // 扩展速度上限以支持高速矿车/鞘翅飞行玩家 (由 0.50D 扩展至 2.50D)
        if (targetVel.lengthSqr() > 6.25D) {
            targetVel = targetVel.normalize().scale(2.50D);
        }

        // 目标移动速度 (提前量预判严格且唯一作用于目标实体的运动，绝不对玩家自身操作引入反向甩靶漂移)
        // 模组武器/现代枪械不继承玩家自身奔跑跳跃动量；若强加反向补偿会导致玩家移动时准星严重偏离 Hitbox，吸附手感极差
        Vec3 effectiveVel = targetVel;
        double baseTotalDist = eye.distanceTo(baseAimPoint);
        double baseHorizDist = Math.sqrt((baseAimPoint.x - eye.x) * (baseAimPoint.x - eye.x) + (baseAimPoint.z - eye.z) * (baseAimPoint.z - eye.z));
        float baseDirectYaw = (float) (Mth.atan2(baseAimPoint.z - eye.z, baseAimPoint.x - eye.x) * (180D / Math.PI)) - 90.0F;
        float baseDirectPitch = (float) -(Mth.atan2(baseAimPoint.y - eye.y, Math.max(0.1, baseHorizDist)) * (180D / Math.PI));

        boolean isFlying = target instanceof net.minecraft.world.entity.FlyingMob
            || target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon
            || target instanceof net.minecraft.world.entity.boss.wither.WitherBoss
            || target.isFallFlying();

        boolean isGun = com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(getHeldBow(player));
        if (isGun) {
            // 现代枪械：直瞄高平无下坠，绝不施加抛物线仰角抬高！
            // 仅解算水平前置提前量 (Yaw) 与飞行时间，仰角严格为直瞄直线 (使用 3D 距离计算飞行时间)
            double flightTime = baseTotalDist / Math.max(speed, 5.0);

            double futureX = baseAimPoint.x + effectiveVel.x * flightTime;
            double futureZ = baseAimPoint.z + effectiveVel.z * flightTime;
            double futureY = baseAimPoint.y;
            if (isFlying && !target.onGround()) {
                futureY += effectiveVel.y * flightTime;
            }

            float straightYaw = (float) (Mth.atan2(futureZ - eye.z, futureX - eye.x) * (180D / Math.PI)) - 90.0F;
            float straightPitch = (float) -(Mth.atan2(futureY - eye.y, Math.hypot(futureX - eye.x, futureZ - eye.z)) * (180D / Math.PI));

            return new PredictedAim(new Vec3(futureX, futureY, futureZ), straightYaw, straightPitch, flightTime);
        }

        boolean isMoving = (effectiveVel.x * effectiveVel.x + effectiveVel.y * effectiveVel.y + effectiveVel.z * effectiveVel.z) > 0.0004;
        if (!isMoving) {
            AutoBallisticsTracker.TrajectorySolution staticTraj = AutoBallisticsTracker.solveTrajectory(eye, baseAimPoint, speed, gravity);
            float staticPitch = (staticTraj != null && staticTraj.reachable) ? staticTraj.pitchDeg : baseDirectPitch;
            double staticFlight = (staticTraj != null) ? staticTraj.flightTicks : (baseHorizDist / Math.max(speed, 0.5D));
            return new PredictedAim(baseAimPoint, baseDirectYaw, staticPitch, staticFlight);
        }


        Vec3 targetPoint = baseAimPoint;
        AutoBallisticsTracker.TrajectorySolution bestTraj = null;
        double maxTicks = Math.min(60.0D, maxDist * 1.5D);

        for (int i = 0; i < 3; i++) {
            bestTraj = AutoBallisticsTracker.solveTrajectory(eye, targetPoint, speed, gravity);
            double time;
            if (bestTraj != null && bestTraj.reachable) {
                time = Mth.clamp(bestTraj.flightTicks, 0.0D, maxTicks);
            } else {
                double curDist = eye.distanceTo(targetPoint);
                time = Mth.clamp(curDist / Math.max(speed, 0.5D), 0.0D, maxTicks);
            }

            double futureX = baseAimPoint.x + effectiveVel.x * time;
            double futureZ = baseAimPoint.z + effectiveVel.z * time;
            double futureY = baseAimPoint.y;

            if (isFlying && !target.onGround()) {
                double curVy = effectiveVel.y;
                double extraY = 0.0;
                int simTicks = (int) Math.min(time, 40.0);
                for (int step = 0; step < simTicks; step++) {
                    extraY += curVy;
                    curVy = (curVy - 0.08) * 0.98;
                }
                futureY = baseAimPoint.y + extraY;
            }

            targetPoint = new Vec3(futureX, futureY, futureZ);
        }

        Vec3 leadOffset = targetPoint.subtract(baseAimPoint);
        // 扩展提前量位移上限 (由 5.0m 扩展至 25.0m)，支持高速鞘翅/飞行生物及高爆速射武器
        if (leadOffset.lengthSqr() > 625.0D) {
            targetPoint = baseAimPoint.add(leadOffset.normalize().scale(25.0D));
        }

        double dx = targetPoint.x - eye.x;
        double dz = targetPoint.z - eye.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Mth.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;

        float targetPitch;
        if (bestTraj != null && bestTraj.reachable) {
            targetPitch = bestTraj.pitchDeg;
        } else if (horizDist < 0.2D || gravity <= 1.0E-6D) {
            targetPitch = (float) -(Mth.atan2(targetPoint.y - eye.y, Math.max(0.01, horizDist)) * (180D / Math.PI));
        } else {
            float directPitch = (float) -(Mth.atan2(targetPoint.y - eye.y, Math.max(0.01, horizDist)) * (180D / Math.PI));
            targetPitch = directPitch - (float) Math.min(25.0, horizDist * gravity * 6.0);
        }

        double flightTime = (bestTraj != null) ? bestTraj.flightTicks : (horizDist / Math.max(speed, 0.5D));
        return new PredictedAim(targetPoint, targetYaw, targetPitch, flightTime);
    }

    private double computeMotionStability(Vec3 measured, Vec3 smoothed) {
        double speed = smoothed.length();
        if (speed < 0.015D) return 1.0D;
        if (measured.lengthSqr() < 1.0E-6D) return 0.80D;
        double agreement = measured.normalize().dot(smoothed.normalize());
        return Mth.clamp(0.70D + Math.max(0.0D, agreement) * 0.30D, 0.70D, 1.0D);
    }

    public static void performAttack(Minecraft mc, Player player) {
        var gameMode = mc.gameMode;
        if (gameMode == null) return;

        LivingEntity locked = getCurrentTarget();
        if (locked != null && locked.isAlive() && !locked.isRemoved() && !AutoAttackerConfig.excludedEntities.contains(locked.getType())) {
            double reach = 4.5D;
            if (player.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get()) != null) {
                reach = player.getAttributeValue(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
            }
            if (player.distanceToSqr(locked) <= reach * reach) {
                gameMode.attack(player, locked);
                player.swing(InteractionHand.MAIN_HAND);
                return;
            }
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) hitResult).getEntity();
            if (AutoAttackerConfig.excludedEntities.contains(target.getType())) {
                return;
            }
            gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);
        } else {
            PlayerInteractEvent.LeftClickEmpty event = new PlayerInteractEvent.LeftClickEmpty(player);
            boolean isCancelled = MinecraftForge.EVENT_BUS.post(event);
            if (isCancelled) return;
            player.resetAttackStrengthTicker();
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    public static double computeTargetY(LivingEntity target, float partialTick, AutoAttackerConfig.TargetPart part, boolean isGun, boolean isHoldingBow, double targetDistXZ) {
        double bbHeight = target.getBbHeight();
        double eyeHeight = target.getEyeHeight();
        double targetY = Mth.lerp((double) partialTick, target.yo, target.getY());

        double headY;
        double torsoY;
        double bodyY = targetY + bbHeight * 0.5D;

        if (target.isBaby()) {
            headY = targetY + Math.max(eyeHeight, bbHeight * 0.82D);
            torsoY = targetY + bbHeight * 0.55D;
        } else if (target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon) {
            headY = targetY + bbHeight * 0.5D;
            torsoY = targetY + bbHeight * 0.4D;
        } else if (bbHeight > 3.0D) {
            headY = targetY + Math.min(eyeHeight, bbHeight * 0.90D);
            torsoY = targetY + bbHeight * 0.60D;
        } else {
            headY = targetY + eyeHeight;
            torsoY = targetY + bbHeight * 0.65D;
        }

        if (part == AutoAttackerConfig.TargetPart.HEAD) {
            if (isGun && targetDistXZ > 40.0) {
                return headY - Math.min(0.12D, bbHeight * 0.05D);
            }
            return headY;
        } else if (part == AutoAttackerConfig.TargetPart.TORSO) {
            return isHoldingBow ? torsoY : bodyY;
        } else { // ADAPTIVE
            if (isGun) {
                if (targetDistXZ > 70.0) {
                    return headY - Math.min(0.16D, bbHeight * 0.08D);
                } else if (targetDistXZ < 3.0) {
                    return torsoY;
                } else {
                    return headY;
                }
            } else {
                if (targetDistXZ <= 25.0) {
                    return headY;
                } else {
                    return isHoldingBow ? torsoY : bodyY;
                }
            }
        }
    }

    public static boolean isHoldingWeapon(Player player) {
        if (player == null) return false;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return false;
        
        if (lastCheckedItem != null && ItemStack.isSameItemSameTags(lastCheckedItem, stack)) {
            return isLastItemWeapon;
        }

        lastCheckedItem = stack;
        isLastItemWeapon = checkIsWeapon(stack);
        return isLastItemWeapon;
    }

    private static boolean checkIsWeapon(ItemStack stack) {
        Item item = stack.getItem();
        
        if (isInSet(stack, AutoAttackerConfig.blacklistItems, AutoAttackerConfig.blacklistTags)) {
            return false;
        }
        if (isInSet(stack, AutoAttackerConfig.whitelistItems, AutoAttackerConfig.whitelistTags)) {
            return true;
        }

        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || item instanceof TridentItem) {
            return true;
        }
        var modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        boolean hasAttackDamage = modifiers.containsKey(Attributes.ATTACK_DAMAGE);
        if (hasAttackDamage) {
            if (stack.is(ItemTags.PICKAXES) ||
                stack.is(ItemTags.SHOVELS) ||
                stack.is(ItemTags.HOES)) {
                return false;
            }
            return true;
        }
        return false;
    }

    private static boolean isInSet(ItemStack stack, Set<Item> items, Set<TagKey<Item>> tags) {
        if (items.contains(stack.getItem())) return true;
        for (TagKey<Item> tag : tags) {
            if (stack.is(tag)) return true;
        }
        return false;
    }
}
