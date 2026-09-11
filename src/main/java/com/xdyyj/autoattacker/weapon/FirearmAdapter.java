package com.xdyyj.autoattacker.weapon;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

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

    private static final Logger LOGGER = LogUtils.getLogger();

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
            Object iGun = safeInvoke(taczGetIGunOrNullMethod, null, stack);
            return iGun != null;
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
                GunStatus status = invokeSilently(() -> {
                    Object iGunObj = safeInvoke(taczGetIGunOrNullMethod, null, stack);
                    if (iGunObj != null) {
                        ResourceLocation gunId = safeInvoke(taczGetGunIdMethod, iGunObj, stack);
                        int curAmmo = safeInvokeOrDefault(taczGetCurrentAmmoMethod, iGunObj, 0, stack);
                        boolean hasBulletInBarrel = false;
                        if (taczHasBulletInBarrelMethod != null) {
                            hasBulletInBarrel = safeInvokeOrDefault(taczHasBulletInBarrelMethod, iGunObj, false, stack);
                        }
                        int rpm = (taczGetRPMMethod != null) ? safeInvokeOrDefault(taczGetRPMMethod, iGunObj, 600, stack) : 600;
                        Object fireModeObj = (taczGetFireModeMethod != null) ? safeInvoke(taczGetFireModeMethod, iGunObj, stack) : null;
                        String fireMode = fireModeObj != null ? fireModeObj.toString() : "AUTO";

                        // 检索 CommonGunIndex
                        Object gunIndexOpt = safeInvoke(taczGetCommonGunIndexMethod, null, gunId);
                        if (gunIndexOpt instanceof Optional<?> opt && opt.isPresent()) {
                            Object gunIndex = opt.get();
                            String rawType = safeInvoke(taczGetTypeMethod, gunIndex);
                            String typeName = translateGunType(rawType);

                            Object bulletData = safeInvoke(taczGetBulletDataMethod, gunIndex);
                            float speedMs = safeInvokeOrDefault(taczGetSpeedMethod, bulletData, 0.0f);
                            float bulletSpeedBlocksPerTick = speedMs / 20.0f;
                            float gravity = safeInvokeOrDefault(taczGetGravityMethod, bulletData, 0.0f);

                            float headshotMult = 1.5f;
                            if (taczGetExtraDamageMethod != null && taczGetHeadShotMultiplierMethod != null) {
                                Object extraDamage = safeInvoke(taczGetExtraDamageMethod, bulletData);
                                if (extraDamage != null) {
                                    headshotMult = safeInvokeOrDefault(taczGetHeadShotMultiplierMethod, extraDamage, 1.5f);
                                }
                            }

                            int maxAmmo = -1;
                            String ammoId = "";
                            if (taczGetGunDataMethod != null) {
                                Object gunData = safeInvoke(taczGetGunDataMethod, gunIndex);
                                if (gunData != null) {
                                    // 优先使用 AttachmentDataUtils 计算包含扩容弹匣/鼓包在内的最终实际弹药容量
                                    if (taczGetAmmoCountWithAttachmentMethod != null) {
                                        maxAmmo = safeInvokeOrDefault(taczGetAmmoCountWithAttachmentMethod, null, -1, stack, gunData);
                                    }
                                    if (maxAmmo <= 0 && taczGetAmmoAmountMethod != null) {
                                        maxAmmo = safeInvokeOrDefault(taczGetAmmoAmountMethod, gunData, -1);
                                    }
                                    // 获取包含配件加成后的最终实际爆头倍率
                                    if (taczGetHeadShotMultiplierWithAttachmentMethod != null) {
                                        double hm = safeInvokeOrDefault(taczGetHeadShotMultiplierWithAttachmentMethod, null, (double) headshotMult, stack, gunData);
                                        headshotMult = (float) hm;
                                    }
                                    if (taczGetAmmoIdMethod != null) {
                                        ResourceLocation ammoRes = safeInvoke(taczGetAmmoIdMethod, gunData);
                                        if (ammoRes != null) ammoId = ammoRes.toString();
                                    }
                                    if (rpm <= 0 && taczGetRoundsPerMinuteMethod != null) {
                                        rpm = safeInvokeOrDefault(taczGetRoundsPerMinuteMethod, gunData, 600);
                                    }
                                }
                            }

                            List<String> attachments = getInstalledAttachments(stack);

                            return new GunStatus(true, typeName, curAmmo, maxAmmo,
                                    bulletSpeedBlocksPerTick, speedMs, gravity, headshotMult, rpm, ammoId, fireMode, hasBulletInBarrel, attachments);
                        }
                    }
                    return null;
                }, null);
                if (status != null) return status;
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
                Object fmi = safeInvoke(pbGetFireModeInstanceMethod, null, stack);
                if (fmi != null) {
                    if (pbGetAmmoMethod != null) {
                        curAmmo = safeInvokeOrDefault(pbGetAmmoMethod, null, -1, stack, fmi);
                    }
                    if (pbFmiGetRpmMethod != null) {
                        rpm = safeInvokeOrDefault(pbFmiGetRpmMethod, fmi, 650);
                    }
                    if (pbFmiGetTypeMethod != null) {
                        Object typeObj = safeInvoke(pbFmiGetTypeMethod, fmi);
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
                        maxAmmo = safeInvokeOrDefault(pbGetMaxAmmoCapacityMethod, stack.getItem(), -1, stack, fmi);
                    }
                }
            }
            if (curAmmo < 0 && tag != null) {
                if (tag.contains("ammo")) curAmmo = tag.getInt("ammo");
                else if (tag.contains("Ammo")) curAmmo = tag.getInt("Ammo");
            }
            if (maxAmmo < 0 && pbGetMaxAmmoCapacityMethod != null) {
                maxAmmo = safeInvokeOrDefault(pbGetMaxAmmoCapacityMethod, stack.getItem(), -1, stack, (Object) null);
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
                maxAmmo = safeInvokeOrDefault(scgunsGetMaxAmmoMethod, null, -1, stack);
            }
            int rpm = 600;
            String fireMode = "AUTO";
            if (scgunsGetModifiedGunMethod != null && scgunsGunItemClass.isInstance(stack.getItem())) {
                Object gun = safeInvoke(scgunsGetModifiedGunMethod, stack.getItem(), stack);
                if (gun != null && scgunsGetGeneralMethod != null) {
                    Object general = safeInvoke(scgunsGetGeneralMethod, gun);
                    if (general != null) {
                        if (scgunsGenGetRateMethod != null) {
                            int rateTicks = safeInvokeOrDefault(scgunsGenGetRateMethod, general, 0);
                            if (rateTicks > 0) rpm = Math.round(1200.0f / rateTicks);
                        }
                        boolean isAuto = true;
                        if (scgunsGenIsAutoMethod != null) {
                            isAuto = safeInvokeOrDefault(scgunsGenIsAutoMethod, general, true);
                        }
                        boolean isRevolver = false;
                        if (scgunsGenIsRevolverMethod != null) {
                            isRevolver = safeInvokeOrDefault(scgunsGenIsRevolverMethod, general, false);
                        }
                        if (!isAuto || isRevolver) {
                            fireMode = "SEMI";
                        }
                    }
                }
            }

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
            if (jegGetModifiedGunMethod != null && jegGunItemClass.isInstance(stack.getItem())) {
                Object gun = safeInvoke(jegGetModifiedGunMethod, stack.getItem(), stack);
                if (gun != null) {
                    if (jegGetGeneralMethod != null) {
                        Object general = safeInvoke(jegGetGeneralMethod, gun);
                        if (general != null) {
                            if (jegGenGetRateMethod != null) {
                                int rateTicks = safeInvokeOrDefault(jegGenGetRateMethod, general, 0);
                                if (rateTicks > 0) rpm = Math.round(1200.0f / rateTicks);
                            }
                            if (jegGenGetFireModeMethod != null) {
                                Object fmObj = safeInvoke(jegGenGetFireModeMethod, general);
                                if (fmObj != null) {
                                    String fmStr = "";
                                    if (jegFireModeGetIdMethod != null) {
                                        Object idObj = safeInvoke(jegFireModeGetIdMethod, fmObj);
                                        if (idObj != null) fmStr = idObj.toString().toLowerCase(Locale.ROOT);
                                    } else {
                                        Method getIdMethod = safeGetMethod(fmObj.getClass(), "getId");
                                        Object idObj = safeInvoke(getIdMethod, fmObj);
                                        if (idObj != null) fmStr = idObj.toString().toLowerCase(Locale.ROOT);
                                    }
                                    if (fmStr.isEmpty()) {
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
                        Object proj = safeInvoke(jegGetProjectileMethod, gun);
                        if (proj != null) {
                            if (jegProjGetSpeedMethod != null) {
                                Number speedNum = safeInvoke(jegProjGetSpeedMethod, proj);
                                if (speedNum != null) bulletSpeed = speedNum.floatValue();
                            }
                            if (jegProjIsGravityMethod != null) {
                                boolean hasGrav = safeInvokeOrDefault(jegProjIsGravityMethod, proj, false);
                                bulletGravity = hasGrav ? 0.015 : 0.003;
                            }
                            if (jegProjGetHeadshotMultiplierMethod != null) {
                                Number hsNum = safeInvoke(jegProjGetHeadshotMultiplierMethod, proj);
                                if (hsNum != null) headshotMult = hsNum.floatValue();
                            }
                            if (jegProjGetItemMethod != null) {
                                Object ammoRes = safeInvoke(jegProjGetItemMethod, proj);
                                if (ammoRes != null) ammoId = ammoRes.toString();
                            }
                        }
                    }
                    if (jegGetReloadsMethod != null && jegReloadsGetMaxAmmoMethod != null) {
                        Object reloads = safeInvoke(jegGetReloadsMethod, gun);
                        if (reloads != null) {
                            maxAmmo = safeInvokeOrDefault(jegReloadsGetMaxAmmoMethod, reloads, -1);
                        }
                    }
                }
            }

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
                Object operator = safeInvoke(taczFromLivingEntityMethod, null, player);
                if (operator != null) {
                    Boolean res = safeInvoke(taczGetSynIsAimingMethod, operator);
                    if (res != null) return res;
                }
            }
            return false;
        }

        // 2. JEG 开镜感知
        if (isJegGun(mainHand)) {
            initJegReflection();
            if (jegAimingHandlerClass != null && jegGetAimingHandlerMethod != null && jegIsAimingMethod != null) {
                Object handler = safeInvoke(jegGetAimingHandlerMethod, null);
                if (handler != null) {
                    return safeInvokeOrDefault(jegIsAimingMethod, handler, false);
                }
            }
        }

        // 3. Scorched Guns 开镜感知
        if (isScorchedGun(mainHand)) {
            initScgunsReflection();
            if (scgunsAimingHandlerClass != null && scgunsGetAimingHandlerMethod != null && scgunsIsAimingMethod != null) {
                Object handler = safeInvoke(scgunsGetAimingHandlerMethod, null);
                if (handler != null) {
                    return safeInvokeOrDefault(scgunsIsAimingMethod, handler, false);
                }
            }
        }

        // 4. CGM 开镜感知
        if (isCgmGun(mainHand)) {
            initCgmReflection();
            if (cgmAimingHandlerClass != null && cgmGetAimingHandlerMethod != null && cgmIsAimingMethod != null) {
                Object handler = safeInvoke(cgmGetAimingHandlerMethod, null);
                if (handler != null) {
                    return safeInvokeOrDefault(cgmIsAimingMethod, handler, false);
                }
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
                Object operator = safeInvoke(taczFromLivingEntityMethod, null, player);
                if (operator != null) {
                    Boolean res = safeInvoke(taczGetSynIsBoltingMethod, operator);
                    if (res != null) return res;
                }
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
                Object operator = safeInvoke(taczFromLivingEntityMethod, null, player);
                if (operator != null) {
                    Object reloadState = safeInvoke(taczGetSynReloadStateMethod, operator);
                    if (reloadState != null) {
                        Long countDown = safeInvoke(taczGetCountDownMethod, reloadState);
                        return countDown != null && countDown > 0L;
                    }
                }
            }
            return false;
        }

        // 2. Point Blank 换弹检测 (客户端状态机全槽位扫描 + NBT 兜底)
        if (isPointBlankGun(mainHand)) {
            initPbReflection();
            if (pbGunClientStateClass != null && pbGetClientStateMethod != null) {
                Boolean isPbReloading = invokeSilently(() -> {
                    int sel = player.getInventory().selected;
                    if (pbResolveSlotIndexMethod != null) {
                        int resolved = safeInvokeOrDefault(pbResolveSlotIndexMethod, null, -1, player, mainHand);
                        if (resolved >= 0) sel = resolved;
                    }
                    Object state = safeInvoke(pbGetClientStateMethod, null, player, mainHand, sel, false);
                    if (state != null) {
                        boolean reloading = (pbStateIsReloadingMethod != null) && safeInvokeOrDefault(pbStateIsReloadingMethod, state, false);
                        boolean preparing = (pbStateIsPreparingReloadMethod != null) && safeInvokeOrDefault(pbStateIsPreparingReloadMethod, state, false);
                        if (reloading || preparing) {
                            return true;
                        }
                    }
                    // 全局槽位防漏扫描：遍历 0~8 号热键栏与 40 号副手
                    for (int s = 0; s <= 8; s++) {
                        if (s == sel) continue;
                        Object altState = safeInvoke(pbGetClientStateMethod, null, player, mainHand, s, false);
                        if (altState != null) {
                            boolean reloading = (pbStateIsReloadingMethod != null) && safeInvokeOrDefault(pbStateIsReloadingMethod, altState, false);
                            boolean preparing = (pbStateIsPreparingReloadMethod != null) && safeInvokeOrDefault(pbStateIsPreparingReloadMethod, altState, false);
                            if (reloading || preparing) return true;
                        }
                    }
                    Object offState = safeInvoke(pbGetClientStateMethod, null, player, mainHand, 40, true);
                    if (offState != null) {
                        boolean reloading = (pbStateIsReloadingMethod != null) && safeInvokeOrDefault(pbStateIsReloadingMethod, offState, false);
                        boolean preparing = (pbStateIsPreparingReloadMethod != null) && safeInvokeOrDefault(pbStateIsPreparingReloadMethod, offState, false);
                        if (reloading || preparing) return true;
                    }
                    return false;
                }, false);
                if (Boolean.TRUE.equals(isPbReloading)) return true;
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
                Object handler = safeInvoke(jegGetReloadHandlerMethod, null);
                if (handler != null) {
                    int timer = safeInvokeOrDefault(jegGetReloadTimerMethod, handler, 0);
                    if (timer > 0) return true;
                }
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
                Object handler = safeInvoke(scgunsGetReloadHandlerMethod, null);
                if (handler != null) {
                    int timer = safeInvokeOrDefault(scgunsGetReloadTimerMethod, handler, 0);
                    if (timer > 0) return true;
                }
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
                Object handler = safeInvoke(cgmGetReloadHandlerMethod, null);
                if (handler != null) {
                    int timer = safeInvokeOrDefault(cgmGetReloadTimerMethod, handler, 0);
                    return timer > 0;
                }
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
                    boolean canReload = safeInvokeOrDefault(taczCanReloadMethod, stack.getItem(), false, player, stack);
                    if (canReload) return true;
                }

                // 1.2 穿透检测玩家背包：针对 IAmmo 与 IAmmoBox (弹药箱) 精准识别
                GunStatus status = getGunStatus(stack);
                ResourceLocation ammoRes = (status.ammoId != null && !status.ammoId.isEmpty()) ? ResourceLocation.tryParse(status.ammoId) : null;

                for (ItemStack invStack : player.getInventory().items) {
                    if (invStack.isEmpty() || invStack.getCount() <= 0) continue;

                    // 检测是否为匹配子弹 (IAmmo)
                    if (taczIAmmoClass != null && taczIAmmoClass.isInstance(invStack.getItem())) {
                        if (taczIsAmmoOfGunMethod != null && safeInvokeOrDefault(taczIsAmmoOfGunMethod, invStack.getItem(), false, stack, invStack)) {
                            return true;
                        }
                    }

                    // 检测是否为匹配弹药箱 (IAmmoBox)，严格校验余弹数 > 0
                    if (taczIAmmoBoxClass != null && taczIAmmoBoxClass.isInstance(invStack.getItem())) {
                        if (taczIsAmmoBoxOfGunMethod != null && safeInvokeOrDefault(taczIsAmmoBoxOfGunMethod, invStack.getItem(), false, stack, invStack)) {
                            int count = (taczGetAmmoCountMethod != null) ? safeInvokeOrDefault(taczGetAmmoCountMethod, invStack.getItem(), invStack.getCount(), invStack) : invStack.getCount();
                            if (count > 0) return true;
                        }
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
                fmi = safeInvoke(pbGetFireModeInstanceMethod, null, stack);
            }
            if (pbCanReloadGunMethod != null) {
                int canReload = safeInvokeOrDefault(pbCanReloadGunMethod, stack.getItem(), 0, stack, player, fmi);
                if (canReload > 0) return true;
            }
            if (pbGetCompatibleAmmoMethod != null) {
                Object compAmmoList = safeInvoke(pbGetCompatibleAmmoMethod, stack.getItem());
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
                Object gun = safeInvoke(scgunsGetModifiedGunMethod, stack.getItem(), stack);
                if (gun != null) {
                    Item ammoItem = safeInvoke(scgunsGetCurrentAmmoItemMethod, gun, stack);
                    if (ammoItem != null) {
                        ItemStack[] stacks = safeInvoke(scgunsFindAmmoStackMethod, null, player, ammoItem);
                        if (stacks != null && stacks.length > 0) return true;
                    }
                }
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
                Object gun = safeInvoke(jegGetModifiedGunMethod, stack.getItem(), stack);
                if (gun != null) {
                    if (jegGetReloadsMethod != null && jegReloadsGetReloadItemMethod != null) {
                        Object reloads = safeInvoke(jegGetReloadsMethod, gun);
                        if (reloads != null) {
                            reloadItem = safeInvoke(jegReloadsGetReloadItemMethod, reloads);
                        }
                    }
                    if (jegGetProjectileMethod != null && jegProjGetItemMethod != null) {
                        Object proj = safeInvoke(jegGetProjectileMethod, gun);
                        if (proj != null) {
                            projItem = safeInvoke(jegProjGetItemMethod, proj);
                        }
                    }
                }
            }

            if (jegFindAmmoStackMethod != null) {
                if (reloadItem != null) {
                    ItemStack[] stacks = safeInvoke(jegFindAmmoStackMethod, null, player, reloadItem);
                    if (stacks != null && stacks.length > 0) return true;
                }
                if (projItem != null) {
                    ItemStack[] stacks = safeInvoke(jegFindAmmoStackMethod, null, player, projItem);
                    if (stacks != null && stacks.length > 0) return true;
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
                if (player instanceof net.minecraft.client.player.LocalPlayer localPlayer &&
                    taczClientGunOperatorClass != null && taczFromLocalPlayerMethod != null && taczClientReloadMethod != null) {
                    Object operator = safeInvoke(taczFromLocalPlayerMethod, null, localPlayer);
                    if (operator != null) {
                        safeInvoke(taczClientReloadMethod, operator);
                        return true;
                    }
                }

                if (taczFromLivingEntityMethod != null && taczReloadMethod != null) {
                    Object operator = safeInvoke(taczFromLivingEntityMethod, null, player);
                    if (operator != null) {
                        safeInvoke(taczReloadMethod, operator);
                        return true;
                    }
                }

                if (taczReloadKeyField != null) {
                    KeyMapping reloadKey = safeGetFieldValue(taczReloadKeyField, null);
                    if (reloadKey != null) {
                        KeyMapping.click(reloadKey.getKey());
                        return true;
                    }
                }
            }
            return false;
        }

        // 2. Point Blank 枪械 (优先模拟 RELOAD_KEY 按键，次级调用 tryReload)
        if (isPointBlankGun(mainHand)) {
            initPbReflection();
            boolean triggered = false;
            if (pbReloadKeyField != null) {
                Object lazyObj = safeGetFieldValue(pbReloadKeyField, null);
                if (lazyObj != null) {
                    KeyMapping reloadKey = null;
                    if (pbLazyGetMethod != null) {
                        reloadKey = safeInvoke(pbLazyGetMethod, lazyObj);
                    } else {
                        Method getMethod = safeGetMethod(lazyObj.getClass(), "get");
                        reloadKey = safeInvoke(getMethod, lazyObj);
                    }
                    if (reloadKey != null) {
                        KeyMapping.click(reloadKey.getKey());
                        triggered = true;
                    }
                }
            }
            if (!triggered && pbTryReloadMethod != null) {
                triggered = safeInvokeOrDefault(pbTryReloadMethod, mainHand.getItem(), false, player, mainHand);
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
                Object handler = safeInvoke(jegGetReloadHandlerMethod, null);
                if (handler != null) {
                    safeInvoke(jegSetReloadingMethod, handler, true);
                    return true;
                }
            }
            if (jegKeyReloadField != null) {
                KeyMapping reloadKey = safeGetFieldValue(jegKeyReloadField, null);
                if (reloadKey != null) {
                    KeyMapping.click(reloadKey.getKey());
                    return true;
                }
            }
            return true;
        }

        // 4. Scorched Guns 枪械 (双管齐下：模拟 KEY_RELOAD 按键点击 + 调度 ReloadHandler)
        if (isScorchedGun(mainHand)) {
            initScgunsReflection();
            if (scgunsKeyReloadField != null) {
                KeyMapping reloadKey = safeGetFieldValue(scgunsKeyReloadField, null);
                if (reloadKey != null) {
                    KeyMapping.click(reloadKey.getKey());
                }
            }
            if (scgunsReloadHandlerClass != null && scgunsGetReloadHandlerMethod != null && scgunsSetReloadingMethod != null) {
                Object handler = safeInvoke(scgunsGetReloadHandlerMethod, null);
                if (handler != null) {
                    safeInvoke(scgunsSetReloadingMethod, handler, true);
                    return true;
                }
            }
            return true;
        }

        // 5. CGM 枪械
        if (isCgmGun(mainHand)) {
            initCgmReflection();
            if (cgmReloadHandlerClass != null && cgmGetReloadHandlerMethod != null && cgmSetReloadingMethod != null) {
                Object handler = safeInvoke(cgmGetReloadHandlerMethod, null);
                if (handler != null) {
                    safeInvoke(cgmSetReloadingMethod, handler, true);
                    return true;
                }
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
                    if (scgunsGetModifiedGunMethod != null && scgunsGetReloadsMethod != null && scgunsReloadsGetReloadTypeMethod != null) {
                        Object gun = safeInvoke(scgunsGetModifiedGunMethod, stack.getItem(), stack);
                        if (gun != null) {
                            Object reloads = safeInvoke(scgunsGetReloadsMethod, gun);
                            if (reloads != null) {
                                Object rtype = safeInvoke(scgunsReloadsGetReloadTypeMethod, reloads);
                                if (rtype != null && (rtype == scgunsReloadTypeManualObj || rtype.toString().contains("MANUAL"))) {
                                    return true;
                                }
                            }
                        }
                    }
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
                Object handler = safeInvoke(scgunsGetReloadHandlerMethod, null);
                if (handler != null) {
                    safeInvoke(scgunsSetReloadingMethod, handler, false);
                }
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
                safeInvoke(taczShootControllerTickMethod, null, shoot);
                if (taczShootKeyField != null) {
                    KeyMapping shootKey = safeGetFieldValue(taczShootKeyField, null);
                    if (shootKey != null) {
                        KeyMapping.set(shootKey.getKey(), shoot);
                    }
                }
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
                    KeyMapping jegShootKey = safeInvoke(jegGetShootMappingMethod, null);
                    if (jegShootKey != null && mc.options != null && jegShootKey != mc.options.keyAttack) {
                        KeyMapping.set(jegShootKey.getKey(), shoot);
                    }
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
                Object iGunObj = safeInvoke(taczGetIGunOrNullMethod, null, stack);
                if (iGunObj != null) {
                    return safeInvoke(taczGetGunIdMethod, iGunObj, stack);
                }
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
                return safeInvokeOrDefault(jegGetChargeProgressMethod, null, 0.0f, player, stack);
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
                Object gun = safeInvoke(jegGetModifiedGunMethod, stack.getItem(), stack);
                if (gun != null) {
                    Object general = safeInvoke(jegGetGeneralMethod, gun);
                    if (general != null) {
                        int val = safeInvokeOrDefault(jegGenGetMaxHoldFireMethod, general, 20);
                        if (val > 0) return val;
                    }
                }
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
            Object handler = safeInvoke(jegGetShootingHandlerMethod, null);
            if (handler != null) {
                return safeInvokeOrDefault(jegGetHoldFireMethod, handler, 0);
            }
        }
        return 0;
    }

    /**
     * 获取武器简短、纯净的战术显示名称 (彻底去除 [枪械]、[枪] 等重复括号前缀与底层类名)
     */
    public static String getCleanGunName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "空手";
        if (isTaczGun(stack)) {
            initTaczReflection();
            if (taczAvailable && taczGetIGunOrNullMethod != null) {
                Object iGun = safeInvoke(taczGetIGunOrNullMethod, null, stack);
                if (iGun != null && taczGetGunIdMethod != null) {
                    ResourceLocation gunId = safeInvoke(taczGetGunIdMethod, iGun, stack);
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
                Object iGun = safeInvoke(taczGetIGunOrNullMethod, null, stack);
                if (iGun != null) {
                    Object[] types = safeInvoke(taczAttachmentTypeValuesMethod, null);
                    if (types != null) {
                        for (Object type : types) {
                            if ("NONE".equals(type.toString())) continue;
                            ItemStack attachStack = safeInvoke(taczGetAttachmentMethod, iGun, stack, type);
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
                Object clientOpt = safeInvoke(taczGetClientGunIndexMethod, null, res);
                if (clientOpt instanceof Optional<?> opt && opt.isPresent()) {
                    Object clientIndex = opt.get();
                    String nameKey = safeInvoke(taczClientGunIndexGetNameMethod, clientIndex);
                    if (nameKey != null && !nameKey.isEmpty()) {
                        String nameStr = net.minecraft.network.chat.Component.translatable(nameKey).getString();
                        if (!nameStr.equals(nameKey)) {
                            return "§6[枪械] " + nameStr;
                        }
                    }
                }
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
        runSilently(() -> {
            taczIGunClass = safeGetClass("com.tacz.guns.api.item.IGun");
            if (taczIGunClass != null) {
                taczGetIGunOrNullMethod = safeGetMethod(taczIGunClass, "getIGunOrNull", ItemStack.class);
                taczGetGunIdMethod = safeGetMethod(taczIGunClass, "getGunId", ItemStack.class);
                taczGetCurrentAmmoMethod = safeGetMethod(taczIGunClass, "getCurrentAmmoCount", ItemStack.class);
                taczGetRPMMethod = safeGetMethod(taczIGunClass, "getRPM", ItemStack.class);
                taczGetFireModeMethod = safeGetMethod(taczIGunClass, "getFireMode", ItemStack.class);
            }

            taczTimelessAPIClass = safeGetClass("com.tacz.guns.api.TimelessAPI");
            if (taczTimelessAPIClass != null) {
                taczGetCommonGunIndexMethod = safeGetMethod(taczTimelessAPIClass, "getCommonGunIndex", ResourceLocation.class);
                taczGetClientGunIndexMethod = safeGetMethod(taczTimelessAPIClass, "getClientGunIndex", ResourceLocation.class);
            }

            taczCommonGunIndexClass = safeGetClass("com.tacz.guns.resource.index.CommonGunIndex");
            if (taczCommonGunIndexClass != null) {
                taczGetBulletDataMethod = safeGetMethod(taczCommonGunIndexClass, "getBulletData");
                taczGetGunDataMethod = safeGetMethod(taczCommonGunIndexClass, "getGunData");
                taczGetTypeMethod = safeGetMethod(taczCommonGunIndexClass, "getType");
            }

            taczBulletDataClass = safeGetClass("com.tacz.guns.resource.pojo.data.gun.BulletData");
            if (taczBulletDataClass != null) {
                taczGetSpeedMethod = safeGetMethod(taczBulletDataClass, "getSpeed");
                taczGetGravityMethod = safeGetMethod(taczBulletDataClass, "getGravity");
                taczGetExtraDamageMethod = safeGetMethod(taczBulletDataClass, "getExtraDamage");
                taczExtraDamageClass = safeGetClass("com.tacz.guns.resource.pojo.data.gun.ExtraDamage");
                if (taczExtraDamageClass != null) {
                    taczGetHeadShotMultiplierMethod = safeGetMethod(taczExtraDamageClass, "getHeadShotMultiplier");
                }
            }

            taczGunDataClass = safeGetClass("com.tacz.guns.resource.pojo.data.gun.GunData");
            if (taczGunDataClass != null) {
                taczGetRoundsPerMinuteMethod = safeGetMethod(taczGunDataClass, "getRoundsPerMinute");
                taczGetAmmoAmountMethod = safeGetMethod(taczGunDataClass, "getAmmoAmount");
                taczGetAmmoIdMethod = safeGetMethod(taczGunDataClass, "getAmmoId");
            }

            taczAttachmentDataUtilsClass = safeGetClass("com.tacz.guns.util.AttachmentDataUtils");
            if (taczAttachmentDataUtilsClass != null && taczGunDataClass != null) {
                taczGetAmmoCountWithAttachmentMethod = safeGetMethod(taczAttachmentDataUtilsClass, "getAmmoCountWithAttachment", ItemStack.class, taczGunDataClass);
                taczGetHeadShotMultiplierWithAttachmentMethod = safeGetMethod(taczAttachmentDataUtilsClass, "getHeadshotMultiplier", ItemStack.class, taczGunDataClass);
            }

            taczAttachmentTypeClass = safeGetClass("com.tacz.guns.api.item.attachment.AttachmentType");
            if (taczIGunClass != null && taczAttachmentTypeClass != null) {
                taczGetAttachmentMethod = safeGetMethod(taczIGunClass, "getAttachment", ItemStack.class, taczAttachmentTypeClass);
                taczAttachmentTypeValuesMethod = safeGetMethod(taczAttachmentTypeClass, "values");
            }

            taczClientGunIndexClass = safeGetClass("com.tacz.guns.resource.index.ClientGunIndex");
            if (taczClientGunIndexClass != null) {
                taczClientGunIndexGetNameMethod = safeGetMethod(taczClientGunIndexClass, "getName");
            }

            taczIGunOperatorClass = safeGetClass("com.tacz.guns.api.entity.IGunOperator");
            if (taczIGunOperatorClass != null) {
                taczFromLivingEntityMethod = safeGetMethod(taczIGunOperatorClass, "fromLivingEntity", LivingEntity.class);
                taczReloadMethod = safeGetMethod(taczIGunOperatorClass, "reload");
                taczGetSynIsAimingMethod = safeGetMethod(taczIGunOperatorClass, "getSynIsAiming");
                taczGetSynIsBoltingMethod = safeGetMethod(taczIGunOperatorClass, "getSynIsBolting");
                taczGetSynReloadStateMethod = safeGetMethod(taczIGunOperatorClass, "getSynReloadState");
            }

            taczReloadStateClass = safeGetClass("com.tacz.guns.api.entity.ReloadState");
            if (taczReloadStateClass != null) {
                taczGetCountDownMethod = safeGetMethod(taczReloadStateClass, "getCountDown");
            }

            taczAbstractGunItemClass = safeGetClass("com.tacz.guns.api.item.gun.AbstractGunItem");
            if (taczAbstractGunItemClass != null) {
                taczCanReloadMethod = safeGetMethod(taczAbstractGunItemClass, "canReload", LivingEntity.class, ItemStack.class);
            }

            taczIAmmoClass = safeGetClass("com.tacz.guns.api.item.IAmmo");
            if (taczIAmmoClass != null) {
                taczIsAmmoOfGunMethod = safeGetMethod(taczIAmmoClass, "isAmmoOfGun", ItemStack.class, ItemStack.class);
            }

            taczIAmmoBoxClass = safeGetClass("com.tacz.guns.api.item.IAmmoBox");
            if (taczIAmmoBoxClass != null) {
                taczIsAmmoBoxOfGunMethod = safeGetMethod(taczIAmmoBoxClass, "isAmmoBoxOfGun", ItemStack.class, ItemStack.class);
                taczGetAmmoCountMethod = safeGetMethod(taczIAmmoBoxClass, "getAmmoCount", ItemStack.class);
            }

            if (taczIGunClass != null) {
                taczHasBulletInBarrelMethod = safeGetMethod(taczIGunClass, "hasBulletInBarrel", ItemStack.class);
                taczHasInventoryAmmoMethod = safeGetMethod(taczIGunClass, "hasInventoryAmmo", LivingEntity.class, ItemStack.class, boolean.class);
            }

            taczClientGunOperatorClass = safeGetClass("com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator");
            if (taczClientGunOperatorClass != null) {
                taczFromLocalPlayerMethod = safeGetMethod(taczClientGunOperatorClass, "fromLocalPlayer", net.minecraft.client.player.LocalPlayer.class);
                taczClientReloadMethod = safeGetMethod(taczClientGunOperatorClass, "reload");
            }

            taczReloadKeyClass = safeGetClass("com.tacz.guns.client.input.ReloadKey");
            if (taczReloadKeyClass != null) {
                taczReloadKeyField = safeGetField(taczReloadKeyClass, "RELOAD_KEY");
            }

            taczShootKeyClass = safeGetClass("com.tacz.guns.client.input.ShootKey");
            if (taczShootKeyClass != null) {
                taczShootControllerTickMethod = safeGetMethod(taczShootKeyClass, "shootControllerTick", boolean.class);
                taczShootKeyField = safeGetField(taczShootKeyClass, "SHOOT_KEY");
            }

            if (taczIGunClass != null && taczGetIGunOrNullMethod != null) {
                taczAvailable = true;
            }
        });
    }

    private static synchronized void initPbReflection() {
        if (pbChecked) return;
        pbChecked = true;
        runSilently(() -> {
            pbGunItemClass = safeGetClass("com.vicmatskiv.pointblank.item.GunItem");
            if (pbGunItemClass != null) {
                Class<?> fireModeClass = safeGetClass("com.vicmatskiv.pointblank.item.FireModeInstance");
                if (fireModeClass != null) {
                    pbGetAmmoMethod = safeGetMethod(pbGunItemClass, "getAmmo", ItemStack.class, fireModeClass);
                    pbGetMaxAmmoCapacityMethod = safeGetMethod(pbGunItemClass, "getMaxAmmoCapacity", ItemStack.class, fireModeClass);
                    pbFmiGetTypeMethod = safeGetMethod(fireModeClass, "getType");
                    pbFmiGetRpmMethod = safeGetMethod(fireModeClass, "getRpm");
                    pbCanReloadGunMethod = safeGetMethod(pbGunItemClass, "canReloadGun", ItemStack.class, Player.class, fireModeClass);
                }
                pbGetFireModeInstanceMethod = safeGetMethod(pbGunItemClass, "getFireModeInstance", ItemStack.class);
                pbTryReloadMethod = safeGetMethod(pbGunItemClass, "tryReload", Player.class, ItemStack.class);
                pbGetCompatibleAmmoMethod = safeGetMethod(pbGunItemClass, "getCompatibleAmmo");
                pbRequestReloadMethod = safeGetMethod(pbGunItemClass, "requestReloadFromServer", Player.class, ItemStack.class);
                pbResolveSlotIndexMethod = safeGetMethod(pbGunItemClass, "resolveSlotIndex", Player.class, ItemStack.class);
            }

            pbGunClientStateClass = safeGetClass("com.vicmatskiv.pointblank.client.GunClientState");
            if (pbGunClientStateClass != null) {
                pbGetClientStateMethod = safeGetMethod(pbGunClientStateClass, "getState", Player.class, ItemStack.class, int.class, boolean.class);
                pbStateIsReloadingMethod = safeGetMethod(pbGunClientStateClass, "isReloading");
                pbStateIsPreparingReloadMethod = safeGetMethod(pbGunClientStateClass, "isPreparingReload");
            }

            Class<?> eventHandler = safeGetClass("com.vicmatskiv.pointblank.client.ClientEventHandler");
            if (eventHandler != null) {
                pbReloadKeyField = safeGetField(eventHandler, "RELOAD_KEY");
                if (pbReloadKeyField != null) {
                    Class<?> type = pbReloadKeyField.getType();
                    pbLazyGetMethod = safeGetMethod(type, "get");
                }
            }

            if (pbGunItemClass != null) {
                pbAvailable = true;
            }
        });
    }

    private static synchronized void initJegReflection() {
        if (jegChecked) return;
        jegChecked = true;
        runSilently(() -> {
            jegGunItemClass = safeGetClass("ttv.migami.jeg.item.GunItem");
            jegGunClass = safeGetClass("ttv.migami.jeg.common.Gun");

            if (jegGunItemClass != null) {
                jegGetModifiedGunMethod = safeGetMethod(jegGunItemClass, "getModifiedGun", ItemStack.class);
            }
            if (jegGunClass != null) {
                jegFindAmmoStackMethod = safeGetMethod(jegGunClass, "findAmmoStack", Player.class, ResourceLocation.class);
                jegGetProjectileMethod = safeGetMethod(jegGunClass, "getProjectile");
                jegGetGeneralMethod = safeGetMethod(jegGunClass, "getGeneral");
                jegGetReloadsMethod = safeGetMethod(jegGunClass, "getReloads");

                Class<?> projClass = safeGetClass("ttv.migami.jeg.common.Gun$Projectile");
                if (projClass != null) {
                    jegProjGetItemMethod = safeGetMethod(projClass, "getItem");
                    jegProjGetSpeedMethod = safeGetMethod(projClass, "getSpeed");
                    jegProjIsGravityMethod = safeGetMethod(projClass, "isGravity");
                    jegProjGetHeadshotMultiplierMethod = safeGetMethod(projClass, "getHeadshotMultiplier");
                }

                Class<?> genClass = safeGetClass("ttv.migami.jeg.common.Gun$General");
                if (genClass != null) {
                    jegGenGetFireModeMethod = safeGetMethod(genClass, "getFireMode");
                    jegGenGetRateMethod = safeGetMethod(genClass, "getRate");
                    jegGenGetMaxHoldFireMethod = safeGetMethod(genClass, "getMaxHoldFire");
                }

                Class<?> fmClass = safeGetClass("ttv.migami.jeg.common.Gun$FireMode");
                if (fmClass != null) {
                    jegFireModeGetIdMethod = safeGetMethod(fmClass, "getId");
                }

                Class<?> reloadsClass = safeGetClass("ttv.migami.jeg.common.Gun$Reloads");
                if (reloadsClass != null) {
                    jegReloadsGetMaxAmmoMethod = safeGetMethod(reloadsClass, "getMaxAmmo");
                    jegReloadsGetReloadItemMethod = safeGetMethod(reloadsClass, "getReloadItem");
                }
            }

            jegChargeTrackerClass = safeGetClass("ttv.migami.jeg.common.ChargeTracker");
            if (jegChargeTrackerClass != null) {
                jegGetChargeProgressMethod = safeGetMethod(jegChargeTrackerClass, "getChargeProgress", Player.class, ItemStack.class);
            }

            Class<?> kbClass = safeGetClass("ttv.migami.jeg.client.KeyBinds");
            if (kbClass != null) {
                jegKeyReloadField = safeGetField(kbClass, "KEY_RELOAD");
                jegGetShootMappingMethod = safeGetMethod(kbClass, "getShootMapping");
                jegGetAimMappingMethod = safeGetMethod(kbClass, "getAimMapping");
            }

            jegReloadHandlerClass = safeGetClass("ttv.migami.jeg.client.handler.ReloadHandler");
            if (jegReloadHandlerClass != null) {
                jegGetReloadHandlerMethod = safeGetMethod(jegReloadHandlerClass, "get");
                jegSetReloadingMethod = safeGetMethod(jegReloadHandlerClass, "setReloading", boolean.class);
                jegGetReloadTimerMethod = safeGetMethod(jegReloadHandlerClass, "getReloadTimer");
            }

            jegShootingHandlerClass = safeGetClass("ttv.migami.jeg.client.handler.ShootingHandler");
            if (jegShootingHandlerClass != null) {
                jegGetShootingHandlerMethod = safeGetMethod(jegShootingHandlerClass, "get");
                jegGetHoldFireMethod = safeGetMethod(jegShootingHandlerClass, "getHoldFire");
            }

            jegAimingHandlerClass = safeGetClass("ttv.migami.jeg.client.handler.AimingHandler");
            if (jegAimingHandlerClass != null) {
                jegGetAimingHandlerMethod = safeGetMethod(jegAimingHandlerClass, "get");
                jegIsAimingMethod = safeGetMethod(jegAimingHandlerClass, "isAiming");
            }

            if (jegGunItemClass != null || jegGunClass != null) {
                jegAvailable = true;
            }
        });
    }

    private static synchronized void initScgunsReflection() {
        if (scgunsChecked) return;
        scgunsChecked = true;
        runSilently(() -> {
            scgunsGunItemClass = safeGetClass("top.ribs.scguns.item.GunItem");
            scgunsGunClass = safeGetClass("top.ribs.scguns.common.Gun");

            if (scgunsGunItemClass != null) {
                scgunsGetModifiedGunMethod = safeGetMethod(scgunsGunItemClass, "getModifiedGun", ItemStack.class);
            }
            if (scgunsGunClass != null) {
                scgunsFindAmmoStackMethod = safeGetMethod(scgunsGunClass, "findAmmoStack", Player.class, Item.class);
                scgunsGetCurrentAmmoItemMethod = safeGetMethod(scgunsGunClass, "getCurrentAmmoItem", ItemStack.class);
                scgunsGetMaxAmmoMethod = safeGetMethod(scgunsGunClass, "getMaxAmmo", ItemStack.class);
                scgunsGetAmmoCountMethod = safeGetMethod(scgunsGunClass, "getAmmoCount", ItemStack.class);
                scgunsGetGeneralMethod = safeGetMethod(scgunsGunClass, "getGeneral");
                scgunsGetReloadsMethod = safeGetMethod(scgunsGunClass, "getReloads");

                Class<?> genClass = safeGetClass("top.ribs.scguns.common.Gun$General");
                if (genClass != null) {
                    scgunsGenIsAutoMethod = safeGetMethod(genClass, "isAuto");
                    scgunsGenIsRevolverMethod = safeGetMethod(genClass, "isRevolver");
                    scgunsGenGetRateMethod = safeGetMethod(genClass, "getRate");
                }

                Class<?> reloadsClass = safeGetClass("top.ribs.scguns.common.Gun$Reloads");
                if (reloadsClass != null) {
                    scgunsReloadsGetReloadTypeMethod = safeGetMethod(reloadsClass, "getReloadType");
                }

                Class<?> reloadTypeClass = safeGetClass("top.ribs.scguns.common.ReloadType");
                if (reloadTypeClass != null) {
                    Field manualField = safeGetField(reloadTypeClass, "MANUAL");
                    scgunsReloadTypeManualObj = safeGetFieldValue(manualField, null);
                }
            }

            Class<?> kbClass = safeGetClass("top.ribs.scguns.client.KeyBinds");
            if (kbClass != null) {
                scgunsKeyReloadField = safeGetField(kbClass, "KEY_RELOAD");
            }

            scgunsReloadHandlerClass = safeGetClass("top.ribs.scguns.client.handler.ReloadHandler");
            if (scgunsReloadHandlerClass != null) {
                scgunsGetReloadHandlerMethod = safeGetMethod(scgunsReloadHandlerClass, "get");
                scgunsSetReloadingMethod = safeGetMethod(scgunsReloadHandlerClass, "setReloading", boolean.class);
                scgunsGetReloadTimerMethod = safeGetMethod(scgunsReloadHandlerClass, "getReloadTimer");
            }

            scgunsAimingHandlerClass = safeGetClass("top.ribs.scguns.client.handler.AimingHandler");
            if (scgunsAimingHandlerClass != null) {
                scgunsGetAimingHandlerMethod = safeGetMethod(scgunsAimingHandlerClass, "get");
                scgunsIsAimingMethod = safeGetMethod(scgunsAimingHandlerClass, "isAiming");
            }

            if (scgunsGunItemClass != null || scgunsGunClass != null) {
                scgunsAvailable = true;
            }
        });
    }

    private static synchronized void initCgmReflection() {
        if (cgmChecked) return;
        cgmChecked = true;
        runSilently(() -> {
            cgmGunItemClass = safeGetClass("com.mrcrayfish.guns.item.GunItem");

            cgmReloadHandlerClass = safeGetClass("com.mrcrayfish.guns.client.handler.ReloadHandler");
            if (cgmReloadHandlerClass != null) {
                cgmGetReloadHandlerMethod = safeGetMethod(cgmReloadHandlerClass, "get");
                cgmSetReloadingMethod = safeGetMethod(cgmReloadHandlerClass, "setReloading", boolean.class);
                cgmGetReloadTimerMethod = safeGetMethod(cgmReloadHandlerClass, "getReloadTimer");
            }

            cgmAimingHandlerClass = safeGetClass("com.mrcrayfish.guns.client.handler.AimingHandler");
            if (cgmAimingHandlerClass != null) {
                cgmGetAimingHandlerMethod = safeGetMethod(cgmAimingHandlerClass, "get");
                cgmIsAimingMethod = safeGetMethod(cgmAimingHandlerClass, "isAiming");
            }

            if (cgmGunItemClass != null) {
                cgmAvailable = true;
            }
        });
    }

    // ==========================================
    // 反射安全调用工具方法 (Reflection Helpers)
    // ==========================================

    @FunctionalInterface
    public interface ReflectionSupplier<T> {
        T get() throws Throwable;
    }

    @FunctionalInterface
    public interface ReflectionAction {
        void run() throws Throwable;
    }

    public static <T> T invokeSilently(ReflectionSupplier<T> supplier, T defaultValue) {
        try {
            return supplier.get();
        } catch (Throwable t) {
            LOGGER.trace("Reflection call failed: {}", t.getMessage());
            return defaultValue;
        }
    }

    public static void runSilently(ReflectionAction action) {
        try {
            action.run();
        } catch (Throwable t) {
            LOGGER.trace("Reflection action failed: {}", t.getMessage());
        }
    }

    public static Class<?> safeGetClass(String className) {
        try {
            return Class.forName(className);
        } catch (Throwable t) {
            LOGGER.trace("Failed to load class {}: {}", className, t.getMessage());
            return null;
        }
    }

    public static Method safeGetMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod(methodName, parameterTypes);
        } catch (Throwable t) {
            LOGGER.trace("Failed to get method {} on {}: {}", methodName, clazz.getName(), t.getMessage());
            return null;
        }
    }

    public static Field safeGetField(Class<?> clazz, String fieldName) {
        if (clazz == null) return null;
        try {
            return clazz.getField(fieldName);
        } catch (Throwable t) {
            LOGGER.trace("Failed to get field {} on {}: {}", fieldName, clazz.getName(), t.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T safeInvoke(Method method, Object target, Object... args) {
        if (method == null) return null;
        try {
            return (T) method.invoke(target, args);
        } catch (Throwable t) {
            LOGGER.trace("Method invocation failed for {}: {}", method.getName(), t.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T safeInvokeOrDefault(Method method, Object target, T defaultValue, Object... args) {
        if (method == null) return defaultValue;
        try {
            Object result = method.invoke(target, args);
            return result != null ? (T) result : defaultValue;
        } catch (Throwable t) {
            LOGGER.trace("Method invocation failed for {}: {}", method.getName(), t.getMessage());
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T safeGetFieldValue(Field field, Object target) {
        if (field == null) return null;
        try {
            return (T) field.get(target);
        } catch (Throwable t) {
            LOGGER.trace("Field access failed for {}: {}", field.getName(), t.getMessage());
            return null;
        }
    }
}
