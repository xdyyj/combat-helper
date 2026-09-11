package com.xdyyj.autoattacker.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.xdyyj.autoattacker.AutoAttackerConfig;
import com.xdyyj.autoattacker.AutoBallisticsTracker;
import com.xdyyj.autoattacker.ClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 战术弹道渲染系统 (支持方案 A + 方案 B 自由切换与协同双显):
 * - 方案 A: 2D 战术 HUD 动态弹着点光标 (投影到屏幕坐标, 杜绝 3D 畸变, 附带距离、阻挡警示与锁定指示)
 * - 方案 B: 3D 离散发光节点/点阵光链 (从眼睛 1.2m 外起步, 零近切面膨胀, 附带终点地面战术光圈)
 */
public final class TrajectoryRenderer {

    private static final int MAX_STEPS = 800;
    private static final double SUBSTEP = 0.12D;
    private static final double ENTITY_HIT_PADDING = 0.18D;
    private static final double MAX_PREVIEW_DISTANCE = 128.0D;
    private static final double MIN_3D_RENDER_DISTANCE = 0.32D; // 避开近裁切面贴脸，同时允许从持弓手部模型侧向自然射出

    // 缓存最新一帧的弹道与屏幕投影数据供 HUD 与 3D 渲染共享
    private static final TrajectoryInfo lastTrajectoryInfo = new TrajectoryInfo();

    private TrajectoryRenderer() {}

    public static TrajectoryInfo getLastTrajectoryInfo() {
        return lastTrajectoryInfo;
    }

    // =========================================================================
    // 3D 场景渲染主入口 (RenderLevelStageEvent.Stage.AFTER_ENTITIES)
    // =========================================================================

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        LivingEntity lockedTarget = ClientEvents.getCurrentTarget();
        boolean hasLockedTarget = (lockedTarget != null && lockedTarget.isAlive() && !lockedTarget.isRemoved());

        ItemStack weaponStack = findSupportedStack(player);
        ProjectileProfile profile = getProjectileProfile(weaponStack);
        boolean hasProjectileWeapon = (profile != null);

        AutoAttackerConfig.TrajectoryStyle style = AutoAttackerConfig.TRAJECTORY_STYLE.get();
        boolean trajectoryMasterEnabled = AutoAttackerConfig.ENABLE_TRAJECTORY_PREVIEW.get() && style != AutoAttackerConfig.TrajectoryStyle.OFF;
        boolean shouldRender3D = trajectoryMasterEnabled && (style == AutoAttackerConfig.TrajectoryStyle.BOTH || style == AutoAttackerConfig.TrajectoryStyle.PARTICLE_CHAIN);

        // 1. 物理弹道模拟与屏幕空间投影计算
        if (hasProjectileWeapon && trajectoryMasterEnabled) {
            Camera camera = event.getCamera();
            float partialTick = event.getPartialTick();
            Vec3 start = player.getEyePosition(partialTick).add(player.getViewVector(partialTick).scale(0.12D));
            Vec3 launchDirection = applyPitchOffset(player.getViewVector(partialTick), profile.pitchOffsetDegrees);

            if (launchDirection.lengthSqr() >= 1.0E-8D) {
                double currentSpeed = calculateCurrentSpeed(player, weaponStack, profile);
                double simSpeed = (currentSpeed > 0.08D) ? currentSpeed : profile.speed;

                // 纯净模拟微元轨迹
                SimResult sim = runSimulation(mc, player, start, launchDirection, simSpeed, profile.gravity, profile.drag, lockedTarget);

                // 计算屏幕空间投影
                ProjectedPoint projected = projectToScreen(camera, sim.endPoint, mc);

                // 更新共享遥测数据
                lastTrajectoryInfo.valid = true;
                lastTrajectoryInfo.impactPos = sim.endPoint;
                lastTrajectoryInfo.impactType = sim.impactType;
                lastTrajectoryInfo.hitEntity = sim.hitEntity;
                lastTrajectoryInfo.hitTarget = sim.hitTarget;
                lastTrajectoryInfo.isBlocked = sim.isBlocked;
                lastTrajectoryInfo.distance = start.distanceTo(sim.endPoint);
                lastTrajectoryInfo.screenX = projected.x;
                lastTrajectoryInfo.screenY = projected.y;
                lastTrajectoryInfo.onScreen = projected.onScreen;
                lastTrajectoryInfo.depth = projected.depth;
                lastTrajectoryInfo.isHoldingBow = profile.isBow;
                lastTrajectoryInfo.isCharged = (currentSpeed > 0.08D);
                lastTrajectoryInfo.timestamp = System.currentTimeMillis();

                // 2. 方案 B: 3D 侧向手部视差发光节点/点阵光链渲染 (双显或3D光束时生效)
                if (shouldRender3D) {
                    PoseStack poseStack = event.getPoseStack();
                    List<Vec3> visualPoints = buildParallaxTrajectoryPoints(mc, camera, player, partialTick, sim.points, weaponStack, sim.endPoint);
                    render3DParticleChain(mc, poseStack, camera, player, visualPoints, sim.endPoint, sim.impactType, sim.hitTarget, sim.isBlocked, lockedTarget);
                }
            } else {
                lastTrajectoryInfo.valid = false;
            }
        } else {
            lastTrajectoryInfo.valid = false;
        }

