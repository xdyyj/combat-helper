package com.xdyyj.autoattacker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;

/**
 * 模组弓与弹道参数智能识别跟踪器 (四级渐进式解析系统 + MC 原版前向微元二分求解器)
 * 1. 动态属性与神话 (Apotheosis/Apothic Attributes) Draw Speed 与 Arrow Velocity 实时探测
 * 2. 状态指纹剔除耐久波动，支持切换武器第 0 tick 零延迟精准载入
 * 3. 磁盘 JSON 异步防抖持久化 (.minecraft/config/combathelper_ballistics.json)
 * 4. MC 原版前向微元物理二分求解器 (彻底根除 2.34° 几何欠角，实现毫厘级命中)
 */
public final class AutoBallisticsTracker {

    public static final class BallisticsProfile {
        public float speed;
        public double gravity;
        public double drag;
        public boolean isHoming;
        public double maxRange;
        public float drawSpeed;      // 蓄力速度倍率 (标准 1.0f, 神话 +60% 则为 1.6f)
        public int minChargeTicks;   // 满蓄力所需 tick 阈值 (Math.ceil(20.0 / drawSpeed))
        public long lastUpdated;     // 录入/更新时间戳 (毫秒)

        public BallisticsProfile(float speed, double gravity, double drag, boolean isHoming, double maxRange, float drawSpeed, int minChargeTicks) {
            this(speed, gravity, drag, isHoming, maxRange, drawSpeed, minChargeTicks, System.currentTimeMillis());
        }

        public BallisticsProfile(float speed, double gravity, double drag, boolean isHoming, double maxRange, float drawSpeed, int minChargeTicks, long lastUpdated) {
            this.speed = speed;
            this.gravity = gravity;
            this.drag = drag;
            this.isHoming = isHoming;
            this.maxRange = maxRange;
            this.drawSpeed = drawSpeed;
            this.minChargeTicks = minChargeTicks;
            this.lastUpdated = (lastUpdated > 0) ? lastUpdated : System.currentTimeMillis();
        }
    }

    public static final class TrajectorySolution {
        public final float pitchDeg;      // 实际计算出的发射俯仰角 (角度, 抬头为负, 低头为正)
        public final double flightTicks;  // 弹道飞行时间 (ticks)
        public final boolean reachable;   // 是否物理可达

        public TrajectorySolution(float pitchDeg, double flightTicks, boolean reachable) {
            this.pitchDeg = pitchDeg;
            this.flightTicks = flightTicks;
            this.reachable = reachable;
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, BallisticsProfile> CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, TrackedProjectile> ACTIVE_PROJECTILES = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 128;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final ScheduledExecutorService ASYNC_IO = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CombatHelper-BallisticsIO");
        t.setDaemon(true);
        return t;
    });

    /**
     * 16 种经典与模组主流弓弩原厂权威物理基准库 (开箱即用，永不丢失)
     */
    private static final Map<String, BallisticsProfile> DEFAULT_FACTORY_PRESETS = new LinkedHashMap<>();
    static {
        // 1. 原版原装经典远程武器
        DEFAULT_FACTORY_PRESETS.put("minecraft:bow", new BallisticsProfile(3.0f, 0.05, 0.99, false, 120.0, 1.0f, 20));
        DEFAULT_FACTORY_PRESETS.put("minecraft:crossbow", new BallisticsProfile(3.15f, 0.05, 0.99, false, 120.0, 1.0f, 25));
        DEFAULT_FACTORY_PRESETS.put("minecraft:trident", new BallisticsProfile(2.5f, 0.05, 0.99, false, 80.0, 1.0f, 10));

        // 2. 植物魔法与额外植物学系列 (零重力直瞄与特色射速)
        DEFAULT_FACTORY_PRESETS.put("extrabotany:failnaught", new BallisticsProfile(7.0f, 0.0, 0.99, false, 180.0, 3.6f, 20));
        DEFAULT_FACTORY_PRESETS.put("botania:crystal_bow", new BallisticsProfile(3.0f, 0.0, 0.99, false, 150.0, 1.0f, 20));
        DEFAULT_FACTORY_PRESETS.put("botania:livingwood_bow", new BallisticsProfile(3.0f, 0.05, 0.99, false, 120.0, 1.0f, 20));

        // 3. 暮色森林系列 (三连弓、自追踪弓、冰霜弓、末影弓)
        DEFAULT_FACTORY_PRESETS.put("twilightforest:triple_bow", new BallisticsProfile(3.0f, 0.05, 0.99, false, 120.0, 1.0f, 24));
        DEFAULT_FACTORY_PRESETS.put("twilightforest:seeker_bow", new BallisticsProfile(2.5f, 0.05, 0.99, true, 100.0, 1.0f, 20));
        DEFAULT_FACTORY_PRESETS.put("twilightforest:ice_bow", new BallisticsProfile(3.0f, 0.05, 0.99, false, 120.0, 1.0f, 20));
        DEFAULT_FACTORY_PRESETS.put("twilightforest:ender_bow", new BallisticsProfile(3.0f, 0.05, 0.99, false, 120.0, 1.0f, 20));

        // 4. Alex's Mobs (筋腱弓高初速低重力)
        DEFAULT_FACTORY_PRESETS.put("alexsmobs:tendon_bow", new BallisticsProfile(4.5f, 0.035, 0.99, false, 160.0, 0.8f, 25));

        // 5. 灾变 (Cataclysm 激光加特林 / 炮击)
        DEFAULT_FACTORY_PRESETS.put("cataclysm:laser_gatling", new BallisticsProfile(8.0f, 0.0, 0.99, false, 200.0, 1.0f, 1));

        // 6. 弓箭手悖论 (Archers Paradox 钻石弓、下界合金弓)
        DEFAULT_FACTORY_PRESETS.put("archers_paradox:diamond_bow", new BallisticsProfile(3.6f, 0.045, 0.99, false, 140.0, 1.15f, 18));
        DEFAULT_FACTORY_PRESETS.put("archers_paradox:netherite_bow", new BallisticsProfile(4.0f, 0.04, 0.99, false, 150.0, 1.25f, 16));

        // 7. 铁魔法 (Iron's Spells 'n Spellbooks) 魔导弓系列
        DEFAULT_FACTORY_PRESETS.put("irons_spellbooks:fire_bow", new BallisticsProfile(3.5f, 0.035, 0.99, false, 140.0, 1.0f, 20));
        DEFAULT_FACTORY_PRESETS.put("irons_spellbooks:ice_bow", new BallisticsProfile(3.5f, 0.035, 0.99, false, 140.0, 1.0f, 20));

        // 8. 新生魔艺 (Ars Nouveau 咒术弓)
        DEFAULT_FACTORY_PRESETS.put("ars_nouveau:spell_bow", new BallisticsProfile(3.5f, 0.03, 0.99, false, 140.0, 1.0f, 20));
    }

