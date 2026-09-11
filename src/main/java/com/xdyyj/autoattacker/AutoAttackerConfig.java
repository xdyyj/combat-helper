package com.xdyyj.autoattacker;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AutoAttackerConfig {
    
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // --- 1. Master Switches ---
    public static final ForgeConfigSpec.BooleanValue ENABLE_MOD;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_ATTACK;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_SHOOT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_AIM_ASSIST;
    
    public static final ForgeConfigSpec.DoubleValue AIM_ASSIST_RANGE;
    public static final ForgeConfigSpec.DoubleValue AIM_ASSIST_SPEED;
    public static final ForgeConfigSpec.DoubleValue LOCK_DEADZONE_THRESHOLD;

    public static final ForgeConfigSpec.BooleanValue ENABLE_AIM_PREDICT;
    public static final ForgeConfigSpec.DoubleValue AIM_PREDICT_BLEND;
    public static final ForgeConfigSpec.DoubleValue AIM_PREDICT_SMOOTH;
    public static final ForgeConfigSpec.DoubleValue AIM_PREDICT_MAX_DIST;
    public static final ForgeConfigSpec.DoubleValue AIM_PREDICT_ARROW_SPEED;
    public static final ForgeConfigSpec.DoubleValue AIM_PREDICT_GRAVITY;
    
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ZERO_GRAVITY_BOWS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOW_GRAVITY_OVERRIDES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BOW_SPEED_OVERRIDES;

    public enum LockMode { HOLD, TOGGLE }
    public static final ForgeConfigSpec.EnumValue<LockMode> AIM_ASSIST_MODE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LOCK_PARTICLES;

    public enum AimLockType {
        SMOOTH("平滑自瞄"),
        HARD("极速强锁");

        private final String displayName;
        AimLockType(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }
    public static final ForgeConfigSpec.EnumValue<AimLockType> AIM_LOCK_TYPE;

    public static final ForgeConfigSpec.BooleanValue ENABLE_AUTO_SWITCH_TARGET;
    public enum SwitchPriority {
        FOV("准星优先"),
        DISTANCE("距离优先"),
        HEALTH("残血优先");

        private final String displayName;
        SwitchPriority(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }
    public static final ForgeConfigSpec.EnumValue<SwitchPriority> AUTO_SWITCH_PRIORITY;

    public enum AutoLockMode {
        OFF("关闭"),
        HOVER("悬停锁定 (指向N秒)"),
        ALWAYS("始终自锁 (持械)");

        private final String displayName;
        AutoLockMode(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }
    public static final ForgeConfigSpec.EnumValue<AutoLockMode> AUTO_LOCK_MODE;
    public static final ForgeConfigSpec.DoubleValue AUTO_LOCK_HOVER_TIME;
    public static final ForgeConfigSpec.DoubleValue AUTO_LOCK_FOV;
    
    public enum TargetPart {
        HEAD("头部优先"),
        TORSO("躯干中心"),
        ADAPTIVE("智能自适应");

        private final String displayName;
        TargetPart(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }
    public static final ForgeConfigSpec.EnumValue<TargetPart> TARGET_PART;

    // 现代枪械战术扩展 (Firearms Tactical Extensions)
    public static final ForgeConfigSpec.BooleanValue ENABLE_GUN_TRIGGERBOT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ANTI_RECOIL;
    public static final ForgeConfigSpec.DoubleValue ANTI_RECOIL_STRENGTH;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ADS_SENSING;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GUN_AUTO_RELOAD;

    public enum LockParticleType { ENCHANTED_HIT, SOUL_FIRE_FLAME, DRAGON_BREATH, WITCH, GLOW, FLAME }
    public static final ForgeConfigSpec.EnumValue<LockParticleType> LOCK_PARTICLE_TYPE;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ENTITY_BLACKLIST;

    public enum TrajectoryStyle {
        BOTH("双显"),
        HUD_RETICLE("2D准星"),
        PARTICLE_CHAIN("3D光束"),
        OFF("关闭");

        private final String displayName;
        TrajectoryStyle(String displayName) {
            this.displayName = displayName;
        }
        public String getDisplayName() {
            return displayName;
        }
    }
    public static final ForgeConfigSpec.EnumValue<TrajectoryStyle> TRAJECTORY_STYLE;

    public static final ForgeConfigSpec.BooleanValue ENABLE_DEBUG_OVERLAY;
    public static final ForgeConfigSpec.BooleanValue HUD_ONLY_WHEN_HOLDING_BOW;
    public static final ForgeConfigSpec.BooleanValue ENABLE_TRAJECTORY_PREVIEW;
    public static final ForgeConfigSpec.BooleanValue SHOW_DISTANCE;
    public static final ForgeConfigSpec.BooleanValue SHOW_HEALTH_BAR;
    public static final ForgeConfigSpec.BooleanValue ENABLE_LEAD_INDICATOR;
    public static final ForgeConfigSpec.IntValue OVERLAY_POS_X;
    public static final ForgeConfigSpec.IntValue OVERLAY_POS_Y;

    // 优化的缓存集合结构
    public static volatile Set<Item> blacklistItems = ConcurrentHashMap.newKeySet();
    public static volatile Set<TagKey<Item>> blacklistTags = ConcurrentHashMap.newKeySet();
    
    public static volatile Set<Item> whitelistItems = ConcurrentHashMap.newKeySet();
    public static volatile Set<TagKey<Item>> whitelistTags = ConcurrentHashMap.newKeySet();

    public static volatile Set<net.minecraft.world.entity.EntityType<?>> excludedEntities = ConcurrentHashMap.newKeySet();

    public static volatile Set<Item> zeroGravityBowItems = ConcurrentHashMap.newKeySet();
    public static volatile Set<TagKey<Item>> zeroGravityBowTags = ConcurrentHashMap.newKeySet();
    public static volatile Map<Item, Double> bowGravityMap = new ConcurrentHashMap<>();
    public static volatile Map<Item, Double> bowSpeedMap = new ConcurrentHashMap<>();

    // --- 3. Server Settings ---
    
    static {
        BUILDER.push("General_Settings");

        ENABLE_MOD = BUILDER
                .comment("Master switch for the entire mod.")
                .translation("config.autoattacker.enable_mod")
                .define("enableMod", true);

        ENABLE_AUTO_ATTACK = BUILDER
                .comment("Enable/Disable automatic melee attacks.")
                .translation("config.autoattacker.enable_auto_attack")
                .define("enableAutoAttack", true);

        ENABLE_AUTO_SHOOT = BUILDER
                .comment("Enable/Disable automatic bow shooting.")
                .translation("config.autoattacker.enable_auto_shoot")
                .define("enableAutoShoot", true);

        BUILDER.pop();

        // --- New Section: Lock-On Settings ---
        BUILDER.push("Lock_On_Settings");

        ENABLE_AIM_ASSIST = BUILDER
                .comment("Enable soft aim assist (lock-on/magnet). Triggered by hotkey.")
                .translation("config.autoattacker.enable_aim_assist")
                .define("enableAimAssist", false);
                
        AIM_ASSIST_RANGE = BUILDER
                .comment("Max distance (blocks) for lock-on target search.")
                .translation("config.autoattacker.aim_assist_range")
                .defineInRange("aimAssistRange", 64.0, 2.0, 120.0);
        
        AIM_ASSIST_SPEED = BUILDER
                .comment("Rotation speed factor (larger = faster lock-on).")
                .translation("config.autoattacker.aim_assist_speed")
                .defineInRange("aimAssistSpeed", 0.15, 0.01, 1.0);

        LOCK_DEADZONE_THRESHOLD = BUILDER
                .comment("Mouse deadzone angle (degrees) before switching locked targets.")
                .translation("config.autoattacker.lock_deadzone_threshold")
                .defineInRange("lockDeadzoneThreshold", 8.0, 1.0, 30.0);

        TARGET_PART = BUILDER
                .comment("Target body part to lock onto: HEAD (headshot priority), TORSO (body center), or ADAPTIVE.")
                .translation("config.autoattacker.target_part")
                .defineEnum("targetPart", TargetPart.HEAD);

        ENABLE_GUN_TRIGGERBOT = BUILDER
                .comment("Enable automatic triggerbot when locking on target with a firearm.")
                .translation("config.autoattacker.enable_gun_triggerbot")
                .define("enableGunTriggerbot", true);

        ENABLE_ANTI_RECOIL = BUILDER
                .comment("Enable anti-recoil compensation for firearms.")
                .translation("config.autoattacker.enable_anti_recoil")
                .define("enableAntiRecoil", false);

        ANTI_RECOIL_STRENGTH = BUILDER
                .comment("Anti-recoil compensation strength multiplier (0.1 to 2.5).")
                .translation("config.autoattacker.anti_recoil_strength")
                .defineInRange("antiRecoilStrength", 1.0, 0.1, 2.5);

        ENABLE_ADS_SENSING = BUILDER
                .comment("Enable ADS (Aim Down Sights) sensing for high-stability micro tracking.")
                .translation("config.autoattacker.enable_ads_sensing")
                .define("enableAdsSensing", true);

        ENABLE_GUN_AUTO_RELOAD = BUILDER
                .comment("Automatically reload gun when magazine and chamber are empty, if ammo is available in inventory.")
                .translation("config.autoattacker.enable_gun_auto_reload")
                .define("enableGunAutoReload", false);

        ENABLE_AIM_PREDICT = BUILDER
                .comment("Enable bow aim prediction.")
                .translation("config.autoattacker.enable_aim_predict")
                .define("enableAimPredict", true);

        AIM_PREDICT_BLEND = BUILDER
                .comment("Blend factor between target position (0.0) and predicted position (1.0) when holding bow.")
                .translation("config.autoattacker.aim_predict_blend")
                .defineInRange("aimPredictBlend", 1.0, 0.0, 1.0);

        AIM_PREDICT_SMOOTH = BUILDER
                .comment("Target velocity smoothing factor for aim prediction (0 to 1).")
                .translation("config.autoattacker.aim_predict_smooth")
                .defineInRange("aimPredictSmooth", 0.5, 0.0, 1.0);

        AIM_PREDICT_MAX_DIST = BUILDER
                .comment("Maximum distance (blocks) for bow aim prediction.")
                .translation("config.autoattacker.aim_predict_max_dist")
                .defineInRange("aimPredictMaxDist", 60.0, 5.0, 120.0);

        AIM_PREDICT_ARROW_SPEED = BUILDER
                .comment("Initial velocity of the arrow for prediction (default 3.0). Adjust this if a mod bow (like Simply Bows) has different arrow speed.")
                .translation("config.autoattacker.aim_predict_arrow_speed")
                .defineInRange("aimPredictArrowSpeed", 3.0, 0.5, 10.0);

        AIM_PREDICT_GRAVITY = BUILDER
                .comment("Gravity drop per tick for arrow prediction (default 0.05). Adjust this if a mod bow changes arrow ballistics.")
                .translation("config.autoattacker.aim_predict_gravity")
                .defineInRange("aimPredictGravity", 0.05, 0.0, 0.5);

        ZERO_GRAVITY_BOWS = BUILDER
                .comment("List of item IDs or Tags (starting with #) for bows with ZERO gravity (e.g., ExtraBotany Failnaught / 百中弓).")
                .translation("config.autoattacker.zero_gravity_bows")
                .defineList("zeroGravityBows", List.of("extrabotany:failnaught"), obj -> obj instanceof String);

        BOW_GRAVITY_OVERRIDES = BUILDER
                .comment("Custom gravity overrides for specific bows in format 'item_id=gravity'.")
                .translation("config.autoattacker.bow_gravity_overrides")
                .defineList("bowGravityOverrides", List.of(), obj -> obj instanceof String);

        BOW_SPEED_OVERRIDES = BUILDER
                .comment("Custom initial arrow speed overrides for specific bows in format 'item_id=speed' (e.g. 'some_mod:fast_bow=4.0').")
                .translation("config.autoattacker.bow_speed_overrides")
                .defineList("bowSpeedOverrides", List.of(), obj -> obj instanceof String);

        AIM_ASSIST_MODE = BUILDER
                .comment("Lock-on mode: HOLD (press and hold) or TOGGLE (press to lock, press again to unlock).")
                .translation("config.autoattacker.aim_assist_mode")
                .defineEnum("aimAssistMode", LockMode.HOLD);

        AIM_LOCK_TYPE = BUILDER
                .comment("Aim lock rigidity type: SMOOTH (smooth interpolated tracking) or HARD (instant 0-frame snap lock).")
                .translation("config.autoattacker.aim_lock_type")
                .defineEnum("aimLockType", AimLockType.SMOOTH);

        ENABLE_AUTO_SWITCH_TARGET = BUILDER
                .comment("Automatically switch to next valid target when current target dies or is lost.")
                .translation("config.autoattacker.enable_auto_switch_target")
                .define("enableAutoSwitchTarget", true);

        AUTO_SWITCH_PRIORITY = BUILDER
                .comment("Target selection priority when auto switching: FOV (closest to crosshair), DISTANCE (closest to player), or HEALTH (lowest health).")
                .translation("config.autoattacker.auto_switch_priority")
                .defineEnum("autoSwitchPriority", SwitchPriority.FOV);

        AUTO_LOCK_MODE = BUILDER
                .comment("Auto-lock mode: OFF (disabled), HOVER (lock after crosshair points at entity for N seconds), or ALWAYS (always auto-lock while holding weapon).")
                .translation("config.autoattacker.auto_lock_mode")
                .defineEnum("autoLockMode", AutoLockMode.OFF);

        AUTO_LOCK_HOVER_TIME = BUILDER
                .comment("Hover duration in seconds required to trigger auto-lock when crosshair points at an entity.")
                .translation("config.autoattacker.auto_lock_hover_time")
                .defineInRange("autoLockHoverTime", 0.3, 0.05, 3.0);

        AUTO_LOCK_FOV = BUILDER
                .comment("FOV search angle in degrees for auto lock detection while holding weapon.")
                .translation("config.autoattacker.auto_lock_fov")
                .defineInRange("autoLockFov", 75.0, 15.0, 180.0);

        ENABLE_LOCK_PARTICLES = BUILDER
                .comment("Spawn particles to highlight the currently locked target. (Deprecated: superseded by tactical brackets)")
                .translation("config.autoattacker.enable_lock_particles")
                .define("enableLockParticles", false);

        LOCK_PARTICLE_TYPE = BUILDER
                .comment("Select the visual style of the lock-on particles.")
                .translation("config.autoattacker.lock_particle_type")
                .defineEnum("lockParticleType", LockParticleType.SOUL_FIRE_FLAME);

        BUILDER.pop();

        BUILDER.push("List_Settings");

        BLACKLIST = BUILDER
                .comment("List of item IDs or Tags (starting with #) to disable auto-attack for.")
                .translation("config.autoattacker.blacklist")
                .defineList("blacklist", List.of(), obj -> obj instanceof String);
        
        WHITELIST = BUILDER
                .comment("List of item IDs or Tags (starting with #) to *force* auto-attack for.")
                .translation("config.autoattacker.whitelist")
                .defineList("whitelist", List.of(), obj -> obj instanceof String);

        ENTITY_BLACKLIST = BUILDER
                .comment("List of Entity IDs (e.g., 'minecraft:villager', 'minecraft:player') to ignore for both lock-on and auto-attack.")
                .translation("config.autoattacker.entity_blacklist")
                .defineList("entityBlacklist", List.of("minecraft:villager", "minecraft:armor_stand"), obj -> obj instanceof String);

        BUILDER.pop();

        // --- Tactical HUD & Visual Settings ---
        BUILDER.push("Tactical_HUD_Settings");

        ENABLE_DEBUG_OVERLAY = BUILDER
                .comment("Enable tactical ballistics HUD overlay in-game.")
                .translation("config.autoattacker.enable_debug_overlay")
                .define("enableDebugOverlay", true);

        HUD_ONLY_WHEN_HOLDING_BOW = BUILDER
                .comment("Only show tactical HUD when holding a bow or crossbow.")
                .translation("config.autoattacker.hud_only_when_holding_bow")
                .define("hudOnlyWhenHoldingBow", true);

        ENABLE_TRAJECTORY_PREVIEW = BUILDER
                .comment("Master switch to render trajectory preview.")
                .translation("config.autoattacker.enable_trajectory_preview")
                .define("enableTrajectoryPreview", true);

        SHOW_DISTANCE = BUILDER
                .comment("Show distance to locked target.")
                .translation("config.autoattacker.show_distance")
                .define("showDistance", true);

        SHOW_HEALTH_BAR = BUILDER
                .comment("Show health bar of locked target.")
                .translation("config.autoattacker.show_health_bar")
                .define("showHealthBar", true);

        TRAJECTORY_STYLE = BUILDER
                .comment("Trajectory visualization style: BOTH (HUD Reticle + 3D Dot Chain), HUD_RETICLE (Plan A 2D Reticle), PARTICLE_CHAIN (Plan B 3D Chain), OFF (Disabled).")
                .translation("config.autoattacker.trajectory_style")
                .defineEnum("trajectoryStyle", TrajectoryStyle.PARTICLE_CHAIN);

        ENABLE_LEAD_INDICATOR = BUILDER
                .comment("Render predicted target interception box in world.")
                .translation("config.autoattacker.enable_lead_indicator")
                .define("enableLeadIndicator", true);

        OVERLAY_POS_X = BUILDER
                .comment("Overlay panel X screen position.")
                .translation("config.autoattacker.overlay_pos_x")
                .defineInRange("overlayPosX", 12, 0, 4000);

        OVERLAY_POS_Y = BUILDER
                .comment("Overlay panel Y screen position.")
                .translation("config.autoattacker.overlay_pos_y")
                .defineInRange("overlayPosY", 36, 0, 4000);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static void refreshLists() {
        Set<Item> newBlacklistItems = ConcurrentHashMap.newKeySet();
        Set<TagKey<Item>> newBlacklistTags = ConcurrentHashMap.newKeySet();
        parseList(BLACKLIST.get(), newBlacklistItems, newBlacklistTags);
        blacklistItems = newBlacklistItems;
        blacklistTags = newBlacklistTags;

        Set<Item> newWhitelistItems = ConcurrentHashMap.newKeySet();
        Set<TagKey<Item>> newWhitelistTags = ConcurrentHashMap.newKeySet();
        parseList(WHITELIST.get(), newWhitelistItems, newWhitelistTags);
        whitelistItems = newWhitelistItems;
        whitelistTags = newWhitelistTags;

        Set<net.minecraft.world.entity.EntityType<?>> newExcludedEntities = ConcurrentHashMap.newKeySet();
        parseEntityList(ENTITY_BLACKLIST.get(), newExcludedEntities);
        excludedEntities = newExcludedEntities;

        Set<Item> newZeroGItems = ConcurrentHashMap.newKeySet();
        Set<TagKey<Item>> newZeroGTags = ConcurrentHashMap.newKeySet();
        parseList(ZERO_GRAVITY_BOWS.get(), newZeroGItems, newZeroGTags);
        zeroGravityBowItems = newZeroGItems;
        zeroGravityBowTags = newZeroGTags;

        Map<Item, Double> newGravityMap = new ConcurrentHashMap<>();
        parseKeyValueDoubleMap(BOW_GRAVITY_OVERRIDES.get(), newGravityMap);
        bowGravityMap = newGravityMap;

        Map<Item, Double> newSpeedMap = new ConcurrentHashMap<>();
        parseKeyValueDoubleMap(BOW_SPEED_OVERRIDES.get(), newSpeedMap);
        bowSpeedMap = newSpeedMap;
    }

    // 优化：配置刷新时进行预解析，避免在运行中频繁解析 String
    @SubscribeEvent
    public static void onConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            refreshLists();
        }
    }

    public static void saveConfig() {
        try {
            SPEC.save();
        } catch (Throwable ignored) {}
    }

    private static void parseKeyValueDoubleMap(List<? extends String> configs, Map<Item, Double> map) {
        for (String entry : configs) {
            if (entry == null || entry.trim().isEmpty()) continue;
            String[] parts = entry.trim().split("=");
            if (parts.length == 2) {
                ResourceLocation loc = ResourceLocation.tryParse(parts[0].trim());
                if (loc != null) {
                    Item item = ForgeRegistries.ITEMS.getValue(loc);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        try {
                            double val = Double.parseDouble(parts[1].trim());
                            map.put(item, val);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
    }

    private static void parseEntityList(List<? extends String> configs, Set<net.minecraft.world.entity.EntityType<?>> set) {
        for (String entry : configs) {
            if (entry == null || entry.trim().isEmpty()) continue;
            ResourceLocation loc = ResourceLocation.tryParse(entry.trim());
            if (loc != null) {
                net.minecraft.world.entity.EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(loc);
                if (type != null) {
                    set.add(type);
                }
            }
        }
    }

    private static void parseList(List<? extends String> configs, Set<Item> itemSet, Set<TagKey<Item>> tagSet) {
        for (String entry : configs) {
            if (entry == null || entry.trim().isEmpty()) continue;
            entry = entry.trim();
            if (entry.startsWith("#")) {
                ResourceLocation loc = ResourceLocation.tryParse(entry.substring(1));
                if (loc != null) {
                    tagSet.add(ItemTags.create(loc));
                }
            } else {
                ResourceLocation loc = ResourceLocation.tryParse(entry);
                if (loc != null) {
                    Item item = ForgeRegistries.ITEMS.getValue(loc);
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        itemSet.add(item);
                    }
                }
            }
        }
    }
}