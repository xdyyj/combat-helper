package com.xdyyj.autoattacker.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * 越肩视角 (Shoulder Surfing Reloaded) 无缝物理与自瞄相机适配器
 * 
 * 1. 零硬依赖：完全通过轻量反射与动态 MethodHandle 绑定，未安装该模组时安全旁路零开销。
 * 2. 视角同步：当处于越肩视角时，将角速度追踪与连续阻尼后坐力反冲精准同步写入 ShoulderSurfingCamera (setYRot/setXRot)，
 *    彻底杜绝原版 player.setYRot 在越肩模式下被相机回写覆盖导致的自瞄/压枪失效与剧烈晃动。
 * 3. 视距视差消除：准星与目标优选自适应对齐屏幕中心越肩相机射线，消除侧向视差偏移。
 */
public final class ShoulderSurfingCompat {
    private static final String MOD_ID = "shouldersurfing";
    private static boolean initialized = false;
    private static boolean isLoaded = false;

    private static Object shoulderSurfingInstance = null;
    private static MethodHandle isShoulderSurfingHandle = null;
    private static MethodHandle isFreeLookingHandle = null;
    private static MethodHandle getCameraHandle = null;
    private static MethodHandle getXRotHandle = null;
    private static MethodHandle getYRotHandle = null;
    private static MethodHandle setXRotHandle = null;
    private static MethodHandle setYRotHandle = null;

    private ShoulderSurfingCompat() {}

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            if (ModList.get() != null && ModList.get().isLoaded(MOD_ID)) {
                Class<?> ssClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing");
                Method getInstanceMethod = ssClass.getMethod("getInstance");
                shoulderSurfingInstance = getInstanceMethod.invoke(null);
                if (shoulderSurfingInstance != null) {
                    Class<?> issClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing");
                    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

                    Method mIsShoulderSurfing = issClass.getMethod("isShoulderSurfing");
                    mIsShoulderSurfing.setAccessible(true);
                    isShoulderSurfingHandle = lookup.unreflect(mIsShoulderSurfing);

                    try {
                        Method mIsFreeLooking = issClass.getMethod("isFreeLooking");
                        mIsFreeLooking.setAccessible(true);
                        isFreeLookingHandle = lookup.unreflect(mIsFreeLooking);
                    } catch (Throwable ignored) {}

                    Method mGetCamera = issClass.getMethod("getCamera");
                    mGetCamera.setAccessible(true);
                    getCameraHandle = lookup.unreflect(mGetCamera);

                    Class<?> camClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.IShoulderSurfingCamera");
                    Method mGetXRot = camClass.getMethod("getXRot");
                    mGetXRot.setAccessible(true);
                    getXRotHandle = lookup.unreflect(mGetXRot);

                    Method mGetYRot = camClass.getMethod("getYRot");
                    mGetYRot.setAccessible(true);
                    getYRotHandle = lookup.unreflect(mGetYRot);

                    Method mSetXRot = camClass.getMethod("setXRot", float.class);
                    mSetXRot.setAccessible(true);
                    setXRotHandle = lookup.unreflect(mSetXRot);

                    Method mSetYRot = camClass.getMethod("setYRot", float.class);
                    mSetYRot.setAccessible(true);
                    setYRotHandle = lookup.unreflect(mSetYRot);

                    isLoaded = true;
                }
            }
        } catch (Throwable t) {
            isLoaded = false;
        }
    }

    /**
     * 检测越肩视角模组是否安装就绪
     */
    public static boolean isInstalled() {
        if (!initialized) init();
        return isLoaded;
    }

    /**
     * 检测玩家当前是否正处于越肩第三人称视角模式
     */
    public static boolean isShoulderSurfing() {
        if (!isInstalled() || isShoulderSurfingHandle == null || shoulderSurfingInstance == null) {
            return false;
        }
        try {
            return (boolean) isShoulderSurfingHandle.invoke(shoulderSurfingInstance);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 检测玩家当前是否正在按住自由观察键 (Free Look)
     */
    public static boolean isFreeLooking() {
        if (!isInstalled() || isFreeLookingHandle == null || shoulderSurfingInstance == null) {
            return false;
        }
        try {
            return (boolean) isFreeLookingHandle.invoke(shoulderSurfingInstance);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 获取越肩相机当前偏航角 Yaw
     */
    public static float getCameraYaw() {
        if (!isShoulderSurfing() || getCameraHandle == null || getYRotHandle == null) {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null ? mc.player.getYRot() : 0.0f;
        }
        try {
            Object camera = getCameraHandle.invoke(shoulderSurfingInstance);
            if (camera != null) {
                return (float) getYRotHandle.invoke(camera);
            }
        } catch (Throwable ignored) {}
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getYRot() : 0.0f;
    }

    /**
     * 获取越肩相机当前俯仰角 Pitch
     */
    public static float getCameraPitch() {
        if (!isShoulderSurfing() || getCameraHandle == null || getXRotHandle == null) {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null ? mc.player.getXRot() : 0.0f;
        }
        try {
            Object camera = getCameraHandle.invoke(shoulderSurfingInstance);
            if (camera != null) {
                return (float) getXRotHandle.invoke(camera);
            }
        } catch (Throwable ignored) {}
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getXRot() : 0.0f;
    }

    /**
     * 同步设置越肩相机的角度 (驱动第三人称屏幕准星直接旋转对齐目标)
     */
    public static void setCameraRotation(float yaw, float pitch) {
        if (!isShoulderSurfing() || getCameraHandle == null) return;
        try {
            Object camera = getCameraHandle.invoke(shoulderSurfingInstance);
            if (camera != null) {
                if (setYRotHandle != null) setYRotHandle.invoke(camera, yaw);
                if (setXRotHandle != null) setXRotHandle.invoke(camera, pitch);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 获取越肩相机的空间坐标位置
     */
    public static Vec3 getCameraPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            if (camera != null) {
                return camera.getPosition();
            }
        }
        return (mc.player != null) ? mc.player.getEyePosition() : Vec3.ZERO;
    }

    /**
     * 获取越肩相机的朝向单位向量 (即屏幕正中央射线)
     */
    public static Vec3 getCameraLookVector() {
        return Vec3.directionFromRotation(getCameraPitch(), getCameraYaw());
    }
}
