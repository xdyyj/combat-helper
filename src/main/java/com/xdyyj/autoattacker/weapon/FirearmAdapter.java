package com.xdyyj.autoattacker.weapon;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用跨模组枪械软反射适配器 (Universal Firearm Adapter)
 * 
 * 全面支持:
 * 1. TACZ (Timeless and Classics Zero)
 * 2. Vic's Point Blank (点目标/Point Blank)
 * 3. Just Enough Guns (JEG)
 * 4. Scorched Guns (焦土枪械)
 * 5. MrCrayfish's Gun Mod (CGM)
 * 6. 通用枪械模组启发式退避适配
 */
public final class FirearmAdapter {

    public static final TagKey<Item> FORGE_GUNS_TAG = ItemTags.create(ResourceLocation.tryParse("forge:guns"));
    public static final TagKey<Item> C_GUNS_TAG = ItemTags.create(ResourceLocation.tryParse("c:guns"));

    private static final Map<Item, Boolean> IS_GUN_CACHE = new ConcurrentHashMap<>();
    private static final Map<Item, GunMeta> GUN_META_CACHE = new ConcurrentHashMap<>();

    // ==========================================
    // 1. TACZ 反射句柄缓存
    // ==========================================
    private static boolean taczChecked = false;
    private static boolean taczAvailable = false;

    private static Class<?> taczIGunClass = null;
    private static Method taczGetIGunOrNullMethod = null;
    private static Method taczGetGunIdMethod = null;
    private static Method taczGetCurrentAmmoMethod = null;
    private static Method taczGetRPMMethod = null;
    private static Method taczGetFireModeMethod = null;

    private static Class<?> taczTimelessAPIClass = null;
    private static Method taczGetCommonGunIndexMethod = null;

    private static Class<?> taczCommonGunIndexClass = null;
    private static Method taczGetBulletDataMethod = null;
    private static Method taczGetGunDataMethod = null;
    private static Method taczGetTypeMethod = null;

    private static Class<?> taczBulletDataClass = null;
    private static Method taczGetSpeedMethod = null;
    private static Method taczGetGravityMethod = null;
    private static Method taczGetExtraDamageMethod = null;

    private static Class<?> taczExtraDamageClass = null;
    private static Method taczGetHeadShotMultiplierMethod = null;

    private static Class<?> taczGunDataClass = null;
    private static Method taczGetRoundsPerMinuteMethod = null;
    private static Method taczGetAmmoAmountMethod = null;
    private static Method taczGetAmmoIdMethod = null;

    private static Class<?> taczAttachmentDataUtilsClass = null;
    private static Method taczGetAmmoCountWithAttachmentMethod = null;
    private static Method taczGetHeadShotMultiplierWithAttachmentMethod = null;
    private static Class<?> taczAttachmentTypeClass = null;
    private static Method taczGetAttachmentMethod = null;
    private static Method taczAttachmentTypeValuesMethod = null;

    private static Class<?> taczIGunOperatorClass = null;
    private static Method taczFromLivingEntityMethod = null;
    private static Method taczReloadMethod = null;

    private static Method taczGetClientGunIndexMethod = null;
    private static Class<?> taczClientGunIndexClass = null;
    private static Method taczClientGunIndexGetNameMethod = null;
    private static Method taczGetSynIsAimingMethod = null;
    private static Method taczGetSynIsBoltingMethod = null;
    private static Method taczGetSynReloadStateMethod = null;

    private static Class<?> taczReloadStateClass = null;
    private static Method taczGetCountDownMethod = null;

    private static Method taczHasBulletInBarrelMethod = null;
    private static Method taczHasInventoryAmmoMethod = null;
    private static Class<?> taczClientGunOperatorClass = null;
    private static Method taczFromLocalPlayerMethod = null;
    private static Method taczClientReloadMethod = null;
    private static Class<?> taczReloadKeyClass = null;
    private static Field taczReloadKeyField = null;
    private static Class<?> taczAbstractGunItemClass = null;
    private static Method taczCanReloadMethod = null;
    private static Class<?> taczIAmmoClass = null;
    private static Method taczIsAmmoOfGunMethod = null;
    private static Class<?> taczIAmmoBoxClass = null;
    private static Method taczIsAmmoBoxOfGunMethod = null;
    private static Method taczGetAmmoCountMethod = null;

    private static Class<?> taczShootKeyClass = null;
    private static Method taczShootControllerTickMethod = null;
    private static Field taczShootKeyField = null;

    // ==========================================
    // 2. Point Blank 反射句柄缓存
    // ==========================================
    private static boolean pbChecked = false;
    private static boolean pbAvailable = false;
    private static Class<?> pbGunItemClass = null;
    private static Method pbGetAmmoMethod = null;
    private static Method pbGetMaxAmmoCapacityMethod = null;
    private static Method pbGetCompatibleAmmoMethod = null;
    private static Method pbRequestReloadMethod = null;
    private static Method pbTryReloadMethod = null;
    private static Method pbCanReloadGunMethod = null;
    private static Method pbGetFireModeInstanceMethod = null;
    private static Method pbFmiGetTypeMethod = null;
    private static Method pbFmiGetRpmMethod = null;
    private static Class<?> pbGunClientStateClass = null;
    private static Method pbGetClientStateMethod = null;
    private static Method pbStateIsReloadingMethod = null;
    private static Method pbStateIsPreparingReloadMethod = null;
    private static Method pbResolveSlotIndexMethod = null;
    private static Field pbReloadKeyField = null;
    private static Method pbLazyGetMethod = null;

    // ==========================================
    // 3. Just Enough Guns (JEG) 反射句柄缓存
    // ==========================================
    private static boolean jegChecked = false;
    private static boolean jegAvailable = false;
    private static Class<?> jegGunItemClass = null;
    private static Class<?> jegGunClass = null;
    private static Method jegFireModeGetIdMethod = null;
    private static Method jegGetModifiedGunMethod = null;
    private static Method jegFindAmmoStackMethod = null;
    private static Method jegGetProjectileMethod = null;
    private static Method jegGetGeneralMethod = null;
    private static Method jegProjGetItemMethod = null;
    private static Method jegGenGetFireModeMethod = null;
    private static Method jegGenGetRateMethod = null;
    private static Method jegGenGetMaxHoldFireMethod = null;
    private static Method jegGetReloadsMethod = null;
    private static Method jegReloadsGetMaxAmmoMethod = null;
    private static Method jegReloadsGetReloadItemMethod = null;
    private static Method jegProjGetSpeedMethod = null;
    private static Method jegProjIsGravityMethod = null;
    private static Method jegProjGetHeadshotMultiplierMethod = null;
    private static Class<?> jegChargeTrackerClass = null;
    private static Method jegGetChargeProgressMethod = null;
    private static Method jegGetShootMappingMethod = null;
    private static Method jegGetAimMappingMethod = null;
    private static Field jegKeyReloadField = null;
    private static Class<?> jegReloadHandlerClass = null;
    private static Method jegGetReloadHandlerMethod = null;
    private static Method jegSetReloadingMethod = null;
    private static Method jegGetReloadTimerMethod = null;
    private static Class<?> jegShootingHandlerClass = null;
    private static Method jegGetShootingHandlerMethod = null;
    private static Method jegGetHoldFireMethod = null;
    private static Class<?> jegAimingHandlerClass = null;
    private static Method jegGetAimingHandlerMethod = null;
    private static Method jegIsAimingMethod = null;

    // ==========================================
    // 4. Scorched Guns (焦土枪械) 反射句柄缓存
    // ==========================================
    private static boolean scgunsChecked = false;
    private static boolean scgunsAvailable = false;
    private static Class<?> scgunsGunItemClass = null;
    private static Class<?> scgunsGunClass = null;
    private static Method scgunsGetModifiedGunMethod = null;
    private static Method scgunsFindAmmoStackMethod = null;
    private static Method scgunsGetCurrentAmmoItemMethod = null;
    private static Method scgunsGetMaxAmmoMethod = null;
    private static Method scgunsGetAmmoCountMethod = null;
    private static Method scgunsGetGeneralMethod = null;
    private static Method scgunsGetReloadsMethod = null;
    private static Method scgunsGenIsAutoMethod = null;
    private static Method scgunsGenIsRevolverMethod = null;
    private static Method scgunsGenGetRateMethod = null;
    private static Method scgunsReloadsGetReloadTypeMethod = null;
    private static Object scgunsReloadTypeManualObj = null;
    private static Field scgunsKeyReloadField = null;
    private static Class<?> scgunsReloadHandlerClass = null;
    private static Method scgunsGetReloadHandlerMethod = null;
    private static Method scgunsSetReloadingMethod = null;
    private static Method scgunsGetReloadTimerMethod = null;
    private static Class<?> scgunsAimingHandlerClass = null;
    private static Method scgunsGetAimingHandlerMethod = null;
    private static Method scgunsIsAimingMethod = null;

    // ==========================================
    // 5. MrCrayfish's Gun Mod (CGM) 反射句柄缓存
    // ==========================================
    private static boolean cgmChecked = false;
    private static boolean cgmAvailable = false;
    private static Class<?> cgmGunItemClass = null;
    private static Class<?> cgmReloadHandlerClass = null;
    private static Method cgmGetReloadHandlerMethod = null;
    private static Method cgmSetReloadingMethod = null;
    private static Method cgmGetReloadTimerMethod = null;
    private static Class<?> cgmAimingHandlerClass = null;
    private static Method cgmGetAimingHandlerMethod = null;
    private static Method cgmIsAimingMethod = null;

    private static boolean isTriggerShooting = false;

    public static final class GunMeta {
        public final String typeName;       // 枪种名称，如 "步枪", "狙击枪", "手枪"
        public final float baseSpeed;       // 子弹初速 (格/刻)
        public final double baseGravity;    // 子弹重力加速度
        public final float headshotMult;    // 爆头伤害倍率 (如 1.5f)

        public GunMeta(String typeName, float baseSpeed, double baseGravity, float headshotMult) {
            this.typeName = typeName;
            this.baseSpeed = baseSpeed;
            this.baseGravity = baseGravity;
            this.headshotMult = headshotMult;
        }
    }

    public static final class GunStatus {
        public final boolean isGun;
        public final String typeName;
        public final int currentAmmo;
        public final int maxAmmo;
        public final float bulletSpeed;     // blocks / tick
        public final float bulletSpeedMs;   // m / s
        public final double bulletGravity;
        public final float headshotMult;
        public final int rpm;
        public final String ammoId;
        public final String fireMode;
        public final boolean hasBulletInBarrel;
        public final int totalAmmo;         // currentAmmo + (hasBulletInBarrel ? 1 : 0), -1 if currentAmmo == -1
        public final List<String> attachments;

        public GunStatus(boolean isGun, String typeName, int currentAmmo, int maxAmmo,
                         float bulletSpeed, float bulletSpeedMs, double bulletGravity,
                         float headshotMult, int rpm, String ammoId, String fireMode,
                         boolean hasBulletInBarrel, List<String> attachments) {
            this.isGun = isGun;
            this.typeName = typeName;
            this.currentAmmo = currentAmmo;
            this.maxAmmo = maxAmmo;
            this.bulletSpeed = bulletSpeed;
            this.bulletSpeedMs = bulletSpeedMs;
            this.bulletGravity = bulletGravity;
            this.headshotMult = headshotMult;
            this.rpm = rpm;
            this.ammoId = ammoId;
            this.fireMode = fireMode;
            this.hasBulletInBarrel = hasBulletInBarrel;
            this.totalAmmo = (currentAmmo < 0) ? -1 : (currentAmmo + (hasBulletInBarrel ? 1 : 0));
            this.attachments = attachments != null ? attachments : Collections.emptyList();
        }