    private static volatile boolean isDirty = false;
    private static ScheduledFuture<?> saveTask = null;
    private static boolean isInitialized = false;

    // 瞬态缓存：用于主渲染帧 O(1) 零垃圾回收比对
    private static ItemStack lastHeldStackRef = ItemStack.EMPTY;
    private static CompoundTag lastHeldTagRef = null;
    private static String lastCachedSignature = null;
    private static BallisticsProfile lastCachedProfile = null;

    private static volatile List<Attribute> cachedDrawSpeedAttributes = null;
    private static volatile List<Attribute> cachedArrowVelocityAttributes = null;

    private static List<Attribute> getDrawSpeedAttributes() {
        List<Attribute> list = cachedDrawSpeedAttributes;
        if (list == null) {
            synchronized (AutoBallisticsTracker.class) {
                list = cachedDrawSpeedAttributes;
                if (list == null) {
                    List<Attribute> found = new ArrayList<>();
                    for (Attribute attr : ForgeRegistries.ATTRIBUTES) {
                        ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(attr);
                        if (key != null) {
                            String path = key.getPath();
                            if (path.equals("draw_speed") || path.contains("draw_speed")) {
                                found.add(attr);
                            }
                        }
                    }
                    list = Collections.unmodifiableList(found);
                    cachedDrawSpeedAttributes = list;
                }
            }
        }
        return list;
    }

    private static List<Attribute> getArrowVelocityAttributes() {
        List<Attribute> list = cachedArrowVelocityAttributes;
        if (list == null) {
            synchronized (AutoBallisticsTracker.class) {
                list = cachedArrowVelocityAttributes;
                if (list == null) {
                    List<Attribute> found = new ArrayList<>();
                    for (Attribute attr : ForgeRegistries.ATTRIBUTES) {
                        ResourceLocation key = ForgeRegistries.ATTRIBUTES.getKey(attr);
                        if (key != null) {
                            String path = key.getPath();
                            if (path.equals("arrow_velocity") || path.contains("arrow_velocity")) {
                                found.add(attr);
                            }
                        }
                    }
                    list = Collections.unmodifiableList(found);
                    cachedArrowVelocityAttributes = list;
                }
            }
        }
        return list;
    }

    private static final class TrackedProjectile {
        final int entityId;
        final String cacheKey;
        final List<Vec3> velHistory = new ArrayList<>(4);
        int age = 0;
        boolean sampled = false;
        boolean isHoming = false;
        int turnCount = 0;

        TrackedProjectile(int entityId, String cacheKey) {
            this.entityId = entityId;
            this.cacheKey = cacheKey;
        }
    }

