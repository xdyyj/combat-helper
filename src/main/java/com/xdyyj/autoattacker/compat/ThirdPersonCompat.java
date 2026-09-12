package com.xdyyj.autoattacker.compat;

import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 统一第三人称视角自瞄与物理相机适配器 (Unified Third-Person Camera & Aim Adapter)
 * 
 * 现已全面兼容适配：
 * 1. 越肩视角重制版 (Shoulder Surfing Reloaded - shouldersurfing)
 * 2. Leawind 的第三人称 (Leawind's Third Person - leawind_third_person)
 * 
 * 特性：
 * - 零硬依赖：完全通过轻量反射与动态 MethodHandle 绑定，未安装对应模组时安全旁路零开销。
 * - 统一相机控制：自适应读取与设置相机角度 (Yaw/Pitch)，解决第三人称自瞄准星对齐与视差偏移问题。
 * - 自由观察/调镜保护：当处于 ShoulderSurfing Free-Look 或 Leawind ADJUST_CAMERA 调镜状态时自动避让自瞄。
 * - 准星与瞄准态唤醒：在锁定敌人时激活 Leawind Aiming 态确保准星正常渲染且镜头居中，失锁后自动平滑复位。
 */
public final class ThirdPersonCompat {
    private static final String MOD_SHOULDER_SURFING = "shouldersurfing";
    private static final String MOD_LEAWIND = "leawind_third_person";

    private static boolean initialized = false;

    // --- Shoulder Surfing 反射绑定 ---
    private static boolean shoulderLoaded = false;
    private static Object shoulderSurfingInstance = null;
    private static MethodHandle ssIsShoulderSurfingHandle = null;
    private static MethodHandle ssIsFreeLookingHandle = null;
    private static MethodHandle ssGetCameraHandle = null;
    private static MethodHandle ssGetXRotHandle = null;
    private static MethodHandle ssGetYRotHandle = null;
    private static MethodHandle ssSetXRotHandle = null;
    private static MethodHandle ssSetYRotHandle = null;

    // --- Leawind's Third Person 反射绑定 ---
    private static boolean leawindLoaded = false;
    private static MethodHandle lwIsThirdPersonHandle = null;
    private static KeyMapping lwAdjustCameraKey = null;
    private static MethodHandle lwGetBaseRuntimeHandle = null;
    private static MethodHandle lwBaseSessionHandle = null;
    private static MethodHandle lwLookControllerHandle = null;
    private static MethodHandle lwLookIsInitializedHandle = null;
    private static MethodHandle lwLookYawDegreesHandle = null;
    private static MethodHandle lwLookPitchDegreesHandle = null;
    private static MethodHandle lwLookInitializeHandle = null; // (float pitch, float yaw)
    private static MethodHandle lwLookRotationCtor = null;      // (float yaw, float pitch)
    private static MethodHandle lwCommitInteractionRotationHandle = null;
    private static MethodHandle lwGetSchedulerRuntimeHandle = null;
    private static MethodHandle lwSchedulerSessionHandle = null;
    private static MethodHandle lwSetAimingHandle = null;
    private static MethodHandle lwIsAimingHandle = null;
    private static MethodHandle lwCameraAdjustmentControllerHandle = null;
    private static MethodHandle lwIsAdjustingHandle = null;

    private static volatile boolean wasLeawindAimingForced = false;

    private ThirdPersonCompat() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        initShoulderSurfing();
        initLeawind();
        neutralizeLegacyShoulderSurfingIntegrations();
    }

    private static void initShoulderSurfing() {
        try {
            boolean loaded = false;
            try {
                if (ModList.get() != null && ModList.get().isLoaded(MOD_SHOULDER_SURFING)) {
                    loaded = true;
                }
            } catch (Throwable ignored) {}

            if (!loaded) {
                try {
                    Class.forName("com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing");
                    loaded = true;
                } catch (Throwable ignored) {}
            }

            if (loaded) {
                Class<?> ssClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.ShoulderSurfing");
                Method getInstanceMethod = ssClass.getMethod("getInstance");
                shoulderSurfingInstance = getInstanceMethod.invoke(null);
                if (shoulderSurfingInstance != null) {
                    Class<?> issClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.IShoulderSurfing");
                    MethodHandles.Lookup lookup = MethodHandles.publicLookup();

                    Method mIsShoulderSurfing = issClass.getMethod("isShoulderSurfing");
                    mIsShoulderSurfing.setAccessible(true);
                    ssIsShoulderSurfingHandle = lookup.unreflect(mIsShoulderSurfing);

                    try {
                        Method mIsFreeLooking = issClass.getMethod("isFreeLooking");
                        mIsFreeLooking.setAccessible(true);
                        ssIsFreeLookingHandle = lookup.unreflect(mIsFreeLooking);
                    } catch (Throwable ignored) {}

                    Method mGetCamera = issClass.getMethod("getCamera");
                    mGetCamera.setAccessible(true);
                    ssGetCameraHandle = lookup.unreflect(mGetCamera);

                    Class<?> camClass = Class.forName("com.github.exopandora.shouldersurfing.api.client.IShoulderSurfingCamera");
                    Method mGetXRot = camClass.getMethod("getXRot");
                    mGetXRot.setAccessible(true);
                    ssGetXRotHandle = lookup.unreflect(mGetXRot);

                    Method mGetYRot = camClass.getMethod("getYRot");
                    mGetYRot.setAccessible(true);
                    ssGetYRotHandle = lookup.unreflect(mGetYRot);

                    Method mSetXRot = camClass.getMethod("setXRot", float.class);
                    mSetXRot.setAccessible(true);
                    ssSetXRotHandle = lookup.unreflect(mSetXRot);

                    Method mSetYRot = camClass.getMethod("setYRot", float.class);
                    mSetYRot.setAccessible(true);
                    ssSetYRotHandle = lookup.unreflect(mSetYRot);

                    shoulderLoaded = true;
                }
            }
        } catch (Throwable ignored) {
            shoulderLoaded = false;
        }
    }

    private static void initLeawind() {
        try {
            boolean loaded = false;
            try {
                if (ModList.get() != null && ModList.get().isLoaded(MOD_LEAWIND)) {
                    loaded = true;
                }
            } catch (Throwable ignored) {}

            if (!loaded) {
                try {
                    Class.forName("io.github.leawind.thirdperson.internal.logic.base.PerspectiveGuard");
                    loaded = true;
                } catch (Throwable ignored) {}
            }

            if (loaded) {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();

                // 1. 视角状态判定
                Class<?> guardClass = Class.forName("io.github.leawind.thirdperson.internal.logic.base.PerspectiveGuard");
                Method mIsThirdPerson = guardClass.getMethod("isThirdPersonCurrentForLocalPlayer");
                mIsThirdPerson.setAccessible(true);
                lwIsThirdPersonHandle = lookup.unreflect(mIsThirdPerson);

                // 2. 调镜热键 (ADJUST_CAMERA)
                try {
                    Class<?> keysClass = Class.forName("io.github.leawind.thirdperson.internal.logic.scheduler.input.ThirdPersonKeyMappings");
                    Field fAdjust = keysClass.getField("ADJUST_CAMERA");
                    Object keyObj = fAdjust.get(null);
                    if (keyObj instanceof KeyMapping km) {
                        lwAdjustCameraKey = km;
                    }
                } catch (Throwable ignored) {}

                // 3. BaseRuntime 与 LookController 相机驱动
                Class<?> baseRuntimeClass = Class.forName("io.github.leawind.thirdperson.internal.logic.base.BaseRuntime");
                Method mGetBaseRuntime = baseRuntimeClass.getMethod("getInstance");
                lwGetBaseRuntimeHandle = lookup.unreflect(mGetBaseRuntime);

                Class<?> baseSessionClass = Class.forName("io.github.leawind.thirdperson.internal.logic.base.BaseSession");
                Method mBaseSession = baseRuntimeClass.getMethod("session");
                lwBaseSessionHandle = lookup.unreflect(mBaseSession);

                Method mLookController = baseSessionClass.getMethod("lookController");
                lwLookControllerHandle = lookup.unreflect(mLookController);

                Class<?> lookCtrlClass = Class.forName("io.github.leawind.thirdperson.internal.logic.base.rotation.LookController");
                Method mIsInit = lookCtrlClass.getMethod("isInitialized");
                lwLookIsInitializedHandle = lookup.unreflect(mIsInit);

                Method mYawDeg = lookCtrlClass.getMethod("yawDegrees");
                lwLookYawDegreesHandle = lookup.unreflect(mYawDeg);

                Method mPitchDeg = lookCtrlClass.getMethod("pitchDegrees");
                lwLookPitchDegreesHandle = lookup.unreflect(mPitchDeg);

                // initialize(float pitch, float yaw)
                Method mInitLook = lookCtrlClass.getMethod("initialize", float.class, float.class);
                lwLookInitializeHandle = lookup.unreflect(mInitLook);

                // LookRotation(float yaw, float pitch)
                Class<?> lookRotClass = Class.forName("io.github.leawind.thirdperson.internal.logic.base.rotation.LookRotation");
                Constructor<?> ctorLookRot = lookRotClass.getConstructor(float.class, float.class);
                lwLookRotationCtor = lookup.unreflectConstructor(ctorLookRot);

                // commitInteractionRotation(LookRotation)
                Method mCommit = baseRuntimeClass.getMethod("commitInteractionRotation", lookRotClass);
                lwCommitInteractionRotationHandle = lookup.unreflect(mCommit);

                // 4. SchedulerRuntime 与瞄准模式控制
                Class<?> schedRuntimeClass = Class.forName("io.github.leawind.thirdperson.internal.logic.scheduler.SchedulerRuntime");
                Method mGetSched = schedRuntimeClass.getMethod("getInstance");
                lwGetSchedulerRuntimeHandle = lookup.unreflect(mGetSched);

                Method mSetAiming = schedRuntimeClass.getMethod("setAiming", boolean.class);
                lwSetAimingHandle = lookup.unreflect(mSetAiming);

                try {
                    Method mIsAiming = schedRuntimeClass.getMethod("isAiming");
                    lwIsAimingHandle = lookup.unreflect(mIsAiming);
                } catch (Throwable ignored) {}

                try {
                    Method mSchedSession = schedRuntimeClass.getMethod("session");
                    lwSchedulerSessionHandle = lookup.unreflect(mSchedSession);

                    Class<?> schedSessionClass = Class.forName("io.github.leawind.thirdperson.internal.logic.scheduler.SchedulerSession");
                    Method mCameraAdjCtrl = schedSessionClass.getMethod("cameraAdjustmentController");
                    lwCameraAdjustmentControllerHandle = lookup.unreflect(mCameraAdjCtrl);

                    Class<?> adjCtrlClass = Class.forName("io.github.leawind.thirdperson.internal.logic.scheduler.camera.CameraAdjustmentController");
                    Method mIsAdjusting = adjCtrlClass.getMethod("isAdjusting");
                    lwIsAdjustingHandle = lookup.unreflect(mIsAdjusting);
                } catch (Throwable ignored) {}

                leawindLoaded = true;
            }
        } catch (Throwable ignored) {
            leawindLoaded = false;
        }
    }

    /**
     * 中和旧版枪械模组 (JEG / Scorched Guns 等) 中已废弃的 ShoulderSurfing 4.x 反射调用，
     * 避免其在 1.20.1 + ShoulderSurfing 5.x 环境下触发 NoClassDefFoundError 导致游戏崩溃闪退。
     */
    public static void neutralizeLegacyShoulderSurfingIntegrations() {
        try {
            Class<?> jegClass = Class.forName("ttv.migami.jeg.JustEnoughGuns");
            java.lang.reflect.Field field = jegClass.getField("shoulderSurfingLoaded");
            field.setBoolean(null, false);
        } catch (Throwable ignored) {}
        try {
            Class<?> scgClass = Class.forName("top.ribs.scguns.ScorchedGuns");
            java.lang.reflect.Field field = scgClass.getField("shoulderSurfingLoaded");
            field.setBoolean(null, false);
        } catch (Throwable ignored) {}
    }

    /**
     * 检测是否有任何支持的第三人称模组已安装
     */
    public static boolean isInstalled() {
        if (!initialized) init();
        return shoulderLoaded || leawindLoaded;
    }

    public static boolean isShoulderSurfingInstalled() {
        if (!initialized) init();
        return shoulderLoaded;
    }

    public static boolean isLeawindInstalled() {
        if (!initialized) init();
        return leawindLoaded;
    }

    /**
     * 检测玩家当前是否处于任何第三人称视角模式 (Shoulder Surfing 或 Leawind)
     */
    public static boolean isThirdPerson() {
        return isShoulderSurfingActive() || isLeawindActive();
    }

    public static boolean isShoulderSurfingActive() {
        if (!initialized) init();
        if (!shoulderLoaded || ssIsShoulderSurfingHandle == null || shoulderSurfingInstance == null) {
            return false;
        }
        try {
            return (boolean) ssIsShoulderSurfingHandle.invoke(shoulderSurfingInstance);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isLeawindActive() {
        if (!initialized) init();
        if (!leawindLoaded || lwIsThirdPersonHandle == null) {
            return false;
        }
        try {
            return (boolean) lwIsThirdPersonHandle.invoke();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 检测玩家当前是否正在按住自由观察/调镜键 (Free-Look / Camera Adjustment)
     */
    public static boolean isFreeLooking() {
        if (!initialized) init();

        if (isShoulderSurfingActive()) {
            if (ssIsFreeLookingHandle != null && shoulderSurfingInstance != null) {
                try {
                    return (boolean) ssIsFreeLookingHandle.invoke(shoulderSurfingInstance);
                } catch (Throwable ignored) {}
            }
            return false;
        }

        if (isLeawindActive()) {
            if (lwAdjustCameraKey != null && lwAdjustCameraKey.isDown()) {
                return true;
            }
            if (lwGetSchedulerRuntimeHandle != null && lwSchedulerSessionHandle != null &&
                lwCameraAdjustmentControllerHandle != null && lwIsAdjustingHandle != null) {
                try {
                    Object sched = lwGetSchedulerRuntimeHandle.invoke();
                    if (sched != null) {
                        Object session = lwSchedulerSessionHandle.invoke(sched);
                        if (session != null) {
                            Object adjCtrl = lwCameraAdjustmentControllerHandle.invoke(session);
                            if (adjCtrl != null) {
                                return (boolean) lwIsAdjustingHandle.invoke(adjCtrl);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return false;
        }

        return false;
    }

    /**
     * 获取第三人称相机当前偏航角 Yaw
     */
    public static float getCameraYaw() {
        if (!initialized) init();

        if (isShoulderSurfingActive() && ssGetCameraHandle != null && ssGetYRotHandle != null) {
            try {
                Object camera = ssGetCameraHandle.invoke(shoulderSurfingInstance);
                if (camera != null) {
                    return (float) ssGetYRotHandle.invoke(camera);
                }
            } catch (Throwable ignored) {}
        }

        if (isLeawindActive() && lwGetBaseRuntimeHandle != null && lwBaseSessionHandle != null &&
            lwLookControllerHandle != null && lwLookYawDegreesHandle != null) {
            try {
                Object base = lwGetBaseRuntimeHandle.invoke();
                if (base != null) {
                    Object session = lwBaseSessionHandle.invoke(base);
                    if (session != null) {
                        Object lookCtrl = lwLookControllerHandle.invoke(session);
                        if (lookCtrl != null) {
                            if (lwLookIsInitializedHandle == null || (boolean) lwLookIsInitializedHandle.invoke(lookCtrl)) {
                                return (float) lwLookYawDegreesHandle.invoke(lookCtrl);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        Minecraft mc = Minecraft.getInstance();
        return (mc != null && mc.player != null) ? mc.player.getYRot() : 0.0f;
    }

    /**
     * 获取第三人称相机当前俯仰角 Pitch
     */
    public static float getCameraPitch() {
        if (!initialized) init();

        if (isShoulderSurfingActive() && ssGetCameraHandle != null && ssGetXRotHandle != null) {
            try {
                Object camera = ssGetCameraHandle.invoke(shoulderSurfingInstance);
                if (camera != null) {
                    return (float) ssGetXRotHandle.invoke(camera);
                }
            } catch (Throwable ignored) {}
        }

        if (isLeawindActive() && lwGetBaseRuntimeHandle != null && lwBaseSessionHandle != null &&
            lwLookControllerHandle != null && lwLookPitchDegreesHandle != null) {
            try {
                Object base = lwGetBaseRuntimeHandle.invoke();
                if (base != null) {
                    Object session = lwBaseSessionHandle.invoke(base);
                    if (session != null) {
                        Object lookCtrl = lwLookControllerHandle.invoke(session);
                        if (lookCtrl != null) {
                            if (lwLookIsInitializedHandle == null || (boolean) lwLookIsInitializedHandle.invoke(lookCtrl)) {
                                return (float) lwLookPitchDegreesHandle.invoke(lookCtrl);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        Minecraft mc = Minecraft.getInstance();
        return (mc != null && mc.player != null) ? mc.player.getXRot() : 0.0f;
    }

    /**
     * 同步设置第三人称相机的角度 (驱动屏幕准星精准对齐目标)
     */
    public static void setCameraRotation(float yaw, float pitch) {
        if (!initialized) init();

        if (isShoulderSurfingActive() && ssGetCameraHandle != null) {
            try {
                Object camera = ssGetCameraHandle.invoke(shoulderSurfingInstance);
                if (camera != null) {
                    if (ssSetYRotHandle != null) ssSetYRotHandle.invoke(camera, yaw);
                    if (ssSetXRotHandle != null) ssSetXRotHandle.invoke(camera, pitch);
                }
            } catch (Throwable ignored) {}
            return;
        }

        if (isLeawindActive() && lwGetBaseRuntimeHandle != null) {
            try {
                Object base = lwGetBaseRuntimeHandle.invoke();
                if (base != null) {
                    // 1. 设置 LookController 相机朝向 (注意参数顺序：pitch 前，yaw 后！)
                    if (lwBaseSessionHandle != null && lwLookControllerHandle != null && lwLookInitializeHandle != null) {
                        Object session = lwBaseSessionHandle.invoke(base);
                        if (session != null) {
                            Object lookCtrl = lwLookControllerHandle.invoke(session);
                            if (lookCtrl != null) {
                                lwLookInitializeHandle.invoke(lookCtrl, pitch, yaw);
                            }
                        }
                    }

                    // 2. 同步玩家交互/面朝方向 (注意 LookRotation 构造参数：yaw 前，pitch 后！)
                    if (lwLookRotationCtor != null && lwCommitInteractionRotationHandle != null) {
                        Object lookRot = lwLookRotationCtor.invoke(yaw, pitch);
                        if (lookRot != null) {
                            lwCommitInteractionRotationHandle.invoke(base, lookRot);
                        }
                    }

                    // 3. 激活 Leawind Aiming 态以开启准星渲染并居中镜头
                    if (lwGetSchedulerRuntimeHandle != null && lwSetAimingHandle != null) {
                        Object sched = lwGetSchedulerRuntimeHandle.invoke();
                        if (sched != null) {
                            lwSetAimingHandle.invoke(sched, true);
                            wasLeawindAimingForced = true;
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 设置瞄准状态 (主要用于驱动 Leawind 准星显示与镜头模式)
     */
    public static void setAiming(boolean aiming) {
        if (!initialized) init();
        if (isLeawindActive() && lwGetSchedulerRuntimeHandle != null && lwSetAimingHandle != null) {
            try {
                Object sched = lwGetSchedulerRuntimeHandle.invoke();
                if (sched != null) {
                    lwSetAimingHandle.invoke(sched, aiming);
                    wasLeawindAimingForced = aiming;
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 当自瞄解除锁定或目标丢失时，平滑重置 Leawind 强制瞄准状态
     */
    public static void resetAiming() {
        if (wasLeawindAimingForced) {
            wasLeawindAimingForced = false;
            if (leawindLoaded && lwGetSchedulerRuntimeHandle != null && lwSetAimingHandle != null) {
                try {
                    Object sched = lwGetSchedulerRuntimeHandle.invoke();
                    if (sched != null) {
                        lwSetAimingHandle.invoke(sched, false);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * 获取第三人称相机的空间世界坐标位置
     */
    public static Vec3 getCameraPosition() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gameRenderer != null) {
            Camera camera = mc.gameRenderer.getMainCamera();
            if (camera != null) {
                return camera.getPosition();
            }
        }
        return (mc != null && mc.player != null) ? mc.player.getEyePosition() : Vec3.ZERO;
    }

    /**
     * 获取第三人称相机的朝向单位向量 (即屏幕正中央准星视线射线)
     */
    public static Vec3 getCameraLookVector() {
        return Vec3.directionFromRotation(getCameraPitch(), getCameraYaw());
    }
}