        public GunStatus(boolean isGun, String typeName, int currentAmmo, int maxAmmo,
                         float bulletSpeed, float bulletSpeedMs, double bulletGravity,
                         float headshotMult, int rpm, String ammoId, String fireMode,
                         boolean hasBulletInBarrel) {
            this(isGun, typeName, currentAmmo, maxAmmo, bulletSpeed, bulletSpeedMs, bulletGravity, headshotMult, rpm, ammoId, fireMode, hasBulletInBarrel, Collections.emptyList());
        }

        public GunStatus(boolean isGun, String typeName, int currentAmmo, int maxAmmo,
                         float bulletSpeed, float bulletSpeedMs, double bulletGravity,
                         float headshotMult, int rpm, String ammoId, String fireMode) {
            this(isGun, typeName, currentAmmo, maxAmmo, bulletSpeed, bulletSpeedMs, bulletGravity, headshotMult, rpm, ammoId, fireMode, false, Collections.emptyList());
        }

        public int getEffectiveMaxAmmo() {
            if (maxAmmo <= 0) return -1;
            if (hasBulletInBarrel || totalAmmo > maxAmmo) {
                return maxAmmo + 1;
            }
            return maxAmmo;
        }

        public static final GunStatus NOT_GUN = new GunStatus(false, "", -1, -1, 0, 0, 1.0f, 1.0f, 0, "", "", false, Collections.emptyList());
    }