    /**
     * 客户端初始化，从磁盘载入持久化数据
     */
    public static synchronized void init() {
        if (isInitialized) return;
        isInitialized = true;
        loadFromDisk();
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(AutoBallisticsTracker::saveToDiskImmediate, "CombatHelper-ShutdownSave"));
        } catch (Throwable ignored) {}
    }

    public static synchronized void ensureLoaded() {
        if (!isInitialized) {
            init();
        }
    }

    /**
     * 清理手持物品与 NBT 标签的瞬态引用 (在世界卸载或退出游戏时调用，防止跨世界内存泄漏)
     */
    public static void clearTransientReferences() {
        lastHeldStackRef = ItemStack.EMPTY;
        lastHeldTagRef = null;
        lastCachedSignature = null;
        lastCachedProfile = null;
        cachedDrawSpeedAttributes = null;
        cachedArrowVelocityAttributes = null;
    }

    /**
     * 获取指定弓的综合弹道参数 (第 0 tick 零延迟精准获取)
     */
    public static BallisticsProfile getProfile(ItemStack bowStack) {
        ensureLoaded();

        if (bowStack == null || bowStack.isEmpty()) {
            return getDefaultProfile();
        }

        // O(1) 瞬态引用快速检查：若手持物品实例与标签引用均未变，直接返回上一次缓存
        CompoundTag curTag = bowStack.getTag();
        if (bowStack == lastHeldStackRef && curTag == lastHeldTagRef && lastCachedProfile != null) {
            return lastCachedProfile;
        }

        // 提取稳态指纹 (已自动过滤 Damage 耐久变动与无害显示变动)
        String signature = getBallisticSignature(bowStack);

        BallisticsProfile profile = CACHE.get(signature);

        // 分层回退机制：如果带附魔/神话词缀的指纹未命中，检查是否有基础武器 ID (如 minecraft:bow / extrabotany:failnaught)
        if (profile == null) {
            ResourceLocation reg = ForgeRegistries.ITEMS.getKey(bowStack.getItem());
            String baseId = reg != null ? reg.toString() : null;
            if (baseId != null && CACHE.containsKey(baseId)) {
                BallisticsProfile baseProf = CACHE.get(baseId);
                profile = cloneAndAdapt(baseProf, bowStack);
                CACHE.put(signature, profile);
                markDirty();
            }
        }

        if (profile == null) {
            profile = computeInitialProfile(bowStack);
            CACHE.put(signature, profile);
            markDirty();
        } else {
            if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(bowStack)) {
                com.xdyyj.autoattacker.weapon.FirearmAdapter.GunStatus gun = 
                    com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunStatus(bowStack);
                if (gun.isGun && (profile.gravity > 0.0001 || profile.speed <= 3.5f || Math.abs(profile.speed - gun.bulletSpeed) > 0.05f)) {
                    profile.speed = gun.bulletSpeed;
                    profile.gravity = 0.0;
                    profile.minChargeTicks = 1;
                    profile.isHoming = false;
                    profile.lastUpdated = System.currentTimeMillis();
                    markDirtyAndSave();
                }
            } else {
                // 检查当前玩家身上是否有动态 draw_speed 或 arrow_velocity 属性加成已生效
                Player player = Minecraft.getInstance().player;
                if (player != null) {
                    float dynamicDrawSpeed = detectDrawSpeed(bowStack, player);
                    if (Math.abs(profile.drawSpeed - dynamicDrawSpeed) > 0.05f) {
                        profile.drawSpeed = dynamicDrawSpeed;
                        if (profile.minChargeTicks <= 0) {
                            profile.minChargeTicks = Math.max(1, (int) Math.ceil(20.0 / dynamicDrawSpeed));
                        }
                        markDirtyAndSave();
                    }
                }
            }
        }

        lastHeldStackRef = bowStack;
        lastHeldTagRef = curTag;
        lastCachedSignature = signature;
        lastCachedProfile = profile;

        return profile;
    }

    /**
     * 计算初始特征弹道参数 (第一级静态/属性探测)
     */
    private static BallisticsProfile computeInitialProfile(ItemStack bowStack) {
        if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(bowStack)) {
            com.xdyyj.autoattacker.weapon.FirearmAdapter.GunStatus gun = 
                com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunStatus(bowStack);
            // 现代枪械采用高平直射直瞄物理 (0.0 重力，180米无下坠直瞄)
            return new BallisticsProfile(gun.bulletSpeed, 0.0, 0.99, false, 180.0, 1.0f, 1);
        }

        Item item = bowStack.getItem();
        Player player = Minecraft.getInstance().player;

        Double cfgGravity = AutoAttackerConfig.bowGravityMap.get(item);
        Double cfgSpeed = AutoAttackerConfig.bowSpeedMap.get(item);

        double baseGravity = cfgGravity != null ? cfgGravity : resolveDefaultGravity(bowStack);
        float baseSpeed = cfgSpeed != null ? cfgSpeed.floatValue() : resolveDefaultSpeed(bowStack);
        double baseRange = AutoAttackerConfig.AIM_PREDICT_MAX_DIST.get();
        boolean isHoming = isHomingWeapon(bowStack);

        // 优先尝试从 Tooltip 语义中提取显式数值 (如 Arch Bows "1.8 Projectile Speed", "0.75 Draw Time")
        com.xdyyj.autoattacker.ui.TooltipBallisticsExtractor.ExtractedBallistics ext = 
            com.xdyyj.autoattacker.ui.TooltipBallisticsExtractor.extract(bowStack, player);
        if (ext != null && ext.hasAnyData()) {
            if (ext.speed != null) baseSpeed = ext.speed;
            if (ext.gravity != null) baseGravity = ext.gravity;
            if (ext.isHoming != null) isHoming = ext.isHoming;
        }

        // 探测神话 / 属性修饰符的速度倍率加成
        float speedMultiplier = detectSpeedMultiplier(bowStack, player);
        float finalSpeed = baseSpeed * speedMultiplier;

        // 探测神话 Draw Speed 蓄力倍率与最小蓄力 tick 数
        float drawSpeed = detectDrawSpeed(bowStack, player);
        int minChargeTicks = (ext != null && ext.drawTicks != null)
            ? ext.drawTicks
            : Math.max(1, (int) Math.ceil(20.0 / drawSpeed));

        return new BallisticsProfile(finalSpeed, baseGravity, 0.99, isHoming, baseRange, drawSpeed, minChargeTicks);
    }

    /**
     * 稳态特征指纹算法：过滤掉耐久变动(Damage)、铁砧惩罚(RepairCost)等噪音，
     * 只对决定物理弹道的物品ID、附魔、属性修饰、神话词缀与宝石生成指纹。
     */
    public static String getBallisticSignature(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";

        // 现代枪械：基于 GunId 建立全局单一条目，绝不随余弹量、枪温变动产生重复条目
        if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(stack)) {
            net.minecraft.resources.ResourceLocation gunId = 
                com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunId(stack);
            if (gunId != null) {
                return "gun:" + gunId.toString();
            }
            ResourceLocation r = ForgeRegistries.ITEMS.getKey(stack.getItem());
            return "gun:" + (r != null ? r.toString() : stack.getItem().getDescriptionId());
        }

        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String id = reg != null ? reg.toString() : stack.getItem().getDescriptionId();
        CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) {
            return id;
        }

        // 过滤掉纯耐久变动 (Damage) 与铁砧维修惩罚 (RepairCost) 等物理无关变动
        CompoundTag cleanTag = tag.copy();
        cleanTag.remove("Damage");
        cleanTag.remove("RepairCost");

        if (cleanTag.isEmpty()) {
            return id;
        }

        // 生成特征指纹：区分普通原版 vs 附魔/宝石/神话弓，彻底杜绝共用规则
        StringBuilder sb = new StringBuilder(id);

        if (tag.contains("Enchantments", Tag.TAG_LIST)) {
            ListTag enchs = tag.getList("Enchantments", Tag.TAG_COMPOUND);
            sb.append("|ench:").append(enchs.size());
        }

        boolean hasApoth = tag.contains("apotheosis:affixes") || tag.contains("affixes") ||
                           tag.contains("apotheosis:gems") || tag.contains("gems") ||
                           tag.contains("apotheosis:sockets") || tag.contains("sockets");
        if (hasApoth) {
            sb.append("|apoth");
        }

        int nbtHash = cleanTag.hashCode();
        sb.append("#").append(Integer.toHexString(nbtHash));

        return sb.toString();
    }

    /**
     * 探测神话 / AttributesLib 蓄力速度 (Draw Speed)
     */
    public static float detectDrawSpeed(ItemStack bowStack, Player player) {
        float drawSpeed = 1.0f;

        // 1. 优先从玩家实体的属性系统中读取最终加成 (神话在手持武器时会自动结算)
        if (player != null) {
            for (Attribute attr : getDrawSpeedAttributes()) {
                net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(attr);
                if (inst != null) {
                    double val = inst.getValue();
                    if (val > 0.05) {
                        return (float) val;
                    }
                }
            }
        }

        // 2. 静态解析 ItemStack 的 AttributeModifiers 与 NBT 词缀
        CompoundTag tag = bowStack.getTag();
        if (tag != null) {
            if (tag.contains("AttributeModifiers", Tag.TAG_LIST)) {
                ListTag list = tag.getList("AttributeModifiers", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag mod = list.getCompound(i);
                    String name = mod.getString("AttributeName").toLowerCase(Locale.ROOT);
                    if (name.contains("draw_speed")) {
                        double amount = mod.getDouble("Amount");
                        int op = mod.getInt("Operation");
                        if (op == 0) drawSpeed += (float) amount;
                        else if (op == 1 || op == 2) drawSpeed *= (1.0f + (float) amount);
                    }
                }
            }

            if (tag.contains("apotheosis:affixes", Tag.TAG_COMPOUND)) {
                CompoundTag affixes = tag.getCompound("apotheosis:affixes");
                for (String k : affixes.getAllKeys()) {
                    String lower = k.toLowerCase(Locale.ROOT);
                    if (lower.contains("draw") || lower.contains("charge")) {
                        float val = affixes.getFloat(k);
                        if (val > 0.0f) {
                            drawSpeed += val;
                        }
                    }
                }
            }
        }

        // 3. 模组弓专有蓄力倍率探测 (如 ExtraBotany 百中弓 FailnaughtItem / Botania 活木弓)
        try {
            Item item = bowStack.getItem();
            Method m = item.getClass().getMethod("chargeVelocityMultiplier", ItemStack.class, net.minecraft.world.entity.LivingEntity.class);
            Object res = m.invoke(item, bowStack, player);
            if (res instanceof Number) {
                float mult = ((Number) res).floatValue();
                if (mult > 0.01f) {
                    drawSpeed *= mult;
                }
            }
        } catch (Throwable ignored) {}

        return Math.max(0.1f, drawSpeed);
    }

    private static final ResourceLocation PULL_PROPERTY = ResourceLocation.tryParse("pull");

    /**
     * 获取玩家当前拉弓的实际蓄力比例 (0.0f ~ 1.0f+)
     * 兼容原版弓、神话 Draw Speed、植物魔法/额外植物学 FailnaughtItem、以及所有注册了 "pull" 属性的 Mod 弓
     */
    public static float getActualChargeProgress(Player player, ItemStack bowStack) {
        if (player == null || bowStack == null || bowStack.isEmpty()) return 0.0f;
        if (!player.isUsingItem() || player.getUseItem() != bowStack) return 0.0f;

        Item item = bowStack.getItem();

        // 1. 优先调用植物魔法 / 额外植物学等 Mod 的 getChargeProcess 方法 (百中弓核心逻辑)
        try {
            Method m = item.getClass().getMethod("getChargeProcess", ItemStack.class, net.minecraft.world.entity.LivingEntity.class);
            Object res = m.invoke(item, bowStack, player);
            if (res instanceof Number) {
                return ((Number) res).floatValue();
            }
        } catch (Throwable ignored) {}

        // 2. 检查客户端 ItemProperties 注册的 "pull" 属性
        try {
            net.minecraft.client.renderer.item.ItemPropertyFunction pullFunc = 
                net.minecraft.client.renderer.item.ItemProperties.getProperty(item, PULL_PROPERTY);
            if (pullFunc != null && player.level() instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
                float pull = pullFunc.call(bowStack, clientLevel, player, 0);
                if (pull > 0.0f) {
                    return pull;
                }
            }
        } catch (Throwable ignored) {}

        // 3. 检查神话或属性修饰的 minChargeTicks
        BallisticsProfile profile = getProfile(bowStack);
        int usedTicks = player.getTicksUsingItem();
        if (profile.minChargeTicks > 0) {
            return (float) usedTicks / (float) profile.minChargeTicks;
        }

        return BowItem.getPowerForTime(usedTicks);
    }

    /**
     * 智能判定当前手持的弓是否已经真正达到 100% 满蓄力 (安全放箭，不早泄)
     */
    public static boolean isBowFullyCharged(Player player, ItemStack bowStack) {
        if (player == null || bowStack == null || bowStack.isEmpty()) return false;
        if (!player.isUsingItem() || player.getUseItem() != bowStack) return false;

        Item item = bowStack.getItem();

        // 1. 针对 ExtraBotany FailnaughtItem 与 Botania 弓类：必须达到满进程 (>= 1.0f) 才是最高 Tier 满伤害 (16.9)
        try {
            Method m = item.getClass().getMethod("getChargeProcess", ItemStack.class, net.minecraft.world.entity.LivingEntity.class);
            Object res = m.invoke(item, bowStack, player);
            if (res instanceof Number) {
                return ((Number) res).floatValue() >= 1.0f;
            }
        } catch (Throwable ignored) {}

        // 2. 检查客户端 ItemProperties 中的 "pull"
        try {
            net.minecraft.client.renderer.item.ItemPropertyFunction pullFunc = 
                net.minecraft.client.renderer.item.ItemProperties.getProperty(item, PULL_PROPERTY);
            if (pullFunc != null && player.level() instanceof net.minecraft.client.multiplayer.ClientLevel clientLevel) {
                float pull = pullFunc.call(bowStack, clientLevel, player, 0);
                return pull >= 1.0f;
            }
        } catch (Throwable ignored) {}

        // 3. 检查神话拉弓速度 (minChargeTicks)
        BallisticsProfile profile = getProfile(bowStack);
        int usedTicks = player.getTicksUsingItem();
        if (profile.minChargeTicks < 20) {
            return usedTicks >= profile.minChargeTicks;
        }

        // 4. 原版标准 BowItem
        return BowItem.getPowerForTime(usedTicks) >= 1.0F;
    }

    /**
     * 探测神话 / AttributesLib 箭矢初速加成 (Arrow Velocity)
     */
    public static float detectSpeedMultiplier(ItemStack bowStack, Player player) {
        float multiplier = 1.0f;

        if (player != null) {
            for (Attribute attr : getArrowVelocityAttributes()) {
                net.minecraft.world.entity.ai.attributes.AttributeInstance inst = player.getAttribute(attr);
                if (inst != null) {
                    double val = inst.getValue();
                    if (val > 0.1) {
                        return (float) val;
                    }
                }
            }
        }

        CompoundTag tag = bowStack.getTag();
        if (tag != null) {
            if (tag.contains("AttributeModifiers", Tag.TAG_LIST)) {
                ListTag list = tag.getList("AttributeModifiers", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag mod = list.getCompound(i);
                    String name = mod.getString("AttributeName").toLowerCase(Locale.ROOT);
                    if (name.contains("velocity") || name.contains("arrow_speed")) {
                        double amount = mod.getDouble("Amount");
                        int op = mod.getInt("Operation");
                        if (op == 0) multiplier += (float) amount;
                        else if (op == 1 || op == 2) multiplier *= (1.0f + (float) amount);
                    }
                }
            }

            if (tag.contains("apotheosis:affixes", Tag.TAG_COMPOUND)) {
                CompoundTag affixes = tag.getCompound("apotheosis:affixes");
                for (String k : affixes.getAllKeys()) {
                    String lower = k.toLowerCase(Locale.ROOT);
                    if (lower.contains("velocity") || lower.contains("projectile_speed") || lower.contains("flight")) {
                        float val = affixes.getFloat(k);
                        if (val > 0.0f) {
                            multiplier += val;
                        }
                    }
                }
            }
        }

        return Math.max(0.2f, multiplier);
    }

    /**
     * 静态指纹：零重力/能量箭检测
     */
    private static double resolveDefaultGravity(ItemStack stack) {
        Item item = stack.getItem();
        if (AutoAttackerConfig.zeroGravityBowItems.contains(item)) return 0.0;
        for (TagKey<Item> tag : AutoAttackerConfig.zeroGravityBowTags) {
            if (stack.is(tag)) return 0.0;
        }

        ResourceLocation res = ForgeRegistries.ITEMS.getKey(item);
        if (res != null) {
            String path = res.getPath().toLowerCase(Locale.ROOT);
            String ns = res.getNamespace().toLowerCase(Locale.ROOT);
            // 仅保留 ExtraBotany 百中弓默认零重力
            if (ns.equals("extrabotany") && path.contains("failnaught")) {
                return 0.0;
            }
        }
        return AutoAttackerConfig.AIM_PREDICT_GRAVITY.get();
    }

    /**
     * 静态指纹：初速检测
     */
    private static float resolveDefaultSpeed(ItemStack stack) {
        Item item = stack.getItem();
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(item);
        if (res != null) {
            String path = res.getPath().toLowerCase(Locale.ROOT);
            // 仅保留 ExtraBotany 百中弓默认初速 7.0
            if (path.contains("failnaught")) return 7.0f;
        }
        if (item instanceof CrossbowItem) return 3.15f;
        return AutoAttackerConfig.AIM_PREDICT_ARROW_SPEED.get().floatValue();
    }

    private static boolean isHomingWeapon(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            String s = tag.toString().toLowerCase(Locale.ROOT);
            if (s.contains("homing") || s.contains("tracking") || s.contains("guidance") || s.contains("auto_aim")) {
                return true;
            }
        }
        ResourceLocation res = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (res != null) {
            String name = res.getPath().toLowerCase(Locale.ROOT);
            return name.contains("homing") || name.contains("tracking") || name.contains("seeker");
        }
        return false;
    }

    private static BallisticsProfile getDefaultProfile() {
        return new BallisticsProfile(
            AutoAttackerConfig.AIM_PREDICT_ARROW_SPEED.get().floatValue(),
            AutoAttackerConfig.AIM_PREDICT_GRAVITY.get(),
            0.99,
            false,
            AutoAttackerConfig.AIM_PREDICT_MAX_DIST.get(),
            1.0f,
            20
        );
    }

    // =========================================================================
    // MC 原版前向微元二分求解器 (彻底解决 2.34° 欠角导致的射击高度偏低问题)
    // =========================================================================

    private static final class SimResult {
        final double hitY;
        final double flightTicks;
        final boolean reached;

        SimResult(double hitY, double flightTicks, boolean reached) {
            this.hitY = hitY;
            this.flightTicks = flightTicks;
            this.reached = reached;
        }
    }

    private static final double SUBSTEP = 0.25D;

    /**
     * 前向微元物理模拟：模拟箭矢沿指定仰角发射，在达到目标水平距离时的真实高度落点
     * 遵循原版纯净空气动力学物理（空气阻力保留率 drag，标准重力 gravity）
     */
    private static SimResult simulateTrajectory(double targetDist, double targetDy, double speed, double gravity, double drag, double elevAngleRad) {
        double vx = speed * Math.cos(elevAngleRad);
        double vy = speed * Math.sin(elevAngleRad);
        double x = 0.0;
        double y = 0.0;

        int maxSteps = 300;
        double minY = Math.min(-100.0, targetDy - 50.0);

        for (int step = 0; step < maxSteps; step++) {
            double nextX = x + vx;
            double nextY = y + vy;

            if (nextX >= targetDist) {
                double frac = (targetDist - x) / Math.max(vx, 1.0E-6);
                frac = Mth.clamp(frac, 0.0, 1.0);
                double hitY = y + vy * frac;
                double hitT = step + frac;
                return new SimResult(hitY, hitT, true);
            }

            x = nextX;
            y = nextY;
            vx *= drag;
            vy = (vy - gravity) * drag;

            if (y < minY && vy < 0.0) break;
        }
        return new SimResult(y, 300.0, false);
    }

    public static TrajectorySolution solveTrajectory(Vec3 eye, Vec3 targetAimPoint, double speed, double gravity) {
        return solveTrajectory(eye, targetAimPoint, speed, gravity, 0.99D);
    }

    /**
     * 二分求解发射仰角：在 8~20 步内收敛到误差 < 0.01 格的绝对精准仰角
     * @param eye 玩家眼睛位置
     * @param targetAimPoint 目标期望击中点 (如胸口中上部)
     * @param speed 初速度
     * @param gravity 重力
     * @param drag 空气阻力/速度保留率
     * @return TrajectorySolution 包含精确 pitch 和预计飞行时间
     */
    public static TrajectorySolution solveTrajectory(Vec3 eye, Vec3 targetAimPoint, double speed, double gravity, double drag) {
        double dx = targetAimPoint.x - eye.x;
        double dz = targetAimPoint.z - eye.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        double targetDy = targetAimPoint.y - eye.y;
        double totalDist = eye.distanceTo(targetAimPoint);

        if (horizDist < 0.15 || gravity <= 1.0E-6D) {
            float directPitch = (float) -(Mth.atan2(targetDy, Math.max(0.001, horizDist)) * (180D / Math.PI));
            double flightTime = totalDist / Math.max(speed, 0.25);
            return new TrajectorySolution(directPitch, flightTime, true);
        }

        // MC 物理二分迭代搜索最优发射仰角 (elevAngle: 向上为正弧度)
        double directAngle = Math.atan2(targetDy, horizDist);
        double low = Math.max(Math.toRadians(-89.0), directAngle - Math.toRadians(5.0));
        double high = Math.min(Math.toRadians(89.0), Math.max(directAngle + Math.toRadians(55.0), Math.toRadians(65.0)));
        if (low >= high) {
            low = Math.toRadians(-89.0);
            high = Math.toRadians(89.0);
        }

        double bestElev = directAngle;
        double bestFlightTime = totalDist / Math.max(speed, 0.25);
        boolean found = false;
        boolean bestReached = false;
        double bestDiff = Double.MAX_VALUE;

        for (int iter = 0; iter < 20; iter++) {
            double mid = (low + high) * 0.5;
            SimResult res = simulateTrajectory(horizDist, targetDy, speed, gravity, drag, mid);

            if (!res.reached) {
                if (mid < Math.toRadians(70.0)) {
                    low = mid;
                } else {
                    high = mid;
                }
                continue;
            }

            bestReached = true;
            double diff = res.hitY - targetDy;
            if (Math.abs(diff) < Math.abs(bestDiff)) {
                bestDiff = diff;
                bestElev = mid;
                bestFlightTime = res.flightTicks;
            }

            if (Math.abs(diff) < 0.01) {
                found = true;
                break;
            }

            if (diff > 0) {
                high = mid; // 落点偏高，压低仰角
            } else {
                low = mid;  // 落点偏低，抬高仰角
            }
        }

        float finalPitchDeg = (float) -(bestElev * (180D / Math.PI));
        boolean reachable = found || (bestReached && Math.abs(bestDiff) < 0.5);
        return new TrajectorySolution(finalPitchDeg, bestFlightTime, reachable);
    }

    // =========================================================================
    // 弹道跟踪与实测管理 (保证物理学常量纯净，杜绝噪声反向污染物理引擎)
    // =========================================================================

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide()) return;
        if (!com.xdyyj.autoattacker.ui.BallisticsCalibrator.isCalibrating()) return;

        Entity entity = event.getEntity();
        if (!isPotentialProjectile(entity)) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // 仅采样由玩家发射的箭矢 (生成位置紧挨着玩家眼睛)
        Vec3 eye = player.getEyePosition();
        if (entity.position().distanceToSqr(eye) < 16.0) {
            Vec3 deltaMovement = entity.getDeltaMovement();
            com.xdyyj.autoattacker.ui.BallisticsCalibrator.recordLaunch(
                entity.getId(),
                eye,
                entity.getXRot(), // 使用箭矢生成时的真实仰角
                entity.getYRot(), // 使用箭矢生成时的真实偏航角
                deltaMovement,
                player.tickCount
            );
        }
    }

    public static void clientTick() {
        if (!com.xdyyj.autoattacker.ui.BallisticsCalibrator.isCalibrating()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 对正在飞行中的样本进行追踪更新
        for (var entry : com.xdyyj.autoattacker.ui.BallisticsCalibrator.getInFlightRecords().entrySet()) {
            Entity ent = mc.level.getEntity(entry.getKey());
            if (ent != null) {
                Vec3 vel = ent.getDeltaMovement();
                // 停下判定：原版 onGround() 或 速度已归零 (扎在生物或方块上)
                boolean isStopped = ent.onGround() || vel.lengthSqr() < 0.005 || !ent.isAlive() || ent.isRemoved();
                com.xdyyj.autoattacker.ui.BallisticsCalibrator.updateInFlight(ent.getId(), ent.position(), vel, isStopped);
            } else {
                // 实体已消失或脱离加载
                com.xdyyj.autoattacker.ui.BallisticsCalibrator.updateInFlight(entry.getKey(), entry.getValue().lastPos, Vec3.ZERO, true);
            }
        }
    }

    private static boolean isPotentialProjectile(Entity entity) {
        if (entity instanceof AbstractArrow || entity instanceof Projectile) return true;
        String name = entity.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("arrow") || name.contains("projectile") || name.contains("bullet") || name.contains("shot");
    }

    private static Double tryGetGravityReflection(Entity entity) {
        Class<?> clazz = entity.getClass();
        for (String methodName : new String[]{"getGravity", "getDefaultGravity", "getGravityVelocity"}) {
            try {
                Method m = clazz.getMethod(methodName);
                m.setAccessible(true);
                Object res = m.invoke(entity);
                if (res instanceof Number num) {
                    return num.doubleValue();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static ItemStack getHeldBow(Player player) {
        if (player.isUsingItem() && (ClientEvents.isBow(player.getUseItem()) || com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(player.getUseItem()))) {
            return player.getUseItem();
        }
        if (ClientEvents.isBow(player.getMainHandItem()) || com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(player.getMainHandItem())) {
            return player.getMainHandItem();
        }
        if (ClientEvents.isBow(player.getOffhandItem()) || com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(player.getOffhandItem())) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    // =========================================================================
    // 磁盘持久化 JSON 读写 (具备平滑迁移、权威预设回退、防写空截断与原子备份)
    // =========================================================================

    private static Path getConfigFile() {
        return FMLPaths.CONFIGDIR.get().resolve("combathelper_ballistics.json");
    }

    private static Path getOldConfigFile() {
        return FMLPaths.CONFIGDIR.get().resolve("autoattacker_ballistics.json");
    }

    private static BallisticsProfile cloneProfile(BallisticsProfile p) {
        return new BallisticsProfile(p.speed, p.gravity, p.drag, p.isHoming, p.maxRange, p.drawSpeed, p.minChargeTicks, p.lastUpdated);
    }

    private static BallisticsProfile cloneAndAdapt(BallisticsProfile base, ItemStack bowStack) {
        Player player = Minecraft.getInstance().player;
        float speedMult = detectSpeedMultiplier(bowStack, player);
        float drawSpeed = detectDrawSpeed(bowStack, player);
        float finalSpeed = base.speed * speedMult;
        float finalDrawSpeed = base.drawSpeed * drawSpeed;
        int minTicks = (base.minChargeTicks > 0)
                ? Math.max(1, (int) Math.ceil((double) base.minChargeTicks / Math.max(0.1, drawSpeed)))
                : Math.max(1, (int) Math.ceil(20.0 / Math.max(0.1, finalDrawSpeed)));
        return new BallisticsProfile(finalSpeed, base.gravity, base.drag, base.isHoming, base.maxRange, finalDrawSpeed, minTicks, System.currentTimeMillis());
    }

    private static synchronized void loadFromDisk() {
        // 1. 先注入 16 种经典武器原厂权威基准预设 (开箱即用，永不空虚)
        for (Map.Entry<String, BallisticsProfile> entry : DEFAULT_FACTORY_PRESETS.entrySet()) {
            CACHE.putIfAbsent(entry.getKey(), cloneProfile(entry.getValue()));
        }

        // 2. 检查新路径与旧路径
        Path path = getConfigFile();
        Path oldPath = getOldConfigFile();
        Path sourcePath = null;
        boolean needMigrate = false;

        if (Files.exists(path)) {
            sourcePath = path;
        } else if (Files.exists(oldPath)) {
            sourcePath = oldPath;
            needMigrate = true;
            LOGGER.info("检测到旧版武器档案库 [autoattacker_ballistics.json]，正在无缝迁移至 [combathelper_ballistics.json]...");
        }

        if (sourcePath != null) {
            boolean loadedSuccess = false;
            try (Reader reader = Files.newBufferedReader(sourcePath)) {
                Map<String, BallisticsProfile> loaded = GSON.fromJson(reader, new TypeToken<Map<String, BallisticsProfile>>() {}.getType());
                if (loaded != null && !loaded.isEmpty()) {
                    long now = System.currentTimeMillis();
                    long idx = 0;
                    for (BallisticsProfile p : loaded.values()) {
                        if (p.lastUpdated <= 0) {
                            p.lastUpdated = now - (loaded.size() - idx) * 1000L;
                        }
                        idx++;
                    }
                    // 用户本地档案覆盖/追加到内存库中
                    CACHE.putAll(loaded);
                    // 自动净化历史迁移碎片数据
                    CACHE.keySet().removeIf(k -> k.contains("modern_kinetic_gun#"));
                    LOGGER.info("成功载入 " + loaded.size() + " 个本地武器弹道档案。当前档案库共计 " + CACHE.size() + " 种武器。");
                    loadedSuccess = true;
                }
            } catch (Exception e) {
                LOGGER.error("读取武器弹道档案失败: " + sourcePath + "，正在尝试从 .bak 备份恢复...", e);
            }

            if (!loadedSuccess) {
                Path backupPath = sourcePath.resolveSibling(sourcePath.getFileName().toString() + ".bak");
                if (Files.exists(backupPath)) {
                    try (Reader backupReader = Files.newBufferedReader(backupPath)) {
                        Map<String, BallisticsProfile> loaded = GSON.fromJson(backupReader, new TypeToken<Map<String, BallisticsProfile>>() {}.getType());
                        if (loaded != null && !loaded.isEmpty()) {
                            long now = System.currentTimeMillis();
                            long idx = 0;
                            for (BallisticsProfile p : loaded.values()) {
                                if (p.lastUpdated <= 0) {
                                    p.lastUpdated = now - (loaded.size() - idx) * 1000L;
                                }
                                idx++;
                            }
                            CACHE.putAll(loaded);
                            CACHE.keySet().removeIf(k -> k.contains("modern_kinetic_gun#"));
                            LOGGER.info("成功从 .bak 备份文件载入 " + loaded.size() + " 个本地武器弹道档案。");
                        }
                    } catch (Exception ex) {
                        LOGGER.error("从 .bak 备份文件读取武器弹道档案亦失败: " + backupPath, ex);
                    }
                }
            }
        }

        // 3. 若从旧文件迁移或新文件尚不存在，立即安全回写磁盘
        if (needMigrate || !Files.exists(path)) {
            saveToDiskImmediate();
        }
    }

    public static void clearAllCache() {
        ensureLoaded();
        CACHE.clear();
        cachedDrawSpeedAttributes = null;
        cachedArrowVelocityAttributes = null;
        // 恢复 16 种经典武器权威原厂预设
        for (Map.Entry<String, BallisticsProfile> entry : DEFAULT_FACTORY_PRESETS.entrySet()) {
            CACHE.put(entry.getKey(), cloneProfile(entry.getValue()));
        }
        lastHeldStackRef = ItemStack.EMPTY;
        lastCachedProfile = null;
        saveToDiskImmediate();
    }

    public static int getCacheSize() {
        ensureLoaded();
        return CACHE.size();
    }

    public static Map<String, BallisticsProfile> getCacheEntries() {
        ensureLoaded();
        return Collections.unmodifiableMap(CACHE);
    }

    public static void removeProfileByKey(String sig) {
        ensureLoaded();
        if (sig == null || sig.isEmpty()) return;
        CACHE.remove(sig);
        lastHeldStackRef = ItemStack.EMPTY;
        lastCachedProfile = null;
        markDirtyAndSave();
    }

    public static void resetProfile(ItemStack stack) {
        ensureLoaded();
        if (stack == null || stack.isEmpty()) return;
        String sig = getBallisticSignature(stack);
        ResourceLocation reg = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String baseId = reg != null ? reg.toString() : null;

        // 如果该武器属于原厂内置预设武器，重置时恢复为原厂权威预设
        if (baseId != null && DEFAULT_FACTORY_PRESETS.containsKey(baseId)) {
            BallisticsProfile factory = DEFAULT_FACTORY_PRESETS.get(baseId);
            CACHE.put(baseId, cloneProfile(factory));
            if (!sig.equals(baseId)) {
                CACHE.remove(sig);
            }
        } else {
            CACHE.remove(sig);
            if (baseId != null) {
                CACHE.remove(baseId);
            }
        }
        lastHeldStackRef = ItemStack.EMPTY;
        lastCachedProfile = null;
        markDirtyAndSave();
    }

    public static BallisticsProfile reparseTooltip(ItemStack stack) {
        ensureLoaded();
        if (stack == null || stack.isEmpty()) return null;
        String sig = getBallisticSignature(stack);
        BallisticsProfile p = computeInitialProfile(stack);
        p.lastUpdated = System.currentTimeMillis();
        CACHE.put(sig, p);
        lastHeldStackRef = ItemStack.EMPTY;
        lastCachedProfile = null;
        markDirtyAndSave();
        return p;
    }

    public static void markDirty() {
        isDirty = true;
        if (saveTask != null && !saveTask.isDone()) {
            saveTask.cancel(false);
        }
        // 300ms 快速防抖异步存盘
        saveTask = ASYNC_IO.schedule(AutoBallisticsTracker::saveToDisk, 300, TimeUnit.MILLISECONDS);
    }

    public static void markDirtyAndSave() {
        isDirty = true;
        ASYNC_IO.execute(AutoBallisticsTracker::saveToDisk);
    }

    public static synchronized void saveToDiskImmediate() {
        ensureLoaded();
        isDirty = true;
        saveToDisk();
    }

    private static synchronized void saveToDisk() {
        if (!isDirty) return;
        Path path = getConfigFile();
        Path tempFile = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try {
            // 防写空安全保护：若 CACHE 为空，但目标文件存在且包含内容，坚决拒绝覆盖并警告
            if (CACHE.isEmpty()) {
                if (Files.exists(path) && Files.size(path) > 10) {
                    LOGGER.warn("安全拦截：内存武器档案库为空，已拒绝覆盖磁盘文件！");
                    isDirty = false;
                    return;
                }
            }

            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            // 写入前保留 .bak 备份文件
            if (Files.exists(path) && Files.size(path) > 0) {
                Path backup = path.resolveSibling(path.getFileName().toString() + ".bak");
                try {
                    Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
                } catch (Throwable ignored) {}
            }

            // 采用临时文件 + 原子替换，防止断电或意外中断导致文件损坏
            try (Writer writer = Files.newBufferedWriter(tempFile)) {
                GSON.toJson(CACHE, writer);
            }
            try {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING);
            }

            isDirty = false;
        } catch (Exception e) {
            LOGGER.error("保存武器弹道档案失败: " + path, e);
        } finally {
            if (Files.exists(tempFile)) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Throwable ignored) {}
            }
        }
    }
}
