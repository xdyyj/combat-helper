package com.xdyyj.autoattacker.compat;

import net.minecraft.world.phys.Vec3;

/**
 * 越肩视角 (Shoulder Surfing Reloaded) 与通用第三人称视角自瞄相机门面 (Facade)
 * 
 * 保持完全向下兼容：所有调用直接转发至统一的 {@link ThirdPersonCompat}，
 * 自动统一适配 Shoulder Surfing Reloaded 与 Leawind's Third Person。
 */
public final class ShoulderSurfingCompat {
    private ShoulderSurfingCompat() {}

    /**
     * 中和旧版枪械模组 (JEG / Scorched Guns 等) 中已废弃的 ShoulderSurfing 4.x 反射调用，
     * 避免其在 1.20.1 + ShoulderSurfing 5.x 环境下触发 NoClassDefFoundError 导致游戏崩溃闪退。
     */
    public static void neutralizeLegacyShoulderSurfingIntegrations() {
        ThirdPersonCompat.neutralizeLegacyShoulderSurfingIntegrations();
    }

    /**
     * 检测越肩/第三人称模组是否安装就绪
     */
    public static boolean isInstalled() {
        return ThirdPersonCompat.isInstalled();
    }

    /**
     * 检测玩家当前是否正处于越肩/第三人称视角模式
     */
    public static boolean isShoulderSurfing() {
        return ThirdPersonCompat.isThirdPerson();
    }

    /**
     * 检测玩家当前是否正在按住自由观察/调镜键 (Free Look / Camera Adjustment)
     */
    public static boolean isFreeLooking() {
        return ThirdPersonCompat.isFreeLooking();
    }

    /**
     * 获取第三人称相机当前偏航角 Yaw
     */
    public static float getCameraYaw() {
        return ThirdPersonCompat.getCameraYaw();
    }

    /**
     * 获取第三人称相机当前俯仰角 Pitch
     */
    public static float getCameraPitch() {
        return ThirdPersonCompat.getCameraPitch();
    }

    /**
     * 同步设置第三人称相机的角度 (驱动屏幕准星精准对齐目标)
     */
    public static void setCameraRotation(float yaw, float pitch) {
        ThirdPersonCompat.setCameraRotation(yaw, pitch);
    }

    /**
     * 同步设置玩家物理实体朝向与网络射击向量 (驱动子弹/箭矢从玩家视线精准射向目标)
     */
    public static void syncPlayerRotation(float playerYaw, float playerPitch) {
        ThirdPersonCompat.syncPlayerRotation(playerYaw, playerPitch);
    }

    /**
     * 获取第三人称相机的空间坐标位置
     */
    public static Vec3 getCameraPosition() {
        return ThirdPersonCompat.getCameraPosition();
    }

    /**
     * 获取第三人称相机的朝向单位向量 (即屏幕正中央射线)
     */
    public static Vec3 getCameraLookVector() {
        return ThirdPersonCompat.getCameraLookVector();
    }
}