    /**
     * 判断指定物品是否为现代枪械 (跨模组通用感知)
     */
    public static boolean isGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return IS_GUN_CACHE.computeIfAbsent(item, k -> detectIsGun(stack, item));
    }

    public static boolean isTaczGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        initTaczReflection();
        if (taczAvailable && taczGetIGunOrNullMethod != null) {
            try {
                return taczGetIGunOrNullMethod.invoke(null, stack) != null;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public static boolean isPointBlankGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        initPbReflection();
        if (pbAvailable && pbGunItemClass != null && pbGunItemClass.isInstance(stack.getItem())) {
            return true;
        }
        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return reg != null && reg.getNamespace().equalsIgnoreCase("pointblank");
    }

    public static boolean isJegGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        initJegReflection();
        if (jegAvailable && jegGunItemClass != null && jegGunItemClass.isInstance(stack.getItem())) {
            return true;
        }
        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return reg != null && reg.getNamespace().equalsIgnoreCase("jeg");
    }

    public static boolean isScorchedGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        initScgunsReflection();
        if (scgunsAvailable && scgunsGunItemClass != null && scgunsGunItemClass.isInstance(stack.getItem())) {
            return true;
        }
        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return reg != null && (reg.getNamespace().equalsIgnoreCase("scguns") || reg.getNamespace().equalsIgnoreCase("scorchedguns"));
    }

    public static boolean isCgmGun(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        initCgmReflection();
        if (cgmAvailable && cgmGunItemClass != null && cgmGunItemClass.isInstance(stack.getItem())) {
            return true;
        }
        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return reg != null && reg.getNamespace().equalsIgnoreCase("cgm");
    }

    private static boolean detectIsGun(ItemStack stack, Item item) {
        // 1. TACZ 优先探测
        if (isTaczGun(stack)) return true;

        // 2. 命名空间探测
        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(item);
        if (reg != null) {
            String ns = reg.getNamespace().toLowerCase(Locale.ROOT);
            if (ns.equals("tacz") || ns.equals("pointblank") || ns.equals("jeg") ||
                ns.equals("scguns") || ns.equals("scorchedguns") || ns.equals("cgm") ||
                ns.equals("superb_warfare") || ns.equals("vic") || ns.equals("mwc") ||
                ns.contains("gun") || ns.contains("weapon")) {
                String path = reg.getPath().toLowerCase(Locale.ROOT);
                if (!path.contains("ammo") && !path.contains("bullet") &&
                    !path.contains("magazine") && !path.contains("attachment") &&
                    !path.contains("casing") && !path.contains("workbench") &&
                    !path.contains("blueprint")) {
                    return true;
                }
            }
        }

        // 3. 检查类继承与类名
        Class<?> clazz = item.getClass();
        while (clazz != null && clazz != Object.class) {
            String className = clazz.getName().toLowerCase(Locale.ROOT);
            if (className.contains("gunitem") || className.contains("firearm") || className.contains("rifle")) {
                if (!className.contains("ammo") && !className.contains("bullet") && !className.contains("attachment")) {
                    return true;
                }
            }
            for (Class<?> iface : clazz.getInterfaces()) {
                String ifaceName = iface.getName().toLowerCase(Locale.ROOT);
                if (ifaceName.contains("igun") || ifaceName.contains("firearm")) {
                    return true;
                }
            }
            clazz = clazz.getSuperclass();
        }

        return false;
    }

    /**
     * 获取枪械的动态战术状态 (弹药数、类型、初速、重力、爆头倍率、RPM等)
     */
    public static GunStatus getGunStatus(ItemStack stack) {
        if (!isGun(stack)) return GunStatus.NOT_GUN;

        // --- 1. TACZ 原厂物理获取 ---
        if (isTaczGun(stack)) {
            initTaczReflection();
            if (taczAvailable && taczGetIGunOrNullMethod != null) {
                try {
                    Object iGunObj = taczGetIGunOrNullMethod.invoke(null, stack);
                    if (iGunObj != null) {
                        ResourceLocation gunId = (ResourceLocation) taczGetGunIdMethod.invoke(iGunObj, stack);
                        int curAmmo = (int) taczGetCurrentAmmoMethod.invoke(iGunObj, stack);
                        boolean hasBulletInBarrel = false;
                        if (taczHasBulletInBarrelMethod != null) {
                            try {
                                hasBulletInBarrel = (boolean) taczHasBulletInBarrelMethod.invoke(iGunObj, stack);
                            } catch (Throwable ignored) {}
                        }
                        int rpm = (taczGetRPMMethod != null) ? (int) taczGetRPMMethod.invoke(iGunObj, stack) : 600;
                        Object fireModeObj = (taczGetFireModeMethod != null) ? taczGetFireModeMethod.invoke(iGunObj, stack) : null;
                        String fireMode = fireModeObj != null ? fireModeObj.toString() : "AUTO";

                        // 检索 CommonGunIndex
                        Object gunIndexOpt = taczGetCommonGunIndexMethod.invoke(null, gunId);
                        if (gunIndexOpt instanceof Optional<?> opt && opt.isPresent()) {
                            Object gunIndex = opt.get();
                            String rawType = (String) taczGetTypeMethod.invoke(gunIndex);
                            String typeName = translateGunType(rawType);

                            Object bulletData = taczGetBulletDataMethod.invoke(gunIndex);
                            float speedMs = (float) taczGetSpeedMethod.invoke(bulletData);
                            float bulletSpeedBlocksPerTick = speedMs / 20.0f;
                            float gravity = (float) taczGetGravityMethod.invoke(bulletData);

                            float headshotMult = 1.5f;
                            if (taczGetExtraDamageMethod != null && taczGetHeadShotMultiplierMethod != null) {
                                Object extraDamage = taczGetExtraDamageMethod.invoke(bulletData);
                                if (extraDamage != null) {
                                    headshotMult = (float) taczGetHeadShotMultiplierMethod.invoke(extraDamage);
                                }
                            }

                            int maxAmmo = -1;
                            String ammoId = "";
                            if (taczGetGunDataMethod != null) {
                                Object gunData = taczGetGunDataMethod.invoke(gunIndex);
                                if (gunData != null) {
                                    // 优先使用 AttachmentDataUtils 计算包含扩容弹匣/鼓包在内的最终实际弹药容量
                                    if (taczGetAmmoCountWithAttachmentMethod != null) {
                                        try {
                                            maxAmmo = (int) taczGetAmmoCountWithAttachmentMethod.invoke(null, stack, gunData);
                                        } catch (Throwable ignored) {}
                                    }
                                    if (maxAmmo <= 0 && taczGetAmmoAmountMethod != null) {
                                        maxAmmo = (int) taczGetAmmoAmountMethod.invoke(gunData);
                                    }
                                    // 获取包含配件加成后的最终实际爆头倍率
                                    if (taczGetHeadShotMultiplierWithAttachmentMethod != null) {
                                        try {
                                            double hm = (double) taczGetHeadShotMultiplierWithAttachmentMethod.invoke(null, stack, gunData);
                                            headshotMult = (float) hm;
                                        } catch (Throwable ignored) {}
                                    }
                                    if (taczGetAmmoIdMethod != null) {
                                        ResourceLocation ammoRes = (ResourceLocation) taczGetAmmoIdMethod.invoke(gunData);
                                        if (ammoRes != null) ammoId = ammoRes.toString();
                                    }
                                    if (rpm <= 0 && taczGetRoundsPerMinuteMethod != null) {
                                        rpm = (int) taczGetRoundsPerMinuteMethod.invoke(gunData);
                                    }
                                }
                            }

                            List<String> attachments = getInstalledAttachments(stack);

                            return new GunStatus(true, typeName, curAmmo, maxAmmo,
                                    bulletSpeedBlocksPerTick, speedMs, gravity, headshotMult, rpm, ammoId, fireMode, hasBulletInBarrel, attachments);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        // --- 2. Point Blank 状态提取 ---
        if (isPointBlankGun(stack)) {
            initPbReflection();
            CompoundTag tag = stack.getTag();
            int curAmmo = -1;
            int maxAmmo = -1;
            int rpm = 650;
            String fireMode = "AUTO";

            if (pbGetFireModeInstanceMethod != null) {
                try {
                    Object fmi = pbGetFireModeInstanceMethod.invoke(null, stack);
                    if (fmi != null) {
                        if (pbGetAmmoMethod != null) {
                            try {
                                curAmmo = (int) pbGetAmmoMethod.invoke(null, stack, fmi);
                            } catch (Throwable ignored) {}
                        }
                        if (pbFmiGetRpmMethod != null) {
                            rpm = (int) pbFmiGetRpmMethod.invoke(fmi);
                        }
                        if (pbFmiGetTypeMethod != null) {
                            Object typeObj = pbFmiGetTypeMethod.invoke(fmi);
                            if (typeObj != null) {
                                String typeStr = typeObj.toString().toUpperCase(Locale.ROOT);
                                if (typeStr.contains("SINGLE") || typeStr.contains("BURST") || typeStr.contains("MANUAL")) {
                                    fireMode = "SEMI";
                                } else {
                                    fireMode = "AUTO";
                                }
                            }
                        }
                        if (pbGetMaxAmmoCapacityMethod != null) {
                            maxAmmo = (int) pbGetMaxAmmoCapacityMethod.invoke(stack.getItem(), stack, fmi);
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (curAmmo < 0 && tag != null) {
                if (tag.contains("ammo")) curAmmo = tag.getInt("ammo");
                else if (tag.contains("Ammo")) curAmmo = tag.getInt("Ammo");
            }
            if (maxAmmo < 0 && pbGetMaxAmmoCapacityMethod != null) {
                try {
                    maxAmmo = (int) pbGetMaxAmmoCapacityMethod.invoke(stack.getItem(), stack, null);
                } catch (Throwable ignored) {}
            }

            Item item = stack.getItem();
            GunMeta meta = GUN_META_CACHE.computeIfAbsent(item, FirearmAdapter::resolveGunMeta);
            return new GunStatus(true, meta.typeName, curAmmo, maxAmmo,
                    meta.baseSpeed, meta.baseSpeed * 20.0f, meta.baseGravity, meta.headshotMult, Math.max(rpm, 120), "", fireMode, false);
        }

        // --- 3. Scorched Guns 状态提取 ---
        if (isScorchedGun(stack)) {
            initScgunsReflection();
            CompoundTag tag = stack.getTag();
            int curAmmo = (tag != null && tag.contains("AmmoCount")) ? tag.getInt("AmmoCount") : -1;
            int maxAmmo = -1;
            if (scgunsGetMaxAmmoMethod != null) {
                try {
                    maxAmmo = (int) scgunsGetMaxAmmoMethod.invoke(null, stack);
                } catch (Throwable ignored) {}
            }
            int rpm = 600;
            String fireMode = "AUTO";
            try {
                if (scgunsGetModifiedGunMethod != null && scgunsGunItemClass.isInstance(stack.getItem())) {
                    Object gun = scgunsGetModifiedGunMethod.invoke(stack.getItem(), stack);
                    if (gun != null && scgunsGetGeneralMethod != null) {
                        Object general = scgunsGetGeneralMethod.invoke(gun);
                        if (general != null) {
                            if (scgunsGenGetRateMethod != null) {
                                int rateTicks = (int) scgunsGenGetRateMethod.invoke(general);
                                if (rateTicks > 0) rpm = Math.round(1200.0f / rateTicks);
                            }
                            boolean isAuto = true;
                            if (scgunsGenIsAutoMethod != null) {
                                isAuto = (boolean) scgunsGenIsAutoMethod.invoke(general);
                            }
                            boolean isRevolver = false;
                            if (scgunsGenIsRevolverMethod != null) {
                                isRevolver = (boolean) scgunsGenIsRevolverMethod.invoke(general);
                            }
                            if (!isAuto || isRevolver) {
                                fireMode = "SEMI";
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            Item item = stack.getItem();
            GunMeta meta = GUN_META_CACHE.computeIfAbsent(item, FirearmAdapter::resolveGunMeta);
            return new GunStatus(true, meta.typeName, curAmmo, maxAmmo,
                    meta.baseSpeed, meta.baseSpeed * 20.0f, meta.baseGravity, meta.headshotMult, Math.max(rpm, 120), "", fireMode, false);
        }

        // --- 4. JEG 状态提取 ---
        if (isJegGun(stack)) {
            initJegReflection();
            CompoundTag tag = stack.getTag();
            int curAmmo = (tag != null && tag.contains("AmmoCount")) ? tag.getInt("AmmoCount") : -1;
            int maxAmmo = -1;
            int rpm = 600;
            String fireMode = "AUTO";
            float bulletSpeed = -1.0f;
            double bulletGravity = -1.0;
            float headshotMult = -1.0f;
            String ammoId = "";
            try {
                if (jegGetModifiedGunMethod != null && jegGunItemClass.isInstance(stack.getItem())) {
                    Object gun = jegGetModifiedGunMethod.invoke(stack.getItem(), stack);
                    if (gun != null) {
                        if (jegGetGeneralMethod != null) {
                            Object general = jegGetGeneralMethod.invoke(gun);
                            if (general != null) {
                                if (jegGenGetRateMethod != null) {
                                    int rateTicks = (int) jegGenGetRateMethod.invoke(general);
                                    if (rateTicks > 0) rpm = Math.round(1200.0f / rateTicks);
                                }
                                if (jegGenGetFireModeMethod != null) {
                                    Object fmObj = jegGenGetFireModeMethod.invoke(general);
                                    if (fmObj != null) {
                                        String fmStr = "";
                                        try {
                                            if (jegFireModeGetIdMethod != null) {
                                                Object idObj = jegFireModeGetIdMethod.invoke(fmObj);
                                                if (idObj != null) fmStr = idObj.toString().toLowerCase(Locale.ROOT);
                                            } else {
                                                Method getIdMethod = fmObj.getClass().getMethod("getId");
                                                Object idObj = getIdMethod.invoke(fmObj);
                                                if (idObj != null) fmStr = idObj.toString().toLowerCase(Locale.ROOT);
                                            }
                                        } catch (Throwable t) {
                                            fmStr = fmObj.toString().toLowerCase(Locale.ROOT);
                                        }
                                        if (fmStr.contains("release_fire") || fmStr.contains("release")) {
                                            fireMode = "RELEASE_FIRE";
                                        } else if (fmStr.contains("semi") || fmStr.contains("burst") || fmStr.contains("single")) {
                                            fireMode = "SEMI";
                                        } else {
                                            fireMode = "AUTO";
                                        }
                                    }
                                }
                            }
                        }
                        if (jegGetProjectileMethod != null) {
                            Object proj = jegGetProjectileMethod.invoke(gun);
                            if (proj != null) {
                                if (jegProjGetSpeedMethod != null) {
                                    bulletSpeed = ((Number) jegProjGetSpeedMethod.invoke(proj)).floatValue();
                                }
                                if (jegProjIsGravityMethod != null) {
                                    boolean hasGrav = (boolean) jegProjIsGravityMethod.invoke(proj);
                                    bulletGravity = hasGrav ? 0.015 : 0.003;
                                }
                                if (jegProjGetHeadshotMultiplierMethod != null) {
                                    headshotMult = ((Number) jegProjGetHeadshotMultiplierMethod.invoke(proj)).floatValue();
                                }
                                if (jegProjGetItemMethod != null) {
                                    Object ammoRes = jegProjGetItemMethod.invoke(proj);
                                    if (ammoRes != null) ammoId = ammoRes.toString();
                                }
                            }
                        }
                        if (jegGetReloadsMethod != null && jegReloadsGetMaxAmmoMethod != null) {
                            Object reloads = jegGetReloadsMethod.invoke(gun);
                            if (reloads != null) {
                                maxAmmo = (int) jegReloadsGetMaxAmmoMethod.invoke(reloads);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            Item item = stack.getItem();
            GunMeta meta = GUN_META_CACHE.computeIfAbsent(item, FirearmAdapter::resolveGunMeta);
            if (bulletSpeed <= 0) bulletSpeed = meta.baseSpeed;
            if (bulletGravity < 0) bulletGravity = meta.baseGravity;
            if (headshotMult <= 0) headshotMult = meta.headshotMult;
            if (maxAmmo <= 0) maxAmmo = 1;
            return new GunStatus(true, meta.typeName, curAmmo, maxAmmo,
                    bulletSpeed, bulletSpeed * 20.0f, bulletGravity, headshotMult, Math.max(rpm, 120), ammoId, fireMode, false);
        }

        // --- 5. 通用枪械启发式退避 ---
        Item item = stack.getItem();
        GunMeta meta = GUN_META_CACHE.computeIfAbsent(item, FirearmAdapter::resolveGunMeta);

        int curAmmo = -1;
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tag.contains("GunCurrentAmmoCount")) {
                curAmmo = tag.getInt("GunCurrentAmmoCount");
            } else if (tag.contains("AmmoCount")) {
                curAmmo = tag.getInt("AmmoCount");
            } else if (tag.contains("Ammo")) {
                curAmmo = tag.getInt("Ammo");
            } else if (tag.contains("ammo")) {
                curAmmo = tag.getInt("ammo");
            }
        }

        boolean hasBulletInBarrel = false;
        if (tag != null && tag.contains("HasBulletInBarrel")) {
            hasBulletInBarrel = tag.getBoolean("HasBulletInBarrel");
        }
        return new GunStatus(true, meta.typeName, curAmmo, -1,
                meta.baseSpeed, meta.baseSpeed * 20.0f, meta.baseGravity, meta.headshotMult, 600, "", "AUTO", hasBulletInBarrel);
    }

    private static String translateGunType(String rawType) {
        if (rawType == null) return "突击步枪";
        String lower = rawType.toLowerCase(Locale.ROOT);
        if (lower.contains("bow") || lower.contains("compound")) return "复合弓/弓箭";
        if (lower.contains("sniper")) return "狙击步枪";
        if (lower.contains("rifle")) return "突击步枪";
        if (lower.contains("shotgun")) return "霰弹枪";
        if (lower.contains("smg")) return "冲锋枪";
        if (lower.contains("pistol")) return "半自动手枪";
        if (lower.contains("mg") || lower.contains("machine")) return "通用机枪";
        if (lower.contains("rpg") || lower.contains("rocket")) return "重火箭筒";
        return rawType.toUpperCase(Locale.ROOT);
    }

    private static GunMeta resolveGunMeta(Item item) {
        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(item);
        String name = (reg != null ? reg.getPath() : item.getDescriptionId()).toLowerCase(Locale.ROOT);

        if (name.contains("bow") || name.contains("compound") || name.contains("primitive")) {
            return new GunMeta("弓箭/复合弓", 8.0f, 0.015, 2.5f);
        }
        if (name.contains("sniper") || name.contains("awp") || name.contains("barrett") || name.contains("m98b") || name.contains("m24") || name.contains("mosin")) {
            return new GunMeta("狙击步枪", 36.0f, 0.003, 2.0f);
        }
        if (name.contains("shotgun") || name.contains("m870") || name.contains("aa12") || name.contains("spas") || name.contains("db") || name.contains("super_shorty")) {
            return new GunMeta("霰弹枪", 16.0f, 0.012, 1.25f);
        }
        if (name.contains("smg") || name.contains("mp5") || name.contains("p90") || name.contains("vector") || name.contains("uzi") || name.contains("thompson")) {
            return new GunMeta("冲锋枪", 22.0f, 0.006, 1.4f);
        }
        if (name.contains("pistol") || name.contains("glock") || name.contains("m1911") || name.contains("deagle") || name.contains("revolver") || name.contains("luger")) {
            return new GunMeta("半自动手枪", 18.0f, 0.008, 1.5f);
        }
        if (name.contains("rpg") || name.contains("rocket") || name.contains("grenade") || name.contains("launcher")) {
            return new GunMeta("重火力", 12.0f, 0.020, 1.0f);
        }

        return new GunMeta("突击步枪", 28.0f, 0.005, 1.5f);
    }

    /**
     * 检测玩家当前是否处于机瞄开镜瞄准状态 (ADS)
     */
    public static boolean isAiming(Player player) {
        if (player == null) return false;
        ItemStack mainHand = player.getMainHandItem();

        // 1. TACZ 开镜感知
        if (isTaczGun(mainHand)) {
            initTaczReflection();
            if (taczAvailable && taczFromLivingEntityMethod != null && taczGetSynIsAimingMethod != null) {
                try {
                    Object operator = taczFromLivingEntityMethod.invoke(null, player);
                    if (operator != null) {
                        Object res = taczGetSynIsAimingMethod.invoke(operator);
                        if (res instanceof Boolean b) return b;
                    }
                } catch (Throwable ignored) {}
            }
            return false;
        }

        // 2. JEG 开镜感知
        if (isJegGun(mainHand)) {
            initJegReflection();
            if (jegAimingHandlerClass != null && jegGetAimingHandlerMethod != null && jegIsAimingMethod != null) {
                try {
                    Object handler = jegGetAimingHandlerMethod.invoke(null);
                    if (handler != null) {
                        return (boolean) jegIsAimingMethod.invoke(handler);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 3. Scorched Guns 开镜感知
        if (isScorchedGun(mainHand)) {
            initScgunsReflection();
            if (scgunsAimingHandlerClass != null && scgunsGetAimingHandlerMethod != null && scgunsIsAimingMethod != null) {
                try {
                    Object handler = scgunsGetAimingHandlerMethod.invoke(null);
                    if (handler != null) {
                        return (boolean) scgunsIsAimingMethod.invoke(handler);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 4. CGM 开镜感知
        if (isCgmGun(mainHand)) {
            initCgmReflection();
            if (cgmAimingHandlerClass != null && cgmGetAimingHandlerMethod != null && cgmIsAimingMethod != null) {
                try {
                    Object handler = cgmGetAimingHandlerMethod.invoke(null);
                    if (handler != null) {
                        return (boolean) cgmIsAimingMethod.invoke(handler);
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 5. Point Blank / 原版通用开镜感知 (按住使用键瞄准)
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keyUse != null) {
            return mc.options.keyUse.isDown();
        }
        return false;
    }

    /**
     * 检测玩家当前是否处于拉栓硬直状态 (Bolting)
     */
    public static boolean isBolting(Player player) {
        if (player == null) return false;
        ItemStack mainHand = player.getMainHandItem();
        if (isTaczGun(mainHand)) {
            initTaczReflection();
            if (taczAvailable && taczFromLivingEntityMethod != null && taczGetSynIsBoltingMethod != null) {
                try {
                    Object operator = taczFromLivingEntityMethod.invoke(null, player);
                    if (operator != null) {
                        Object res = taczGetSynIsBoltingMethod.invoke(operator);
                        if (res instanceof Boolean b) return b;
                    }
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    /**
     * 检测玩家当前是否处于换弹状态 (Reloading)
     */
    public static boolean isReloading(Player player) {
        if (player == null) return false;
        ItemStack mainHand = player.getMainHandItem();

        // 1. TACZ 换弹检测
        if (isTaczGun(mainHand)) {
            initTaczReflection();
            if (taczAvailable && taczFromLivingEntityMethod != null && taczGetSynReloadStateMethod != null && taczGetCountDownMethod != null) {
                try {
                    Object operator = taczFromLivingEntityMethod.invoke(null, player);
                    if (operator != null) {
                        Object reloadState = taczGetSynReloadStateMethod.invoke(operator);
                        if (reloadState != null) {
                            long countDown = (long) taczGetCountDownMethod.invoke(reloadState);
                            return countDown > 0L;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return false;
        }

        // 2. Point Blank 换弹检测 (客户端状态机全槽位扫描 + NBT 兜底)
        if (isPointBlankGun(mainHand)) {
            initPbReflection();
            if (pbGunClientStateClass != null && pbGetClientStateMethod != null) {
                try {
                    int sel = player.getInventory().selected;
                    if (pbResolveSlotIndexMethod != null) {
                        try {
                            int resolved = (int) pbResolveSlotIndexMethod.invoke(null, player, mainHand);
                            if (resolved >= 0) sel = resolved;
                        } catch (Throwable ignored) {}
                    }
                    Object state = pbGetClientStateMethod.invoke(null, player, mainHand, sel, false);
                    if (state != null) {
                        boolean reloading = (pbStateIsReloadingMethod != null) && (boolean) pbStateIsReloadingMethod.invoke(state);
                        boolean preparing = (pbStateIsPreparingReloadMethod != null) && (boolean) pbStateIsPreparingReloadMethod.invoke(state);
                        if (reloading || preparing) {
                            return true;
                        }
                    }
                    // 全局槽位防漏扫描：遍历 0~8 号热键栏与 40 号副手
                    for (int s = 0; s <= 8; s++) {
                        if (s == sel) continue;
                        Object altState = pbGetClientStateMethod.invoke(null, player, mainHand, s, false);
                        if (altState != null) {
                            boolean reloading = (pbStateIsReloadingMethod != null) && (boolean) pbStateIsReloadingMethod.invoke(altState);
                            boolean preparing = (pbStateIsPreparingReloadMethod != null) && (boolean) pbStateIsPreparingReloadMethod.invoke(altState);
                            if (reloading || preparing) return true;
                        }
                    }
                    Object offState = pbGetClientStateMethod.invoke(null, player, mainHand, 40, true);
                    if (offState != null) {
                        boolean reloading = (pbStateIsReloadingMethod != null) && (boolean) pbStateIsReloadingMethod.invoke(offState);
                        boolean preparing = (pbStateIsPreparingReloadMethod != null) && (boolean) pbStateIsPreparingReloadMethod.invoke(offState);
                        if (reloading || preparing) return true;
                    }
                } catch (Throwable ignored) {}
            }
            CompoundTag tag = mainHand.getTag();
            if (tag != null) {
                if (tag.contains("reloading") && tag.getBoolean("reloading")) return true;
                if (tag.contains("IsReloading") && tag.getBoolean("IsReloading")) return true;
            }
        }

        // 3. JEG 换弹检测 (ReloadHandler.get().getReloadTimer() > 0 + NBT IsReloading)
        if (isJegGun(mainHand)) {
            initJegReflection();
            if (jegReloadHandlerClass != null && jegGetReloadHandlerMethod != null && jegGetReloadTimerMethod != null) {
                try {
                    Object handler = jegGetReloadHandlerMethod.invoke(null);
                    if (handler != null) {
                        int timer = (int) jegGetReloadTimerMethod.invoke(handler);
                        if (timer > 0) return true;
                    }
                } catch (Throwable ignored) {}
            }
            CompoundTag tag = mainHand.getTag();
            if (tag != null && tag.contains("IsReloading") && tag.getBoolean("IsReloading")) {
                return true;
            }
        }

        // 4. Scorched Guns 换弹检测 (ReloadHandler.get().getReloadTimer() > 0 + NBT IsReloading)
        if (isScorchedGun(mainHand)) {
            initScgunsReflection();
            if (scgunsReloadHandlerClass != null && scgunsGetReloadHandlerMethod != null && scgunsGetReloadTimerMethod != null) {
                try {
                    Object handler = scgunsGetReloadHandlerMethod.invoke(null);
                    if (handler != null) {
                        int timer = (int) scgunsGetReloadTimerMethod.invoke(handler);
                        if (timer > 0) return true;
                    }
                } catch (Throwable ignored) {}
            }
            CompoundTag tag = mainHand.getTag();
            if (tag != null && tag.contains("IsReloading") && tag.getBoolean("IsReloading")) {
                return true;
            }
        }

        // 5. CGM 换弹检测 (ReloadHandler.get().getReloadTimer() > 0)
        if (isCgmGun(mainHand)) {
            initCgmReflection();
            if (cgmReloadHandlerClass != null && cgmGetReloadHandlerMethod != null && cgmGetReloadTimerMethod != null) {
                try {
                    Object handler = cgmGetReloadHandlerMethod.invoke(null);
                    if (handler != null) {
                        int timer = (int) cgmGetReloadTimerMethod.invoke(handler);
                        return timer > 0;
                    }
                } catch (Throwable ignored) {}
            }
        }

        return false;
    }

    /**
     * 检查玩家背包是否有匹配该枪械的备弹 (创造模式或有备弹返回 true)
     */
    public static boolean hasInventoryAmmo(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (player.isCreative()) return true;

        // 1. TACZ 备弹检测 (优先 canReload，再穿透扫描 IAmmo 与 IAmmoBox 弹药箱)
        if (isTaczGun(stack)) {
            initTaczReflection();
            if (taczAvailable) {
                // 1.1 权威 API: 判定当前枪械是否满足换弹条件 (含背包弹药与弹药箱检测)
                if (taczCanReloadMethod != null) {
                    try {
                        boolean canReload = (boolean) taczCanReloadMethod.invoke(stack.getItem(), player, stack);
                        if (canReload) return true;
                    } catch (Throwable ignored) {}
                }

                // 1.2 穿透检测玩家背包：针对 IAmmo 与 IAmmoBox (弹药箱) 精准识别
                GunStatus status = getGunStatus(stack);
                ResourceLocation ammoRes = (status.ammoId != null && !status.ammoId.isEmpty()) ? ResourceLocation.tryParse(status.ammoId) : null;

                for (ItemStack invStack : player.getInventory().items) {
                    if (invStack.isEmpty() || invStack.getCount() <= 0) continue;

                    // 检测是否为匹配子弹 (IAmmo)
                    if (taczIAmmoClass != null && taczIAmmoClass.isInstance(invStack.getItem())) {
                        try {
                            if (taczIsAmmoOfGunMethod != null && (boolean) taczIsAmmoOfGunMethod.invoke(invStack.getItem(), stack, invStack)) {
                                return true;
                            }
                        } catch (Throwable ignored) {}
                    }

                    // 检测是否为匹配弹药箱 (IAmmoBox)，严格校验余弹数 > 0
                    if (taczIAmmoBoxClass != null && taczIAmmoBoxClass.isInstance(invStack.getItem())) {
                        try {
                            if (taczIsAmmoBoxOfGunMethod != null && (boolean) taczIsAmmoBoxOfGunMethod.invoke(invStack.getItem(), stack, invStack)) {
                                int count = (taczGetAmmoCountMethod != null) ? (int) taczGetAmmoCountMethod.invoke(invStack.getItem(), invStack) : invStack.getCount();
                                if (count > 0) return true;
                            }
                        } catch (Throwable ignored) {}
                    }

                    // 注册名兜底匹配
                    if (ammoRes != null) {
                        ResourceLocation itemRes = ForgeRegistries.ITEMS.getKey(invStack.getItem());
                        if (ammoRes.equals(itemRes)) return true;
                    }
                }
            }
            return false;
        }

        // 2. Point Blank 备弹检测 (优先 canReloadGun / getCompatibleAmmo)
        if (isPointBlankGun(stack)) {
            initPbReflection();
            Object fmi = null;
            if (pbGetFireModeInstanceMethod != null) {
                try {
                    fmi = pbGetFireModeInstanceMethod.invoke(null, stack);
                } catch (Throwable ignored) {}
            }
            if (pbCanReloadGunMethod != null) {
                try {
                    int canReload = (int) pbCanReloadGunMethod.invoke(stack.getItem(), stack, player, fmi);
                    if (canReload > 0) return true;
                } catch (Throwable ignored) {}
            }
            if (pbGetCompatibleAmmoMethod != null) {
                try {
                    Object compAmmoList = pbGetCompatibleAmmoMethod.invoke(stack.getItem());
                    if (compAmmoList instanceof Collection<?> collection) {
                        for (Object ammoItemObj : collection) {
                            if (ammoItemObj instanceof Item ammoItem) {
                                for (ItemStack invStack : player.getInventory().items) {
                                    if (!invStack.isEmpty() && invStack.getCount() > 0 && invStack.getItem() == ammoItem) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // 退避兜底：扫描背包中是否有 Point Blank 弹药物品
            for (ItemStack invStack : player.getInventory().items) {
                if (!invStack.isEmpty() && invStack.getCount() > 0) {
                    ResourceLocation itemRes = ForgeRegistries.ITEMS.getKey(invStack.getItem());
                    if (itemRes != null && itemRes.getNamespace().equalsIgnoreCase("pointblank")) {
                        String p = itemRes.getPath();
                        if (p.contains("ammo") || p.contains("bullet") || p.contains("round") || p.contains("box") || p.contains("cartridge")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        // 3. Scorched Guns 备弹检测 (原厂 Gun.findAmmoStack 真实弹药解析)
        if (isScorchedGun(stack)) {
            initScgunsReflection();
            if (scgunsFindAmmoStackMethod != null && scgunsGetCurrentAmmoItemMethod != null && scgunsGetModifiedGunMethod != null) {
                try {
                    Object gun = scgunsGetModifiedGunMethod.invoke(stack.getItem(), stack);
                    if (gun != null) {
                        Item ammoItem = (Item) scgunsGetCurrentAmmoItemMethod.invoke(gun, stack);
                        if (ammoItem != null) {
                            ItemStack[] stacks = (ItemStack[]) scgunsFindAmmoStackMethod.invoke(null, player, ammoItem);
                            if (stacks != null && stacks.length > 0) return true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            // 退避兜底：检查背包中属于 scguns 命名空间的物品
            for (ItemStack invStack : player.getInventory().items) {
                if (!invStack.isEmpty()) {
                    ResourceLocation itemRes = ForgeRegistries.ITEMS.getKey(invStack.getItem());
                    if (itemRes != null && (itemRes.getNamespace().equalsIgnoreCase("scguns") || itemRes.getNamespace().equalsIgnoreCase("scorchedguns"))) {
                        String p = itemRes.getPath();
                        if (p.contains("powder") || p.contains("ball") || p.contains("bullet") || p.contains("ammo") || p.contains("shell") || p.contains("cartridge")) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        // 4. JEG 备弹检测 (支持 SingleItem 填装物如绿宝石块、常规弹药、以及跨命名空间原版物品)
        if (isJegGun(stack)) {
            initJegReflection();
            ResourceLocation reloadItem = null;
            ResourceLocation projItem = null;

            if (jegGetModifiedGunMethod != null) {
                try {
                    Object gun = jegGetModifiedGunMethod.invoke(stack.getItem(), stack);
                    if (gun != null) {
                        if (jegGetReloadsMethod != null && jegReloadsGetReloadItemMethod != null) {
                            try {
                                Object reloads = jegGetReloadsMethod.invoke(gun);
                                if (reloads != null) {
                                    reloadItem = (ResourceLocation) jegReloadsGetReloadItemMethod.invoke(reloads);
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (jegGetProjectileMethod != null && jegProjGetItemMethod != null) {
                            try {
                                Object proj = jegGetProjectileMethod.invoke(gun);
                                if (proj != null) {
                                    projItem = (ResourceLocation) jegProjGetItemMethod.invoke(proj);
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                } catch (Throwable ignored) {}
            }

            if (jegFindAmmoStackMethod != null) {
                if (reloadItem != null) {
                    try {
                        ItemStack[] stacks = (ItemStack[]) jegFindAmmoStackMethod.invoke(null, player, reloadItem);
                        if (stacks != null && stacks.length > 0) return true;
                    } catch (Throwable ignored) {}
                }
                if (projItem != null) {
                    try {
                        ItemStack[] stacks = (ItemStack[]) jegFindAmmoStackMethod.invoke(null, player, projItem);
                        if (stacks != null && stacks.length > 0) return true;
                    } catch (Throwable ignored) {}
                }
            }

            // 背包穿透扫描：匹配 reloadItem (如 minecraft:emerald_block) 或 projItem (如 minecraft:emerald) 或 jeg 弹药
            for (ItemStack invStack : player.getInventory().items) {
                if (!invStack.isEmpty()) {
                    ResourceLocation itemRes = ForgeRegistries.ITEMS.getKey(invStack.getItem());
                    if (itemRes != null) {
                        if (reloadItem != null && reloadItem.equals(itemRes)) return true;
                        if (projItem != null && projItem.equals(itemRes)) return true;
                        if (itemRes.getNamespace().equalsIgnoreCase("jeg")) {
                            String p = itemRes.getPath();
                            if (p.contains("ammo") || p.contains("bullet") || p.contains("shell") || p.contains("round") || p.contains("cartridge") || p.contains("arrow")) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        // 5. CGM / 通用枪械退避方案：优先根据 ammoId 匹配，再扫描背包
        GunStatus status = getGunStatus(stack);
        if (status.ammoId != null && !status.ammoId.isEmpty()) {
            ResourceLocation ammoRes = ResourceLocation.tryParse(status.ammoId);
            if (ammoRes != null) {
                for (ItemStack invStack : player.getInventory().items) {
                    if (!invStack.isEmpty()) {
                        ResourceLocation itemRes = ForgeRegistries.ITEMS.getKey(invStack.getItem());
                        if (ammoRes.equals(itemRes)) {
                            return true;
                        }
                    }
                }
            }
        }

        ResourceLocation gunReg = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String gunNs = gunReg != null ? gunReg.getNamespace().toLowerCase(Locale.ROOT) : "";
        for (ItemStack invStack : player.getInventory().items) {
            if (!invStack.isEmpty()) {
                ResourceLocation itemRes = ForgeRegistries.ITEMS.getKey(invStack.getItem());
                if (itemRes != null) {
                    String itemNs = itemRes.getNamespace().toLowerCase(Locale.ROOT);
                    String itemPath = itemRes.getPath().toLowerCase(Locale.ROOT);
                    if ((itemNs.equals(gunNs) || itemNs.equals("minecraft")) && (itemPath.contains("ammo") || itemPath.contains("bullet") || itemPath.contains("magazine") || itemPath.contains("round") || itemPath.contains("powder") || itemPath.contains("arrow"))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 触发枪械换弹动作 (跨模组分流调度)
     */
    public static boolean triggerReload(Player player) {
        if (player == null) return false;
        ItemStack mainHand = player.getMainHandItem();

        // 核心防抖防打断：若当前武器已经在换弹中，严禁重复触发打断换弹！
        if (isReloading(player)) {
            return false;
        }

        // 1. TACZ 枪械
        if (isTaczGun(mainHand)) {
            initTaczReflection();
            if (taczAvailable) {
                try {
                    if (player instanceof net.minecraft.client.player.LocalPlayer localPlayer &&
                        taczClientGunOperatorClass != null && taczFromLocalPlayerMethod != null && taczClientReloadMethod != null) {
                        Object operator = taczFromLocalPlayerMethod.invoke(null, localPlayer);
                        if (operator != null) {
                            taczClientReloadMethod.invoke(operator);
                            return true;
                        }
                    }
                } catch (Throwable ignored) {}

                try {
                    if (taczFromLivingEntityMethod != null && taczReloadMethod != null) {
                        Object operator = taczFromLivingEntityMethod.invoke(null, player);
                        if (operator != null) {
                            taczReloadMethod.invoke(operator);
                            return true;
                        }
                    }
                } catch (Throwable ignored) {}

                try {
                    if (taczReloadKeyField != null) {
                        KeyMapping reloadKey = (KeyMapping) taczReloadKeyField.get(null);
                        if (reloadKey != null) {
                            KeyMapping.click(reloadKey.getKey());
                            return true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return false;
        }

        // 2. Point Blank 枪械 (优先模拟 RELOAD_KEY 按键，次级调用 tryReload)
        if (isPointBlankGun(mainHand)) {
            initPbReflection();
            boolean triggered = false;
            if (pbReloadKeyField != null) {
                try {
                    Object lazyObj = pbReloadKeyField.get(null);
                    if (lazyObj != null) {
                        KeyMapping reloadKey = null;
                        if (pbLazyGetMethod != null) {
                            reloadKey = (KeyMapping) pbLazyGetMethod.invoke(lazyObj);
                        } else {
                            Method getMethod = lazyObj.getClass().getMethod("get");
                            reloadKey = (KeyMapping) getMethod.invoke(lazyObj);
                        }
                        if (reloadKey != null) {
                            KeyMapping.click(reloadKey.getKey());
                            triggered = true;
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (!triggered && pbTryReloadMethod != null) {
                try {
                    triggered = (boolean) pbTryReloadMethod.invoke(mainHand.getItem(), player, mainHand);
                } catch (Throwable ignored) {}
            }
            return triggered;
        }

        // 3. JEG 枪械 (标记 NBT IsReloading 并调用 ReloadHandler.setReloading(true))
        if (isJegGun(mainHand)) {
            initJegReflection();
            CompoundTag tag = mainHand.getTag();
            if (tag != null) {
                tag.putBoolean("IsReloading", true);
            }
            if (jegReloadHandlerClass != null && jegGetReloadHandlerMethod != null && jegSetReloadingMethod != null) {
                try {
                    Object handler = jegGetReloadHandlerMethod.invoke(null);
                    if (handler != null) {
                        jegSetReloadingMethod.invoke(handler, true);
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
            if (jegKeyReloadField != null) {
                try {
                    KeyMapping reloadKey = (KeyMapping) jegKeyReloadField.get(null);
                    if (reloadKey != null) {
                        KeyMapping.click(reloadKey.getKey());
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
            return true;
        }

        // 4. Scorched Guns 枪械 (双管齐下：模拟 KEY_RELOAD 按键点击 + 调度 ReloadHandler)
        if (isScorchedGun(mainHand)) {
            initScgunsReflection();
            if (scgunsKeyReloadField != null) {
                try {
                    KeyMapping reloadKey = (KeyMapping) scgunsKeyReloadField.get(null);
                    if (reloadKey != null) {
                        KeyMapping.click(reloadKey.getKey());
                    }
                } catch (Throwable ignored) {}
            }
            if (scgunsReloadHandlerClass != null && scgunsGetReloadHandlerMethod != null && scgunsSetReloadingMethod != null) {
                try {
                    Object handler = scgunsGetReloadHandlerMethod.invoke(null);
                    if (handler != null) {
                        scgunsSetReloadingMethod.invoke(handler, true);
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
            return true;
        }

        // 5. CGM 枪械
        if (isCgmGun(mainHand)) {
            initCgmReflection();
            if (cgmReloadHandlerClass != null && cgmGetReloadHandlerMethod != null && cgmSetReloadingMethod != null) {
                try {
                    Object handler = cgmGetReloadHandlerMethod.invoke(null);
                    if (handler != null) {
                        cgmSetReloadingMethod.invoke(handler, true);
                        return true;
                    }
                } catch (Throwable ignored) {}
            }
        }

        return false;
    }

    /**
     * 判断当前枪械是否处于逐发装填状态 (如 Scorched Guns 手动/左轮一颗一颗填装)
     */
    public static boolean isManualReloading(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return false;
        if (isScorchedGun(stack)) {
            initScgunsReflection();
            CompoundTag tag = stack.getTag();
            if (tag != null && (tag.contains("scguns:IsReloading") || tag.contains("IsReloading"))) {
                boolean isRel = tag.getBoolean("scguns:IsReloading") || tag.getBoolean("IsReloading");
                if (isRel) {
                    try {
                        if (scgunsGetModifiedGunMethod != null && scgunsGetReloadsMethod != null && scgunsReloadsGetReloadTypeMethod != null) {
                            Object gun = scgunsGetModifiedGunMethod.invoke(stack.getItem(), stack);
                            if (gun != null) {
                                Object reloads = scgunsGetReloadsMethod.invoke(gun);
                                if (reloads != null) {
                                    Object rtype = scgunsReloadsGetReloadTypeMethod.invoke(reloads);
                                    if (rtype != null && (rtype == scgunsReloadTypeManualObj || rtype.toString().contains("MANUAL"))) {
                                        return true;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                    if (tag.contains("IsManualReload") && tag.getBoolean("IsManualReload")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 打断当前枪械换弹动作 (用于单发装填左轮/散弹在锁定敌人时中途紧急开火)
     */
    public static void interruptReload(Player player) {
        if (player == null) return;
        ItemStack mainHand = player.getMainHandItem();
        if (isScorchedGun(mainHand)) {
            initScgunsReflection();
            if (scgunsReloadHandlerClass != null && scgunsGetReloadHandlerMethod != null && scgunsSetReloadingMethod != null) {
                try {
                    Object handler = scgunsGetReloadHandlerMethod.invoke(null);
                    if (handler != null) {
                        scgunsSetReloadingMethod.invoke(handler, false);
                    }
                } catch (Throwable ignored) {}
            }
            CompoundTag tag = mainHand.getTag();
            if (tag != null) {
                tag.putString("scguns:ReloadState", "STOPPING");
                tag.putBoolean("scguns:IsPlayingReloadStop", true);
            }
        }
    }

    /**
     * 控制枪械射击击发状态 (Triggerbot 核心调度)
     * 
     * 智能分流:
     * - 手持 TACZ 枪械: 调度 TACZ ShootKey 控制器;
     * - 手持 Point Blank / JEG / Scorched Guns / CGM / 其他枪械: 模拟原版 keyAttack (模组均监听攻击键);
     * - 停止射击 (shoot == false): 同时释放 TACZ 控制器与 keyAttack，绝对防止按键黏连卡死!
     */
    public static void setTriggerShoot(boolean shoot, Player player) {
        ItemStack mainHand = (player != null) ? player.getMainHandItem() : ItemStack.EMPTY;
        boolean isTacz = isTaczGun(mainHand);

        // 1. 如果是 TACZ 枪械，或者正在停止射击 (shoot == false)
        if (isTacz || !shoot) {
            initTaczReflection();
            if (taczAvailable && taczShootControllerTickMethod != null) {
                try {
                    taczShootControllerTickMethod.invoke(null, shoot);
                    if (taczShootKeyField != null) {
                        KeyMapping shootKey = (KeyMapping) taczShootKeyField.get(null);
                        if (shootKey != null) {
                            KeyMapping.set(shootKey.getKey(), shoot);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. 如果不是 TACZ 枪械 (如 Point Blank, JEG, Scorched Guns, CGM)，或者正在停止射击 (shoot == false)
        if (!isTacz || !shoot) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.options != null && mc.options.keyAttack != null) {
                KeyMapping.set(mc.options.keyAttack.getKey(), shoot);
            }
            if (isJegGun(mainHand)) {
                initJegReflection();
                if (jegGetShootMappingMethod != null) {
                    try {
                        KeyMapping jegShootKey = (KeyMapping) jegGetShootMappingMethod.invoke(null);
                        if (jegShootKey != null && mc.options != null && jegShootKey != mc.options.keyAttack) {
                            KeyMapping.set(jegShootKey.getKey(), shoot);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        isTriggerShooting = shoot;
    }

    public static boolean isTriggerShooting() {
        return isTriggerShooting;
    }

    /**
     * 获取枪械唯一 ResourceLocation 标识 (如 tacz:hk416a5)
     */
    public static ResourceLocation getGunId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (isTaczGun(stack)) {
            initTaczReflection();
            if (taczAvailable && taczGetIGunOrNullMethod != null && taczGetGunIdMethod != null) {
                try {
                    Object iGunObj = taczGetIGunOrNullMethod.invoke(null, stack);
                    if (iGunObj != null) {
                        return (ResourceLocation) taczGetGunIdMethod.invoke(iGunObj, stack);
                    }
                } catch (Throwable ignored) {}
            }
        }
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("GunId")) {
            return ResourceLocation.tryParse(tag.getString("GunId"));
        }
        return ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    /**
     * 判断枪械是否处于半自动/单发/点射模式
     */
    public static boolean isSemiAuto(GunStatus gunStatus) {
        if (gunStatus == null || gunStatus.fireMode == null) return false;
        String m = gunStatus.fireMode.toUpperCase(Locale.ROOT);
        if (m.contains("RELEASE")) return false;
        return m.contains("SEMI") || m.contains("BURST") || m.contains("SINGLE") || m.contains("MANUAL");
    }

    /**
     * 判断枪械/弓是否处于蓄力释放模式 (如 JEG 复合弓/原始弓 RELEASE_FIRE)
     */
    public static boolean isReleaseFire(GunStatus gunStatus) {
        if (gunStatus == null || gunStatus.fireMode == null) return false;
        return gunStatus.fireMode.toUpperCase(Locale.ROOT).contains("RELEASE");
    }

    /**
     * 获取玩家当前手持蓄力武器的蓄力完成百分比 (0.0f ~ 1.0f)
     */
    public static float getChargeProgress(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty()) return 0.0f;
        if (isJegGun(stack)) {
            initJegReflection();
            if (jegChargeTrackerClass != null && jegGetChargeProgressMethod != null) {
                try {
                    return (float) jegGetChargeProgressMethod.invoke(null, player, stack);
                } catch (Throwable ignored) {}
            }
        }
        return 0.0f;
    }

    /**
     * 获取蓄力武器最大蓄力刻数 (默认 20 刻 / 1 秒)
     */
    public static int getMaxHoldFire(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 20;
        if (isJegGun(stack)) {
            initJegReflection();
            if (jegGetModifiedGunMethod != null && jegGetGeneralMethod != null && jegGenGetMaxHoldFireMethod != null) {
                try {
                    Object gun = jegGetModifiedGunMethod.invoke(stack.getItem(), stack);
                    if (gun != null) {
                        Object general = jegGetGeneralMethod.invoke(gun);
                        if (general != null) {
                            int val = (int) jegGenGetMaxHoldFireMethod.invoke(general);
                            if (val > 0) return val;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return 20;
    }

    /**
     * 获取 JEG 客户端当前射击蓄力刻数 (0 ~ maxHoldFire)
     */
    public static int getJegHoldFire() {
        initJegReflection();
        if (jegShootingHandlerClass != null && jegGetShootingHandlerMethod != null && jegGetHoldFireMethod != null) {
            try {
                Object handler = jegGetShootingHandlerMethod.invoke(null);
                if (handler != null) {
                    return (int) jegGetHoldFireMethod.invoke(handler);
                }
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    /**
     * 获取武器直观易读的战术名称 (支持 ItemStack 原生物品名)
     */
    /**
     * 获取武器简短、纯净的战术显示名称 (彻底去除 [枪械]、[枪] 等重复括号前缀与底层类名)
     */
    public static String getCleanGunName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "空手";
        if (isTaczGun(stack)) {
            initTaczReflection();
            if (taczAvailable && taczGetIGunOrNullMethod != null) {
                try {
                    Object iGun = taczGetIGunOrNullMethod.invoke(null, stack);
                    if (iGun != null && taczGetGunIdMethod != null) {
                        ResourceLocation gunId = (ResourceLocation) taczGetGunIdMethod.invoke(iGun, stack);
                        if (gunId != null) {
                            String langKey = gunId.getNamespace() + ".gun." + gunId.getPath() + ".name";
                            if (net.minecraft.client.resources.language.I18n.exists(langKey)) {
                                return net.minecraft.client.resources.language.I18n.get(langKey);
                            }
                            String altKey = "gun." + gunId.getNamespace() + "." + gunId.getPath() + ".name";
                            if (net.minecraft.client.resources.language.I18n.exists(altKey)) {
                                return net.minecraft.client.resources.language.I18n.get(altKey);
                            }
                            String tr = net.minecraft.network.chat.Component.translatable(langKey).getString();
                            if (!tr.equals(langKey)) return tr;
                            return formatGunId(gunId.getPath());
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        String hover = stack.getHoverName().getString();
        hover = hover.replaceAll("^\\[.*?\\]\\s*", "").trim();
        return hover.isEmpty() ? "现代枪械" : hover;
    }

    /**
     * 获取枪械已装配的配件名称列表 (瞄具、消音器、扩容弹匣、握把、激光、枪托等)
     */
    public static List<String> getInstalledAttachments(ItemStack stack) {
        List<String> list = new ArrayList<>();
        if (stack == null || stack.isEmpty()) return list;

        // 1. TACZ 配件提取
        if (isTaczGun(stack)) {
            initTaczReflection();
            if (taczAvailable && taczGetIGunOrNullMethod != null && taczGetAttachmentMethod != null && taczAttachmentTypeValuesMethod != null) {
                try {
                    Object iGun = taczGetIGunOrNullMethod.invoke(null, stack);
                    if (iGun != null) {
                        Object[] types = (Object[]) taczAttachmentTypeValuesMethod.invoke(null);
                        if (types != null) {
                            for (Object type : types) {
                                if ("NONE".equals(type.toString())) continue;
                                ItemStack attachStack = (ItemStack) taczGetAttachmentMethod.invoke(iGun, stack, type);
                                if (attachStack != null && !attachStack.isEmpty()) {
                                    String name = attachStack.getHoverName().getString();
                                    name = name.replaceAll("^\\[.*?\\]\\s*", "").trim();
                                    if (!name.isEmpty() && !list.contains(name)) {
                                        list.add(name);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        // 2. Point Blank 配件提取 (从 NBT 或配件槽)
        if (isPointBlankGun(stack)) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("Attachments")) {
                CompoundTag attTag = tag.getCompound("Attachments");
                for (String key : attTag.getAllKeys()) {
                    CompoundTag itemTag = attTag.getCompound(key);
                    ItemStack attachStack = ItemStack.of(itemTag);
                    if (!attachStack.isEmpty()) {
                        String name = attachStack.getHoverName().getString();
                        name = name.replaceAll("^\\[.*?\\]\\s*", "").trim();
                        if (!name.isEmpty() && !list.contains(name)) list.add(name);
                    }
                }
            }
        }

        return list;
    }

    public static String getReadableGunName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "空手模式";
        if (isTaczGun(stack)) {
            ResourceLocation gunId = getGunId(stack);
            if (gunId != null) {
                return getReadableGunName(gunId.toString());
            }
        }
        // 对于 Point Blank, JEG, Scorched Guns, CGM 以及其他模组，直接取 Minecraft 本地化展示名称
        String hoverName = stack.getHoverName().getString();
        hoverName = hoverName.replaceAll("^\\[.*?\\]\\s*", "").replaceAll("^§[0-9a-fk-or]\\[.*?\\]\\s*", "").trim();
        return "§6[枪械] " + hoverName;
    }

    /**
     * 将枪械 ID 格式化为直观易读的战术名称 (如 gun:tacz:glock_17 -> §6[枪械] 格洛克 17 手枪)
     */
    public static String getReadableGunName(String gunIdStr) {
        if (gunIdStr == null || gunIdStr.isEmpty()) return "现代枪械";
        String cleanId = gunIdStr;
        if (cleanId.startsWith("gun:")) {
            cleanId = cleanId.substring(4);
        }
        if (cleanId.contains("#")) {
            cleanId = cleanId.substring(0, cleanId.indexOf('#'));
        }
        if (cleanId.contains("modern_kinetic_gun")) {
            return "§6[枪械] 现代枪械";
        }
        ResourceLocation res = ResourceLocation.tryParse(cleanId);
        if (res != null) {
            // 1. 标准 TACZ 模组本地化语言键: <namespace>.gun.<path>.name
            String langKey = res.getNamespace() + ".gun." + res.getPath() + ".name";
            if (net.minecraft.client.resources.language.I18n.exists(langKey)) {
                return "§6[枪械] " + net.minecraft.client.resources.language.I18n.get(langKey);
            }
            String translated = net.minecraft.network.chat.Component.translatable(langKey).getString();
            if (!translated.equals(langKey)) {
                return "§6[枪械] " + translated;
            }

            // 2. 备选语言键: gun.<namespace>.<path>.name
            String altKey = "gun." + res.getNamespace() + "." + res.getPath() + ".name";
            if (net.minecraft.client.resources.language.I18n.exists(altKey)) {
                return "§6[枪械] " + net.minecraft.client.resources.language.I18n.get(altKey);
            }

            // 3. 通过 TACZ 客户端索引反射读取
            initTaczReflection();
            if (taczAvailable && taczGetClientGunIndexMethod != null && taczClientGunIndexGetNameMethod != null) {
                try {
                    Object clientOpt = taczGetClientGunIndexMethod.invoke(null, res);
                    if (clientOpt instanceof Optional<?> opt && opt.isPresent()) {
                        Object clientIndex = opt.get();
                        String nameKey = (String) taczClientGunIndexGetNameMethod.invoke(clientIndex);
                        if (nameKey != null && !nameKey.isEmpty()) {
                            String nameStr = net.minecraft.network.chat.Component.translatable(nameKey).getString();
                            if (!nameStr.equals(nameKey)) {
                                return "§6[枪械] " + nameStr;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return "§6[枪械] " + formatGunId(res.getPath());
        }
        return "§6[枪械] " + cleanId;
    }

    public static String formatGunId(String path) {
        if (path == null) return "未知枪械";
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.contains("compound_bow")) return "复合弓";
        if (lower.contains("primitive_bow")) return "原始弓";
        if (lower.contains("hk416") || lower.contains("416")) return "HK-416A5 突击步枪";
        if (lower.contains("glock")) return "格洛克 17 手枪";
        if (lower.contains("aug")) return "AUG 突击步枪";
        if (lower.contains("timeless")) return "永恒 .50 Z型";
        if (lower.contains("spr15")) return "SPR-15 HB 射手步枪";
        if (lower.contains("ak47") || lower.contains("ak_47")) return "AK-47 突击步枪";
        if (lower.contains("m4a1")) return "M4A1 卡宾枪";
        if (lower.contains("awp")) return "AWP 狙击步枪";
        if (lower.contains("m98b")) return "M98B 狙击步枪";
        if (lower.contains("deagle")) return "沙漠之鹰";
        if (lower.contains("m870")) return "雷明顿 M870";
        if (lower.contains("aa12")) return "AA-12 霰弹枪";
        if (lower.contains("rpg")) return "RPG-7 火箭筒";

        // 首字母大写美化并替换下划线
        StringBuilder sb = new StringBuilder();
        boolean cap = true;
        for (char c : path.toCharArray()) {
            if (c == '_' || c == '-') {
                sb.append(' ');
                cap = true;
            } else if (cap) {
                sb.append(Character.toUpperCase(c));
                cap = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // =========================================================================
    // 模组反射初始化方法群
    // =========================================================================

    private static synchronized void initTaczReflection() {
        if (taczChecked) return;
        taczChecked = true;
        try {
            taczIGunClass = Class.forName("com.tacz.guns.api.item.IGun");
            taczGetIGunOrNullMethod = taczIGunClass.getMethod("getIGunOrNull", ItemStack.class);
            taczGetGunIdMethod = taczIGunClass.getMethod("getGunId", ItemStack.class);
            taczGetCurrentAmmoMethod = taczIGunClass.getMethod("getCurrentAmmoCount", ItemStack.class);
            try {
                taczGetRPMMethod = taczIGunClass.getMethod("getRPM", ItemStack.class);
            } catch (Throwable ignored) {}
            try {
                taczGetFireModeMethod = taczIGunClass.getMethod("getFireMode", ItemStack.class);
            } catch (Throwable ignored) {}

            taczTimelessAPIClass = Class.forName("com.tacz.guns.api.TimelessAPI");
            taczGetCommonGunIndexMethod = taczTimelessAPIClass.getMethod("getCommonGunIndex", ResourceLocation.class);

            taczCommonGunIndexClass = Class.forName("com.tacz.guns.resource.index.CommonGunIndex");
            taczGetBulletDataMethod = taczCommonGunIndexClass.getMethod("getBulletData");
            taczGetGunDataMethod = taczCommonGunIndexClass.getMethod("getGunData");
            taczGetTypeMethod = taczCommonGunIndexClass.getMethod("getType");

            taczBulletDataClass = Class.forName("com.tacz.guns.resource.pojo.data.gun.BulletData");
            taczGetSpeedMethod = taczBulletDataClass.getMethod("getSpeed");
            taczGetGravityMethod = taczBulletDataClass.getMethod("getGravity");
            try {
                taczGetExtraDamageMethod = taczBulletDataClass.getMethod("getExtraDamage");
                taczExtraDamageClass = Class.forName("com.tacz.guns.resource.pojo.data.gun.ExtraDamage");
                taczGetHeadShotMultiplierMethod = taczExtraDamageClass.getMethod("getHeadShotMultiplier");
            } catch (Throwable ignored) {}

            taczGunDataClass = Class.forName("com.tacz.guns.resource.pojo.data.gun.GunData");
            try {
                taczGetRoundsPerMinuteMethod = taczGunDataClass.getMethod("getRoundsPerMinute");
                taczGetAmmoAmountMethod = taczGunDataClass.getMethod("getAmmoAmount");
                taczGetAmmoIdMethod = taczGunDataClass.getMethod("getAmmoId");
            } catch (Throwable ignored) {}

            try {
                taczAttachmentDataUtilsClass = Class.forName("com.tacz.guns.util.AttachmentDataUtils");
                taczGetAmmoCountWithAttachmentMethod = taczAttachmentDataUtilsClass.getMethod("getAmmoCountWithAttachment", ItemStack.class, taczGunDataClass);
                taczGetHeadShotMultiplierWithAttachmentMethod = taczAttachmentDataUtilsClass.getMethod("getHeadshotMultiplier", ItemStack.class, taczGunDataClass);
            } catch (Throwable ignored) {}

            try {
                taczAttachmentTypeClass = Class.forName("com.tacz.guns.api.item.attachment.AttachmentType");
                taczGetAttachmentMethod = taczIGunClass.getMethod("getAttachment", ItemStack.class, taczAttachmentTypeClass);
                taczAttachmentTypeValuesMethod = taczAttachmentTypeClass.getMethod("values");
            } catch (Throwable ignored) {}

            try {
                taczGetClientGunIndexMethod = taczTimelessAPIClass.getMethod("getClientGunIndex", ResourceLocation.class);
                taczClientGunIndexClass = Class.forName("com.tacz.guns.resource.index.ClientGunIndex");
                taczClientGunIndexGetNameMethod = taczClientGunIndexClass.getMethod("getName");
            } catch (Throwable ignored) {}

            taczIGunOperatorClass = Class.forName("com.tacz.guns.api.entity.IGunOperator");
            taczFromLivingEntityMethod = taczIGunOperatorClass.getMethod("fromLivingEntity", LivingEntity.class);
            try {
                taczReloadMethod = taczIGunOperatorClass.getMethod("reload");
            } catch (Throwable ignored) {}
            taczGetSynIsAimingMethod = taczIGunOperatorClass.getMethod("getSynIsAiming");
            taczGetSynIsBoltingMethod = taczIGunOperatorClass.getMethod("getSynIsBolting");
            taczGetSynReloadStateMethod = taczIGunOperatorClass.getMethod("getSynReloadState");

            taczReloadStateClass = Class.forName("com.tacz.guns.api.entity.ReloadState");
            taczGetCountDownMethod = taczReloadStateClass.getMethod("getCountDown");

            try {
                taczAbstractGunItemClass = Class.forName("com.tacz.guns.api.item.gun.AbstractGunItem");
                taczCanReloadMethod = taczAbstractGunItemClass.getMethod("canReload", LivingEntity.class, ItemStack.class);
            } catch (Throwable ignored) {}

            try {
                taczIAmmoClass = Class.forName("com.tacz.guns.api.item.IAmmo");
                taczIsAmmoOfGunMethod = taczIAmmoClass.getMethod("isAmmoOfGun", ItemStack.class, ItemStack.class);
            } catch (Throwable ignored) {}

            try {
                taczIAmmoBoxClass = Class.forName("com.tacz.guns.api.item.IAmmoBox");
                taczIsAmmoBoxOfGunMethod = taczIAmmoBoxClass.getMethod("isAmmoBoxOfGun", ItemStack.class, ItemStack.class);
                taczGetAmmoCountMethod = taczIAmmoBoxClass.getMethod("getAmmoCount", ItemStack.class);
            } catch (Throwable ignored) {}

            try {
                taczHasBulletInBarrelMethod = taczIGunClass.getMethod("hasBulletInBarrel", ItemStack.class);
            } catch (Throwable ignored) {}
            try {
                taczHasInventoryAmmoMethod = taczIGunClass.getMethod("hasInventoryAmmo", LivingEntity.class, ItemStack.class, boolean.class);
            } catch (Throwable ignored) {}

            try {
                taczClientGunOperatorClass = Class.forName("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
                taczFromLocalPlayerMethod = taczClientGunOperatorClass.getMethod("fromLocalPlayer", net.minecraft.client.player.LocalPlayer.class);
                taczClientReloadMethod = taczClientGunOperatorClass.getMethod("reload");
            } catch (Throwable ignored) {}

            try {
                taczReloadKeyClass = Class.forName("com.tacz.guns.client.input.ReloadKey");
                taczReloadKeyField = taczReloadKeyClass.getField("RELOAD_KEY");
            } catch (Throwable ignored) {}

            try {
                taczShootKeyClass = Class.forName("com.tacz.guns.client.input.ShootKey");
                taczShootControllerTickMethod = taczShootKeyClass.getMethod("shootControllerTick", boolean.class);
                taczShootKeyField = taczShootKeyClass.getField("SHOOT_KEY");
            } catch (Throwable ignored) {}

            taczAvailable = true;
        } catch (Throwable ignored) {
            taczAvailable = false;
        }
    }

    private static synchronized void initPbReflection() {
        if (pbChecked) return;
        pbChecked = true;
        try {
            pbGunItemClass = Class.forName("com.vicmatskiv.pointblank.item.GunItem");
            try {
                Class<?> fireModeClass = Class.forName("com.vicmatskiv.pointblank.item.FireModeInstance");
                pbGetAmmoMethod = pbGunItemClass.getMethod("getAmmo", ItemStack.class, fireModeClass);
                pbGetMaxAmmoCapacityMethod = pbGunItemClass.getMethod("getMaxAmmoCapacity", ItemStack.class, fireModeClass);
                pbFmiGetTypeMethod = fireModeClass.getMethod("getType");
                pbFmiGetRpmMethod = fireModeClass.getMethod("getRpm");
            } catch (Throwable ignored) {}
            try {
                pbGetFireModeInstanceMethod = pbGunItemClass.getMethod("getFireModeInstance", ItemStack.class);
            } catch (Throwable ignored) {}
            try {
                pbTryReloadMethod = pbGunItemClass.getMethod("tryReload", Player.class, ItemStack.class);
            } catch (Throwable ignored) {}
            try {
                Class<?> fmiClass = Class.forName("com.vicmatskiv.pointblank.item.FireModeInstance");
                pbCanReloadGunMethod = pbGunItemClass.getMethod("canReloadGun", ItemStack.class, Player.class, fmiClass);
            } catch (Throwable ignored) {}
            try {
                pbGetCompatibleAmmoMethod = pbGunItemClass.getMethod("getCompatibleAmmo");
            } catch (Throwable ignored) {}
            try {
                pbRequestReloadMethod = pbGunItemClass.getMethod("requestReloadFromServer", Player.class, ItemStack.class);
            } catch (Throwable ignored) {}
            try {
                pbGunClientStateClass = Class.forName("com.vicmatskiv.pointblank.client.GunClientState");
                pbGetClientStateMethod = pbGunClientStateClass.getMethod("getState", Player.class, ItemStack.class, int.class, boolean.class);
                pbStateIsReloadingMethod = pbGunClientStateClass.getMethod("isReloading");
                pbStateIsPreparingReloadMethod = pbGunClientStateClass.getMethod("isPreparingReload");
            } catch (Throwable ignored) {}
            try {
                pbResolveSlotIndexMethod = pbGunItemClass.getMethod("resolveSlotIndex", Player.class, ItemStack.class);
            } catch (Throwable ignored) {}
            try {
                Class<?> eventHandler = Class.forName("com.vicmatskiv.pointblank.client.ClientEventHandler");
                pbReloadKeyField = eventHandler.getField("RELOAD_KEY");
                if (pbReloadKeyField != null) {
                    Class<?> type = pbReloadKeyField.getType();
                    try {
                        pbLazyGetMethod = type.getMethod("get");
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            pbAvailable = true;
        } catch (Throwable ignored) {
            pbAvailable = false;
        }
    }

    private static synchronized void initJegReflection() {
        if (jegChecked) return;
        jegChecked = true;
        try {
            jegGunItemClass = Class.forName("ttv.migami.jeg.item.GunItem");
            jegGunClass = Class.forName("ttv.migami.jeg.common.Gun");
            try {
                jegGetModifiedGunMethod = jegGunItemClass.getMethod("getModifiedGun", ItemStack.class);
                jegFindAmmoStackMethod = jegGunClass.getMethod("findAmmoStack", Player.class, ResourceLocation.class);
                jegGetProjectileMethod = jegGunClass.getMethod("getProjectile");
                jegGetGeneralMethod = jegGunClass.getMethod("getGeneral");

                Class<?> projClass = Class.forName("ttv.migami.jeg.common.Gun$Projectile");
                jegProjGetItemMethod = projClass.getMethod("getItem");

                Class<?> genClass = Class.forName("ttv.migami.jeg.common.Gun$General");
                jegGenGetFireModeMethod = genClass.getMethod("getFireMode");
                jegGenGetRateMethod = genClass.getMethod("getRate");
                try {
                    Class<?> fmClass = Class.forName("ttv.migami.jeg.common.Gun$FireMode");
                    jegFireModeGetIdMethod = fmClass.getMethod("getId");
                } catch (Throwable ignored) {}
                try {
                    jegGenGetMaxHoldFireMethod = genClass.getMethod("getMaxHoldFire");
                } catch (Throwable ignored) {}

                try {
                    jegGetReloadsMethod = jegGunClass.getMethod("getReloads");
                    Class<?> reloadsClass = Class.forName("ttv.migami.jeg.common.Gun$Reloads");
                    jegReloadsGetMaxAmmoMethod = reloadsClass.getMethod("getMaxAmmo");
                    try {
                        jegReloadsGetReloadItemMethod = reloadsClass.getMethod("getReloadItem");
                    } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}

                try {
                    jegProjGetSpeedMethod = projClass.getMethod("getSpeed");
                    jegProjIsGravityMethod = projClass.getMethod("isGravity");
                    jegProjGetHeadshotMultiplierMethod = projClass.getMethod("getHeadshotMultiplier");
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
            try {
                jegChargeTrackerClass = Class.forName("ttv.migami.jeg.common.ChargeTracker");
                jegGetChargeProgressMethod = jegChargeTrackerClass.getMethod("getChargeProgress", Player.class, ItemStack.class);
            } catch (Throwable ignored) {}
            try {
                Class<?> kbClass = Class.forName("ttv.migami.jeg.client.KeyBinds");
                jegKeyReloadField = kbClass.getField("KEY_RELOAD");
                try {
                    jegGetShootMappingMethod = kbClass.getMethod("getShootMapping");
                    jegGetAimMappingMethod = kbClass.getMethod("getAimMapping");
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}
            try {
                jegReloadHandlerClass = Class.forName("ttv.migami.jeg.client.handler.ReloadHandler");
                jegGetReloadHandlerMethod = jegReloadHandlerClass.getMethod("get");
                jegSetReloadingMethod = jegReloadHandlerClass.getMethod("setReloading", boolean.class);
                jegGetReloadTimerMethod = jegReloadHandlerClass.getMethod("getReloadTimer");
            } catch (Throwable ignored) {}
            try {
                jegShootingHandlerClass = Class.forName("ttv.migami.jeg.client.handler.ShootingHandler");
                jegGetShootingHandlerMethod = jegShootingHandlerClass.getMethod("get");
                jegGetHoldFireMethod = jegShootingHandlerClass.getMethod("getHoldFire");
            } catch (Throwable ignored) {}
            try {
                jegAimingHandlerClass = Class.forName("ttv.migami.jeg.client.handler.AimingHandler");
                jegGetAimingHandlerMethod = jegAimingHandlerClass.getMethod("get");
                jegIsAimingMethod = jegAimingHandlerClass.getMethod("isAiming");
            } catch (Throwable ignored) {}
            jegAvailable = true;
        } catch (Throwable ignored) {
            jegAvailable = false;
        }
    }

    private static synchronized void initScgunsReflection() {
        if (scgunsChecked) return;
        scgunsChecked = true;
        try {
            scgunsGunItemClass = Class.forName("top.ribs.scguns.item.GunItem");
            scgunsGunClass = Class.forName("top.ribs.scguns.common.Gun");
            try {
                scgunsGetModifiedGunMethod = scgunsGunItemClass.getMethod("getModifiedGun", ItemStack.class);
                scgunsFindAmmoStackMethod = scgunsGunClass.getMethod("findAmmoStack", Player.class, Item.class);
                scgunsGetCurrentAmmoItemMethod = scgunsGunClass.getMethod("getCurrentAmmoItem", ItemStack.class);
                scgunsGetMaxAmmoMethod = scgunsGunClass.getMethod("getMaxAmmo", ItemStack.class);
                scgunsGetAmmoCountMethod = scgunsGunClass.getMethod("getAmmoCount", ItemStack.class);
                scgunsGetGeneralMethod = scgunsGunClass.getMethod("getGeneral");
                scgunsGetReloadsMethod = scgunsGunClass.getMethod("getReloads");

                Class<?> genClass = Class.forName("top.ribs.scguns.common.Gun$General");
                scgunsGenIsAutoMethod = genClass.getMethod("isAuto");
                scgunsGenIsRevolverMethod = genClass.getMethod("isRevolver");
                scgunsGenGetRateMethod = genClass.getMethod("getRate");

                Class<?> reloadsClass = Class.forName("top.ribs.scguns.common.Gun$Reloads");
                scgunsReloadsGetReloadTypeMethod = reloadsClass.getMethod("getReloadType");

                Class<?> reloadTypeClass = Class.forName("top.ribs.scguns.common.ReloadType");
                Field manualField = reloadTypeClass.getField("MANUAL");
                scgunsReloadTypeManualObj = manualField.get(null);
            } catch (Throwable ignored) {}
            try {
                Class<?> kbClass = Class.forName("top.ribs.scguns.client.KeyBinds");
                scgunsKeyReloadField = kbClass.getField("KEY_RELOAD");
            } catch (Throwable ignored) {}
            try {
                scgunsReloadHandlerClass = Class.forName("top.ribs.scguns.client.handler.ReloadHandler");
                scgunsGetReloadHandlerMethod = scgunsReloadHandlerClass.getMethod("get");
                scgunsSetReloadingMethod = scgunsReloadHandlerClass.getMethod("setReloading", boolean.class);
                scgunsGetReloadTimerMethod = scgunsReloadHandlerClass.getMethod("getReloadTimer");
            } catch (Throwable ignored) {}
            try {
                scgunsAimingHandlerClass = Class.forName("top.ribs.scguns.client.handler.AimingHandler");
                scgunsGetAimingHandlerMethod = scgunsAimingHandlerClass.getMethod("get");
                scgunsIsAimingMethod = scgunsAimingHandlerClass.getMethod("isAiming");
            } catch (Throwable ignored) {}
            scgunsAvailable = true;
        } catch (Throwable ignored) {
            scgunsAvailable = false;
        }
    }

    private static synchronized void initCgmReflection() {
        if (cgmChecked) return;
        cgmChecked = true;
        try {
            cgmGunItemClass = Class.forName("com.mrcrayfish.guns.item.GunItem");
            try {
                cgmReloadHandlerClass = Class.forName("com.mrcrayfish.guns.client.handler.ReloadHandler");
                cgmGetReloadHandlerMethod = cgmReloadHandlerClass.getMethod("get");
                cgmSetReloadingMethod = cgmReloadHandlerClass.getMethod("setReloading", boolean.class);
                cgmGetReloadTimerMethod = cgmReloadHandlerClass.getMethod("getReloadTimer");
            } catch (Throwable ignored) {}
            try {
                cgmAimingHandlerClass = Class.forName("com.mrcrayfish.guns.client.handler.AimingHandler");
                cgmGetAimingHandlerMethod = cgmAimingHandlerClass.getMethod("get");
                cgmIsAimingMethod = cgmAimingHandlerClass.getMethod("isAiming");
            } catch (Throwable ignored) {}
            cgmAvailable = true;
        } catch (Throwable ignored) {
            cgmAvailable = false;
        }
    }
}
