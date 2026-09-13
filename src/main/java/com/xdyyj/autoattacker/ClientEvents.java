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
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.TickEvent;
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
import com.xdyyj.autoattacker.compat.ShoulderSurfingCompat;

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

    // --- 枪械自动换弹节流控制与死锁锁止 (Throttled Auto-Reload & Lockout) ---
    private long lastAutoReloadCheckTime = 0L;
    private long lastAutoReloadTriggerTime = 0L;
    private boolean cachedHasInventoryAmmo = true;
    private ItemStack lastAutoReloadItem = ItemStack.EMPTY;
    private int autoReloadRetryCount = 0;
    private boolean autoReloadLockout = false;
    private static final int MAX_RELOAD_RETRIES = 3;

    // --- 鼠标死区与目标切换机制 (Mouse Deadzone & Flick Switch) ---
    private float mouseDeflectionYaw = 0f;
    private float mouseDeflectionPitch = 0f;
    // 鼠标瞬时活跃度：与上面的累积甩枪量相互独立。
    // 累积量用于「是否切换目标」的判定（需要保持住），
    // 活跃度用于「压枪阻尼」（需要快速衰减），二者语义相反，故分开维护。
    private float mouseActivity = 0f;
    private long lastSwitchTime = 0L;
    private float lastLockedYaw = 0f;
    private float lastLockedPitch = 0f;
    private boolean wasLockedLastFrame = false;
    // 上一帧本模组自身写入的朝向增量。用于在统计"玩家手动偏转"时剔除自转，
    // 否则压枪/吸附自己每帧转动的角度会被误计为玩家甩枪，形成自阻尼闭环。
    private float selfAppliedYaw = 0f;
    private float selfAppliedPitch = 0f;
    // 上一帧自瞄「尚未完成的追踪量」(度)。用于区分「相机变化来自自瞄追目标」与
    // 「来自玩家甩枪」：前者即使本模组只写入了一部分，缺口也不该算作玩家操作。
    private float lastAimTrackingGapYaw = 0f;
    private float lastAimTrackingGapPitch = 0f;

    // --- 动态相对运动前馈补偿状态 (Dynamic Rotational Kinematic Feedforward) ---
    private LivingEntity lastTrackedTarget = null;
    private float lastDestYaw = Float.NaN;
    private float lastDestPitch = Float.NaN;
    private float lastDestCamYaw = Float.NaN;
    private float lastDestCamPitch = Float.NaN;

    // --- 目标运动追踪与平滑状态 ---
    private LivingEntity lastTarget = null;
    private Vec3 smoothedTargetVelocity = Vec3.ZERO;
    private Vec3 lastMeasuredVelocity = Vec3.ZERO;

    // --- 换武器平滑过渡 (Weapon-Swap Smooth Transit) ---
    // 锁定状态下切换手持武器时，上游的视线宽容期/目标重算会让目标角在一帧内突变，
    // 导致镜头硬切跳跃。此机制在输出端做速率限制 (slew-rate limiting)：
    // 镜头始终朝当前目标推进，只是推进速度受上限约束，与上游逻辑完全解耦。
    private ItemStack lastSwapWatchItem = ItemStack.EMPTY;
    private final SwapTransit swapTransit = new SwapTransit();
    private static final long SWAP_TRANSIT_DURATION_NANOS = 320_000_000L; // 320ms 过渡窗口
    // 速率上限（度/秒）。换武器瞬间目标角可能突变数十度，若上限过低，320ms 窗口内
    // 只能转过不到 2°，窗口一结束便直接跳到目标角 —— 视觉上仍是「瞬间跳跃」。
    // 取值需保证常见突变（~90°）能在窗口内收敛：90° / 0.32s ≈ 281°/s。
    private static final float SWAP_TRANSIT_RATE_START = 30.0f;           // 过渡起始最大角速度 (度/秒)
    private static final float SWAP_TRANSIT_RATE_FULL = 320.0f;           // 过渡结束时的最大角速度 (度/秒)

    // 走位前馈的合理上限 (度/帧)。正常走位引起的目标相对角位移远小于此值；
    // 超出者基本都源于目标自身移动或方位角几何翻转，不应前馈。
    private static final float KINEMATIC_FEEDFORWARD_MAX_DEG = 15.0f;

    // 自瞄「忙碌」判据：上一帧未完成的追踪缺口超过此角度时，认为相机转动由自瞄主导，
    // 该帧的相机位移不计入玩家偏转(死区)累计。
    private static final float AIM_TRACKING_BUSY_DEG = 3.0f;

    // 自瞄追踪期间，单帧残差的限幅值 (度)。平滑跟踪的追赶滞后残差通常远小于此，
    // 而玩家主动甩枪的单帧位移通常远大于此，故限幅可压制滞后而不影响真实操作。
    private static final float DEADZONE_RESIDUAL_CLAMP_DEG = 2.0f;

    // 部位锁定切换的瞄准高度平滑速率 (格/秒)。见 smoothAimHeight。
    private static final double AIM_HEIGHT_SMOOTH_RATE = 1.8D;

    // 部位锁定平滑状态 (见 smoothAimHeight)
    private LivingEntity smoothedAimHeightTarget = null;
    private double smoothedAimHeight = Double.NaN;

    // 切换目标的门槛倍数 (相对死区阈值) 与收益余量。切换是重决策，需明显高于
    // 普通死区且新目标确有更优评分，避免微残差引发「切走又切回」的乒乓。
    private static final double TARGET_SWITCH_THRESHOLD_MULT = 1.6D;
    private static final double TARGET_SWITCH_GAIN_MARGIN = 0.10D;

    // 自动转火的搜索角度 (度)。保持原有 90° 不变 —— 该值决定「多远的目标可被转火选中」，
    // 属功能能力而非灵敏度，无明确证据不应收窄。
    private static final float AUTO_SWITCH_MAX_ANGLE = 90.0f;

    // 切换目标时最多对多少个候选做视线检测。候选先按与准星的夹角升序排列，
    // 因此只需检查最靠近准星的前若干个，避免在大范围内逐一做昂贵的射线检测。
    private static final int MAX_SWITCH_CANDIDATE_SCANS = 4;

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
    private static float staticLastPredictedPitch = 0.0f;
    private static float staticLastPredictedCamYaw = 0.0f;
    private static float staticLastPredictedCamPitch = 0.0f;

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

    public static float getLastPredictedPitch() {
        return staticLastPredictedPitch;
    }

    public static float getLastPredictedCamYaw() {
        return staticLastPredictedCamYaw;
    }

    public static float getLastPredictedCamPitch() {
        return staticLastPredictedCamPitch;
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
        mouseActivity = 0f;
        // 自转剔除基准同样归零：切换/放弃目标后相机基准已无意义，若保留旧增量，
        // 下一帧 wrapDegrees(cur - lastLocked) - selfApplied 会把两帧间的真实位移
        // 全算进 mouseDeflection，可能直接越过甩脱阈值而解除锁定。
        selfAppliedYaw = 0f;
        selfAppliedPitch = 0f;
        lastAimTrackingGapYaw = 0f;
        lastAimTrackingGapPitch = 0f;
        for (int i = 0; i < VEL_SAMPLE_CAP; i++) {
            velSamples[i] = Vec3.ZERO;
        }
    }

    public void clearSessionState() {
        this.currentTarget = null;
        this.autoLockHoverTarget = null;
        this.autoLockHoverTicks = 0;
        this.lastTrackedTarget = null;
        this.lastDestYaw = Float.NaN;
        this.lastDestPitch = Float.NaN;
        this.lastDestCamYaw = Float.NaN;
        this.lastDestCamPitch = Float.NaN;
        this.lastAutoReloadItem = ItemStack.EMPTY;
        lastCheckedItem = ItemStack.EMPTY;
        staticCurrentTarget = null;
        staticSmoothedTargetVelocity = Vec3.ZERO;
        staticLastPredictedAim = null;
        staticLastPredictedYaw = 0.0f;
        staticLastPredictedPitch = 0.0f;
        staticLastPredictedCamYaw = 0.0f;
        staticLastPredictedCamPitch = 0.0f;
        resetTargetTracking();
        AutoBallisticsTracker.clearTransientReferences();
        com.xdyyj.autoattacker.compat.ThirdPersonCompat.resetAiming();
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

        if (ShoulderSurfingCompat.isShoulderSurfing() && ShoulderSurfingCompat.isFreeLooking()) {
            this.didSuppressVanillaAttack = false;
            if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isTriggerShooting()) {
                com.xdyyj.autoattacker.weapon.FirearmAdapter.setTriggerShoot(false, player);
            }
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            ShoulderSurfingCompat.neutralizeLegacyShoulderSurfingIntegrations();
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
                                // 与渲染帧的瞄准高度保持同源平滑：否则屏幕准星(平滑后)与
                                // 实际射击点(原始高度)会错开，表现为箭矢偏离准星。
                                // advance=false：此处只读取渲染帧已推进的平滑值，不重复迭代。
                                targetY = smoothAimHeight(currentTarget, targetY, 1.0f / 20.0f, false);
                                Vec3 baseAimPoint = new Vec3(currentTarget.getX(), targetY, currentTarget.getZ());

                                float sendYaw = player.getYRot();
                                float sendPitch = player.getXRot();

                                if (profile.isHoming) {
                                    double dX = currentTarget.getX() - eye.x;
                                    double dY = targetY - eye.y;
                                    double dZ = currentTarget.getZ() - eye.z;
                                    double horizDist = Math.sqrt(dX * dX + dZ * dZ);
                                    sendYaw = safeAimYaw(dX, dZ, player.getYRot());
                                    sendPitch = (float) -(Mth.atan2(dY, horizDist) * (180D / Math.PI));
                                } else {
                                    PredictedAim predicted = computePredictedAim(player, currentTarget, profile.speed, profile.gravity, eye, baseAimPoint);
                                    if (predicted != null) {
                                        sendYaw = predicted.targetYaw;
                                        sendPitch = predicted.targetPitch;
                                    }
                                }

                                if (ShoulderSurfingCompat.isShoulderSurfing()) {
                                    ShoulderSurfingCompat.syncPlayerRotation(sendYaw, sendPitch);
                                } else {
                                    if (mc.getConnection() != null) {
                                        mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(sendYaw, sendPitch, player.onGround()));
                                    }
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
                    if (!ItemStack.isSameItemSameTags(mainHand, lastAutoReloadItem)) {
                        lastAutoReloadItem = mainHand.copy();
                        lastAutoReloadTriggerTime = 0L;
                        autoReloadRetryCount = 0;
                        autoReloadLockout = false;
                    }

                    if (!autoReloadLockout) {
                        // 关键核心防失败机制：根据枪械 RPM 动态计算射击冷却缓冲时间，确保服务端 shootCoolDown 彻底清零
                        // 射击间隔 ms = 60000 / RPM (如 600 RPM -> 100ms)，加上 150ms 掉包/网络 tick 缓冲，最少 200ms
                        int rpm = (gunStatus.rpm > 0) ? gunStatus.rpm : 600;
                        long requiredBufferMs = Math.max(200L, (60000L / rpm) + 150L);

                        if (now - lastGunShootTime >= requiredBufferMs) {
                            // 检测背包是否真正有备用子弹 (含 TACZ 弹药箱 / 创造模式)
                            boolean hasAmmo = com.xdyyj.autoattacker.weapon.FirearmAdapter.hasInventoryAmmo(player, mainHand);
                            if (hasAmmo) {
                                // 换弹防重保护：重试防抖间隔 1200ms，杜绝高频重复触发
                                if (now - lastAutoReloadTriggerTime > 1200L) {
                                    if (autoReloadRetryCount >= MAX_RELOAD_RETRIES) {
                                        autoReloadLockout = true;
                                    } else {
                                        lastAutoReloadTriggerTime = now;
                                        autoReloadRetryCount++;
                                        com.xdyyj.autoattacker.weapon.FirearmAdapter.triggerReload(player);
                                    }
                                }
                            } else {
                                autoReloadLockout = true;
                            }
                        }
                    }
                    // 若处于锁止或无备弹，绝不重复发送换弹发包，彻底消除死循环
                } else if (gunStatus.totalAmmo > 0) {
                    // 枪内有子弹时重置触发标记与锁止，确保打空当瞬能第一时间就绪换弹
                    lastAutoReloadTriggerTime = 0L;
                    autoReloadRetryCount = 0;
                    autoReloadLockout = false;
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
                        boolean isShoulder = ShoulderSurfingCompat.isShoulderSurfing();
                        float currentYaw = isShoulder ? ShoulderSurfingCompat.getCameraYaw() : player.getYRot();
                        float currentPitch = isShoulder ? ShoulderSurfingCompat.getCameraPitch() : player.getXRot();
                        float targetAimYaw = isShoulder ? staticLastPredictedCamYaw : ((staticLastPredictedAim != null) ? staticLastPredictedAim.targetYaw : staticLastPredictedYaw);
                        float targetAimPitch = isShoulder ? staticLastPredictedCamPitch : ((staticLastPredictedAim != null) ? staticLastPredictedAim.targetPitch : currentPitch);
                        float yawDiff = Math.abs(Mth.wrapDegrees(targetAimYaw - currentYaw));
                        float pitchDiff = Math.abs(targetAimPitch - currentPitch);
                        
                        // 远距离 (如 68m) 射击时准星容差自适应微缩，提升远距点杀与爆头精度
                        double targetDist = player.distanceTo(currentTarget);
                        float maxDiff = (targetDist > 35.0) ? 9.0f : 14.0f;
                        boolean onTarget = (yawDiff <= maxDiff && pitchDiff <= maxDiff) ||
                                (isShoulder && mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() == currentTarget);

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
                                    if (onTarget) {
                                        if (isShoulder) {
                                            ShoulderSurfingCompat.syncPlayerRotation(staticLastPredictedYaw, staticLastPredictedPitch);
                                        }
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
                            if (onTarget) {
                                if (isShoulder) {
                                    ShoulderSurfingCompat.syncPlayerRotation(staticLastPredictedYaw, staticLastPredictedPitch);
                                }
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
                    // 锁定保持距离应与搜索距离同源：此前枪械硬编码 64.0，
                    // 而搜索用的是 AIM_ASSIST_RANGE(默认/常见配置为 120)，
                    // 导致 64 格外的目标「能锁上但立刻被判超距脱锁」反复闪跳。
                    double maxLockDist = Math.max(64.0, AutoAttackerConfig.AIM_ASSIST_RANGE.get());
                    if (isHoldingBow && AutoAttackerConfig.ENABLE_AIM_PREDICT.get()) {
                        maxLockDist = Math.max(maxLockDist, AutoAttackerConfig.AIM_PREDICT_MAX_DIST.get());
                    }
                    boolean isInvalid = !isValidTarget(player, currentTarget);
                    boolean isOutOfRange = currentTarget.level() != player.level() || currentTarget.distanceToSqr(player) > maxLockDist * maxLockDist;

                    // 自动转火(enableAutoSwitchTarget)是独立开关，只要它开启就生效，
                    // 不因 autoLockMode 而改变存在性 —— 避免静默禁用用户已开启的功能。
                    boolean autoSwitchAllowed = AutoAttackerConfig.ENABLE_AUTO_SWITCH_TARGET.get();

                    if (isInvalid || isOutOfRange) {
                        // 「目标已失效」(死亡/移除/变友军/旁观/进排除名单) 属确定性失效，立即处理；
                        // 仅「超距」给宽容期 —— 目标在射程边缘来回移动会反复越界，零宽容会使锁定闪断。
                        boolean hardInvalid = isInvalid;
                        if (hardInvalid) {
                            if (autoSwitchAllowed) {
                                currentTarget = pickBetterTarget(player, currentTarget, searchRange, AUTO_SWITCH_MAX_ANGLE);
                            } else {
                                currentTarget = null;
                            }
                            lostTargetGraceTicks = 0;
                        } else {
                            lostTargetGraceTicks++;
                            if (lostTargetGraceTicks > MAX_LOST_GRACE_TICKS) {
                                if (autoSwitchAllowed) {
                                    currentTarget = pickBetterTarget(player, currentTarget, searchRange, AUTO_SWITCH_MAX_ANGLE);
                                } else {
                                    currentTarget = null;
                                }
                                lostTargetGraceTicks = 0;
                            }
                        }
                    } else if (!hasLineOfSightMultiPoint(player, currentTarget)) {
                        lostTargetGraceTicks++;
                        if (lostTargetGraceTicks > MAX_LOST_GRACE_TICKS) {
                            if (autoSwitchAllowed) {
                                // 目标长时间隐蔽入掩体，转火视野内确有更优的暴露目标
                                currentTarget = pickBetterTarget(player, currentTarget, searchRange, AUTO_SWITCH_MAX_ANGLE);
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
        // 开启新的渲染帧：使 FirearmAdapter.getGunStatus 的帧内缓存失效。
        // 必须置于所有早退分支之前，否则暂停/无目标等早退会让缓存跨帧永久命中，
        // 使 HUD 弹药数等状态不再刷新。
        com.xdyyj.autoattacker.weapon.FirearmAdapter.beginGunStatusFrame();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;

        // 换武器检测必须最先执行：保证基线始终跟随实际手持物，
        // 否则早退分支会让 lastSwapWatchItem 陈旧，恢复后误判为"刚换武器"。
        updateSwapTransit(player);

        boolean isShoulderNow = ShoulderSurfingCompat.isShoulderSurfing();
        if (isShoulderNow && ShoulderSurfingCompat.isFreeLooking()) {
            wasLockedLastFrame = false;
            lastTrackedTarget = null;
            lastDestYaw = Float.NaN;
            lastDestPitch = Float.NaN;
            // 同 lostTargetGrace 分支：不追踪期间须清空自转剔除量，否则恢复首帧会拿陈旧基准
            // 相减，把期间的真实相机位移误判成玩家甩枪而解除锁定。
            selfAppliedYaw = 0f;
            selfAppliedPitch = 0f;
            lastAimTrackingGapYaw = 0f;
            lastAimTrackingGapPitch = 0f;
            mouseDeflectionYaw = 0f;
            mouseDeflectionPitch = 0f;
            lastLockedYaw = 0f;
            lastLockedPitch = 0f;
            com.xdyyj.autoattacker.compat.ThirdPersonCompat.resetAiming();
            return;
        }

        if (currentTarget == null) {
            lastAimFrameNanos = 0L;
            staticCurrentTarget = null;
            staticLastPredictedAim = null;
            wasLockedLastFrame = false;
            lastTrackedTarget = null;
            lastDestYaw = Float.NaN;
            lastDestPitch = Float.NaN;
            selfAppliedYaw = 0f;
            selfAppliedPitch = 0f;
            lastAimTrackingGapYaw = 0f;
            lastAimTrackingGapPitch = 0f;
            // 脱锁期间必须一并清空玩家偏转累计与上一帧锁定角：否则残留量会跨过这次
            // 脱锁带到下一次锁定，新目标刚锁上就可能被上一次的残留量顶过死区而立刻甩脱。
            mouseDeflectionYaw = 0f;
            mouseDeflectionPitch = 0f;
            lastLockedYaw = 0f;
            lastLockedPitch = 0f;
            com.xdyyj.autoattacker.compat.ThirdPersonCompat.resetAiming();
            return;
        }

        long now = System.nanoTime();
        float deltaSec = (lastAimFrameNanos == 0L) ? (1.0f / 60.0f) : (now - lastAimFrameNanos) / 1e9f;
        lastAimFrameNanos = now;
        // 兜底语义：过小则视为 60fps 单帧；过大 (卡顿/加载) 应夹到 0.25s 上限而不是回退成 1/60，
        // 否则"长时间停顿"会被当成"刚刚过去一帧"，平滑量停留在高位导致下一帧过冲。
        if (!(deltaSec > 0f)) deltaSec = 1.0f / 60.0f;   // 含 NaN 判断
        if (deltaSec > 0.25f) deltaSec = 0.25f;

        applyAimAssist(player, currentTarget, event.renderTickTime, deltaSec, isShoulderNow);
    }

    private void applyAimAssist(Player player, LivingEntity target, float partialTick, float deltaSec, boolean isShoulder) {
        Minecraft mc = Minecraft.getInstance();

        // --- 鼠标死区与目标切换机制 (Mouse Deadzone & Switch Logic) ---
        float curYaw = isShoulder ? ShoulderSurfingCompat.getCameraYaw() : player.getYRot();
        float curPitch = isShoulder ? ShoulderSurfingCompat.getCameraPitch() : player.getXRot();
        if (wasLockedLastFrame) {
            // 剔除本模组上一帧自身写入的转动量：只有超出自身贡献的部分才是玩家真实鼠标输入。
            // 未剔除时，压枪/吸附每帧的自转会被当成玩家甩枪 → userDamping 被自己压低 → 自阻尼闭环。
            float mouseDeltaYaw = Mth.wrapDegrees(curYaw - lastLockedYaw) - selfAppliedYaw;
            float mouseDeltaPitch = (curPitch - lastLockedPitch) - selfAppliedPitch;

            // 自瞄是否正在主动追踪目标：上一帧仍有未完成的追踪缺口，或本帧已写入可观转动量。
            boolean aimTrackingBusy =
                    Math.abs(lastAimTrackingGapYaw) > AIM_TRACKING_BUSY_DEG
                            || Math.abs(lastAimTrackingGapPitch) > AIM_TRACKING_BUSY_DEG
                            || Math.abs(selfAppliedYaw) > AIM_TRACKING_BUSY_DEG
                            || Math.abs(selfAppliedPitch) > AIM_TRACKING_BUSY_DEG;

            // 自瞄正在追踪目标时，相机的转动主要由自瞄驱动而非玩家鼠标。
            // 平滑跟踪必然存在「追赶滞后」——目标越快，滞后越大，残差也越大。
            // 若把这份残差原样计入玩家偏转，就会出现「目标越快越容易越过死区而脱锁」。
            // 注意不能整帧丢弃：那样自瞄一忙死区就永久失效，玩家将无法甩枪切目标/解锁。
            // 故只对残差做限幅——保留玩家真实的大幅甩动，仅压制滞后造成的零头。
            if (aimTrackingBusy) {
                mouseDeltaYaw = Mth.clamp(mouseDeltaYaw, -DEADZONE_RESIDUAL_CLAMP_DEG, DEADZONE_RESIDUAL_CLAMP_DEG);
                mouseDeltaPitch = Mth.clamp(mouseDeltaPitch, -DEADZONE_RESIDUAL_CLAMP_DEG, DEADZONE_RESIDUAL_CLAMP_DEG);
            }

            // 累计玩家施加的鼠标偏转位移 (供"是否切换目标"判定，需要保持住)
            mouseDeflectionYaw += mouseDeltaYaw;
            mouseDeflectionPitch += mouseDeltaPitch;

            // 动态阻尼衰减 (半衰期约 150ms，微小手抖快速归零)
            float decay = (float) Math.exp(-deltaSec * 6.5f);
            mouseDeflectionYaw *= decay;
            mouseDeflectionPitch *= decay;

            // 独立的瞬时活跃度 (供压枪阻尼)：本帧鼠标位移取峰值后快速衰减 (半衰期约 60ms)。
            // 与累积量分离后，一次甩枪不会长时间压制压枪强度，手指微抖也不会被累计成大位移。
            float instant = (float) Math.hypot(mouseDeltaYaw, mouseDeltaPitch) * 20.0f; // 折算为近似"度/秒"量级
            float activityDecay = (float) Math.exp(-deltaSec * 11.5f);
            mouseActivity = Math.max(instant, mouseActivity * activityDecay);

            double deflection = Math.hypot(mouseDeflectionYaw, mouseDeflectionPitch);
            double deadzoneThreshold = AutoAttackerConfig.LOCK_DEADZONE_THRESHOLD.get();

            // 超过阈值：判定为玩家主动甩动准星切换目标。
            // 切换门槛取死区的 1.6 倍：切换是重决策，若与普通死区同值，平滑跟踪的
            // 微小残差就会频繁触发无意义的切换(切走又切回，来回乒乓)。
            long now = System.currentTimeMillis();
            if (deflection >= deadzoneThreshold * TARGET_SWITCH_THRESHOLD_MULT && (now - lastSwitchTime > 220L)) {
                ItemStack heldStack = getHeldBow(player);
                boolean holdingBow = !heldStack.isEmpty();
                double searchDist = (holdingBow && AutoAttackerConfig.ENABLE_AIM_PREDICT.get())
                        ? Math.max(AutoAttackerConfig.AIM_ASSIST_RANGE.get(), AutoAttackerConfig.AIM_PREDICT_MAX_DIST.get())
                        : AutoAttackerConfig.AIM_ASSIST_RANGE.get();

                // 「换一个目标」属于自动切换目标功能，必须由 enableAutoSwitchTarget 控制。
                // 该开关关闭时不得把锁定换到别的目标上——否则玩家手动锁定的目标会被
                // 旁边的敌人抢走。关闭时仅保留下方「甩脱解除锁定」，那才是玩家自己的操作。
                LivingEntity switchTarget = AutoAttackerConfig.ENABLE_AUTO_SWITCH_TARGET.get()
                        ? findSwitchTarget(player, target, searchDist, 75.0f)
                        : null;
                // 还需新目标确有收益才切：仅有「另一个可见目标」不足以构成切换理由，
                // 否则残差触发时会切向一个明显更差的目标，随后又切回原目标(乒乓)。
                if (switchTarget != null && switchTarget != target
                        && switchYieldsGain(player, target, switchTarget, searchDist)) {
                    target = switchTarget;
                    currentTarget = switchTarget;
                    lastSwitchTime = now;
                    mouseDeflectionYaw = 0f;
                    mouseDeflectionPitch = 0f;
                    mouseActivity = 0f;
                    TacticalDebugPanel.setStatus("切换锁定: " + switchTarget.getType().getDescription().getString());
                } else if (deflection >= deadzoneThreshold * 2.5) {
                    // 若无其他目标且强力甩开视角 (>2.5倍阈值)，解脱锁定并给予静默期
                    currentTarget = null;
                    staticCurrentTarget = null;
                    lastSwitchTime = now + 600L; // 给予 600ms 静默期，防止瞬间重新锁回
                    mouseDeflectionYaw = 0f;
                    mouseDeflectionPitch = 0f;
                    mouseActivity = 0f;
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
        double rawBaseTargetY = computeTargetY(target, partialTick, AutoAttackerConfig.TARGET_PART.get(), isGun, isHoldingBow, targetDistXZ);
        // 部位锁定平滑：TARGET_PART 手动切换、或 ADAPTIVE 依距离自动换部位时，瞄准点会
        // 发生阶跃。若直接采用新高度，视角会瞬间跳一下。这里对瞄准高度做限速收敛
        // (slew-rate limiting)：每帧朝目标高度移动有限步长，切换看起来是平滑滑过去的。
        double baseTargetY = smoothAimHeight(target, rawBaseTargetY, deltaSec);

        Vec3 eye = new Vec3(px, py, pz);
        Vec3 baseAimPoint = new Vec3(tx, baseTargetY, tz);

        double dX0 = tx - px;
        double dY0 = baseTargetY - py;
        double dZ0 = tz - pz;
        double horizDist0 = Math.sqrt(dX0 * dX0 + dZ0 * dZ0);
        float directYaw = safeAimYaw(dX0, dZ0, player.getYRot());
        float directPitch = (float) -(Mth.atan2(dY0, horizDist0) * (180D / Math.PI));

        float destYaw = directYaw;
        float destPitch = directPitch;
        Vec3 finalTargetPoint = baseAimPoint;

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
                staticLastPredictedPitch = destPitch;

                if (predicted.interceptPos != null) {
                    finalTargetPoint = baseAimPoint.lerp(predicted.interceptPos, strength);
                }
            } else {
                staticLastPredictedYaw = directYaw;
                staticLastPredictedPitch = directPitch;
            }
        } else {
            staticLastPredictedAim = null;
            staticLastPredictedYaw = directYaw;
            staticLastPredictedPitch = directPitch;
        }

        float destCamYaw = destYaw;
        float destCamPitch = destPitch;

        if (isShoulder) {
            Vec3 camPos = ShoulderSurfingCompat.getCameraPosition();
            double cdx = finalTargetPoint.x - camPos.x;
            double cdy = finalTargetPoint.y - camPos.y;
            double cdz = finalTargetPoint.z - camPos.z;
            double cHoriz = Math.sqrt(cdx * cdx + cdz * cdz);
            destCamYaw = safeAimYaw(cdx, cdz, destYaw);
            destCamPitch = (float) -(Mth.atan2(cdy, cHoriz) * (180D / Math.PI));

            staticLastPredictedCamYaw = destCamYaw;
            staticLastPredictedCamPitch = destCamPitch;
        } else {
            staticLastPredictedCamYaw = destYaw;
            staticLastPredictedCamPitch = destPitch;
        }

        // 视线丢失宽容期内不强行拉拽视角撞墙
        if (lostTargetGraceTicks > 0) {
            wasLockedLastFrame = false;
            lastTrackedTarget = null;
            lastDestYaw = Float.NaN;
            lastDestPitch = Float.NaN;
            lastDestCamYaw = Float.NaN;
            lastDestCamPitch = Float.NaN;
            // 宽容期内不追踪目标，相机变化不应算作玩家甩枪。若保留上一帧的自转剔除量
            // (selfAppliedYaw 可高达十余度)，恢复追踪的首帧会用陈旧基准相减，把这两帧的
            // 全部真实位移误判为玩家主动甩脱 → 累计超过死区 → 无故解除锁定。
            // SMOOTH 逐帧写入角度故必中此坑，HARD 在该模式下不经过本段所以不受影响。
            selfAppliedYaw = 0f;
            selfAppliedPitch = 0f;
            lastAimTrackingGapYaw = 0f;
            lastAimTrackingGapPitch = 0f;
            // 同 currentTarget==null 分支：宽容期内不追踪，玩家偏转与上一帧锁定角一并清零，
            // 避免这段"非追踪期"的残留跨到恢复之后影响死区判定。
            mouseDeflectionYaw = 0f;
            mouseDeflectionPitch = 0f;
            lastLockedYaw = 0f;
            lastLockedPitch = 0f;
            return;
        }

        // ==========================================
        // 极速强锁 (Hard-Lock / Snap Lock): 0-Frame 绝对吸附死锁
        // ==========================================
        if (AutoAttackerConfig.AIM_LOCK_TYPE.get() == AutoAttackerConfig.AimLockType.HARD) {
            float hardPlayerYaw = destYaw;
            float hardPlayerPitch = Mth.clamp(destPitch, -89.5F, 89.5F);

            float hardCamYaw = isShoulder ? destCamYaw : hardPlayerYaw;
            float hardCamPitch = isShoulder ? Mth.clamp(destCamPitch, -89.5F, 89.5F) : hardPlayerPitch;

            if (isShoulder) {
                ShoulderSurfingCompat.setCameraRotation(hardCamYaw, hardCamPitch);
                ShoulderSurfingCompat.syncPlayerRotation(hardPlayerYaw, hardPlayerPitch);
            } else {
                player.setYRot(hardPlayerYaw);
                player.setXRot(hardPlayerPitch);
                player.yRotO = hardPlayerYaw;
                player.xRotO = hardPlayerPitch;
                player.yHeadRot = hardPlayerYaw;
                player.yHeadRotO = hardPlayerYaw;
            }

            lastLockedYaw = hardCamYaw;
            lastLockedPitch = hardCamPitch;
            // HARD 强锁为瞬移分支，不参与阻尼计算；清零自身增量避免残留值污染后续 SMOOTH 帧
            selfAppliedYaw = 0f;
            selfAppliedPitch = 0f;
            lastAimTrackingGapYaw = 0f;
            lastAimTrackingGapPitch = 0f;
            wasLockedLastFrame = true;
            lastTrackedTarget = target;
            lastDestYaw = hardPlayerYaw;
            lastDestPitch = hardPlayerPitch;
            lastDestCamYaw = hardCamYaw;
            lastDestCamPitch = hardCamPitch;
            return;
        }

        // ==========================================
        // 动态相对运动前馈补偿 (Dynamic Rotational Kinematic Feedforward)
        // 当角色自身移动 (例如按 A/D 往左右走位) 时，目标相对玩家视线必然发生横向转动。
        // 分别为越肩相机 (屏幕准星) 与玩家实体 (子弹射击原点) 前馈补偿该角位移，消除走位滞后，使准星绝对咬死在目标身上，子弹绝不偏斜！
        // ==========================================
        float kinematicCamYaw = 0.0f;
        float kinematicCamPitch = 0.0f;
        float kinematicPlayerYaw = 0.0f;
        float kinematicPlayerPitch = 0.0f;

        float targetCamYaw = isShoulder ? destCamYaw : destYaw;
        float targetCamPitch = isShoulder ? destCamPitch : destPitch;

        if (wasLockedLastFrame && lastTrackedTarget == target) {
            if (!Float.isNaN(lastDestCamYaw) && !Float.isNaN(lastDestCamPitch)) {
                kinematicCamYaw = Mth.wrapDegrees(targetCamYaw - lastDestCamYaw);
                kinematicCamPitch = targetCamPitch - lastDestCamPitch;
            }
            if (!Float.isNaN(lastDestYaw) && !Float.isNaN(lastDestPitch)) {
                kinematicPlayerYaw = Mth.wrapDegrees(destYaw - lastDestYaw);
                kinematicPlayerPitch = destPitch - lastDestPitch;
            }
            // 前馈只应补偿「玩家自身走位」造成的目标相对角位移，量级为每秒几度。
            // 目标自身快速移动（如从头顶飞过导致方位角瞬间翻转 180°）也会产生巨大
            // 帧间差值，若原样前馈：相机会被推着一个跳变角度暴走，同时该值被记成
            // 自瞄输出、下一帧与实际转过的角度对不上而产生残差 → 误判玩家甩枪 → 脱锁。
            // 故对超出合理走位量级的差值一律不施加前馈（交给正常平滑回路收敛）。
            if (Math.abs(kinematicCamYaw) > KINEMATIC_FEEDFORWARD_MAX_DEG) kinematicCamYaw = 0.0f;
            if (Math.abs(kinematicCamPitch) > KINEMATIC_FEEDFORWARD_MAX_DEG) kinematicCamPitch = 0.0f;
            if (Math.abs(kinematicPlayerYaw) > KINEMATIC_FEEDFORWARD_MAX_DEG) kinematicPlayerYaw = 0.0f;
            if (Math.abs(kinematicPlayerPitch) > KINEMATIC_FEEDFORWARD_MAX_DEG) kinematicPlayerPitch = 0.0f;
        }
        lastTrackedTarget = target;
        lastDestYaw = destYaw;
        lastDestPitch = destPitch;
        lastDestCamYaw = targetCamYaw;
        lastDestCamPitch = targetCamPitch;

        float curCamYaw = isShoulder ? ShoulderSurfingCompat.getCameraYaw() : player.getYRot();
        float curCamPitch = isShoulder ? ShoulderSurfingCompat.getCameraPitch() : player.getXRot();
        float currentCamYawAfterKinematic = curCamYaw + kinematicCamYaw;
        float currentCamPitchAfterKinematic = curCamPitch + kinematicCamPitch;

        float deltaCamY = Mth.wrapDegrees(targetCamYaw - currentCamYawAfterKinematic);
        float deltaCamX = Mth.wrapDegrees(targetCamPitch - currentCamPitchAfterKinematic);

        float curPlayerYaw = player.getYRot();
        float curPlayerPitch = player.getXRot();
        float currentPlayerYawAfterKinematic = curPlayerYaw + kinematicPlayerYaw;
        float currentPlayerPitchAfterKinematic = curPlayerPitch + kinematicPlayerPitch;

        float deltaPlayerY = Mth.wrapDegrees(destYaw - currentPlayerYawAfterKinematic);
        float deltaPlayerX = Mth.wrapDegrees(destPitch - currentPlayerPitchAfterKinematic);

        float baseFactor = AutoAttackerConfig.AIM_ASSIST_SPEED.get().floatValue();
        if (isHoldingBow && AutoAttackerConfig.ENABLE_AIM_PREDICT.get()) {
            baseFactor = Math.max(baseFactor, 0.35f);
        }
        // 角色走位/横移时自动增强角速度追踪刚度，防止剧烈横拉时被惯性甩脱
        if (player.getDeltaMovement().horizontalDistanceSqr() > 0.0004) {
            baseFactor = Math.max(baseFactor, 0.40f);
        }

        // 枪械后坐力抑制 (平滑连续阻尼 Anti-Recoil，消除高频抖动) 与机瞄感知 (ADS Sensing)
        com.xdyyj.autoattacker.weapon.FirearmAdapter.GunStatus gunStatus =
                com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunStatus(player.getMainHandItem());
        boolean isRelease = isGun && com.xdyyj.autoattacker.weapon.FirearmAdapter.isReleaseFire(gunStatus);

        // 真实开火判据：必须同时满足「有开火输入」+「枪械确实能打出子弹」。
        // 仅在按住左键就无条件视作开火，会让空仓 / 冷却 / 卡壳期间压枪持续满强度生效，
        // 把准星一路压下压死。totalAmmo <= 0 视为空仓，不产生后坐力。
        boolean hasAmmo = gunStatus == null || gunStatus.totalAmmo != 0;
        boolean triggerShooting = com.xdyyj.autoattacker.weapon.FirearmAdapter.isTriggerShooting();
        boolean fireInput = triggerShooting
                || (mc.options.keyAttack.isDown() && hasAmmo)
                || (isRelease && mc.options.keyUse.isDown() && hasAmmo);
        if (fireInput) {
            lastGunShootTime = System.currentTimeMillis();
        }
        boolean isGunFiring = isGun && (System.currentTimeMillis() - lastGunShootTime < (isRelease ? 250L : 350L));

        boolean isGunAiming = isGun && AutoAttackerConfig.ENABLE_ADS_SENSING.get() && com.xdyyj.autoattacker.weapon.FirearmAdapter.isAiming(player);

        float factorPitch = baseFactor;
        float factorYaw = baseFactor;

        // 压枪段在「锁定」与「未锁定」下均生效。
        // 锁定状态不会自动补足后坐力：computeTargetY 只决定瞄准点(头部/躯干)，
        // 而锁定回路的刚度由 baseFactor(aimAssistSpeed) 决定，远距离下不足以压住后坐力，
        // 因此需要压枪段额外抬高刚度。二者是叠加关系，不是替代关系。
        if (isGun && AutoAttackerConfig.ENABLE_ANTI_RECOIL.get()) {
            float targetRecoil = isGunFiring ? 1.0f : 0.0f;
            float lerpRate = isGunFiring ? (deltaSec * 18.0f) : (deltaSec * 9.0f);
            smoothedRecoilBoost = Mth.lerp(Mth.clamp(lerpRate, 0.0f, 1.0f), smoothedRecoilBoost, targetRecoil);

            float recoilMult = (float) AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get().doubleValue();

            // 玩家手动甩动/拉枪阻尼：若检测到玩家正在主动移动鼠标，按瞬时活跃度动态削减压枪下压刚度，
            // 绝不跟玩家手感抢夺控制权。使用独立的瞬时活跃度 (半衰期 ~60ms) 而非累积甩枪量，
            // 避免一次甩枪后压枪被长时间压制，也避免手指微抖被累计成大位移而误削压枪。
            float userDamping = (float) Mth.clamp(1.0 - (mouseActivity / 120.0), 0.25, 1.0);

            // 当枪械处于射击状态时：平衡水平与垂直追踪刚度，避免俯仰角极速收敛而偏航角严重滞后导致斜向偏右上
            if (isGunFiring) {
                float distBoost = (float) Mth.clamp(targetDistXZ / 30.0, 1.0, 2.0);
                // 上限必须随 baseFactor 放开：固定上限会在远距离(叠加 distBoost 后)被触顶截断，
                // 导致「越远越需要压枪、实际却压不动」。改为「基准 + 增量」并只对增量设上限。
                if (deltaCamX > 0) {
                    float add = Math.min(0.75f, 0.32f * recoilMult * smoothedRecoilBoost * distBoost * userDamping);
                    factorPitch = Mth.clamp(baseFactor + add, baseFactor, Math.max(baseFactor, 0.95f));
                } else {
                    float add = Math.min(0.45f, 0.20f * recoilMult * smoothedRecoilBoost * userDamping);
                    factorPitch = Mth.clamp(baseFactor + add, baseFactor, Math.max(baseFactor, 0.95f));
                }
                float addYaw = Math.min(0.55f, 0.25f * recoilMult * smoothedRecoilBoost * userDamping);
                factorYaw = Mth.clamp(baseFactor + addYaw, baseFactor, Math.max(baseFactor, 0.95f));
            } else {
                float add = Math.min(0.40f, 0.20f * recoilMult * smoothedRecoilBoost * userDamping);
                factorPitch = Mth.clamp(baseFactor + add, baseFactor, Math.max(baseFactor, 0.95f));
                float addYaw = Math.min(0.35f, 0.15f * recoilMult * smoothedRecoilBoost * userDamping);
                factorYaw = Mth.clamp(baseFactor + addYaw, baseFactor, Math.max(baseFactor, 0.95f));
            }
        } else {
            smoothedRecoilBoost = 0.0f;
        }

        if (isGunAiming) {
            // 机瞄状态下微调抗抖：仅在水平或非后坐力回拉时平滑，下压抗后坐力期间绝不削弱下压刚度！
            factorYaw *= 0.85f;
            if (!isGunFiring || deltaCamX <= 0) {
                factorPitch *= 0.85f;
            }
        }

        // 微小角距平滑阻尼 (Micro-angle Damping Buffer)，当视角与目标差角极小 (<0.03°) 时渐进衰减拉拽，彻底杜绝镜头高频震荡 (Jitter)
        float absDeltaCamY = Math.abs(deltaCamY);
        float absDeltaCamX = Math.abs(deltaCamX);
        float dampedCamDeltaY = deltaCamY;
        float dampedCamDeltaX = deltaCamX;
        if (absDeltaCamY < 0.03f) {
            dampedCamDeltaY *= (absDeltaCamY / 0.03f);
        }
        if (absDeltaCamX < 0.03f) {
            dampedCamDeltaX *= (absDeltaCamX / 0.03f);
        }

        // 夹取平滑因子：factor 若为 NaN 或越界，Math.pow 会产出 NaN，
        // 而 Mth.clamp 不拦 NaN，最终会让 player.setXRot(NaN) 把视角永久锁死（需重登）。
        float fy = Mth.clamp(factorYaw, 0.0f, 0.95f);
        float fx = Mth.clamp(factorPitch, 0.0f, 0.95f);
        if (Float.isNaN(fy)) fy = 0.5f;
        if (Float.isNaN(fx)) fx = 0.5f;
        float alphaY = 1.0f - (float) Math.pow(1.0 - fy, deltaSec * 20.0);
        float alphaX = 1.0f - (float) Math.pow(1.0 - fx, deltaSec * 20.0);
        float stepCamY = kinematicCamYaw + dampedCamDeltaY * alphaY;
        float stepCamX = kinematicCamPitch + dampedCamDeltaX * alphaX;

        float newCamYaw = curCamYaw + stepCamY;
        float newCamPitch = Mth.clamp(curCamPitch + stepCamX, -89.5F, 89.5F);

        // 换武器平滑过渡：拦截目标角突变，把镜头从上一帧朝向平滑滑向目标朝向
        // (未锁定或开火时自动让位，不干扰锁定吸附与开枪手感)
        float[] transit = applySwapTransit(newCamYaw, newCamPitch, isGunFiring, currentTarget != null, deltaSec);
        newCamYaw = transit[0];
        newCamPitch = transit[1];

        float newPlayerYaw;
        float newPlayerPitch;

        if (isShoulder) {
            // 关键几何收敛：第三人称越肩模式下，屏幕正中心准星在世界空间对应一条视线射线。
            // 以目标所在距离 D 处为基准交点 crosshairWorldPoint，逆解算玩家眼睛至该交点的射击朝向 newPlayerYaw/Pitch。
            // 无论准星处于平滑插值移动中还是完全锁死，发射出的子弹/箭矢均严格穿透准星屏幕所指目标，彻底消除视差与不对称平滑漂移！
            Vec3 camPos = ShoulderSurfingCompat.getCameraPosition();
            double targetDist = Math.max(0.5, camPos.distanceTo(finalTargetPoint));
            Vec3 camDir = Vec3.directionFromRotation(newCamPitch, newCamYaw);
            Vec3 crosshairWorldPoint = camPos.add(camDir.scale(targetDist));

            double pdx = crosshairWorldPoint.x - eye.x;
            double pdy = crosshairWorldPoint.y - eye.y;
            double pdz = crosshairWorldPoint.z - eye.z;
            double pHoriz = Math.sqrt(pdx * pdx + pdz * pdz);
            newPlayerYaw = safeAimYaw(pdx, pdz, curPlayerYaw);
            float rawPlayerPitch = (float) -(Mth.atan2(pdy, pHoriz) * (180D / Math.PI));

            // 若使用具备弹道重力下坠的抛物线武器 (非直瞄枪械)，叠加上弹道解算所得的抛物线仰角补偿
            if (isHoldingBow && !isGun && profile != null && !profile.isHoming && staticLastPredictedAim != null) {
                float gravityPitchDelta = destPitch - directPitch;
                newPlayerPitch = Mth.clamp(rawPlayerPitch + gravityPitchDelta, -89.5F, 89.5F);
            } else {
                newPlayerPitch = Mth.clamp(rawPlayerPitch, -89.5F, 89.5F);
            }

            ShoulderSurfingCompat.setCameraRotation(newCamYaw, newCamPitch);
            ShoulderSurfingCompat.syncPlayerRotation(newPlayerYaw, newPlayerPitch);
        } else {
            newPlayerYaw = newCamYaw;
            newPlayerPitch = newCamPitch;
            // 同步端点与头部转向
            player.setYRot(newPlayerYaw);
            player.setXRot(newPlayerPitch);
            player.yRotO = newPlayerYaw;
            player.xRotO = newPlayerPitch;
            player.yHeadRot = newPlayerYaw;
            player.yHeadRotO = newPlayerYaw;
        }

        // 记录本帧最终朝向，并保存"本模组自身施加的增量"供下一帧剔除自转
        float finalYaw = isShoulder ? newCamYaw : newPlayerYaw;
        float finalPitch = isShoulder ? newCamPitch : newPlayerPitch;
        selfAppliedYaw = Mth.wrapDegrees(finalYaw - curYaw);
        selfAppliedPitch = finalPitch - curPitch;

        lastLockedYaw = finalYaw;
        lastLockedPitch = finalPitch;
        // 记录本帧剩余未完成的追踪缺口，供下一帧判定相机转动是否由自瞄主导。
        lastAimTrackingGapYaw = deltaCamY;
        lastAimTrackingGapPitch = deltaCamX;
        wasLockedLastFrame = true;
    }

    /**
     * 安全的水平方位角计算。
     *
     * atan2(dz, dx) 在水平偏移趋近 0 (目标几乎在正下方/正上方) 时退化为任意值 ——
     * 例如 dx=0.4, dz=0 时恒得 0°，减 90 后固定为 -90°，与目标真实方位无关。
     * 这会让 destYaw 突然甩向无关方向、准星偏离目标被判为跟丢。
     *
     * @param fallbackYaw 水平偏移过小时沿用的偏航角 (通常是当前实际视角)
     */
    private static float safeAimYaw(double dx, double dz, float fallbackYaw) {
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.05D) return fallbackYaw;
        return (float) (Mth.atan2(dz, dx) * (180D / Math.PI)) - 90.0F;
    }

    private LivingEntity getClosestTargetInFOV(Player player, double range, float maxAngle) {
        return getPrioritizedTarget(player, range, maxAngle, AutoAttackerConfig.AUTO_SWITCH_PRIORITY.get(), null);
    }

    // =========================================================================
    // 换武器平滑过渡 (Weapon-Swap Smooth Transit)
    // =========================================================================

    /** 换武器平滑过渡的运行时状态 (每实例独立) */
    private final class SwapTransit {
        boolean active = false;
        long startNanos = 0L;
        float curYaw = 0f;    // 当前实际输出的朝向 (逐帧累积, 始终跟随目标)
        float curPitch = 0f;
    }

    /**
     * 每帧检测主手物品是否变化，变化即开启一段过渡窗口。
     *
     * 注意：换武器本身会让 currentTarget 短暂失效 (视线/目标重算)，
     * 因此这里不要求 currentTarget 非空 —— 否则最需要平滑的那一刻反而被跳过。
     * 真正是否施加限速，由 applySwapTransit 在渲染阶段结合锁定状态决定。
     *
     * @param isShoulder 当前是否第三人称越肩
     */
    private void updateSwapTransit(Player player) {
        ItemStack held = player.getMainHandItem();

        // 仅比较物品与 NBT，忽略堆叠数量 (丢弃/拾取同类物品不应触发过渡)
        boolean changed = !ItemStack.isSameItemSameTags(held, lastSwapWatchItem);
        if (changed) {
            lastSwapWatchItem = held.copy();
        }

        if (!changed) return;

        swapTransit.active = true;
        swapTransit.startNanos = System.nanoTime();
        // 起点取"此刻的实际朝向"，保证视觉上从当前位置开始滑动。
        // isShoulder 在此处才查询：未换武器时无需探测人称，避免每帧一次反射查询。
        boolean isShoulder = ShoulderSurfingCompat.isShoulderSurfing();
        if (isShoulder) {
            swapTransit.curYaw = ShoulderSurfingCompat.getCameraYaw();
            swapTransit.curPitch = ShoulderSurfingCompat.getCameraPitch();
        } else {
            swapTransit.curYaw = player.getYRot();
            swapTransit.curPitch = player.getXRot();
        }
    }

    /**
     * 换武器后的镜头限速收敛 (Slew-Rate Limiting)。
     *
     * 设计要点：绝不用「固定起点 + 曲线插值」——那会让镜头在过渡窗口内脱离目标移动，
     * 表现为准星僵在原处不跟随。此处对「本帧目标角」做速率限制：
     * 每帧都朝当前目标推进，只是推进速度受上限约束，因此目标移动时准星持续跟随。
     *
     * 仅当处于锁定状态时才施加；开火时立即让位，绝不干扰开枪吸附。
     *
     * @param firingNow 当前是否处于开火状态
     * @param locked   当前是否有锁定目标
     * @param deltaSec 本帧时长
     * @return 经过限速后的角度 [yaw, pitch]
     */
    private float[] applySwapTransit(float targetYaw, float targetPitch,
                                     boolean firingNow, boolean locked, float deltaSec) {
        if (!swapTransit.active) {
            return new float[]{targetYaw, targetPitch};
        }

        // 未锁定 / 开火：立即结束过渡，完全不参与插值
        if (firingNow || !locked) {
            swapTransit.active = false;
            return new float[]{targetYaw, targetPitch};
        }

        long elapsed = System.nanoTime() - swapTransit.startNanos;
        if (elapsed >= SWAP_TRANSIT_DURATION_NANOS || elapsed < 0L) {
            swapTransit.active = false;
            return new float[]{targetYaw, targetPitch};
        }

        // 速率上限从 RATE_START 线性放开到 RATE_FULL：
        // 起步稍缓避免顿挫，但全程保留足够跟随速率，绝不出现准星停摆。
        float t = (float) elapsed / (float) SWAP_TRANSIT_DURATION_NANOS;
        float maxRate = SWAP_TRANSIT_RATE_START + (SWAP_TRANSIT_RATE_FULL - SWAP_TRANSIT_RATE_START) * t;

        // 每帧都从"上一帧实际输出朝向"重新对齐，消除与真实相机的漂移
        // (curYaw/curPitch 即上一帧本方法返回的角度)
        float deltaYaw = Mth.wrapDegrees(targetYaw - swapTransit.curYaw);
        float deltaPitch = targetPitch - swapTransit.curPitch;

        float safeDelta = (deltaSec <= 0f || deltaSec > 0.25f) ? (1.0f / 60.0f) : deltaSec;
        float maxStep = Math.max(0.01f, maxRate * safeDelta);

        // 安全阀：已收敛到位 (偏差小于半个步长) 即提前结束过渡，
        // 避免与上游平滑步进形成双重限速叠加、导致收敛拖沓。
        if (Math.abs(deltaYaw) <= maxStep && Math.abs(deltaPitch) <= maxStep) {
            swapTransit.active = false;
            return new float[]{targetYaw, targetPitch};
        }

        swapTransit.curYaw += Mth.clamp(deltaYaw, -maxStep, maxStep);
        swapTransit.curPitch += Mth.clamp(deltaPitch, -maxStep, maxStep);

        return new float[]{swapTransit.curYaw, swapTransit.curPitch};
    }

    /**
     * 判定玩家准星是否正在指向特定实体 (线段与 Hitbox 相交判定，或极小角度对齐)
     */
    private LivingEntity getCrosshairPointingTarget(Player player, double range) {
        boolean isShoulder = ShoulderSurfingCompat.isShoulderSurfing();
        if (isShoulder) {
            HitResult hitResult = Minecraft.getInstance().hitResult;
            if (hitResult instanceof EntityHitResult entityHit) {
                Entity hitEntity = entityHit.getEntity();
                if (hitEntity instanceof LivingEntity living && isValidTarget(player, living)) {
                    return living;
                }
            }
        }

        Vec3 eyePos = isShoulder ? ShoulderSurfingCompat.getCameraPosition() : player.getEyePosition();
        Vec3 lookVec = isShoulder ? ShoulderSurfingCompat.getCameraLookVector() : player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(range));
        AABB searchBox = player.getBoundingBox().inflate(range);

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> isValidTarget(player, e));

        LivingEntity bestEntity = null;

        // 先收集「准星命中盒」的候选及其距离 (廉价几何计算)，排序后只对最近的前若干个
        // 做视线检测。此前对每个候选都调 hasLineOfSightMultiPoint(最坏 4 次射线)，
        // 且在两个分支里重复调用。
        List<Candidate> hits = new java.util.ArrayList<>();
        for (LivingEntity entity : entities) {
            // Hitbox 稍微 inflate 0.15D，让准星手感更舒适自然
            AABB aabb = entity.getBoundingBox().inflate(0.15D);
            Optional<Vec3> hit = aabb.clip(eyePos, endPos);
            if (hit.isPresent()) {
                hits.add(new Candidate(entity, 0.0D, eyePos.distanceToSqr(hit.get()), 0.0D, true));
            } else {
                // 如果距离稍远，角度在极小容差内也算作指向候选
                double dX = entity.getX() - eyePos.x;
                double dY = (entity.getY() + entity.getBbHeight() * 0.5D) - eyePos.y;
                double dZ = entity.getZ() - eyePos.z;
                double length = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
                if (length > 0.001D && length <= range) {
                    double dot = (dX * lookVec.x + dY * lookVec.y + dZ * lookVec.z) / length;
                    double angle = Math.acos(Mth.clamp(dot, -1.0, 1.0)) * (180.0 / Math.PI);
                    // 视线张角：距离越远允许的容差越紧致
                    double allowedAngle = Math.max(1.8, Math.min(4.5, 20.0 / length));
                    if (angle <= allowedAngle) {
                        hits.add(new Candidate(entity, angle, player.distanceToSqr(entity), 0.0D));
                    }
                }
            }
        }

        if (hits.isEmpty()) return null;

        // 命中盒相交者优先 (距离即射线命中距)，其次按与准星夹角
        hits.sort((a, b) -> {
            if (a.hitbox != b.hitbox) return a.hitbox ? -1 : 1;
            if (a.hitbox) return Double.compare(a.distSqr, b.distSqr);
            return Double.compare(a.angle, b.angle);
        });

        int limit = Math.min(hits.size(), MAX_SWITCH_CANDIDATE_SCANS);
        double bestDistSqr = Double.MAX_VALUE;
        for (int i = 0; i < limit; i++) {
            Candidate c = hits.get(i);
            if (!hasLineOfSightMultiPoint(player, c.entity)) continue;
            if (c.distSqr < bestDistSqr) {
                bestDistSqr = c.distSqr;
                bestEntity = c.entity;
            }
        }

        // 兜底：前若干个候选都被遮挡时，才回退到全量扫描，保证不因候选上限而漏掉可见目标。
        // 该路径仅在「准星附近存在多个候选但最近者全被挡住」时触发，属罕见情况。
        if (bestEntity == null && hits.size() > limit) {
            for (int i = limit; i < hits.size(); i++) {
                Candidate c = hits.get(i);
                if (!hasLineOfSightMultiPoint(player, c.entity)) continue;
                if (c.distSqr < bestDistSqr) {
                    bestDistSqr = c.distSqr;
                    bestEntity = c.entity;
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
        boolean isShoulder = ShoulderSurfingCompat.isShoulderSurfing();
        Vec3 eyePos = isShoulder ? ShoulderSurfingCompat.getCameraPosition() : player.getEyePosition();
        Vec3 lookVec = isShoulder ? ShoulderSurfingCompat.getCameraLookVector() : player.getViewVector(1.0F);
        AABB searchBox = player.getBoundingBox().inflate(range);

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != excludeTarget && isValidTarget(player, e));

        double rangeSqr = range * range;

        // 与 findSwitchTarget 同理：先做廉价的角度筛选，按夹角升序后只对最靠近准星的
        // 前若干个做视线检测，避免在大范围搜索时逐一对全部实体做昂贵射线检测。
        List<Candidate> candidates = new java.util.ArrayList<>();
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

            candidates.add(new Candidate(entity, angle, distSqr, alignment));
        }

        if (candidates.isEmpty()) return null;

        // DISTANCE / HEALTH 优先级与夹角无关，须全量评估；FOV 优先级则按夹角取前若干个。
        if (priority == AutoAttackerConfig.SwitchPriority.FOV) {
            candidates.sort((a, b) -> Double.compare(a.angle, b.angle));
        }
        int limit = (priority == AutoAttackerConfig.SwitchPriority.FOV)
                ? Math.min(candidates.size(), MAX_SWITCH_CANDIDATE_SCANS)
                : candidates.size();

        LivingEntity bestEntity = null;
        double bestScore = -Double.MAX_VALUE;

        for (int i = 0; i < limit; i++) {
            Candidate c = candidates.get(i);
            if (!hasLineOfSightMultiPoint(player, c.entity)) continue;

            double score = scoreCandidate(c, range, priority);
            if (score > bestScore) {
                bestScore = score;
                bestEntity = c.entity;
            }
        }

        // 兜底：FOV 优先级下若前若干个候选都被遮挡，回退扫描其余候选，
        // 避免候选上限导致本来可见的目标被漏掉。
        if (bestEntity == null && limit < candidates.size()) {
            for (int i = limit; i < candidates.size(); i++) {
                Candidate c = candidates.get(i);
                if (!hasLineOfSightMultiPoint(player, c.entity)) continue;
                double score = scoreCandidate(c, range, priority);
                if (score > bestScore) {
                    bestScore = score;
                    bestEntity = c.entity;
                }
            }
        }

        return bestEntity;
    }

    /** 按选定优先级为候选打分 (与选择逻辑同源)。 */
    private static double scoreCandidate(Candidate c, double range, AutoAttackerConfig.SwitchPriority priority) {
        return switch (priority) {
            case FOV -> c.alignment - 0.15D * (Math.sqrt(c.distSqr) / range);
            case DISTANCE -> -Math.sqrt(c.distSqr);
            case HEALTH -> -c.entity.getHealth();
        };
    }

    /**
     * 在视野内挑选一个「确实优于当前目标」的候选，用于自动转火。
     *
     * 与 getPrioritizedTarget 的区别：除非当前目标已经不可用(死亡/消失)，否则候选必须
     * 在评分上明显超出当前目标才返回。否则返回 null(保持原锁定)，避免仅仅因为
     * 旁边出现了另一个可见目标就把锁定换走。
     */
    private LivingEntity pickBetterTarget(Player player, LivingEntity current, double range, float maxAngle) {
        LivingEntity candidate = getPrioritizedTarget(player, range, maxAngle,
                AutoAttackerConfig.AUTO_SWITCH_PRIORITY.get(), current);
        if (candidate == null) return null;
        if (current == null || current.isRemoved() || !current.isAlive()) return candidate;
        return switchYieldsGain(player, current, candidate, range) ? candidate : null;
    }

    /**
     * 判断切到新目标是否确有收益：新目标评分必须超出原目标一个明显幅度。
     * 仅「存在另一个可见目标」不构成切换理由 —— 否则残差误触发死区时会切向
     * 一个明显更差的目标，随后残差再次触发又切回原目标，形成来回乒乓。
     */
    private boolean switchYieldsGain(Player player, LivingEntity current, LivingEntity candidate, double range) {
        if (current == null || current.isRemoved() || !current.isAlive()) return true;
        boolean isShoulder = ShoulderSurfingCompat.isShoulderSurfing();
        Vec3 eyePos = isShoulder ? ShoulderSurfingCompat.getCameraPosition() : player.getEyePosition();
        Vec3 lookVec = isShoulder ? ShoulderSurfingCompat.getCameraLookVector() : player.getViewVector(1.0F);
        double curScore = scoreTarget(player, current, eyePos, lookVec, range);
        double newScore = scoreTarget(player, candidate, eyePos, lookVec, range);
        return newScore > curScore + TARGET_SWITCH_GAIN_MARGIN;
    }

    /**
     * 目标评分 (与 findSwitchTarget 内部一致)：对齐度为主权重，距离为次权重。
     * 供「切换是否值得」比较使用。
     */
    private double scoreTarget(Player player, LivingEntity entity, Vec3 eyePos, Vec3 lookVec, double range) {
        double dX = entity.getX() - eyePos.x;
        double dY = (entity.getY() + entity.getBbHeight() * 0.5D) - eyePos.y;
        double dZ = entity.getZ() - eyePos.z;
        double length = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
        if (length <= 0.0001D) return -Double.MAX_VALUE;
        double alignment = (dX * lookVec.x + dY * lookVec.y + dZ * lookVec.z) / length;
        double normDist = player.distanceToSqr(entity) <= 0 ? 0 : Math.sqrt(player.distanceToSqr(entity)) / range;
        return alignment * 2.5D - 0.25D * normDist;
    }

    private LivingEntity findSwitchTarget(Player player, LivingEntity excludeTarget, double range, float maxAngle) {
        boolean isShoulder = ShoulderSurfingCompat.isShoulderSurfing();
        Vec3 eyePos = isShoulder ? ShoulderSurfingCompat.getCameraPosition() : player.getEyePosition();
        Vec3 lookVec = isShoulder ? ShoulderSurfingCompat.getCameraLookVector() : player.getViewVector(1.0F);
        AABB searchBox = player.getBoundingBox().inflate(range);

        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
            e -> e != excludeTarget && isValidTarget(player, e));

        double rangeSqr = range * range;

        // 先用廉价的角度/距离计算筛出候选，按与准星的夹角排序，只对最靠近准星的前若干个
        // 做视线检测。视线检测每次最多 4 次 level().clip，是全链路最贵的操作；此前对每个
        // 通过角度过滤的实体都做一次，搜索范围 120 格时单帧可达上千次射线检测。
        // 切换目标本质是「挑最靠近准星的那个」，无需对全部候选逐一验证视线。
        List<Candidate> candidates = new java.util.ArrayList<>();
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

            candidates.add(new Candidate(entity, angle, distSqr, alignment));
        }

        if (candidates.isEmpty()) return null;

        candidates.sort((a, b) -> Double.compare(a.angle, b.angle));
        int limit = Math.min(candidates.size(), MAX_SWITCH_CANDIDATE_SCANS);

        LivingEntity bestEntity = null;
        double bestScore = -Double.MAX_VALUE;
        for (int i = 0; i < limit; i++) {
            Candidate c = candidates.get(i);
            if (!hasLineOfSightMultiPoint(player, c.entity)) continue;

            double normDist = Math.sqrt(c.distSqr) / range;
            double score = c.alignment * 2.5D - 0.25D * normDist;

            if (score > bestScore) {
                bestScore = score;
                bestEntity = c.entity;
            }
        }

        // 兜底：最靠近准星的若干个都被遮挡时，回退扫描其余候选，避免候选上限导致漏掉可见目标。
        if (bestEntity == null && candidates.size() > limit) {
            for (int i = limit; i < candidates.size(); i++) {
                Candidate c = candidates.get(i);
                if (!hasLineOfSightMultiPoint(player, c.entity)) continue;
                double normDist = Math.sqrt(c.distSqr) / range;
                double score = c.alignment * 2.5D - 0.25D * normDist;
                if (score > bestScore) {
                    bestScore = score;
                    bestEntity = c.entity;
                }
            }
        }

        return bestEntity;
    }

    /** 切换/选中目标的候选记录 (实体 + 预计算的角度/距离/对齐度)。 */
    private static final class Candidate {
        final LivingEntity entity;
        final double angle;
        final double distSqr;
        final double alignment;
        /** 是否为准星命中盒相交 (优先于纯角度接近)。 */
        final boolean hitbox;

        Candidate(LivingEntity entity, double angle, double distSqr, double alignment) {
            this(entity, angle, distSqr, alignment, false);
        }

        Candidate(LivingEntity entity, double angle, double distSqr, double alignment, boolean hitbox) {
            this.entity = entity;
            this.angle = angle;
            this.distSqr = distSqr;
            this.alignment = alignment;
            this.hitbox = hitbox;
        }
    }

    /**
     * 多点视线检测 (玩家眼睛 / 相机视点均可用)。
     *
     * @param viewPos 视线起点。第一人称传 player.getEyePosition()，
     *                第三人称必须传相机位置 —— 否则会出现"选目标用相机射线、
     *                验视线却用实体眼睛"的基准错配，导致越肩视角下目标刚锁上就掉。
     */
    private static boolean hasLineOfSightFrom(Player player, LivingEntity target, Vec3 viewPos) {
        boolean eyeBased = viewPos == null || viewPos.distanceToSqr(player.getEyePosition()) < 1.0E-6D;
        // player.hasLineOfSight 就是「眼睛→目标眼睛」的一次 raycast。
        // 当视点即玩家眼睛时，它已覆盖下方的检测点 1，命中即返回，避免重复一次 raycast。
        if (player.hasLineOfSight(target)) return true;

        Vec3 eye = viewPos != null ? viewPos : player.getEyePosition();
        AABB bb = target.getBoundingBox();

        // 1. 眼睛部位点 (仅当视点不是玩家眼睛时才有别于 player.hasLineOfSight，需显式检测)
        if (!eyeBased) {
            Vec3 headEyePos = target.getEyePosition();
            if (player.level().clip(new ClipContext(eye, headEyePos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
                return true;
            }
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

    /**
     * 按当前人称选取视线基准。
     *
     * 注意：不能只在「相机」与「眼睛」之间二选一 —— 第三人称相机位于玩家后上方，
     * 大俯角(如目标在脚下)时相机到目标的射线会穿过玩家自身或脚下方块，
     * 导致明明可见却判定无视线而脱锁。这里两个基准都试，任一通路即算有视线。
     */
    private static boolean hasLineOfSightMultiPoint(Player player, LivingEntity target) {
        // 先试实体眼睛 (稳定的基础基准)
        Vec3 eyePos = player.getEyePosition();
        if (hasLineOfSightFrom(player, target, eyePos)) {
            return true;
        }
        // 第三人称下再试相机视点 (覆盖越肩视角的准星指向)
        if (ShoulderSurfingCompat.isShoulderSurfing()) {
            Vec3 camPos = ShoulderSurfingCompat.getCameraPosition();
            if (camPos != null && camPos.distanceToSqr(eyePos) > 1.0E-4) {
                return hasLineOfSightFrom(player, target, camPos);
            }
        }
        return false;
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
        float baseDirectYaw = safeAimYaw(baseAimPoint.x - eye.x, baseAimPoint.z - eye.z, player.getYRot());
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

            float straightYaw = safeAimYaw(futureX - eye.x, futureZ - eye.z, player.getYRot());
            float straightPitch = (float) -(Mth.atan2(futureY - eye.y, Math.hypot(futureX - eye.x, futureZ - eye.z)) * (180D / Math.PI));

            return new PredictedAim(new Vec3(futureX, futureY, futureZ), straightYaw, straightPitch, flightTime);
        }

        ItemStack bowStack = getHeldBow(player);
        boolean isHoldingBow = !bowStack.isEmpty();
        AutoBallisticsTracker.BallisticsProfile profile = isHoldingBow ? AutoBallisticsTracker.getProfile(bowStack) : null;
        double drag = profile != null ? profile.drag : 0.99D;

        boolean isMoving = (effectiveVel.x * effectiveVel.x + effectiveVel.y * effectiveVel.y + effectiveVel.z * effectiveVel.z) > 0.0004;
        if (!isMoving) {
            AutoBallisticsTracker.TrajectorySolution staticTraj = AutoBallisticsTracker.solveTrajectory(eye, baseAimPoint, speed, gravity, drag);
            float staticPitch;
            if (staticTraj != null && staticTraj.reachable) {
                staticPitch = staticTraj.pitchDeg;
            } else {
                staticPitch = baseDirectPitch;
            }
            double staticFlight = (staticTraj != null) ? staticTraj.flightTicks : (baseHorizDist / Math.max(speed, 0.5D));
            return new PredictedAim(baseAimPoint, baseDirectYaw, staticPitch, staticFlight);
        }


        Vec3 targetPoint = baseAimPoint;
        AutoBallisticsTracker.TrajectorySolution bestTraj = null;
        double maxTicks = Math.min(60.0D, maxDist * 1.5D);

        for (int i = 0; i < 3; i++) {
            bestTraj = AutoBallisticsTracker.solveTrajectory(eye, targetPoint, speed, gravity, drag);
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
        float targetYaw = safeAimYaw(dx, dz, player.getYRot());

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
            // 属性查询只为取 reach：一次取得 Holder 即可，无需再查一遍属性表。
            var reachAttr = player.getAttribute(net.minecraftforge.common.ForgeMod.ENTITY_REACH.get());
            double reach = (reachAttr != null) ? reachAttr.getValue() : 4.5D;
            if (player.distanceToSqr(locked) <= reach * reach) {
                // 近身攻击实体：不发空挥通知。Botania 系武器的剑气只在原版判定为
                // 「空挥」(LeftClickEmpty，即准星未命中任何实体)时才生成；
                // 打实体时补发通知会导致近身战斗也冒光束，与武器原本语义不符。
                gameMode.attack(player, locked);
                player.swing(InteractionHand.MAIN_HAND);
                // 攻击后重置蓄力 ticker：否则 getAttackStrengthScale 恒为 1.0，
                // 攻击将不再受武器攻速节流(退化为每 tick 连击)。
                player.resetAttackStrengthTicker();
                return;
            }
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) hitResult).getEntity();
            if (AutoAttackerConfig.excludedEntities.contains(target.getType())) {
                return;
            }
            // 同样：命中实体时不发空挥通知。
            gameMode.attack(player, target);
            player.swing(InteractionHand.MAIN_HAND);
            player.resetAttackStrengthTicker();
        } else {
            // 纯空挥：这才是 Botania 系剑气唯一应触发的情形。
            // 先发通知(此时服务端蓄力仍为满值，能通过其 attackStrength == 1.0 校验)，
            // 再重置客户端蓄力 ticker 使攻击受攻速节流。
            ForgeHooks.onEmptyLeftClick(player);
            player.swing(InteractionHand.MAIN_HAND);
            player.resetAttackStrengthTicker();
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

    /**
     * 瞄准高度的限速平滑 (Slew-Rate Limiting)。
     *
     * 用于部位锁定切换：TARGET_PART 在 HEAD/TORSO/ADAPTIVE 间切换时，或 ADAPTIVE 因
     * 距离越过阈值而自动换部位时，computeTargetY 的返回值会阶跃。直接采用会让视角跳变；
     * 此处每帧朝新高度移动有限步长，使切换呈现为平滑滑动。
     *
     * 不做「固定起点 + 曲线插值」：那会在过渡期间脱离目标实际高度，目标移动时准星失准。
     * 这里每帧都以最新目标高度为收敛点，只限制单帧变化量。
     *
     * @param target       当前锁定目标 (变更时立即重置，避免跨目标滑动)
     * @param rawHeight    computeTargetY 算出的本帧目标高度
     * @param deltaSec     帧间隔(秒)
     */
    private double smoothAimHeight(LivingEntity target, double rawHeight, float deltaSec) {
        return smoothAimHeight(target, rawHeight, deltaSec, true);
    }

    /**
     * @param advance 是否推进平滑状态。渲染帧传 true(每帧迭代一次)；
     *                同一渲染帧内的其它读取方(tick 阶段的射箭路径)传 false，
     *                只取当前平滑值，避免以不同步长二次推进导致收敛速率翻倍。
     */
    private double smoothAimHeight(LivingEntity target, double rawHeight, float deltaSec, boolean advance) {
        if (smoothedAimHeightTarget != target) {
            // 换了目标：直接从该目标的实际高度起步，不做跨目标滑移
            smoothedAimHeightTarget = target;
            smoothedAimHeight = rawHeight;
            return rawHeight;
        }
        if (Double.isNaN(smoothedAimHeight)) {
            smoothedAimHeight = rawHeight;
            return rawHeight;
        }
        if (!advance) {
            return smoothedAimHeight;
        }

        float dt = (deltaSec > 0f && deltaSec <= 0.25f) ? deltaSec : (1.0f / 60.0f);
        // 每秒最多移动的高度(格/秒)。取值需在「切换肉眼可辨」与「不拖沓」之间平衡：
        // 1.8 格/秒下，头↔躯干(约 0.5~1.0 格)约 0.3~0.6 秒走完。
        double maxStep = AIM_HEIGHT_SMOOTH_RATE * dt;
        double diff = rawHeight - smoothedAimHeight;
        if (Math.abs(diff) <= maxStep) {
            smoothedAimHeight = rawHeight;
        } else {
            smoothedAimHeight += Math.signum(diff) * maxStep;
        }
        return smoothedAimHeight;
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