        // 3. 目标实体战术锁定 (方案 1: 已全面升级为 2D 屏幕空间自适应 HUD 框，清晰度 100%，彻底告别 1px 细线吞没)
        if (hasLockedTarget) {
            PoseStack poseStack = event.getPoseStack();
            Camera camera = event.getCamera();
            float partialTick = event.getPartialTick();
            boolean isBlocked = lastTrajectoryInfo.valid && lastTrajectoryInfo.isBlocked;

            // 移动靶拦截提前量导引光标
            double targetSpeed = ClientEvents.getSmoothedTargetSpeed();
            if (hasProjectileWeapon && AutoAttackerConfig.ENABLE_LEAD_INDICATOR.get() && targetSpeed >= 0.04) {
                ClientEvents.PredictedAim predicted = ClientEvents.getLastPredictedAim();
                if (predicted != null) {
                    Vec3 intercept = predicted.interceptPos;
                    Vec3 targetCenter = lockedTarget.position().add(0, lockedTarget.getBbHeight() * 0.65D, 0);
                    if (intercept.distanceTo(targetCenter) >= 0.30D) {
                        renderLeadReticle(poseStack, camera, intercept, isBlocked, partialTick);
                    }
                }
            }
        }
    }

    // =========================================================================
    // 方案 A: 2D 战术 HUD 动态弹着点光标 (RenderGuiOverlayEvent.Post)
    // =========================================================================

    public static void renderHudReticle(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        // 1. 方案 A: 2D 战术 HUD 动态弹着点准星 (仅在开启 2D准星 或 双显 时渲染)
        AutoAttackerConfig.TrajectoryStyle style = AutoAttackerConfig.TRAJECTORY_STYLE.get();
        boolean previewEnabled = AutoAttackerConfig.ENABLE_TRAJECTORY_PREVIEW.get() && style != AutoAttackerConfig.TrajectoryStyle.OFF;
        boolean shouldRender2D = previewEnabled && (style == AutoAttackerConfig.TrajectoryStyle.BOTH || style == AutoAttackerConfig.TrajectoryStyle.HUD_RETICLE);

        if (shouldRender2D && lastTrajectoryInfo.valid && lastTrajectoryInfo.onScreen && lastTrajectoryInfo.depth > 0.05D) {
            render2DImpactReticle(graphics, font, screenW, screenH, lastTrajectoryInfo);
        }

        // 2. 目标锁定战术指示 (仅保留极简头顶浮空测距与血条，分别由设置项控制开关，彻底杜绝双显重复标签)
        LivingEntity lockedTarget = ClientEvents.getCurrentTarget();
        if (lockedTarget != null && lockedTarget.isAlive() && !lockedTarget.isRemoved() && camera != null) {
            renderAdaptiveTargetLockOn2D(graphics, font, mc, lockedTarget, camera, partialTick, screenW, screenH);
        }
    }

    /**
     * 方案 A: 2D 战术 HUD 动态弹着点准星
     * 屏幕空间自适应投影，高对比度微晶折角标尺，永不被 3D 几何或杂色吞没
     */
    private static void render2DImpactReticle(GuiGraphics graphics, Font font, int screenW, int screenH, TrajectoryInfo info) {
        int cx = (int) Math.round(info.screenX);
        int cy = (int) Math.round(info.screenY);

        if (cx < -20 || cx > screenW + 20 || cy < -20 || cy > screenH + 20) return;

        int reticleColor;
        int glowColor;
        if (info.isBlocked) {
            reticleColor = 0xFFFF3333; // 红色遮挡警示
            glowColor = 0x66FF1744;
        } else if (info.hitTarget) {
            reticleColor = 0xFF00E676; // 锁定目标命中 (亮绿)
            glowColor = 0x6600E676;
        } else if (info.hitEntity != null) {
            reticleColor = 0xFFFF9100; // 普通生物命中 (橙色)
            glowColor = 0x66FF9100;
        } else {
            reticleColor = 0xFF00E5FF; // 地面落点 (电光青)
            glowColor = 0x5500E5FF;
        }

        // 中心精确落点实心像素
        graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, reticleColor);

        // 四向战术刻度标尺 (中心留出空隙，防止遮挡靶心)
        graphics.fill(cx, cy - 7, cx + 1, cy - 3, reticleColor); // 上
        graphics.fill(cx, cy + 4, cx + 1, cy + 8, reticleColor); // 下
        graphics.fill(cx - 7, cy, cx - 3, cy + 1, reticleColor); // 左
        graphics.fill(cx + 4, cy, cx + 8, cy + 1, reticleColor); // 右

        // 四角战术折角括号 (Tactical Brackets, 半径 9px)
        int r = 9;
        int cLen = 3;
        // 左上
        graphics.fill(cx - r, cy - r, cx - r + cLen, cy - r + 1, reticleColor);
        graphics.fill(cx - r, cy - r, cx - r + 1, cy - r + cLen, reticleColor);
        // 右上
        graphics.fill(cx + r - cLen + 1, cy - r, cx + r + 1, cy - r + 1, reticleColor);
        graphics.fill(cx + r, cy - r, cx + r + 1, cy - r + cLen, reticleColor);
        // 左下
        graphics.fill(cx - r, cy + r, cx - r + cLen, cy + r + 1, reticleColor);
        graphics.fill(cx - r, cy + r - cLen + 1, cx - r + 1, cy + r + 1, reticleColor);
        // 右下
        graphics.fill(cx + r - cLen + 1, cy + r, cx + r + 1, cy + r + 1, reticleColor);
        graphics.fill(cx + r, cy + r - cLen + 1, cx + r + 1, cy + r + 1, reticleColor);

        // 底部落点测距标签 (若开启距离显示或遇到阻挡/锁定)
        boolean showDist = AutoAttackerConfig.SHOW_DISTANCE.get();
        if (showDist || info.isBlocked || info.hitTarget) {
            String distStr;
            if (info.isBlocked) {
                distStr = String.format(Locale.ROOT, "! %.1fm [遮挡]", info.distance);
            } else if (info.hitTarget) {
                distStr = String.format(Locale.ROOT, "%.1fm [命中]", info.distance);
            } else {
                distStr = String.format(Locale.ROOT, "%.1fm", info.distance);
            }

            int txtW = font.width(distStr);
            int tagX = cx - txtW / 2;
            int tagY = cy + r + 4;

            if (tagY + 11 <= screenH) {
                graphics.fill(tagX - 3, tagY - 1, tagX + txtW + 3, tagY + 10, 0x88000000);
                graphics.renderOutline(tagX - 3, tagY - 1, txtW + 6, 11, glowColor);
                graphics.drawString(font, distStr, tagX, tagY, reticleColor, false);
            }
        }
    }

    /**
     * 目标锁定指示器: 极简头顶浮空测距 (3D Head Floating Tag) 与微型血条
     * 实体本身由 MinecraftMixin 原生 3D 发光轮廓高亮，彻底移除一切遮挡视野的 2D 几何框与多余双显
     */
    private static void renderAdaptiveTargetLockOn2D(GuiGraphics graphics, Font font, Minecraft mc,
                                                     LivingEntity target, Camera camera, float partialTick,
                                                     int screenW, int screenH) {
        if (target == null || !target.isAlive() || target.isRemoved() || camera == null) return;

        double tx = Mth.lerp((double) partialTick, target.xo, target.getX());
        double ty = Mth.lerp((double) partialTick, target.yo, target.getY());
        double tz = Mth.lerp((double) partialTick, target.zo, target.getZ());
        float bbH = target.getBbHeight();

        Vec3 centerPos = new Vec3(tx, ty + bbH * 0.5D, tz);
        ProjectedPoint centerProj = projectToScreen(camera, centerPos, mc);
        if (centerProj.depth <= 0.05D) {
            return;
        }

        double dist = camera.getPosition().distanceTo(centerPos);

        // 偏离视野时绘制边缘引导小箭头
        if (!centerProj.onScreen) {
            drawOffscreenIndicator(graphics, font, screenW, screenH, screenW / 2, screenH / 2,
                    centerProj.x, centerProj.y, dist);
            return;
        }

        // 视线内时: 仅在头顶自然浮空处显示微型纯净测距 (若开启测距) 与极细微血条 (若开启血条)
        boolean showDistance = AutoAttackerConfig.SHOW_DISTANCE.get();
        boolean showHealth = AutoAttackerConfig.SHOW_HEALTH_BAR.get();

        if (!showDistance && !showHealth) return;

        boolean isBlocked = lastTrajectoryInfo.valid && lastTrajectoryInfo.isBlocked;
        Vec3 headPos = new Vec3(tx, ty + bbH + 0.35D, tz);
        ProjectedPoint headProj = projectToScreen(camera, headPos, mc);

        if (headProj.onScreen && headProj.depth > 0.05D) {
            int hx = (int) Math.round(headProj.x);
            int hy = (int) Math.round(headProj.y);

            if (showDistance) {
                String distText = (isBlocked ? "! " : "") + String.format(Locale.ROOT, "%.1fm", dist);
                int txtW = font.width(distText);
                int txtColor = isBlocked ? 0xFFFF5252 : 0xFFE0E0E0;
                graphics.drawString(font, distText, hx - txtW / 2, hy - 4, txtColor, true);
            }

            if (showHealth) {
                float maxHp = target.getMaxHealth();
                if (maxHp > 0.1F) {
                    float hp = Mth.clamp(target.getHealth(), 0.0F, maxHp);
                    float hpRatio = hp / maxHp;
                    int barW = 18;
                    int barX = hx - barW / 2;
                    int barY = showDistance ? (hy + 6) : (hy - 1);
                    if (barY + 2 <= screenH - 2) {
                        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + 2, 0x88000000);
                        int fillW = (int) (barW * hpRatio);
                        int hpColor = hpRatio > 0.5F ? 0xFF00E676 : (hpRatio > 0.25F ? 0xFFFFD600 : 0xFFFF1744);
                        graphics.fill(barX, barY, barX + fillW, barY + 1, hpColor);
                    }
                }
            }
        }
    }

    private static void drawOffscreenIndicator(GuiGraphics graphics, Font font, int w, int h, int cx, int cy,
                                               double targetX, double targetY, double distance) {
        double dx = targetX - cx;
        double dy = targetY - cy;
        double angle = Math.atan2(dy, dx);

        int margin = 24;
        int clampX = (int) Mth.clamp(cx + Math.cos(angle) * (w / 2.0 - margin), margin, w - margin);
        int clampY = (int) Mth.clamp(cy + Math.sin(angle) * (h / 2.0 - margin), margin, h - margin);

        // 绘制边缘指示箭头与测距提示
        String distStr = String.format(Locale.ROOT, "%.0fm", distance);
        int txtW = font.width(distStr);

        graphics.fill(clampX - 4, clampY - 4, clampX + 5, clampY + 5, 0xDD000000);
        graphics.fill(clampX - 3, clampY - 3, clampX + 4, clampY + 4, 0xFF00E5FF);

        graphics.fill(clampX - txtW / 2 - 2, clampY + 6, clampX + txtW / 2 + 2, clampY + 16, 0xAA000000);
        graphics.drawString(font, distStr, clampX - txtW / 2, clampY + 7, 0xFFE0E0E0, false);
    }

    // =========================================================================
    // 方案 B: 3D 侧向手部视差发光节点/点阵光链渲染 (从旁边射出，中心准星100%无遮挡)
    // =========================================================================

    /**
     * 将同轴物理微元轨迹转换为从武器持手侧向射出并平滑收敛至落点的 3D 拟真视差轨迹
     */
    private static List<Vec3> buildParallaxTrajectoryPoints(Minecraft mc, Camera camera, Player player, float partialTick,
                                                            List<Vec3> physicalPoints, ItemStack weaponStack, Vec3 endPoint) {
        if (physicalPoints.size() < 2) return physicalPoints;

        boolean isFirstPerson = mc.options.getCameraType().isFirstPerson();
        Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).normalize();
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0E-6D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        boolean isRightHanded = (player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT);
        boolean usingOffhand = (player.getOffhandItem() == weaponStack);
        boolean onRightSide = usingOffhand ? !isRightHanded : isRightHanded;
        double sideSign = onRightSide ? 1.0D : -1.0D;

        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 visualOrigin;
        if (isFirstPerson) {
            // 第一人称：手部武器模型位于右下侧 (或左手在左下侧)，自然前伸，精致不遮挡视线
            visualOrigin = eyePos
                    .add(right.scale(sideSign * 0.26D))
                    .add(up.scale(-0.18D))
                    .add(forward.scale(0.32D));
        } else {
            // 第三人称：位于玩家角色持武器手臂外侧
            visualOrigin = eyePos
                    .add(right.scale(sideSign * 0.36D))
                    .add(up.scale(-0.24D))
                    .add(forward.scale(0.15D));
        }

        Vec3 physStart = physicalPoints.get(0);
        Vec3 handOffset = visualOrigin.subtract(physStart);

        double totalDistance = physStart.distanceTo(endPoint);
        double convergeDistance = Math.min(totalDistance, 10.0D);
        if (convergeDistance < 1.0E-4D) convergeDistance = 1.0D;

        List<Vec3> visualPoints = new ArrayList<>(physicalPoints.size());
        double accumulatedDist = 0.0D;
        Vec3 prevPhys = physStart;

        for (int i = 0; i < physicalPoints.size(); i++) {
            Vec3 curPhys = physicalPoints.get(i);
            accumulatedDist += curPhys.distanceTo(prevPhys);
            prevPhys = curPhys;

            if (accumulatedDist >= convergeDistance) {
                // 已完全收敛到真实物理弹道轨迹
                visualPoints.add(curPhys);
            } else {
                // 三次平滑缓出 (Cubic Ease-out)：起点平缓升起，自然顺畅汇入飞行弹道
                double u = accumulatedDist / convergeDistance;
                double factor = (1.0D - u) * (1.0D - u) * (1.0D - u);
                Vec3 offset = handOffset.scale(factor);
                visualPoints.add(curPhys.add(offset));
            }
        }

        return visualPoints;
    }

    // =========================================================================
    // 方案 B: 高精屏幕空间正交羽化光束 (Screen-Aligned Feathered Energy Beam)
    // 采用专业着色器无深度写入管线 + 屏幕对齐正交基 + 边缘渐变羽化消除一切杂色与锯齿
    // =========================================================================

    private static void render3DParticleChain(Minecraft mc, PoseStack poseStack, Camera camera, Player player,
                                              List<Vec3> points, Vec3 impactPos, ImpactType impactType,
                                              boolean hitTarget, boolean isBlocked, LivingEntity lockedTarget) {
        if (points.size() < 2) return;

        Vec3 cameraPos = camera.getPosition();

        // 1. 过滤掉距离摄像机过近的点 (防止切入近裁剪面)
        List<Vec3> validPoints = new ArrayList<>(points.size());
        for (Vec3 pt : points) {
            if (pt.distanceTo(cameraPos) >= MIN_3D_RENDER_DISTANCE) {
                validPoints.add(pt);
            }
        }
        if (validPoints.size() < 2) return;

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = poseStack.last().pose();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // 2. 摄像机屏幕视平面正交基 (Screen-Aligned View Basis)
        Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).normalize();
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 camRight = forward.cross(worldUp);
        if (camRight.lengthSqr() < 1.0E-6D) {
            camRight = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            camRight = camRight.normalize();
        }
        Vec3 camUp = camRight.cross(forward).normalize();

        int numPoints = validPoints.size();
        Vec3[] sideNormals = new Vec3[numPoints];
        double[] halfWidths = new double[numPoints];
        float[][] coreColors = new float[numPoints][3]; // r, g, b (中心高亮光芯)
        float[][] edgeColors = new float[numPoints][3]; // r, g, b (外缘羽化光晕)

        // 纯净高对比度战术调色板 (无杂色、高清晰度):
        // 开阔瞄准：中心为纯净白青芯，外缘为鲜艳赛博电光青
        // 锁定目标：中心为高贵白金芯，外缘为纯正战术炽金
        float baseCoreR = hitTarget ? 1.00F : 0.88F;
        float baseCoreG = hitTarget ? 0.98F : 0.98F;
        float baseCoreB = hitTarget ? 0.80F : 1.00F;

        float baseEdgeR = hitTarget ? 1.00F : 0.00F;
        float baseEdgeG = hitTarget ? 0.70F : 0.82F;
        float baseEdgeB = hitTarget ? 0.05F : 1.00F;

        double totalTrajectoryLen = 0.0D;
        for (int i = 0; i < numPoints - 1; i++) {
            totalTrajectoryLen += validPoints.get(i).distanceTo(validPoints.get(i + 1));
        }
        if (totalTrajectoryLen < 1.0E-4D) totalTrajectoryLen = 1.0D;

        double accumulatedDist = 0.0D;
        Vec3 prevPt = validPoints.get(0);

        for (int i = 0; i < numPoints; i++) {
            Vec3 pt = validPoints.get(i);
            accumulatedDist += pt.distanceTo(prevPt);
            prevPt = pt;

            // 轨迹切线 T
            Vec3 tangent;
            if (i == 0) {
                tangent = validPoints.get(1).subtract(pt);
            } else if (i == numPoints - 1) {
                tangent = pt.subtract(validPoints.get(numPoints - 2));
            } else {
                tangent = validPoints.get(i + 1).subtract(validPoints.get(i - 1));
            }
            if (tangent.lengthSqr() < 1.0E-8D) {
                tangent = forward;
            } else {
                tangent = tangent.normalize();
            }

            // 屏幕平面投影垂直法线：确保在任何极端视角屏幕上都拥有平滑均一宽度
            double tx = tangent.dot(camRight);
            double ty = tangent.dot(camUp);
            double len = Math.sqrt(tx * tx + ty * ty);

            Vec3 side;
            if (len < 1.0E-4D) {
                side = camRight;
            } else {
                double nx = -ty / len;
                double ny = tx / len;
                side = camRight.scale(nx).add(camUp.scale(ny)).normalize();
            }
            sideNormals[i] = side;

            double distToCam = pt.distanceTo(cameraPos);

            // 样式 A: 削细 60% 极细纯净激光丝 (带屏幕空间恒定像素保底，远距离永不消隐)
            // 手部出膛 1.5 米内平滑淡发展开，杜绝近景晃眼
            double handFade = Mth.clamp(accumulatedDist / 1.5D, 0.40D, 1.0D);
            // 屏幕空间恒定 ~1.4 像素保底：distToCam * 0.0018D
            // 近处保持物理纤细 (0.0035m)，远处自动保持 1.4px 激光线，无论 50m 还是 100m 都清晰连贯、绝不消失！
            double pixelFloor = distToCam * 0.0018D;
            halfWidths[i] = Math.max(0.0035D, pixelFloor) * handFade;

            // 末端阻挡警示渐变：受阻时在撞击点前 2.5 米内平滑过渡为猩红
            float cr = baseCoreR, cg = baseCoreG, cb = baseCoreB;
            float er = baseEdgeR, eg = baseEdgeG, eb = baseEdgeB;
            if (isBlocked) {
                double distToImpact = pt.distanceTo(impactPos);
                if (distToImpact <= 2.5D) {
                    float blend = (float) Mth.clamp(1.0D - (distToImpact / 2.5D), 0.0D, 1.0D);
                    cr = Mth.lerp(blend, baseCoreR, 1.00F);
                    cg = Mth.lerp(blend, baseCoreG, 0.35F);
                    cb = Mth.lerp(blend, baseCoreB, 0.35F);
                    er = Mth.lerp(blend, baseEdgeR, 1.00F);
                    eg = Mth.lerp(blend, baseEdgeG, 0.10F);
                    eb = Mth.lerp(blend, baseEdgeB, 0.10F);
                }
            }

            coreColors[i][0] = cr;
            coreColors[i][1] = cg;
            coreColors[i][2] = cb;

            edgeColors[i][0] = er;
            edgeColors[i][1] = eg;
            edgeColors[i][2] = eb;
        }

        // 3. 渲染样式 A 极细微光光束：削细 60% 后极度轻灵的高能激光丝 (核心半透明通透光流)
        VertexConsumer quadConsumer = bufferSource.getBuffer(ModRenderTypes.TRAJECTORY_BEAM);

        float coreAlpha = hitTarget ? 0.82F : 0.72F;
        float wingAlpha = hitTarget ? 0.32F : 0.22F;

        for (int i = 0; i < numPoints - 1; i++) {
            Vec3 p0 = validPoints.get(i);
            Vec3 p1 = validPoints.get(i + 1);
            Vec3 s0 = sideNormals[i];
            Vec3 s1 = sideNormals[i + 1];

            double wCore0 = halfWidths[i] * 0.45D;
            double wOuter0 = halfWidths[i] * 1.15D;

            double wCore1 = halfWidths[i + 1] * 0.45D;
            double wOuter1 = halfWidths[i + 1] * 1.15D;

            Vec3 lOut0 = p0.subtract(s0.scale(wOuter0));
            Vec3 lIn0 = p0.subtract(s0.scale(wCore0));
            Vec3 rIn0 = p0.add(s0.scale(wCore0));
            Vec3 rOut0 = p0.add(s0.scale(wOuter0));

            Vec3 lOut1 = p1.subtract(s1.scale(wOuter1));
            Vec3 lIn1 = p1.subtract(s1.scale(wCore1));
            Vec3 rIn1 = p1.add(s1.scale(wCore1));
            Vec3 rOut1 = p1.add(s1.scale(wOuter1));

            float cr0 = coreColors[i][0], cg0 = coreColors[i][1], cb0 = coreColors[i][2];
            float er0 = edgeColors[i][0], eg0 = edgeColors[i][1], eb0 = edgeColors[i][2];

            float cr1 = coreColors[i + 1][0], cg1 = coreColors[i + 1][1], cb1 = coreColors[i + 1][2];
            float er1 = edgeColors[i + 1][0], eg1 = edgeColors[i + 1][1], eb1 = edgeColors[i + 1][2];

            // 1. 左羽化翼：透明外缘 -> 纤细光芯
            quadConsumer.vertex(matrix, (float) lOut0.x, (float) lOut0.y, (float) lOut0.z).color(er0, eg0, eb0, 0.00F).endVertex();
            quadConsumer.vertex(matrix, (float) lIn0.x, (float) lIn0.y, (float) lIn0.z).color(cr0, cg0, cb0, wingAlpha).endVertex();
            quadConsumer.vertex(matrix, (float) lIn1.x, (float) lIn1.y, (float) lIn1.z).color(cr1, cg1, cb1, wingAlpha).endVertex();
            quadConsumer.vertex(matrix, (float) lOut1.x, (float) lOut1.y, (float) lOut1.z).color(er1, eg1, eb1, 0.00F).endVertex();

            // 2. 纤细激光芯：纯净高透高亮光芯
            quadConsumer.vertex(matrix, (float) lIn0.x, (float) lIn0.y, (float) lIn0.z).color(cr0, cg0, cb0, coreAlpha).endVertex();
            quadConsumer.vertex(matrix, (float) rIn0.x, (float) rIn0.y, (float) rIn0.z).color(cr0, cg0, cb0, coreAlpha).endVertex();
            quadConsumer.vertex(matrix, (float) rIn1.x, (float) rIn1.y, (float) rIn1.z).color(cr1, cg1, cb1, coreAlpha).endVertex();
            quadConsumer.vertex(matrix, (float) lIn1.x, (float) lIn1.y, (float) lIn1.z).color(cr1, cg1, cb1, coreAlpha).endVertex();

            // 3. 右羽化翼：纤细光芯 -> 透明外缘
            quadConsumer.vertex(matrix, (float) rIn0.x, (float) rIn0.y, (float) rIn0.z).color(cr0, cg0, cb0, wingAlpha).endVertex();
            quadConsumer.vertex(matrix, (float) rOut0.x, (float) rOut0.y, (float) rOut0.z).color(er0, eg0, eb0, 0.00F).endVertex();
            quadConsumer.vertex(matrix, (float) rOut1.x, (float) rOut1.y, (float) rOut1.z).color(er1, eg1, eb1, 0.00F).endVertex();
            quadConsumer.vertex(matrix, (float) rIn1.x, (float) rIn1.y, (float) rIn1.z).color(cr1, cg1, cb1, wingAlpha).endVertex();
        }

        bufferSource.endBatch(ModRenderTypes.TRAJECTORY_BEAM);

        // 4. 终点落点指示：双层精细战术刻度环 (仅命中方块时平铺绘制，命中实体则由实体锁框负责)
        if (impactType == ImpactType.BLOCK && impactPos.distanceTo(cameraPos) >= MIN_3D_RENDER_DISTANCE) {
            float ringR = isBlocked ? 1.00F : (hitTarget ? 1.00F : 0.00F);
            float ringG = isBlocked ? 0.25F : (hitTarget ? 0.80F : 0.90F);
            float ringB = isBlocked ? 0.20F : (hitTarget ? 0.15F : 1.00F);
            VertexConsumer lineConsumer = bufferSource.getBuffer(RenderType.lines());
            drawDualRingTacticalReticle(lineConsumer, matrix, impactPos, ringR, ringG, ringB, 0.90F);
            bufferSource.endBatch(RenderType.lines());
        }

        poseStack.popPose();
    }


    /**
     * 终点双层精细战术刻度环 (外环 + 内环 + 4向直角标线，平铺于撞击表面)
     */
    private static void drawDualRingTacticalReticle(VertexConsumer consumer, Matrix4f matrix, Vec3 center,
                                                    float r, float g, float b, float a) {
        Vec3 elevated = center.add(0, 0.02D, 0); // 微抬 0.02m 防 Z-fighting

        // 外层细环 (半径 0.30m)
        int segOuter = 20;
        float rOuter = 0.30F;
        for (int i = 0; i < segOuter; i++) {
            double a1 = i * (Math.PI * 2.0 / segOuter);
            double a2 = (i + 1) * (Math.PI * 2.0 / segOuter);
            Vec3 p1 = elevated.add(Math.cos(a1) * rOuter, 0, Math.sin(a1) * rOuter);
            Vec3 p2 = elevated.add(Math.cos(a2) * rOuter, 0, Math.sin(a2) * rOuter);
            drawLine(consumer, matrix, p1, p2, r, g, b, 0.75F * a);
        }

        // 内层微环 (半径 0.10m)
        int segInner = 12;
        float rInner = 0.10F;
        for (int i = 0; i < segInner; i++) {
            double a1 = i * (Math.PI * 2.0 / segInner);
            double a2 = (i + 1) * (Math.PI * 2.0 / segInner);
            Vec3 p1 = elevated.add(Math.cos(a1) * rInner, 0, Math.sin(a1) * rInner);
            Vec3 p2 = elevated.add(Math.cos(a2) * rInner, 0, Math.sin(a2) * rInner);
            drawLine(consumer, matrix, p1, p2, r, g, b, 0.90F * a);
        }

        // 四向战术外伸刻度线 (North, South, East, West)
        float tickStart = 0.30F;
        float tickEnd = 0.36F;
        drawLine(consumer, matrix, elevated.add(tickStart, 0, 0), elevated.add(tickEnd, 0, 0), r, g, b, a);
        drawLine(consumer, matrix, elevated.add(-tickEnd, 0, 0), elevated.add(-tickStart, 0, 0), r, g, b, a);
        drawLine(consumer, matrix, elevated.add(0, 0, tickStart), elevated.add(0, 0, tickEnd), r, g, b, a);
        drawLine(consumer, matrix, elevated.add(0, 0, -tickEnd), elevated.add(0, 0, -tickStart), r, g, b, a);
    }

    private static void drawLine(VertexConsumer consumer, Matrix4f matrix, Vec3 from, Vec3 to,
                                 float r, float g, float b, float a) {
        Vec3 normal = to.subtract(from);
        if (normal.lengthSqr() < 1.0E-8D) {
            normal = new Vec3(0, 1, 0);
        } else {
            normal = normal.normalize();
        }
        consumer.vertex(matrix, (float) from.x, (float) from.y, (float) from.z)
                .color(r, g, b, a).normal((float) normal.x, (float) normal.y, (float) normal.z).endVertex();
        consumer.vertex(matrix, (float) to.x, (float) to.y, (float) to.z)
                .color(r, g, b, a).normal((float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }

    // =========================================================================
    // 物理微元模拟引擎 (纯净独立物理计算)
    // =========================================================================

    private static SimResult runSimulation(Minecraft mc, Player player, Vec3 start, Vec3 launchDirection,
                                           double speed, double gravity, double drag, LivingEntity lockedTarget) {
        Vec3 velocity = launchDirection.normalize().scale(speed);
        double maxLength = MAX_PREVIEW_DISTANCE;
        Vec3 position = start;
        double traveled = 0.0D;
        ImpactType impactType = ImpactType.RANGE_END;
        LivingEntity hitEntity = null;
        boolean hitTarget = false;

        List<Vec3> points = new ArrayList<>();
        points.add(start);

        double substepDrag = Math.pow(drag, SUBSTEP);

        for (int step = 0; step < MAX_STEPS && traveled < maxLength; step++) {
            Vec3 proposed = position.add(velocity.scale(SUBSTEP));
            double remaining = maxLength - traveled;
            double proposedDistance = position.distanceTo(proposed);
            if (proposedDistance > remaining && proposedDistance > 1.0E-8D) {
                proposed = position.add(proposed.subtract(position).scale(remaining / proposedDistance));
            }

            SegmentImpact impact = findNearestImpact(mc, player, position, proposed, lockedTarget);
            Vec3 next = (impact == null) ? proposed : impact.location;

            points.add(next);
            traveled += position.distanceTo(next);
            position = next;

            if (impact != null) {
                impactType = impact.type;
                hitEntity = impact.hitEntity;
                hitTarget = impact.hitTarget;
                break;
            }
            if (traveled >= maxLength - 1.0E-6D) break;

            velocity = velocity.scale(substepDrag).add(0.0D, -gravity * SUBSTEP, 0.0D);
        }

        boolean isBlocked = (impactType == ImpactType.BLOCK);
        return new SimResult(points, position, impactType, hitEntity, hitTarget, isBlocked);
    }

    private static SegmentImpact findNearestImpact(Minecraft mc, Player player, Vec3 from, Vec3 to, LivingEntity lockedTarget) {
        HitResult blockHit = mc.level.clip(new ClipContext(
                from,
                to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
        ));

        Vec3 nearestLocation = null;
        ImpactType nearestType = null;
        LivingEntity nearestEntity = null;
        boolean hitTarget = false;
        double nearestDistanceSqr = Double.POSITIVE_INFINITY;

        if (blockHit.getType() != HitResult.Type.MISS) {
            nearestLocation = blockHit.getLocation();
            nearestType = ImpactType.BLOCK;
            nearestDistanceSqr = from.distanceToSqr(nearestLocation);
        }

        AABB searchBox = new AABB(
                Math.min(from.x, to.x),
                Math.min(from.y, to.y),
                Math.min(from.z, to.z),
                Math.max(from.x, to.x),
                Math.max(from.y, to.y),
                Math.max(from.z, to.z)
        ).inflate(ENTITY_HIT_PADDING + 0.25D);

        // 优先检测锁定目标
        if (lockedTarget != null && !lockedTarget.isRemoved() && lockedTarget.isAlive()) {
            Optional<Vec3> clipped = lockedTarget.getBoundingBox().inflate(ENTITY_HIT_PADDING).clip(from, to);
            if (clipped.isPresent()) {
                Vec3 location = clipped.get();
                double distanceSqr = from.distanceToSqr(location);
                if (distanceSqr < nearestDistanceSqr) {
                    nearestLocation = location;
                    nearestType = ImpactType.ENTITY;
                    nearestEntity = lockedTarget;
                    nearestDistanceSqr = distanceSqr;
                    hitTarget = true;
                }
            }
        }

        // 检测其它生物碰撞
        LivingEntity finalLockedTarget = lockedTarget;
        List<Entity> entities = mc.level.getEntities(player, searchBox, entity ->
                entity != player
                        && entity != player.getVehicle()
                        && entity != finalLockedTarget
                        && !entity.isSpectator()
                        && entity.isPickable()
                        && entity instanceof LivingEntity
        );

        for (Entity entity : entities) {
            Optional<Vec3> clipped = entity.getBoundingBox().inflate(ENTITY_HIT_PADDING).clip(from, to);
            if (clipped.isEmpty()) continue;

            Vec3 location = clipped.get();
            double distanceSqr = from.distanceToSqr(location);
            if (distanceSqr < nearestDistanceSqr) {
                nearestLocation = location;
                nearestType = ImpactType.ENTITY;
                nearestEntity = (LivingEntity) entity;
                nearestDistanceSqr = distanceSqr;
                hitTarget = false;
            }
        }

        return nearestLocation == null ? null : new SegmentImpact(nearestLocation, nearestType, nearestEntity, hitTarget);
    }

    // =========================================================================
    // 3D 摄像机到 2D 屏幕精准几何投影 (对标 camera-lock-on 原生算法)
    // =========================================================================

    public static ProjectedPoint projectToScreen(Camera camera, Vec3 point, Minecraft mc) {
        Vec3 delta = point.subtract(camera.getPosition());
        Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot()).normalize();
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1.0E-8D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(forward).normalize();

        double depth = delta.dot(forward);
        if (depth <= 0.05D) {
            return new ProjectedPoint(0, 0, false, depth);
        }

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        double fov = Mth.clamp(mc.options.fov().get(), 30, 110);
        double focal = (height * 0.5D) / Math.tan(Math.toRadians(fov * 0.5D));
        double x = width * 0.5D + delta.dot(right) * focal / depth;
        double y = height * 0.5D - delta.dot(up) * focal / depth;

        boolean onScreen = (x >= 0 && x <= width && y >= 0 && y <= height);
        return new ProjectedPoint(x, y, onScreen, depth);
    }

    // =========================================================================
    // 3D 战术锁定折角框 (Billboard 渲染在目标实体胸口)
    // =========================================================================

    private static void renderTargetReticle(PoseStack poseStack, Camera camera,
                                            LivingEntity target, boolean isBlocked, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        double tx = Mth.lerp((double) partialTick, target.xo, target.getX());
        double ty = Mth.lerp((double) partialTick, target.yo, target.getY()) + target.getBbHeight() * 0.65D;
        double tz = Mth.lerp((double) partialTick, target.zo, target.getZ());

        Vec3 targetPos = new Vec3(tx, ty, tz);
        Vec3 relative = targetPos.subtract(camera.getPosition());
        double distance = relative.length();
        if (distance > MAX_PREVIEW_DISTANCE) return;

        float r = 1.00F;
        float g = isBlocked ? 0.22F : 0.78F;
        float b = isBlocked ? 0.18F : 0.12F;
        float a = 0.95F;

        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(camera.rotation());

        float scale = Mth.clamp((float) (distance * 0.05D), 0.15F, 1.5F);
        poseStack.scale(scale, scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        double time = (mc.level.getGameTime() + partialTick) * 0.05D;
        drawReticle(consumer, matrix, (float) time, r, g, b, a);

        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static void renderLeadReticle(PoseStack poseStack, Camera camera,
                                          Vec3 interceptPos, boolean isBlocked, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 relative = interceptPos.subtract(camera.getPosition());
        double distance = relative.length();
        if (distance > MAX_PREVIEW_DISTANCE) return;

        float r = 1.00F;
        float g = isBlocked ? 0.22F : 0.78F;
        float b = isBlocked ? 0.18F : 0.12F;
        float a = 0.88F;

        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(camera.rotation());

        float scale = Mth.clamp((float) (distance * 0.05D), 0.15F, 1.5F);
        poseStack.scale(scale, scale, scale);

        Matrix4f matrix = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        double time = (mc.level.getGameTime() + partialTick) * 0.05D;
        drawLeadReticle(consumer, matrix, (float) time, r, g, b, a);

        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    private static void drawReticle(VertexConsumer consumer, Matrix4f matrix, float theta,
                                    float red, float green, float blue, float alpha) {
        float distance = 0.25F;
        float arm = 0.08F;
        float cosine = Mth.cos(theta);
        float sine = Mth.sin(theta);
        float[][] corners = {
                {distance, distance},
                {-distance, distance},
                {-distance, -distance},
                {distance, -distance}
        };

        for (float[] corner : corners) {
            float cornerX = corner[0];
            float cornerY = corner[1];
            float rotatedX = cornerX * cosine - cornerY * sine;
            float rotatedY = cornerX * sine + cornerY * cosine;
            float horizontalDirection = cornerX > 0.0F ? -arm : arm;
            float verticalDirection = cornerY > 0.0F ? -arm : arm;

            // 黑色阴影背衬线
            drawReticleLine(consumer, matrix, rotatedX, rotatedY,
                    rotatedX + horizontalDirection * cosine,
                    rotatedY + horizontalDirection * sine,
                    0.05F, 0.05F, 0.05F, 0.65F * alpha);
            drawReticleLine(consumer, matrix, rotatedX, rotatedY,
                    rotatedX - verticalDirection * sine,
                    rotatedY + verticalDirection * cosine,
                    0.05F, 0.05F, 0.05F, 0.65F * alpha);

            // 战术亮线
            drawReticleLine(consumer, matrix, rotatedX, rotatedY,
                    rotatedX + horizontalDirection * cosine,
                    rotatedY + horizontalDirection * sine,
                    red, green, blue, alpha);
            drawReticleLine(consumer, matrix, rotatedX, rotatedY,
                    rotatedX - verticalDirection * sine,
                    rotatedY + verticalDirection * cosine,
                    red, green, blue, alpha);
        }

        // 中心战术菱形点
        float diamond = 0.04F;
        drawReticleLine(consumer, matrix, 0.0F, diamond, diamond, 0.0F, red, green, blue, alpha);
        drawReticleLine(consumer, matrix, diamond, 0.0F, 0.0F, -diamond, red, green, blue, alpha);
        drawReticleLine(consumer, matrix, 0.0F, -diamond, -diamond, 0.0F, red, green, blue, alpha);
        drawReticleLine(consumer, matrix, -diamond, 0.0F, 0.0F, diamond, red, green, blue, alpha);
    }

    private static void drawLeadReticle(VertexConsumer consumer, Matrix4f matrix, float theta,
                                        float red, float green, float blue, float alpha) {
        float radius = 0.16F;
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            float angle1 = (float) (i * 2.0 * Math.PI / segments) + theta;
            float angle2 = (float) ((i + 1) * 2.0 * Math.PI / segments) + theta;
            float rx1 = Mth.cos(angle1) * radius;
            float ry1 = Mth.sin(angle1) * radius;
            float rx2 = Mth.cos(angle2) * radius;
            float ry2 = Mth.sin(angle2) * radius;
            drawReticleLine(consumer, matrix, rx1, ry1, rx2, ry2, red, green, blue, alpha);
        }

        float ch = 0.05F;
        drawReticleLine(consumer, matrix, -ch, 0.0F, ch, 0.0F, red, green, blue, alpha);
        drawReticleLine(consumer, matrix, 0.0F, -ch, 0.0F, ch, red, green, blue, alpha);
    }

    private static void drawReticleLine(VertexConsumer consumer, Matrix4f matrix,
                                        float x1, float y1, float x2, float y2,
                                        float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, x1, y1, 0.0F)
                .color(red, green, blue, alpha)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
        consumer.vertex(matrix, x2, y2, 0.0F)
                .color(red, green, blue, alpha)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    // =========================================================================
    // 武器弹道配置解析
    // =========================================================================

    private static ItemStack findSupportedStack(Player player) {
        // 严格仅在手持远程武器 (弓、弩) 时激活弹道预测
        ItemStack main = player.getMainHandItem();
        if (ClientEvents.isBow(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (ClientEvents.isBow(off)) {
            return off;
        }
        if (player.isUsingItem() && ClientEvents.isBow(player.getUseItem())) {
            return player.getUseItem();
        }
        return ItemStack.EMPTY;
    }

    private static ProjectileProfile getProjectileProfile(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        // 仅当确认是弓或弩时才返回弹道数据，彻底杜绝手持近战武器（如剑/斧）时误显抛物线
        if (ClientEvents.isBow(stack)) {
            AutoBallisticsTracker.BallisticsProfile learned = AutoBallisticsTracker.getProfile(stack);
            if (learned != null) {
                return new ProjectileProfile(learned.speed, learned.gravity, learned.drag, 0.0D, !(stack.getItem() instanceof CrossbowItem));
            }
            if (stack.is(Items.CROSSBOW)) {
                return new ProjectileProfile(3.15D, 0.05D, 0.99D, 0.0D, false);
            }
            return new ProjectileProfile(3.0D, 0.05D, 0.99D, 0.0D, true);
        }

        return null;
    }

    private static double calculateCurrentSpeed(Player player, ItemStack stack, ProjectileProfile profile) {
        if (profile.isBow) {
            if (!player.isUsingItem() || player.getUseItem() != stack) {
                return 0.0D;
            }
            int useDuration = player.getTicksUsingItem();
            float power = BowItem.getPowerForTime(useDuration);
            return power * profile.speed;
        }

        if (stack.getItem() instanceof CrossbowItem) {
            return CrossbowItem.isCharged(stack) ? profile.speed : 0.0D;
        }

        if (player.isUsingItem() && player.getUseItem() == stack) {
            return profile.speed;
        }

        return profile.speed;
    }

    private static Vec3 applyPitchOffset(Vec3 direction, double pitchOffsetDegrees) {
        if (direction.lengthSqr() < 1.0E-8D) return Vec3.ZERO;
        Vec3 normalized = direction.normalize();
        if (Math.abs(pitchOffsetDegrees) < 1.0E-6D) return normalized;

        double horizontalLength = Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z);
        if (horizontalLength < 1.0E-6D) return normalized;

        Vec3 horizontal = new Vec3(normalized.x / horizontalLength, 0.0D, normalized.z / horizontalLength);
        double pitch = Math.atan2(-normalized.y, horizontalLength) + Math.toRadians(pitchOffsetDegrees);
        double horizontalScale = Math.cos(pitch);
        return horizontal.scale(horizontalScale).add(0.0D, -Math.sin(pitch), 0.0D).normalize();
    }

    // =========================================================================
    // 内部数据类
    // =========================================================================

    public static final class ProjectedPoint {
        public final double x;
        public final double y;
        public final boolean onScreen;
        public final double depth;

        public ProjectedPoint(double x, double y, boolean onScreen, double depth) {
            this.x = x;
            this.y = y;
            this.onScreen = onScreen;
            this.depth = depth;
        }
    }

    public static final class TrajectoryInfo {
        public boolean valid = false;
        public Vec3 impactPos = Vec3.ZERO;
        public ImpactType impactType = ImpactType.NONE;
        public LivingEntity hitEntity = null;
        public boolean hitTarget = false;
        public boolean isBlocked = false;
        public double distance = 0.0D;
        public double screenX = 0.0D;
        public double screenY = 0.0D;
        public boolean onScreen = false;
        public double depth = 0.0D;
        public boolean isHoldingBow = false;
        public boolean isCharged = false;
        public long timestamp = 0L;
    }

    private record ProjectileProfile(double speed, double gravity, double drag, double pitchOffsetDegrees, boolean isBow) {}

    public enum ImpactType {
        NONE,
        BLOCK,
        ENTITY,
        RANGE_END
    }

    private static final class SegmentImpact {
        private final Vec3 location;
        private final ImpactType type;
        private final LivingEntity hitEntity;
        private final boolean hitTarget;

        private SegmentImpact(Vec3 location, ImpactType type, LivingEntity hitEntity, boolean hitTarget) {
            this.location = location;
            this.type = type;
            this.hitEntity = hitEntity;
            this.hitTarget = hitTarget;
        }
    }

    private static final class SimResult {
        private final List<Vec3> points;
        private final Vec3 endPoint;
        private final ImpactType impactType;
        private final LivingEntity hitEntity;
        private final boolean hitTarget;
        private final boolean isBlocked;

        private SimResult(List<Vec3> points, Vec3 endPoint, ImpactType impactType, LivingEntity hitEntity, boolean hitTarget, boolean isBlocked) {
            this.points = points;
            this.endPoint = endPoint;
            this.impactType = impactType;
            this.hitEntity = hitEntity;
            this.hitTarget = hitTarget;
            this.isBlocked = isBlocked;
        }
    }
}