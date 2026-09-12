package com.xdyyj.autoattacker.weapon;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 外部模组镜头后坐力抑制器 (External Gun Camera Recoil Suppressor)
 * 
 * 作用：
 * 针对 JEG (Just Enough Guns)、Scorched Guns 等模组在 RenderTickEvent (Phase.END) 阶段
 * 强制篡改玩家视角 (player.setXRot / player.setYRot) 的行为进行精确后坐力镜头拦截。
 * 
 * 原理：
 * JEG 和 Scorched Guns 在渲染事件中会检测 cameraRecoil > 0。
 * 当 AutoAttacker 锁定了目标时，本抑制器将其实例内部的 cameraRecoil 和 progressCameraRecoil 实时清零。
 * 这样既保留了枪支模型的射击后坐动画与开火特效，又彻底消除了因外部模组强行偏转视角与自瞄吸附相互死锁产生的剧烈晃动，
 * 保证非 TAC 枪械在射击与横向走位时准星丝滑锁定、弹道笔直命中目标。
 */
public final class GunRecoilSuppressor {
    private static boolean initialized = false;
    private static final List<RecoilHolder> HOLDERS = new ArrayList<>();

    private static class RecoilHolder {
        private final Object instanceSupplier;
        private final Field cameraRecoilField;
        private final Field progressCameraRecoilField;

        public RecoilHolder(Object instance, Field cameraRecoilField, Field progressCameraRecoilField) {
            this.instanceSupplier = instance;
            this.cameraRecoilField = cameraRecoilField;
            this.progressCameraRecoilField = progressCameraRecoilField;
        }

        public void suppress() {
            try {
                if (cameraRecoilField != null && instanceSupplier != null) {
                    cameraRecoilField.setFloat(instanceSupplier, 0.0f);
                }
                if (progressCameraRecoilField != null && instanceSupplier != null) {
                    progressCameraRecoilField.setFloat(instanceSupplier, 0.0f);
                }
            } catch (Throwable ignored) {}
        }
    }

    private GunRecoilSuppressor() {}

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // 1. JEG (Just Enough Guns)
        tryRegisterHandler("ttv.migami.jeg.client.handler.GunRecoilHandler");

        // 2. Scorched Guns
        tryRegisterHandler("top.ribs.scguns.client.handler.GunRecoilHandler");
        tryRegisterHandler("top.ribs.scguns.client.handler.RecoilHandler");

        // 3. MrCrayfish's Gun Mod (CGM)
        tryRegisterHandler("com.mrcrayfish.guns.client.handler.GunRecoilHandler");
        tryRegisterHandler("com.mrcrayfish.guns.client.handler.RecoilHandler");
    }

    private static void tryRegisterHandler(String className) {
        try {
            Class<?> clazz = Class.forName(className);
            Method getMethod = clazz.getMethod("get");
            Object instance = getMethod.invoke(null);
            if (instance != null) {
                Field camRecoil = null;
                Field progRecoil = null;
                try {
                    camRecoil = clazz.getDeclaredField("cameraRecoil");
                    camRecoil.setAccessible(true);
                } catch (Throwable ignored) {}
                try {
                    progRecoil = clazz.getDeclaredField("progressCameraRecoil");
                    progRecoil.setAccessible(true);
                } catch (Throwable ignored) {}

                if (camRecoil != null || progRecoil != null) {
                    HOLDERS.add(new RecoilHolder(instance, camRecoil, progRecoil));
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 抑制外部模组的镜头后坐晃动
     */
    public static void suppressExternalCameraRecoil() {
        if (!initialized) {
            init();
        }
        for (int i = 0; i < HOLDERS.size(); i++) {
            HOLDERS.get(i).suppress();
        }
    }
}
