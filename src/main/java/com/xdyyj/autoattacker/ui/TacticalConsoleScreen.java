package com.xdyyj.autoattacker.ui;

import com.xdyyj.autoattacker.AutoAttackerConfig;
import com.xdyyj.autoattacker.AutoBallisticsTracker;
import com.xdyyj.autoattacker.ClientEvents;
import com.xdyyj.autoattacker.weapon.FirearmAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 现代暗黑侧边战术抽屉配置终端 (Side-Drawer Tactical HUD Console)
 * 
 * 核心特性:
 * 1. 全选项 (i) 图标战术悬浮说明 (Tooltip):
 *    - 每个功能配有高精简战术 (i) 徽章，鼠标移入即弹出原生半透明深色悬浮窗，详尽展示功能原理与推荐设置。
 * 2. 多维度排序系统 (特征库 & 各名单):
 *    - 支持【时间】与【字母】双维度自由切换；
 *    - 支持【倒序 ↓ (最新/Z-A)】与【正序 ↑ (最早/A-Z)】。
 * 3. 武器档案库性能防护:
 *    - 默认折叠，展开后分页限制 (15条/页)，上千条数据丝滑流畅零卡顿；
 *    - 纯净战术管理，保留单项红 X 删除与重置。
 * 4. 名单管理极致人机交互:
 *    - 4 分段直切选项卡，手持武器/准星实体一键录入，单项红 X 秒删。
 */
public class TacticalConsoleScreen extends Screen {

    private final Screen parent;
    private int currentTab = 0;
    private static final String[] TABS = {"锁定", "弹道", "特征", "名单"};

    // Tab 2 (特征库 / 武器档案) 折叠、分页与排序状态
    private boolean isArchiveExpanded = false;
    private int archivePage = 0;
    private static final int ARCHIVE_PAGE_SIZE = 15;
    private boolean archiveSortByTime = true;      // true = 按时间, false = 按字母
    private boolean archiveSortAscending = false;   // false = 倒序 (最新/Z-A), true = 正序 (最早/A-Z)

    // Tab 3 (名单管理) 当前分类与排序状态
    private int activeListCategory = 0; // 0 = 免重力, 1 = 黑名单, 2 = 白名单, 3 = 排除实体
    private static final String[] LIST_CATEGORY_NAMES = {"免重力", "黑名单", "白名单", "排除实体"};
    private boolean listSortByTime = true;         // true = 按录入时间, false = 按字母
    private boolean listSortAscending = false;      // false = 倒序 (最新/Z-A), true = 正序 (最早/A-Z)

    private double scrollOffset = 0;
    private double maxScroll = 0;

    // 当前分页构建好的组件列表
    private final List<UIItem> currentItems = new ArrayList<>();
    private SliderItem activeDraggingSlider = null;
    private boolean isDraggingScrollbar = false;

    // 延迟渲染的悬浮说明提示 (Tooltip)
    private List<Component> hoveredTooltip = null;
    private int tooltipMouseX = 0;
    private int tooltipMouseY = 0;

    // Toast 状态反馈胶囊
    private String toastMessage = null;
    private long toastExpiry = 0;

    // 底部重置确认时间戳
    private long footerResetConfirmTime = 0L;

    public TacticalConsoleScreen(Screen parent) {
        super(Component.literal("Tactical Console"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        rebuildCurrentTab();
    }

    @Override
    public void onClose() {
        AutoAttackerConfig.saveConfig();
        AutoBallisticsTracker.saveToDiskImmediate();
        if (this.parent != null) {
            this.minecraft.setScreen(this.parent);
        } else {
            this.minecraft.setScreen(null);
        }
    }

    private int getDrawerWidth() {
        return Math.max(260, Math.min(360, (int) (this.width * 0.38)));
    }

    public void setHoveredTooltip(List<Component> tooltip, int mouseX, int mouseY) {
        this.hoveredTooltip = tooltip;
        this.tooltipMouseX = mouseX;
        this.tooltipMouseY = mouseY;
    }

    public static List<Component> makeTooltip(String title, String... lines) {
        List<Component> list = new ArrayList<>();
        list.add(Component.literal("§b§l" + title));
        for (String line : lines) {
            list.add(Component.literal("§7" + line));
        }
        return list;
    }

    // =========================================================================
    // 统一组件构建器 (单一真理来源，绝对杜绝坐标错位)
    // =========================================================================

    private void rebuildCurrentTab() {
        currentItems.clear();
        try {
        int drawerW = getDrawerWidth();
        int w = drawerW - 20;
        int x = 10;
        int contentTop = 58;

        if (currentTab == 0) {
            // =================================================================
            // Tab 0: 锁定 (根据手持武器4态智能自适应)
            // =================================================================
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            ItemStack held = ClientEvents.getActiveWeapon(player);
            ClientEvents.WeaponCategory category = ClientEvents.getWeaponCategory(held);

            // 顶部手持状态大卡片
            currentItems.add(new WeaponBannerCard(held, category));

            currentItems.add(new HeaderItem("// 核心总控"));

            currentItems.add(new ToggleItem("模组总开关",
                    AutoAttackerConfig.ENABLE_MOD::get,
                    val -> {
                        AutoAttackerConfig.ENABLE_MOD.set(val);
                        showToast("模组: " + (val ? "已启用" : "已禁用"));
                    },
                    makeTooltip("模组总开关",
                            "Auto Attacker 战术核心总控制开关。",
                            "关闭后将立即休眠所有自动化功能，包括自动近战挥击、自动拉弓射箭、自瞄吸附与提前量预判。")));

            currentItems.add(new ToggleItem("自动近战挥击",
                    AutoAttackerConfig.ENABLE_AUTO_ATTACK::get,
                    val -> {
                        AutoAttackerConfig.ENABLE_AUTO_ATTACK.set(val);
                        showToast("自动近战: " + (val ? "开启" : "关闭"));
                    },
                    makeTooltip("自动近战挥击",
                            "智能武器 CD 满蓄力精准同步挥砍。",
                            "按住攻击键（左键）时，系统自动在武器蓄力冷却（Attack Indicator）达到 100% 满额时出刀。",
                            "确保每一击发挥最大基础伤害与横扫暴击，彻底杜绝过快乱击导致的伤害严重折减。")));

            if (category == ClientEvents.WeaponCategory.BOW || category == ClientEvents.WeaponCategory.OTHER) {
                currentItems.add(new ToggleItem("满蓄力自动放箭",
                        AutoAttackerConfig.ENABLE_AUTO_SHOOT::get,
                        val -> {
                            AutoAttackerConfig.ENABLE_AUTO_SHOOT.set(val);
                            showToast("自动放箭: " + (val ? "开启" : "关闭"));
                        },
                        makeTooltip("满蓄力自动放箭",
                                "远程弓弩满蓄力即刻自动释放。",
                                "手持弓弩按住使用键（右键）蓄力时，一旦武器拉至 100% 最大初速蓄力点，立即自动放箭。",
                                "自动适配神话 (Apotheosis) 蓄力速度宝石与植物学弓，杜绝早泄下坠或过度拉满浪费后摇。")));
            }

            currentItems.add(new HeaderItem("// 瞄准吸附"));

            currentItems.add(new ToggleItem("自瞄吸附",
                    AutoAttackerConfig.ENABLE_AIM_ASSIST::get,
                    val -> {
                        AutoAttackerConfig.ENABLE_AIM_ASSIST.set(val);
                        showToast("自瞄吸附: " + (val ? "开启" : "关闭"));
                    },
                    makeTooltip("自瞄吸附",
                            "平滑磁力视角锁定系统。",
                            "按下锁定快捷键（默认 R 键）时，准星视角将自动吸附并持续咬住射程内最近的目标。")));

            currentItems.add(new CycleItem("瞄准模式",
                    () -> AutoAttackerConfig.AIM_LOCK_TYPE.get().getDisplayName(),
                    () -> {
                        AutoAttackerConfig.AimLockType next = AutoAttackerConfig.AIM_LOCK_TYPE.get() == AutoAttackerConfig.AimLockType.SMOOTH ?
                                AutoAttackerConfig.AimLockType.HARD : AutoAttackerConfig.AimLockType.SMOOTH;
                        AutoAttackerConfig.AIM_LOCK_TYPE.set(next);
                        showToast("瞄准模式: " + next.getDisplayName());
                    },
                    makeTooltip("强锁与平滑模式切换",
                            "控制自瞄锁死强度与转向机制。",
                            "【平滑自瞄】：采用物理插值渐进跟枪，动作平滑拟真，适合日常体验；",
                            "【极速强锁】：0 帧瞬间咬死目标与落点，免疫反向鼠标阻力与强后坐力，绝对死锁！")));

            currentItems.add(new ToggleItem("自动切换目标",
                    AutoAttackerConfig.ENABLE_AUTO_SWITCH_TARGET::get,
                    val -> {
                        AutoAttackerConfig.ENABLE_AUTO_SWITCH_TARGET.set(val);
                        showToast("自动切靶: " + (val ? "开启" : "关闭"));
                    },
                    makeTooltip("自动切换目标 (Switch on Kill)",
                            "当前锁定目标死亡、脱离射程或进掩体后，毫秒级无缝自动锁定视野内下一名目标。",
                            "彻底消除击杀后手动寻敌按键的空窗期，实现连续收割。")));

            currentItems.add(new CycleItem("切靶策略",
                    () -> AutoAttackerConfig.AUTO_SWITCH_PRIORITY.get().getDisplayName(),
                    () -> {
                        AutoAttackerConfig.SwitchPriority cur = AutoAttackerConfig.AUTO_SWITCH_PRIORITY.get();
                        AutoAttackerConfig.SwitchPriority next = (cur == AutoAttackerConfig.SwitchPriority.FOV)
                                ? AutoAttackerConfig.SwitchPriority.DISTANCE
                                : (cur == AutoAttackerConfig.SwitchPriority.DISTANCE)
                                ? AutoAttackerConfig.SwitchPriority.HEALTH
                                : AutoAttackerConfig.SwitchPriority.FOV;
                        AutoAttackerConfig.AUTO_SWITCH_PRIORITY.set(next);
                        showToast("切靶策略: " + next.getDisplayName());
                    },
                    makeTooltip("自动切靶优选策略",
                            "目标失效后选择下一位敌人的评判维度。",
                            "【准星优先】：优先选择离当前视线角距最近的目标，视角晃动最小；",
                            "【距离优先】：优先锁定离自己最近的近战危险敌人；",
                            "【残血优先】：优先寻找剩余血量最低的残血目标进行斩杀。")));

            currentItems.add(new CycleItem("自动搜索模式",
                    () -> AutoAttackerConfig.AUTO_LOCK_MODE.get().getDisplayName(),
                    () -> {
                        AutoAttackerConfig.AutoLockMode cur = AutoAttackerConfig.AUTO_LOCK_MODE.get();
                        AutoAttackerConfig.AutoLockMode next = switch (cur) {
                            case OFF -> AutoAttackerConfig.AutoLockMode.HOVER;
                            case HOVER -> AutoAttackerConfig.AutoLockMode.ALWAYS;
                            case ALWAYS -> AutoAttackerConfig.AutoLockMode.OFF;
                        };
                        AutoAttackerConfig.AUTO_LOCK_MODE.set(next);
                        showToast("自动搜索模式: " + next.getDisplayName());
                        rebuildCurrentTab();
                    },
                    makeTooltip("自动搜索与锁定模式",
                            "控制自动发现并吸附目标的运作规则：",
                            "【关闭】：完全禁用自动索敌，仅由手动按键（R 键）进行锁定；",
                            "【悬停锁定 (指向N秒)】：当准星指向生物持续达到设定秒数时自动锁定，甩镜头不误触；",
                            "【始终自锁 (持械)】：手持武器时，只要视野内出现敌人立即咬合锁定。")));

            if (AutoAttackerConfig.AUTO_LOCK_MODE.get() == AutoAttackerConfig.AutoLockMode.HOVER) {
                currentItems.add(new SliderItem("悬停判定时间",
                        AutoAttackerConfig.AUTO_LOCK_HOVER_TIME::get,
                        val -> AutoAttackerConfig.AUTO_LOCK_HOVER_TIME.set(Math.round(val * 100.0) / 100.0),
                        0.05, 3.0, "%.2f 秒",
                        makeTooltip("悬停锁定延迟时长",
                                "准星需持续停留在生物身上多久才触发自动锁定。",
                                "推荐：0.20 ~ 0.50 秒。过短可能快速扫过时误锁，过长则响应不够敏捷。")));
            } else if (AutoAttackerConfig.AUTO_LOCK_MODE.get() == AutoAttackerConfig.AutoLockMode.ALWAYS) {
                currentItems.add(new SliderItem("搜索视角(FOV)",
                        AutoAttackerConfig.AUTO_LOCK_FOV::get,
                        val -> AutoAttackerConfig.AUTO_LOCK_FOV.set(Math.round(val * 2.0) / 2.0),
                        15.0, 180.0, "%.0f°",
                        makeTooltip("持械自锁搜索视场 (FOV)",
                                "手持武器时，全自动索敌在玩家正前方检测敌人的锥形视野夹角。",
                                "推荐值：60° ~ 90°。角度越小越集中在正前方，角度越大周边范围越广。")));
            }

            currentItems.add(new CycleItem("手动按键模式",
                    () -> AutoAttackerConfig.AIM_ASSIST_MODE.get() == AutoAttackerConfig.LockMode.HOLD ? "长按" : "单击",
                    () -> {
                        AutoAttackerConfig.LockMode next = AutoAttackerConfig.AIM_ASSIST_MODE.get() == AutoAttackerConfig.LockMode.HOLD ?
                                AutoAttackerConfig.LockMode.TOGGLE : AutoAttackerConfig.LockMode.HOLD;
                        AutoAttackerConfig.AIM_ASSIST_MODE.set(next);
                        showToast("按键模式: " + (next == AutoAttackerConfig.LockMode.HOLD ? "长按" : "单击"));
                    },
                    makeTooltip("手动锁定按键机制",
                            "快捷键（默认 R）的手动按压判定机制。",
                            "【长按】：按住快捷键期间保持视角吸附，松开按键立即恢复自由视界；",
                            "【单击】：按一下快捷键开启持续锁定，再次按下解除锁定。")));

            currentItems.add(new CycleItem("瞄准部位",
                    () -> AutoAttackerConfig.TARGET_PART.get().getDisplayName(),
                    () -> {
                        AutoAttackerConfig.TargetPart cur = AutoAttackerConfig.TARGET_PART.get();
                        AutoAttackerConfig.TargetPart next = (cur == AutoAttackerConfig.TargetPart.HEAD)
                                ? AutoAttackerConfig.TargetPart.TORSO
                                : (cur == AutoAttackerConfig.TargetPart.TORSO)
                                ? AutoAttackerConfig.TargetPart.ADAPTIVE
                                : AutoAttackerConfig.TargetPart.HEAD;
                        AutoAttackerConfig.TARGET_PART.set(next);
                        showToast("瞄准部位: " + next.getDisplayName());
                    },
                    makeTooltip("瞄准锁定部位",
                            "设置自瞄准星吸附的目标解算部位。",
                            "【头部优先】：直接锁定生物眼部与头部 Hitbox，完美发挥枪械 150%~250% 爆头暴击；",
                            "【躯干中心】：稳定瞄准身体几何中心，容错率高，适合近距腰射与霰弹枪；",
                            "【智能自适应】：中近距离优先锁头，远距离或击退翻滚时自动回退锁胸。")));

            currentItems.add(new SliderItem("锁定范围",
                    AutoAttackerConfig.AIM_ASSIST_RANGE::get,
                    val -> AutoAttackerConfig.AIM_ASSIST_RANGE.set(Math.round(val * 2.0) / 2.0),
                    2.0, 120.0, "%.1f 格",
                    makeTooltip("锁定搜索半径",
                            "自动锁定能够感应并搜寻敌对目标的最大直线距离（格/米）。",
                            "超出此范围的目标将不会被自瞄系统探测和吸附。",
                            "推荐值：30.0 ~ 80.0 格（近战建议 3.0 ~ 10.0 格）。")));

            currentItems.add(new SliderItem("吸附速度",
                    AutoAttackerConfig.AIM_ASSIST_SPEED::get,
                    val -> AutoAttackerConfig.AIM_ASSIST_SPEED.set(Math.round(val * 100.0) / 100.0),
                    0.01, 1.0, "%.2f",
                    makeTooltip("视角追踪平滑速度",
                            "控制准星吸附转向目标时的角速度插值比例。",
                            "数值越低转动越柔和平滑；数值越高转速越快（1.00 为瞬间锁死）。",
                            "推荐值：0.20 ~ 0.45（兼具战术跟枪手感与机动性）。")));

            currentItems.add(new SliderItem("死区切换",
                    AutoAttackerConfig.LOCK_DEADZONE_THRESHOLD::get,
                    val -> AutoAttackerConfig.LOCK_DEADZONE_THRESHOLD.set(Math.round(val * 10.0) / 10.0),
                    1.0, 30.0, "%.1f°",
                    makeTooltip("目标脱锁死区阈值",
                            "强行转火与摆脱吸附的鼠标甩动角度阈值（度）。",
                            "锁定状态下，当玩家手动向外快速移动鼠标超过此角度时，判定玩家意图转火，自动解除旧目标并优先瞄向视野内新敌人。",
                            "有效防止因吸附锁定过死而无法转火反打。")));

            if (category == ClientEvents.WeaponCategory.GUN || category == ClientEvents.WeaponCategory.OTHER) {
                String gunHeader = (category == ClientEvents.WeaponCategory.GUN) ? "// 枪械战术 (现代枪械 / TACZ)" : "// 枪械战术 (全局预设)";
                currentItems.add(new HeaderItem(gunHeader));

                currentItems.add(new ToggleItem("枪械自动开火",
                        AutoAttackerConfig.ENABLE_GUN_TRIGGERBOT::get,
                        val -> {
                            AutoAttackerConfig.ENABLE_GUN_TRIGGERBOT.set(val);
                            showToast("枪械自动开火: " + (val ? "开启" : "关闭"));
                        },
                        makeTooltip("枪械自动开火 (Triggerbot)",
                                "自瞄锁定目标且准星咬准时全自动击发。",
                                "手持现代枪械（如 TACZ HK-416 等）且已锁定目标时，只要视线无遮挡、有弹药且非拉栓换弹状态，系统全自动进行开火扫射。",
                                "目标击杀或脱锁时即刻安全停火，杜绝浪费弹药。")));

                currentItems.add(new ToggleItem("自动压枪补偿",
                        AutoAttackerConfig.ENABLE_ANTI_RECOIL::get,
                        val -> {
                            AutoAttackerConfig.ENABLE_ANTI_RECOIL.set(val);
                            showToast("自动压枪: " + (val ? "开启" : "关闭"));
                        },
                        makeTooltip("智能后坐力抑制 (Anti-Recoil)",
                                "实时抵抗枪口上跳，死咬爆头 Hitbox。",
                                "现代枪械连发时会产生剧烈的垂直与水平后坐力散布。",
                                "开启后系统在射击瞬间施加连续阻尼平滑补偿，消除抖动抽搐并咬紧目标。")));

                currentItems.add(new SliderItem("压枪补偿强度",
                        AutoAttackerConfig.ANTI_RECOIL_STRENGTH::get,
                        val -> AutoAttackerConfig.ANTI_RECOIL_STRENGTH.set(Math.round(val * 100.0) / 100.0),
                        0.1, 2.5, "%.2fx",
                        makeTooltip("后坐力反冲补偿倍率",
                                "控制自动压枪的反拉阻尼与刚度。",
                                "1.00x = 匹配标准枪械垂直后坐力；",
                                "1.50x ~ 2.00x = 针对大口径机枪或剧烈改装枪口跳跃的极限死锁。")));

                currentItems.add(new ToggleItem("机瞄开镜感知",
                        AutoAttackerConfig.ENABLE_ADS_SENSING::get,
                        val -> {
                            AutoAttackerConfig.ENABLE_ADS_SENSING.set(val);
                            showToast("机瞄感知: " + (val ? "开启" : "关闭"));
                        },
                        makeTooltip("机瞄开镜感知 (ADS Sensing)",
                                "智能探测右键开镜机瞄状态。",
                                "右键机瞄放大瞄准时，系统自动切换为超平滑微调追踪算法，避免视野拉近后的画面剧烈抖动与眩晕。")));

                currentItems.add(new ToggleItem("枪械空仓换弹",
                        AutoAttackerConfig.ENABLE_GUN_AUTO_RELOAD::get,
                        val -> {
                            AutoAttackerConfig.ENABLE_GUN_AUTO_RELOAD.set(val);
                            showToast("空仓自动换弹: " + (val ? "开启" : "关闭"));
                        },
                        makeTooltip("枪械空仓自动换弹 (Auto-Reload)",
                                "弹药打空后自动换弹，背包无弹药时自动停用。",
                                "当手持枪械弹匣与枪膛彻底打空至 0 发时，系统自动触发换弹动作；",
                                "若背包中没有备用子弹，系统自动停止换弹并休眠，避免频繁发起无谓操作与性能开销。")));
            }

            if (category == ClientEvents.WeaponCategory.BOW || category == ClientEvents.WeaponCategory.OTHER) {
                String bowHeader = (category == ClientEvents.WeaponCategory.BOW) ? "// 弹道预判 (远程弓弩)" : "// 弹道预判 (全局预设)";
                currentItems.add(new HeaderItem(bowHeader));

                currentItems.add(new ToggleItem("提前量预判",
                        AutoAttackerConfig.ENABLE_AIM_PREDICT::get,
                        val -> {
                            AutoAttackerConfig.ENABLE_AIM_PREDICT.set(val);
                            showToast("提前量预判: " + (val ? "开启" : "关闭"));
                        },
                        makeTooltip("提前量预判",
                                "基于微元动力学的移动目标交会前置计算。",
                                "根据箭矢飞行速度、重力加速度与目标的运动矢量，解算出未来交会拦截点，并将准星智能抬升与前置偏移。")));

                currentItems.add(new SliderItem("预判权重",
                        AutoAttackerConfig.AIM_PREDICT_BLEND::get,
                        val -> AutoAttackerConfig.AIM_PREDICT_BLEND.set(Math.round(val * 100.0) / 100.0),
                        0.0, 1.0, "%.2f",
                        makeTooltip("预判混合插值权重",
                                "本体准星与预判拦截点之间的融合比例。",
                                "1.00 = 100% 瞄准未来交会预判点（对移动目标命中率最高）；",
                                "0.00 = 纯瞄准敌人当前物理中心。")));

                currentItems.add(new SliderItem("速度平滑",
                        AutoAttackerConfig.AIM_PREDICT_SMOOTH::get,
                        val -> AutoAttackerConfig.AIM_PREDICT_SMOOTH.set(Math.round(val * 100.0) / 100.0),
                        0.0, 1.0, "%.2f",
                        makeTooltip("目标速度平滑滤波",
                                "消除目标急停、摩擦碰撞或受击击退时的瞬时抖动。",
                                "数值越高滤波越稳定抗抖，数值越低对变向反应越灵敏。")));

                currentItems.add(new SliderItem("最大射程",
                        AutoAttackerConfig.AIM_PREDICT_MAX_DIST::get,
                        val -> AutoAttackerConfig.AIM_PREDICT_MAX_DIST.set((double) Math.round(val)),
                        5.0, 120.0, "%.0f 格",
                        makeTooltip("远程预判极限距离",
                                "提前量微元解算的最大作用直线距离（格）。",
                                "超出此射程的目标停止提前量运算，回退为基础直瞄。")));

                currentItems.add(new ToggleItem("拦截光圈",
                        AutoAttackerConfig.ENABLE_LEAD_INDICATOR::get,
                        val -> {
                            AutoAttackerConfig.ENABLE_LEAD_INDICATOR.set(val);
                            showToast("拦截光圈: " + (val ? "开启" : "关闭"));
                        },
                        makeTooltip("拦截指引光圈",
                                "在世界三维空间中渲染未来拦截落点圆环。",
                                "直观指示箭矢预计与目标相撞的战术拦截点。")));
            }
        } else if (currentTab == 1) {
            // =================================================================
            // Tab 1: 弹道 (落点预测、样式切换、目标标牌、悬浮小窗)
            // =================================================================
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            ItemStack held = ClientEvents.getActiveWeapon(player);
            ClientEvents.WeaponCategory category = ClientEvents.getWeaponCategory(held);

            String predHeader = (category == ClientEvents.WeaponCategory.GUN) ? "// 弹道预测 §8(当前手持枪械为高平直瞄)" :
                                (category == ClientEvents.WeaponCategory.MELEE) ? "// 弹道预测 §8(当前手持近战兵刃)" : "// 弹道预测 (抛物线模拟)";
            currentItems.add(new HeaderItem(predHeader));

            if (category == ClientEvents.WeaponCategory.GUN) {
                currentItems.add(new EmptyNoticeItem("当前手持现代枪械 (高平直瞄0G)，落点预测仅在手持弓弩时生效"));
            } else if (category == ClientEvents.WeaponCategory.MELEE) {
                currentItems.add(new EmptyNoticeItem("当前手持近战兵刃，落点预测仅在手持远程弓弩时生效"));
            }

            currentItems.add(new ToggleItem("落点预测",
                    AutoAttackerConfig.ENABLE_TRAJECTORY_PREVIEW::get,
                    val -> {
                        AutoAttackerConfig.ENABLE_TRAJECTORY_PREVIEW.set(val);
                        showToast("落点预测: " + (val ? "开启" : "关闭"));
                    },
                    makeTooltip("落点预测",
                            "实时抛物线前向动力学物理模拟。",
                            "手持远程武器时，在世界中实时预计算并显示箭矢最终下坠落点方块或命中实体。")));

            currentItems.add(new CycleItem("显示样式",
                    () -> {
                        AutoAttackerConfig.TrajectoryStyle s = AutoAttackerConfig.TRAJECTORY_STYLE.get();
                        if (s == AutoAttackerConfig.TrajectoryStyle.HUD_RETICLE) return "2D准星";
                        if (s == AutoAttackerConfig.TrajectoryStyle.PARTICLE_CHAIN) return "3D光束";
                        if (s == AutoAttackerConfig.TrajectoryStyle.BOTH) return "双显";
                        return "关闭";
                    },
                    () -> {
                        AutoAttackerConfig.TrajectoryStyle cur = AutoAttackerConfig.TRAJECTORY_STYLE.get();
                        AutoAttackerConfig.TrajectoryStyle next =
                                (cur == AutoAttackerConfig.TrajectoryStyle.BOTH) ? AutoAttackerConfig.TrajectoryStyle.HUD_RETICLE :
                                (cur == AutoAttackerConfig.TrajectoryStyle.HUD_RETICLE) ? AutoAttackerConfig.TrajectoryStyle.PARTICLE_CHAIN :
                                (cur == AutoAttackerConfig.TrajectoryStyle.PARTICLE_CHAIN) ? AutoAttackerConfig.TrajectoryStyle.OFF :
                                AutoAttackerConfig.TrajectoryStyle.BOTH;
                        AutoAttackerConfig.TRAJECTORY_STYLE.set(next);
                        String name = (next == AutoAttackerConfig.TrajectoryStyle.HUD_RETICLE) ? "2D准星" :
                                      (next == AutoAttackerConfig.TrajectoryStyle.PARTICLE_CHAIN) ? "3D光束" :
                                      (next == AutoAttackerConfig.TrajectoryStyle.BOTH) ? "双显" : "关闭";
                        showToast("显示样式: " + name);
                    },
                    makeTooltip("落点显示样式",
                            "切换抛物线落点的视觉呈现形式。",
                            "【2D准星】：在最终落点处渲染屏幕平视战术准星；",
                            "【3D光束】：从玩家手持发射源到落点渲染粒子轨迹链；",
                            "【双显】：同时开启 2D 准星标记与 3D 轨迹粒子；",
                            "【关闭】：隐藏所有落点预测画面。")));

            currentItems.add(new ToggleItem("仅手持显示",
                    AutoAttackerConfig.HUD_ONLY_WHEN_HOLDING_BOW::get,
                    val -> {
                        AutoAttackerConfig.HUD_ONLY_WHEN_HOLDING_BOW.set(val);
                        showToast("仅手持显示: " + (val ? "开启" : "关闭"));
                    },
                    makeTooltip("仅手持远程武器显示",
                            "手持感知与视界防遮挡过滤。",
                            "开启后，仅在主手或副手手持远程弓弩武器时才渲染抛物线；空手、拿剑或工具时自动隐藏，保持战斗视界清爽。")));

            currentItems.add(new HeaderItem("// 目标标牌"));

            currentItems.add(new ToggleItem("距离数值",
                    AutoAttackerConfig.SHOW_DISTANCE::get,
                    val -> {
                        AutoAttackerConfig.SHOW_DISTANCE.set(val);
                        showToast("距离显示: " + (val ? "开启" : "关闭"));
                    },
                    makeTooltip("距离数值标牌",
                            "战术测距仪 HUD 叠加。",
                            "在当前锁定或瞄准的目标头顶显示精确到 0.1 米的直线距离。")));

            currentItems.add(new ToggleItem("目标血条",
                    AutoAttackerConfig.SHOW_HEALTH_BAR::get,
                    val -> {
                        AutoAttackerConfig.SHOW_HEALTH_BAR.set(val);
                        showToast("血条显示: " + (val ? "开启" : "关闭"));
                    },
                    makeTooltip("目标生命条标牌",
                            "敌人战术血量显示。",
                            "在锁定目标头顶展示当前生命值与最大生命值比例血条。")));

            currentItems.add(new HeaderItem("// 悬浮小窗"));

            currentItems.add(new ToggleItem("U键小窗",
                    AutoAttackerConfig.ENABLE_DEBUG_OVERLAY::get,
                    val -> {
                        AutoAttackerConfig.ENABLE_DEBUG_OVERLAY.set(val);
                        showToast("悬浮小窗: " + (val ? "开启" : "关闭"));
                    },
                    makeTooltip("U键悬浮监视小窗",
                            "实时弹道参数 HUD 监控窗口。",
                            "在屏幕左上角显示当前武器初速、重力、飞行时间及蓄力状态等底层物理数据。",
                            "在游戏中随时按 U 键即可快速切换小窗显隐。")));

            currentItems.add(new ButtonItem("重置小窗位置到左上角", () -> {
                TacticalDebugPanel.setPosition(12, 36);
                showToast("小窗位置已重置 (12, 36)");
            }, makeTooltip("重置小窗坐标", "将悬浮小窗位置重置到默认安全坐标 (X:12, Y:36)。")));

        } else if (currentTab == 2) {
            // =================================================================
            // Tab 2: 武器特征档案库 (默认折叠、分页上限防卡顿、字母/时间正倒序排序)
            // =================================================================
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            ItemStack held = ClientEvents.getActiveWeapon(player);
            ClientEvents.WeaponCategory category = ClientEvents.getWeaponCategory(held);

            currentItems.add(new HeaderItem("// 当前手持武器状态"));
            if (category == ClientEvents.WeaponCategory.GUN) {
                AutoBallisticsTracker.BallisticsProfile profile = AutoBallisticsTracker.getProfile(held);
                currentItems.add(new HeldWeaponStatusCard(held, profile));
                final ItemStack finalHeld = held;
                currentItems.add(new ButtonItem("重置当前枪械档案 (恢复原厂直瞄)", () -> {
                    AutoBallisticsTracker.resetProfile(finalHeld);
                    showToast("当前枪械档案已重置为原厂直瞄");
                    rebuildCurrentTab();
                }, makeTooltip("枪械原厂重置",
                        "将当前手持枪械的弹道档案重置为原厂直瞄参数（初速恢复出厂值，重力归零）。",
                        "保持高平直射，无下坠无仰角。")).withConfirmation("§c再次点击确认恢复原厂直瞄"));
            } else if (category == ClientEvents.WeaponCategory.BOW) {
                AutoBallisticsTracker.BallisticsProfile profile = AutoBallisticsTracker.getProfile(held);
                currentItems.add(new HeldWeaponStatusCard(held, profile));
                final ItemStack finalHeld = held;
                currentItems.add(new ButtonItem("从当前物品描述重新解析", () -> {
                    AutoBallisticsTracker.reparseTooltip(finalHeld);
                    showToast("已从描述重新解析参数");
                    rebuildCurrentTab();
                }, makeTooltip("Tooltip 语义反演",
                        "重新扫描当前手持武器的 Tooltip 文本描述。",
                        "提取例如 '1.8 Projectile Speed' 或 '0.75 Draw Time' 等模组专属物理属性。")));

                currentItems.add(new ButtonItem("重置当前武器档案 (重新自适应)", () -> {
                    AutoBallisticsTracker.resetProfile(finalHeld);
                    showToast("当前武器档案已重置");
                    rebuildCurrentTab();
                }, makeTooltip("自适应档案重置",
                        "从特征库中清除当前手持武器的档案记录。",
                        "下次射击时，系统将根据箭矢真实飞行轨迹重新自适应学习。")).withConfirmation("§c再次点击确认重置档案"));
            } else if (category == ClientEvents.WeaponCategory.MELEE) {
                currentItems.add(new HeldMeleeStatusCard(held));
            } else {
                currentItems.add(new EmptyNoticeItem("当前未手持武器 (手持枪械/弓弩/近战时将自动识别)"));
            }

            // 档案库折叠与条目全景
            Map<String, AutoBallisticsTracker.BallisticsProfile> cacheEntries = AutoBallisticsTracker.getCacheEntries();
            int totalEntries = cacheEntries.size();

            currentItems.add(new HeaderItem("// 武器档案库 (已存 " + totalEntries + " 种)"));

            if (!isArchiveExpanded) {
                // 默认折叠收缩状态 (零多余渲染消耗)
                currentItems.add(new ButtonItem("▶ 展开武器档案列表 (" + totalEntries + " 种)", () -> {
                    isArchiveExpanded = true;
                    archivePage = 0;
                    rebuildCurrentTab();
                }, makeTooltip("展开武器档案列表", "展开查看所有已学习的武器档案条目（配有防卡顿分页与多维排序）。")));
            } else {
                // 展开状态
                currentItems.add(new ButtonItem("▼ 折叠武器档案列表", () -> {
                    isArchiveExpanded = false;
                    rebuildCurrentTab();
                }, makeTooltip("折叠武器档案列表", "收起档案列表，保持控制台精简清爽。")));

                if (cacheEntries.isEmpty()) {
                    currentItems.add(new EmptyNoticeItem("档案库为空 (使用武器时将自动建立档案)"));
                } else {
                    // 1. 排序控制栏 (依据: 时间/字母, 方向: 倒序/正序)
                    currentItems.add(new SortBarItem(
                            () -> archiveSortByTime ? "依据: 时间" : "依据: 字母",
                            () -> {
                                archiveSortByTime = !archiveSortByTime;
                                archivePage = 0;
                                rebuildCurrentTab();
                            },
                            makeTooltip("档案排序依据",
                                    "切换武器档案列表的排序维度。",
                                    "【时间】：按武器录入或最近更新时间排序；",
                                    "【字母】：按武器名称拼音/字母排序 (A-Z)。",
                                    "§8(点击切换依据)"),
                            () -> archiveSortAscending ? "方向: 正序 ↑" : "方向: 倒序 ↓",
                            () -> {
                                archiveSortAscending = !archiveSortAscending;
                                archivePage = 0;
                                rebuildCurrentTab();
                            },
                            makeTooltip("档案排序方向",
                                    "切换正序或倒序排列。",
                                    "【倒序 ↓】：最新录入在最前 / Z 到 A；",
                                    "【正序 ↑】：最早录入在最前 / A 到 Z。",
                                    "§8(点击切换方向)")
                    ));

                    // 2. 排序列表条目 (名称预映射计算，避免 O(N log N) 比较时重复创建 ItemStack)
                    List<Map.Entry<String, AutoBallisticsTracker.BallisticsProfile>> entryList = new ArrayList<>(cacheEntries.entrySet());
                    if (archiveSortByTime) {
                        entryList.sort((e1, e2) -> {
                            long t1 = e1.getValue().lastUpdated;
                            long t2 = e2.getValue().lastUpdated;
                            int cmp = Long.compare(t1, t2);
                            return archiveSortAscending ? cmp : -cmp; // 倒序 = 较大时间戳在前
                        });
                    } else {
                        Map<String, String> nameCache = new HashMap<>(entryList.size());
                        for (Map.Entry<String, AutoBallisticsTracker.BallisticsProfile> e : entryList) {
                            nameCache.put(e.getKey(), getReadableWeaponName(e.getKey()));
                        }
                        entryList.sort((e1, e2) -> {
                            String n1 = nameCache.getOrDefault(e1.getKey(), "");
                            String n2 = nameCache.getOrDefault(e2.getKey(), "");
                            int cmp = n1.compareToIgnoreCase(n2);
                            return archiveSortAscending ? cmp : -cmp;
                        });
                    }

                    int maxPages = Math.max(1, (int) Math.ceil((double) entryList.size() / ARCHIVE_PAGE_SIZE));
                    if (archivePage >= maxPages) archivePage = maxPages - 1;
                    if (archivePage < 0) archivePage = 0;

                    // 3. 分页控制栏 (若超过 1 页)
                    if (maxPages > 1) {
                        currentItems.add(new PaginationBarItem(archivePage + 1, maxPages,
                                () -> {
                                    if (archivePage > 0) {
                                        archivePage--;
                                        rebuildCurrentTab();
                                    }
                                },
                                () -> {
                                    if (archivePage < maxPages - 1) {
                                        archivePage++;
                                        rebuildCurrentTab();
                                    }
                                }));
                    }

                    // 4. 限制只渲染当前页的至多 15 条，杜绝掉帧
                    int startIdx = archivePage * ARCHIVE_PAGE_SIZE;
                    int endIdx = Math.min(startIdx + ARCHIVE_PAGE_SIZE, entryList.size());

                    for (int i = startIdx; i < endIdx; i++) {
                        var entry = entryList.get(i);
                        String sig = entry.getKey();
                        AutoBallisticsTracker.BallisticsProfile prof = entry.getValue();

                        currentItems.add(new ProfileArchiveCard(sig, prof, () -> {
                            AutoBallisticsTracker.removeProfileByKey(sig);
                            showToast("已删除档案: " + getReadableWeaponName(sig));
                            rebuildCurrentTab();
                        }));
                    }
                }
            }

            currentItems.add(new HeaderItem("// 全局档案维护"));
            currentItems.add(new ButtonItem("恢复原厂预设武器档案", () -> {
                AutoBallisticsTracker.clearAllCache();
                showToast("已恢复原厂权威预设档案 (16种经典武器)");
                rebuildCurrentTab();
            }, makeTooltip("恢复原厂预设", "清空自定义微调记录，并将档案库重置恢复为 16 种经典与主流模组弓弩的原厂权威物理基准。")).withConfirmation("§c§l再次点击确认恢复原厂预设"));

            currentItems.add(new ButtonItem("保存档案库到本地磁盘", () -> {
                AutoBallisticsTracker.saveToDiskImmediate();
                showToast("武器档案已持久化到磁盘");
            }, makeTooltip("保存到磁盘", "强制立即将当前内存中的武器档案库写入 combathelper_ballistics.json 文件。")));

        } else {
            // =================================================================
            // Tab 3: 名单管理 (4分段直切、一键录入、单项红X删除、多维排序)
            // =================================================================
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.player;
            ItemStack held = player != null ? player.getMainHandItem() : ItemStack.EMPTY;

            // 1. 顶部 4 分段直选导航栏
            currentItems.add(new HeaderItem("// 名单分类"));
            String[] categoryLabels = {
                    "免重力 (" + AutoAttackerConfig.ZERO_GRAVITY_BOWS.get().size() + ")",
                    "黑名单 (" + AutoAttackerConfig.BLACKLIST.get().size() + ")",
                    "白名单 (" + AutoAttackerConfig.WHITELIST.get().size() + ")",
                    "排除 (" + AutoAttackerConfig.ENTITY_BLACKLIST.get().size() + ")"
            };
            currentItems.add(new SegmentedBarItem(categoryLabels, () -> activeListCategory, cat -> {
                activeListCategory = cat;
                rebuildCurrentTab();
            }));

            // 2. 极简快捷录入行 (直观明了)
            currentItems.add(new HeaderItem("// 快捷录入"));
            if (activeListCategory < 3) {
                // 物品类名单 (免重力弓 / 攻击黑名单 / 攻击白名单)
                if (!held.isEmpty()) {
                    Item item = held.getItem();
                    ResourceLocation loc = ForgeRegistries.ITEMS.getKey(item);
                    String itemStr = loc != null ? loc.toString() : "";
                    List<String> curList = new ArrayList<>(getListConfig(activeListCategory).get());
                    boolean contains = curList.contains(itemStr);

                    currentItems.add(new QuickItemActionCard(held, contains,
                            LIST_CATEGORY_NAMES[activeListCategory], () -> {
                        if (contains) {
                            curList.remove(itemStr);
                            showToast("已移出: " + held.getHoverName().getString());
                        } else {
                            curList.add(itemStr);
                            showToast("已添加: " + held.getHoverName().getString());
                        }
                        getListConfig(activeListCategory).set(curList);
                        AutoAttackerConfig.refreshLists();
                        AutoAttackerConfig.saveConfig();
                        rebuildCurrentTab();
                    }));
                } else {
                    currentItems.add(new EmptyNoticeItem("主手未持有物品 (手持物品即可一键录入)"));
                }
            } else {
                // 实体类名单 (排除实体)
                LivingEntity target = ClientEvents.getCurrentTarget();
                if (target != null && target.isAlive()) {
                    ResourceLocation eloc = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
                    String entityStr = eloc != null ? eloc.toString() : "";
                    List<String> curList = new ArrayList<>(AutoAttackerConfig.ENTITY_BLACKLIST.get());
                    boolean contains = curList.contains(entityStr);

                    currentItems.add(new QuickEntityActionCard(target, contains, () -> {
                        if (contains) {
                            curList.remove(entityStr);
                            showToast("已移出排除: " + target.getName().getString());
                        } else {
                            curList.add(entityStr);
                            showToast("已加入排除: " + target.getName().getString());
                        }
                        AutoAttackerConfig.ENTITY_BLACKLIST.set(curList);
                        AutoAttackerConfig.refreshLists();
                        AutoAttackerConfig.saveConfig();
                        rebuildCurrentTab();
                    }));
                } else {
                    currentItems.add(new EmptyNoticeItem("准星未锁定实体 (瞄准目标即可一键排除)"));
                }
            }

            // 3. 名单全景条目与排序
            List<String> entries = new ArrayList<>(getListConfig(activeListCategory).get());
            currentItems.add(new HeaderItem("// " + LIST_CATEGORY_NAMES[activeListCategory] + " 条目 (" + entries.size() + " 项，点击红 X 删除)"));

            if (entries.isEmpty()) {
                currentItems.add(new EmptyNoticeItem("该名单为空"));
            } else {
                boolean isEntity = (activeListCategory == 3);

                // 排序控制栏
                currentItems.add(new SortBarItem(
                        () -> listSortByTime ? "依据: 录入时间" : "依据: 名称字母",
                        () -> {
                            listSortByTime = !listSortByTime;
                            rebuildCurrentTab();
                        },
                        makeTooltip("名单排序依据",
                                "切换名单条目列表的排序维度。",
                                "【录入时间】：按加入名单的先后次序排序；",
                                "【名称字母】：按显示名称或注册 ID 字母排序 (A-Z)。",
                                "§8(点击切换依据)"),
                        () -> listSortAscending ? "方向: 正序 ↑" : "方向: 倒序 ↓",
                        () -> {
                            listSortAscending = !listSortAscending;
                            rebuildCurrentTab();
                        },
                        makeTooltip("名单排序方向",
                                "切换排序方向。",
                                "【倒序 ↓】：最新加入在最前 / Z 到 A；",
                                "【正序 ↑】：最早加入在最前 / A 到 Z。",
                                "§8(点击切换方向)")
                ));

                // 记录原始索引作为录入先后依据
                Map<String, Integer> originIndices = new HashMap<>();
                for (int i = 0; i < entries.size(); i++) {
                    originIndices.put(entries.get(i), i);
                }

                if (listSortByTime) {
                    entries.sort((id1, id2) -> {
                        int idx1 = originIndices.getOrDefault(id1, 0);
                        int idx2 = originIndices.getOrDefault(id2, 0);
                        int cmp = Integer.compare(idx1, idx2);
                        return listSortAscending ? cmp : -cmp; // 倒序 = 较大索引(最新)在前
                    });
                } else {
                    Map<String, String> nameCache = new HashMap<>(entries.size());
                    for (String rawId : entries) {
                        nameCache.put(rawId, getReadableEntryName(rawId, isEntity));
                    }
                    entries.sort((id1, id2) -> {
                        String n1 = nameCache.getOrDefault(id1, "");
                        String n2 = nameCache.getOrDefault(id2, "");
                        int cmp = n1.compareToIgnoreCase(n2);
                        return listSortAscending ? cmp : -cmp;
                    });
                }

                for (String rawId : entries) {
                    currentItems.add(new ListEntryCard(rawId, isEntity, () -> {
                        List<String> list = new ArrayList<>(getListConfig(activeListCategory).get());
                        list.remove(rawId);
                        getListConfig(activeListCategory).set(list);
                        AutoAttackerConfig.refreshLists();
                        AutoAttackerConfig.saveConfig();
                        showToast("已删除: " + rawId);
                        rebuildCurrentTab();
                    }));
                }
            }

            // 4. 底部并排紧凑维护栏
            currentItems.add(new HeaderItem("// 名单维护"));
            currentItems.add(new DualButtonItem(
                    "清空当前名单", () -> {
                getListConfig(activeListCategory).set(List.of());
                AutoAttackerConfig.refreshLists();
                AutoAttackerConfig.saveConfig();
                showToast(LIST_CATEGORY_NAMES[activeListCategory] + " 已清空");
                rebuildCurrentTab();
            },
                    "恢复默认名单", () -> {
                AutoAttackerConfig.ZERO_GRAVITY_BOWS.set(List.of("extrabotany:failnaught"));
                AutoAttackerConfig.BLACKLIST.set(List.of());
                AutoAttackerConfig.WHITELIST.set(List.of());
                AutoAttackerConfig.ENTITY_BLACKLIST.set(List.of("minecraft:villager", "minecraft:armor_stand"));
                AutoAttackerConfig.refreshLists();
                AutoAttackerConfig.saveConfig();
                showToast("已恢复全部名单为默认");
                rebuildCurrentTab();
            }
            ).withConfirmation(true, true));
        }

        // 精准统一布局计算: 每个组件的 y 绝对一致！
        int curY = contentTop + 4;
        for (UIItem item : currentItems) {
            item.x = x;
            item.y = curY;
            item.w = w;
            curY += item.h + 4;
        }

        int contentBottom = this.height - 36;
        int contentH = contentBottom - contentTop;
        int totalContentH = curY - (contentTop + 4) + 12;
        this.maxScroll = Math.max(0, totalContentH - contentH);
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0.0, this.maxScroll);
        } catch (Throwable t) {
            t.printStackTrace();
            currentItems.clear();
            currentItems.add(new HeaderItem("// 界面组件加载异常"));
            currentItems.add(new EmptyNoticeItem("名单/组件加载捕获到异常: " + t.getClass().getSimpleName()));
            int drawerW = getDrawerWidth();
            int w = drawerW - 20;
            int x = 10;
            int curY = 58 + 4;
            for (UIItem item : currentItems) {
                item.x = x;
                item.y = curY;
                item.w = w;
                curY += item.h + 4;
            }
        }
    }

    private ForgeConfigSpec.ConfigValue<List<? extends String>> getListConfig(int category) {
        if (category == 0) return AutoAttackerConfig.ZERO_GRAVITY_BOWS;
        if (category == 1) return AutoAttackerConfig.BLACKLIST;
        if (category == 2) return AutoAttackerConfig.WHITELIST;
        return AutoAttackerConfig.ENTITY_BLACKLIST;
    }

    private static String getReadableWeaponName(String signature) {
        if (signature == null || signature.isEmpty()) return "未知武器";
        if (signature.startsWith("gun:") || signature.contains("modern_kinetic_gun")) {
            return FirearmAdapter.getReadableGunName(signature);
        }
        String cleanSig = signature;
        if (cleanSig.contains("#")) {
            cleanSig = cleanSig.substring(0, cleanSig.indexOf('#'));
        }
        String itemId = cleanSig.contains("|") ? cleanSig.substring(0, cleanSig.indexOf('|')) : cleanSig;
        ResourceLocation loc = ResourceLocation.tryParse(itemId);
        Item item = (loc != null) ? ForgeRegistries.ITEMS.getValue(loc) : null;
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            return new ItemStack(item).getHoverName().getString();
        }
        return itemId;
    }

    private static String getReadableEntryName(String rawId, boolean isEntity) {
        if (rawId == null || rawId.isEmpty()) return "未知";
        ResourceLocation loc = ResourceLocation.tryParse(rawId);
        if (loc == null) return rawId;
        if (isEntity) {
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(loc);
            if (type != null) {
                return type.getDescription().getString();
            }
        } else {
            Item item = ForgeRegistries.ITEMS.getValue(loc);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item).getHoverName().getString();
            }
        }
        return rawId;
    }

    // =========================================================================
    // 渲染主流程
    // =========================================================================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 清除上一帧的 Tooltip 记录
        this.hoveredTooltip = null;

        graphics.fillGradient(0, 0, this.width, this.height, 0x55000000, 0x77000000);

        int drawerW = getDrawerWidth();

        graphics.fill(0, 0, drawerW, this.height, 0xF00D1117);
        graphics.fill(drawerW - 1, 0, drawerW, this.height, 0xFF30363D);

        renderHeader(graphics, font, drawerW, mouseX, mouseY);
        renderTabBar(graphics, font, drawerW, mouseX, mouseY);

        int contentTop = 58;
        int contentBottom = this.height - 36;
        int contentH = contentBottom - contentTop;

        graphics.enableScissor(0, contentTop, drawerW, contentBottom);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);

        int mouseContentY = (int) (mouseY + scrollOffset);

        for (UIItem item : currentItems) {
            item.render(graphics, font, mouseX, mouseContentY, mouseY, this);
        }

        graphics.pose().popPose();
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = drawerW - 6;
            int trackW = 3;
            graphics.fill(trackX, contentTop, trackX + trackW, contentBottom, 0x1AFFFFFF);

            int scrollbarH = Math.max(22, (int) ((float) contentH / (contentH + maxScroll) * contentH));
            int scrollbarY = contentTop + (int) ((scrollOffset / maxScroll) * (contentH - scrollbarH));
            boolean barHov = mouseX >= trackX - 2 && mouseX <= trackX + trackW + 2 && mouseY >= contentTop && mouseY <= contentBottom;

            graphics.fill(trackX, scrollbarY, trackX + trackW, scrollbarY + scrollbarH, (isDraggingScrollbar || barHov) ? 0xFF58A6FF : 0xAA388BFD);
        }

        renderFooter(graphics, font, drawerW, mouseX, mouseY);

        if (toastMessage != null) {
            long remaining = toastExpiry - System.currentTimeMillis();
            if (remaining > 0) {
                float alpha = Math.min(1.0f, remaining / 300.0f);
                int aBg = (int) (alpha * 0xF0);
                int aBorder = (int) (alpha * 0xFF);
                int aText = (int) (alpha * 0xFF);

                int textW = font.width(toastMessage);
                int toastW = textW + 16;
                int toastX = drawerW + 16;
                int toastY = 12;

                graphics.fill(toastX, toastY, toastX + toastW, toastY + 16, (aBg << 24) | 0x161B22);
                graphics.renderOutline(toastX, toastY, toastW, 16, (aBorder << 24) | 0x388BFD);
                graphics.drawCenteredString(font, toastMessage, toastX + toastW / 2, toastY + 4, (aText << 24) | 0xF0F6FC);
            } else {
                toastMessage = null;
            }
        }

        // 在最顶层安全渲染悬浮说明框 (不受 scissor 裁切或底图遮挡)
        if (this.hoveredTooltip != null && !this.hoveredTooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, this.hoveredTooltip, this.tooltipMouseX, this.tooltipMouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphics graphics, Font font, int drawerW, int mouseX, int mouseY) {
        graphics.fill(12, 10, 14, 22, 0xFF388BFD);
        graphics.drawString(font, "AUTO ATTACKER", 18, 9, 0xFFF0F6FC, false);
        graphics.drawString(font, "// 战术控制台", 18, 19, 0xFF7D8590, false);

        int btnSize = 14;
        int closeX = drawerW - 22;
        int closeY = 10;
        boolean closeHov = mouseX >= closeX && mouseX <= closeX + btnSize && mouseY >= closeY && mouseY <= closeY + btnSize;
        graphics.fill(closeX, closeY, closeX + btnSize, closeY + btnSize, closeHov ? 0xFF8A2424 : 0xFF1C2128);
        graphics.renderOutline(closeX, closeY, btnSize, btnSize, closeHov ? 0xFFC93B3B : 0x33FFFFFF);
        graphics.drawCenteredString(font, "X", closeX + btnSize / 2, closeY + 3, closeHov ? 0xFFFFFFFF : 0xFF8B949E);
    }

    private void renderTabBar(GuiGraphics graphics, Font font, int drawerW, int mouseX, int mouseY) {
        int x = 10;
        int y = 33;
        int w = drawerW - 20;
        int h = 20;

        graphics.fill(x, y, x + w, y + h, 0xFF161B22);
        graphics.renderOutline(x, y, w, h, 0x22FFFFFF);

        int segW = w / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tx = x + i * segW;
            int tw = (i == TABS.length - 1) ? (w - i * segW) : segW;
            boolean isSel = (i == currentTab);
            boolean hov = mouseX >= tx && mouseX < tx + tw && mouseY >= y && mouseY < y + h;

            if (isSel) {
                graphics.fill(tx, y + 1, tx + tw, y + h - 1, 0xFF1F6FEB);
                graphics.drawCenteredString(font, TABS[i], tx + tw / 2, y + 6, 0xFFFFFFFF);
            } else {
                if (hov) {
                    graphics.fill(tx, y + 1, tx + tw, y + h - 1, 0x1AFFFFFF);
                }
                graphics.drawCenteredString(font, TABS[i], tx + tw / 2, y + 6, hov ? 0xFFE6EDF3 : 0xFF8B949E);
            }
        }
    }

    private void renderFooter(GuiGraphics graphics, Font font, int drawerW, int mouseX, int mouseY) {
        int footerY = this.height - 34;
        graphics.fill(0, footerY, drawerW, this.height, 0xF0161B22);
        graphics.fill(0, footerY, drawerW, footerY + 1, 0xFF30363D);

        int btnW = (drawerW - 26) / 2;
        int btnH = 20;
        int btnY = footerY + 7;

        int resetX = 10;
        boolean resetHov = mouseX >= resetX && mouseX <= resetX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        long now = System.currentTimeMillis();
        boolean resetConfirming = UIItem.isConfirmPending(footerResetConfirmTime, now);
        graphics.fill(resetX, btnY, resetX + btnW, btnY + btnH, resetConfirming ? (resetHov ? 0xFFDA3633 : 0xFF8A2424) : (resetHov ? 0xFF2B313A : 0xFF21262D));
        graphics.renderOutline(resetX, btnY, btnW, btnH, resetConfirming ? 0xFFF85149 : (resetHov ? 0xFF8B949E : 0x25FFFFFF));
        graphics.drawCenteredString(font, resetConfirming ? "§c再次点击确认" : "重置当前页", resetX + btnW / 2, btnY + 6, resetConfirming ? 0xFFFFFFFF : (resetHov ? 0xFFFFFFFF : 0xFFC9D1D9));

        int saveX = resetX + btnW + 6;
        boolean saveHov = mouseX >= saveX && mouseX <= saveX + btnW && mouseY >= btnY && mouseY <= btnY + btnH;
        graphics.fill(saveX, btnY, saveX + btnW, btnY + btnH, saveHov ? 0xFF2EA043 : 0xFF238636);
        graphics.renderOutline(saveX, btnY, btnW, btnH, saveHov ? 0xFF3FB950 : 0x33FFFFFF);
        graphics.drawCenteredString(font, "保存退出", saveX + btnW / 2, btnY + 6, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int drawerW = getDrawerWidth();

        if (mouseX > drawerW) {
            this.onClose();
            return true;
        }

        int closeX = drawerW - 22;
        int closeY = 10;
        if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= closeY && mouseY <= closeY + 14) {
            this.onClose();
            return true;
        }

        int tabY = 33;
        if (mouseY >= tabY && mouseY <= tabY + 20) {
            int tx = 10;
            int tw = (drawerW - 20) / TABS.length;
            int clicked = (int) (mouseX - tx) / tw;
            if (clicked >= 0 && clicked < TABS.length) {
                currentTab = clicked;
                scrollOffset = 0;
                rebuildCurrentTab();
                return true;
            }
        }

        int footerY = this.height - 34;
        if (mouseY >= footerY && mouseY <= this.height) {
            int btnW = (drawerW - 26) / 2;
            int btnH = 20;
            int btnY = footerY + 7;

            int resetX = 10;
            if (mouseX >= resetX && mouseX <= resetX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                long now = System.currentTimeMillis();
                if (UIItem.isConfirmExpired(footerResetConfirmTime, now)) {
                    footerResetConfirmTime = now;
                    showToast("请在 3 秒内再次点击以确认重置当前页");
                    return true;
                }
                footerResetConfirmTime = 0L;
                resetCurrentTabDefaults();
                rebuildCurrentTab();
                showToast("已重置当前页为默认");
                return true;
            }

            int saveX = resetX + btnW + 6;
            if (mouseX >= saveX && mouseX <= saveX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                this.onClose();
                return true;
            }
        }

        if (maxScroll > 0 && mouseX >= drawerW - 10 && mouseX <= drawerW) {
            isDraggingScrollbar = true;
            updateScrollFromMouse(mouseY);
            return true;
        }

        int contentTop = 58;
        int contentBottom = this.height - 34;
        if (mouseY >= contentTop && mouseY <= contentBottom) {
            int virtualY = (int) (mouseY + scrollOffset);
            for (UIItem item : currentItems) {
                if (mouseX >= item.x && mouseX <= item.x + item.w && virtualY >= item.y && virtualY <= item.y + item.h) {
                    if (item.mouseClicked(mouseX, virtualY, button)) {
                        AutoAttackerConfig.saveConfig();
                        return true;
                    }
                }
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateScrollFromMouse(double mouseY) {
        int contentTop = 58;
        int contentBottom = this.height - 34;
        int contentH = contentBottom - contentTop;
        double ratio = Mth.clamp((mouseY - contentTop) / (double) contentH, 0.0, 1.0);
        this.scrollOffset = ratio * maxScroll;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingScrollbar) {
            updateScrollFromMouse(mouseY);
            return true;
        }
        if (activeDraggingSlider != null) {
            activeDraggingSlider.onDrag(mouseX);
            AutoAttackerConfig.saveConfig();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        activeDraggingSlider = null;
        isDraggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int drawerW = getDrawerWidth();
        if (mouseX <= drawerW) {
            this.scrollOffset = Mth.clamp(this.scrollOffset - delta * 24.0, 0.0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void resetCurrentTabDefaults() {
        if (currentTab == 0) {
            AutoAttackerConfig.ENABLE_MOD.set(true);
            AutoAttackerConfig.ENABLE_AUTO_ATTACK.set(true);
            AutoAttackerConfig.ENABLE_AUTO_SHOOT.set(true);
            AutoAttackerConfig.ENABLE_AIM_ASSIST.set(false);
            AutoAttackerConfig.AIM_ASSIST_MODE.set(AutoAttackerConfig.LockMode.HOLD);
            AutoAttackerConfig.AIM_ASSIST_RANGE.set(64.0);
            AutoAttackerConfig.AIM_ASSIST_SPEED.set(0.15);
            AutoAttackerConfig.LOCK_DEADZONE_THRESHOLD.set(8.0);
            AutoAttackerConfig.TARGET_PART.set(AutoAttackerConfig.TargetPart.HEAD);
            AutoAttackerConfig.ENABLE_AIM_PREDICT.set(true);
            AutoAttackerConfig.AIM_PREDICT_BLEND.set(1.0);
            AutoAttackerConfig.AIM_PREDICT_SMOOTH.set(0.5);
            AutoAttackerConfig.AIM_PREDICT_MAX_DIST.set(60.0);
            AutoAttackerConfig.ENABLE_LEAD_INDICATOR.set(true);
            AutoAttackerConfig.ENABLE_GUN_AUTO_RELOAD.set(false);
            AutoAttackerConfig.ENABLE_ANTI_RECOIL.set(false);
            showToast("锁定与核心设置已恢复默认");
        } else if (currentTab == 1) {
            AutoAttackerConfig.ENABLE_TRAJECTORY_PREVIEW.set(true);
            AutoAttackerConfig.TRAJECTORY_STYLE.set(AutoAttackerConfig.TrajectoryStyle.PARTICLE_CHAIN);
            AutoAttackerConfig.HUD_ONLY_WHEN_HOLDING_BOW.set(true);
            AutoAttackerConfig.SHOW_DISTANCE.set(true);
            AutoAttackerConfig.SHOW_HEALTH_BAR.set(true);
            AutoAttackerConfig.ENABLE_DEBUG_OVERLAY.set(true);
            showToast("弹道设置已恢复默认");
        } else if (currentTab == 2) {
            AutoBallisticsTracker.clearAllCache();
            showToast("武器档案已全部重置");
        } else {
            AutoAttackerConfig.BLACKLIST.set(List.of());
            AutoAttackerConfig.WHITELIST.set(List.of());
            AutoAttackerConfig.ZERO_GRAVITY_BOWS.set(List.of("extrabotany:failnaught"));
            AutoAttackerConfig.ENTITY_BLACKLIST.set(List.of("minecraft:villager", "minecraft:armor_stand"));
            AutoAttackerConfig.refreshLists();
            showToast("名单设置已恢复默认");
        }
        AutoAttackerConfig.saveConfig();
    }

    private void showToast(String msg) {
        this.toastMessage = msg;
        this.toastExpiry = System.currentTimeMillis() + 1800;
    }

    // =========================================================================
    // UI 组件类定义 (UI Items)
    // =========================================================================

    public abstract static class UIItem {
        /** 全局高危操作二次确认窗口 (毫秒) */
        static final long CONFIRM_WINDOW_MS = 3000L;

        int x, y, w, h;
        abstract void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen);
        abstract boolean mouseClicked(double mouseX, double mouseY, int button);

        /** 卡片/控件通用悬停判定 */
        final boolean isHovered(int mouseX, int mouseContentY) {
            return mouseX >= x && mouseX <= x + w && mouseContentY >= y && mouseContentY <= y + h;
        }

        /** 通用卡片外框:深色底 + 悬停高亮描边 */
        final void drawCardFrame(GuiGraphics graphics, boolean hovered) {
            graphics.fill(x, y, x + w, y + h, hovered ? 0xFF161B22 : 0x08FFFFFF);
            graphics.renderOutline(x, y, w, h, hovered ? 0x33FFFFFF : 0x12FFFFFF);
        }

        /** (i) 战术徽章水平起点 (依赖 label 宽度) */
        final int infoBadgeX(Font font, String label) {
            return x + 6 + font.width(label) + 5;
        }

        /**
         * 渲染 (i) 战术徽章并处理 tooltip 悬浮。
         * @param infoYOffset 徽章相对 y 的垂直偏移
         * @param iTextYOffset "i" 字符相对徽章顶部的基线偏移
         */
        final void drawInfoBadge(GuiGraphics graphics, Font font, String label,
                                 List<Component> tooltip, int mouseX, int mouseContentY,
                                 int realMouseY, TacticalConsoleScreen screen,
                                 int infoYOffset, int iTextYOffset) {
            int infoSize = 11;
            int infoX = infoBadgeX(font, label);
            int infoY = y + infoYOffset;
            boolean infoHov = mouseX >= infoX && mouseX <= infoX + infoSize
                    && mouseContentY >= infoY && mouseContentY <= infoY + infoSize;

            if (tooltip != null && !tooltip.isEmpty()) {
                graphics.fill(infoX, infoY, infoX + infoSize, infoY + infoSize, infoHov ? 0xFF1F6FEB : 0x1AFFFFFF);
                graphics.renderOutline(infoX, infoY, infoSize, infoSize, infoHov ? 0xFF58A6FF : 0x33FFFFFF);
                graphics.drawCenteredString(font, "i", infoX + infoSize / 2, infoY + iTextYOffset, infoHov ? 0xFFFFFFFF : 0xFF8B949E);

                if (infoHov) {
                    screen.setHoveredTooltip(tooltip, mouseX, realMouseY);
                }
            }
        }

        /** 判断点击是否落在 (i) 徽章区域内 (徽章仅用于查看 tooltip,不触发切换) */
        final boolean isInfoBadgeClicked(double mouseX, double mouseY, String label, int infoYOffset) {
            int infoSize = 11;
            int infoX = infoBadgeX(Minecraft.getInstance().font, label);
            int infoY = y + infoYOffset;
            return mouseX >= infoX && mouseX <= infoX + infoSize
                    && mouseY >= infoY && mouseY <= infoY + infoSize;
        }

        /** 高危操作二次确认:窗口外的首次点击视为"进入确认态" */
        static boolean isConfirmExpired(long lastClickTime, long now) {
            return now - lastClickTime > CONFIRM_WINDOW_MS;
        }

        /** 高危操作二次确认:是否处于确认等待态 */
        static boolean isConfirmPending(long lastClickTime, long now) {
            return now - lastClickTime <= CONFIRM_WINDOW_MS;
        }
    }

    /**
     * 通用「红 X 删除 + 3 秒二次确认」按钮。
     * 首次点击进入确认态 (按钮展宽并显示"确认?")，窗口内再次点击才执行删除；
     * 超时或确认后自动复位。
     *
     * 抽出前 ListEntryCard / ProfileArchiveCard 各自复制了一份完整实现。
     */
    public static final class DeleteConfirmButton {
        private final int btnYOffset;
        private long confirmTime = 0L;

        /** @param btnYOffset 按钮相对卡片 y 的垂直偏移 */
        DeleteConfirmButton(int btnYOffset) {
            this.btnYOffset = btnYOffset;
        }

        private boolean isPending() {
            return UIItem.isConfirmPending(confirmTime, System.currentTimeMillis());
        }

        private int btnW(boolean pending) {
            return pending ? 36 : 16;
        }

        /** 当前状态下按钮宽度 (供调用方计算文字可用宽度) */
        int currentWidth() {
            return btnW(isPending());
        }

        private boolean isHovered(int mouseX, int mouseContentY, int x, int y, int w, boolean pending) {
            int bw = btnW(pending);
            int bx = x + w - bw - 4;
            int by = y + btnYOffset;
            return mouseX >= bx && mouseX <= bx + bw && mouseContentY >= by && mouseContentY <= by + 16;
        }

        /** 渲染按钮 (需在卡片外框之后调用) */
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int x, int y, int w) {
            boolean pending = isPending();
            int bw = btnW(pending);
            int bx = x + w - bw - 4;
            int by = y + btnYOffset;
            boolean hov = isHovered(mouseX, mouseContentY, x, y, w, pending);

            int bgCol = pending ? (hov ? 0xFFDA3633 : 0xFF8A2424) : (hov ? 0xFF8A2424 : 0xFF21262D);
            int borderCol = pending ? 0xFFF85149 : (hov ? 0xFFC93B3B : 0x22FFFFFF);

            graphics.fill(bx, by, bx + bw, by + 16, bgCol);
            graphics.renderOutline(bx, by, bw, 16, borderCol);
            graphics.drawCenteredString(font, pending ? "确认?" : "x", bx + bw / 2, by + 4, 0xFFFFFFFF);
        }

        /**
         * 处理点击。命中且通过二次确认时执行 onDelete。
         * @return 是否消费了本次点击
         */
        boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, Runnable onDelete) {
            boolean pending = isPending();
            if (!isHovered((int) mouseX, (int) mouseY, x, y, w, pending)) {
                return false;
            }

            long now = System.currentTimeMillis();
            if (UIItem.isConfirmExpired(confirmTime, now)) {
                confirmTime = now;
                return true;
            }
            confirmTime = 0L;
            onDelete.run();
            return true;
        }
    }

    public static class HeaderItem extends UIItem {
        private final String title;

        HeaderItem(String title) {
            this.title = title;
            this.h = 16;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            graphics.drawString(font, title, x, y + 2, 0xFF58A6FF, false);
            graphics.fill(x, y + 13, x + w, y + 14, 0x22FFFFFF);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    public static class EmptyNoticeItem extends UIItem {
        private final String notice;

        EmptyNoticeItem(String notice) {
            this.notice = notice;
            this.h = 24;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            graphics.fill(x, y, x + w, y + h, 0x08FFFFFF);
            graphics.renderOutline(x, y, w, h, 0x12FFFFFF);
            graphics.drawCenteredString(font, notice, x + w / 2, y + 8, 0xFF7D8590);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    public static class ToggleItem extends UIItem {
        private final String label;
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;
        private final List<Component> tooltip;

        ToggleItem(String label, Supplier<Boolean> getter, Consumer<Boolean> setter, List<Component> tooltip) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.tooltip = tooltip;
            this.h = 22;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            boolean hovered = isHovered(mouseX, mouseContentY);
            boolean active = getter.get();

            drawCardFrame(graphics, hovered);

            graphics.drawString(font, label, x + 6, y + 7, 0xFFF0F6FC, false);

            // (i) 徽章渲染
            drawInfoBadge(graphics, font, label, tooltip, mouseX, mouseContentY, realMouseY, screen, 5, 2);

            int sw = 28;
            int sh = 13;
            int sx = x + w - sw - 6;
            int sy = y + 4;

            graphics.fill(sx, sy, sx + sw, sy + sh, active ? 0xFF238636 : 0xFF21262D);
            graphics.renderOutline(sx, sy, sw, sh, active ? 0xFF3FB950 : 0x33FFFFFF);

            int thumbW = 9;
            int tx = active ? (sx + sw - thumbW - 2) : (sx + 2);
            graphics.fill(tx, sy + 2, tx + thumbW, sy + sh - 2, 0xFFF0F6FC);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isInfoBadgeClicked(mouseX, mouseY, label, 5)) {
                return true; // 仅供查看 Tooltip，不触发切换
            }
            setter.accept(!getter.get());
            return true;
        }
    }

    public static class CycleItem extends UIItem {
        private final String label;
        private final Supplier<String> valueGetter;
        private final Runnable cycleAction;
        private final List<Component> tooltip;

        CycleItem(String label, Supplier<String> valueGetter, Runnable cycleAction, List<Component> tooltip) {
            this.label = label;
            this.valueGetter = valueGetter;
            this.cycleAction = cycleAction;
            this.tooltip = tooltip;
            this.h = 22;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            boolean hovered = isHovered(mouseX, mouseContentY);
            drawCardFrame(graphics, hovered);

            graphics.drawString(font, label, x + 6, y + 7, 0xFFF0F6FC, false);

            // (i) 徽章渲染
            drawInfoBadge(graphics, font, label, tooltip, mouseX, mouseContentY, realMouseY, screen, 5, 2);

            String curVal = valueGetter.get();
            int cw = Math.max(48, font.width(curVal) + 16);
            int ch = 15;
            int cx = x + w - cw - 6;
            int cy = y + 3;

            boolean btnHov = mouseX >= cx && mouseX <= cx + cw && mouseContentY >= cy && mouseContentY <= cy + ch;
            graphics.fill(cx, cy, cx + cw, cy + ch, btnHov ? 0xFF2B313A : 0xFF21262D);
            graphics.renderOutline(cx, cy, cw, ch, btnHov ? 0xFF58A6FF : 0x33FFFFFF);
            graphics.drawCenteredString(font, curVal, cx + cw / 2, cy + 4, btnHov ? 0xFF58A6FF : 0xFFC9D1D9);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isInfoBadgeClicked(mouseX, mouseY, label, 5)) {
                return true;
            }
            cycleAction.run();
            return true;
        }
    }

    public static class SliderItem extends UIItem {
        private final String label;
        private final Supplier<Double> getter;
        private final Consumer<Double> setter;
        private final double min, max;
        private final String format;
        private final List<Component> tooltip;

        SliderItem(String label, Supplier<Double> getter, Consumer<Double> setter, double min, double max, String format, List<Component> tooltip) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
            this.format = format;
            this.tooltip = tooltip;
            this.h = 30;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            boolean hovered = isHovered(mouseX, mouseContentY);
            drawCardFrame(graphics, hovered);

            graphics.drawString(font, label, x + 6, y + 3, 0xFFF0F6FC, false);

            // (i) 徽章渲染 (滑块卡片排版更紧凑:徽章贴 y+2, 字基线 infoY+1)
            drawInfoBadge(graphics, font, label, tooltip, mouseX, mouseContentY, realMouseY, screen, 2, 1);

            double val = getter.get();
            String valStr = String.format(Locale.ROOT, format, val);
            int valW = font.width(valStr);
            graphics.drawString(font, valStr, x + w - valW - 6, y + 3, 0xFF58A6FF, false);

            // 轨道
            int trackX = x + 6;
            int trackY = y + 17;
            int trackW = w - 12;
            int trackH = 4;

            graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0xFF21262D);

            double progress = Mth.clamp((val - min) / (max - min), 0.0, 1.0);
            int fillW = (int) (progress * trackW);
            graphics.fill(trackX, trackY, trackX + fillW, trackY + trackH, 0xFF1F6FEB);

            int thumbX = trackX + fillW - 3;
            int thumbY = trackY - 3;
            graphics.fill(thumbX, thumbY, thumbX + 6, thumbY + 10, 0xFFF0F6FC);
        }

        void onDrag(double mouseX) {
            int trackX = x + 6;
            int trackW = w - 12;
            double progress = Mth.clamp((mouseX - trackX) / (double) trackW, 0.0, 1.0);
            double newVal = min + progress * (max - min);
            setter.accept(newVal);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isInfoBadgeClicked(mouseX, mouseY, label, 2)) {
                return true;
            }
            int trackY = y + 12;
            if (mouseY >= trackY && mouseY <= y + h) {
                onDrag(mouseX);
                if (Minecraft.getInstance().screen instanceof TacticalConsoleScreen sc) {
                    sc.activeDraggingSlider = this;
                }
                return true;
            }
            return false;
        }
    }

    public static class ButtonItem extends UIItem {
        private final String text;
        private final Runnable action;
        private final List<Component> tooltip;
        private String confirmPrompt = null;
        private long lastClickTime = 0L;

        ButtonItem(String text, Runnable action) {
            this(text, action, null);
        }

        ButtonItem(String text, Runnable action, List<Component> tooltip) {
            this.text = text;
            this.action = action;
            this.tooltip = tooltip;
            this.h = 22;
        }

        public ButtonItem withConfirmation(String confirmPrompt) {
            this.confirmPrompt = confirmPrompt;
            return this;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            boolean hovered = isHovered(mouseX, mouseContentY);
            boolean isConfirming = confirmPrompt != null && UIItem.isConfirmPending(lastClickTime, System.currentTimeMillis());

            int bg = isConfirming ? (hovered ? 0xFF8A2424 : 0xFF3D1616) : (hovered ? 0xFF21262D : 0xFF161B22);
            int border = isConfirming ? 0xFFF85149 : (hovered ? 0xFF58A6FF : 0x22FFFFFF);

            graphics.fill(x, y, x + w, y + h, bg);
            graphics.renderOutline(x, y, w, h, border);

            String display = isConfirming ? confirmPrompt : text;
            int textCol = isConfirming ? 0xFFFF7B72 : (hovered ? 0xFF58A6FF : 0xFFC9D1D9);
            graphics.drawCenteredString(font, display, x + w / 2, y + 7, textCol);

            if (hovered && tooltip != null && !tooltip.isEmpty()) {
                screen.setHoveredTooltip(tooltip, mouseX, realMouseY);
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (confirmPrompt != null) {
                long now = System.currentTimeMillis();
                if (UIItem.isConfirmExpired(lastClickTime, now)) {
                    lastClickTime = now;
                    if (Minecraft.getInstance().screen instanceof TacticalConsoleScreen sc) {
                        sc.showToast("请在 3 秒内再次点击以确认");
                    }
                    return true;
                }
                lastClickTime = 0L;
            }
            action.run();
            return true;
        }
    }

    public static class DualButtonItem extends UIItem {
        private final String text1;
        private final Runnable action1;
        private final String text2;
        private final Runnable action2;
        private boolean reqConfirm1 = false;
        private boolean reqConfirm2 = false;
        private long confirm1Time = 0L;
        private long confirm2Time = 0L;

        DualButtonItem(String text1, Runnable action1, String text2, Runnable action2) {
            this.text1 = text1;
            this.action1 = action1;
            this.text2 = text2;
            this.action2 = action2;
            this.h = 22;
        }

        public DualButtonItem withConfirmation(boolean confirm1, boolean confirm2) {
            this.reqConfirm1 = confirm1;
            this.reqConfirm2 = confirm2;
            return this;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            int halfW = (w - 4) / 2;
            int x1 = x;
            int x2 = x + halfW + 4;

            boolean hov1 = mouseX >= x1 && mouseX <= x1 + halfW && mouseContentY >= y && mouseContentY <= y + h;
            boolean hov2 = mouseX >= x2 && mouseX <= x2 + halfW && mouseContentY >= y && mouseContentY <= y + h;

            long now = System.currentTimeMillis();
            boolean isConf1 = reqConfirm1 && UIItem.isConfirmPending(confirm1Time, now);
            boolean isConf2 = reqConfirm2 && UIItem.isConfirmPending(confirm2Time, now);

            int bg1 = isConf1 ? (hov1 ? 0xFFDA3633 : 0xFF8A2424) : (hov1 ? 0xFF8A2424 : 0xFF1C2128);
            int border1 = isConf1 ? 0xFFF85149 : (hov1 ? 0xFFC93B3B : 0x22FFFFFF);
            String str1 = isConf1 ? "§c再次点击确认" : text1;

            graphics.fill(x1, y, x1 + halfW, y + h, bg1);
            graphics.renderOutline(x1, y, halfW, h, border1);
            graphics.drawCenteredString(font, str1, x1 + halfW / 2, y + 7, isConf1 ? 0xFFFFFFFF : (hov1 ? 0xFFFFFFFF : 0xFFC9D1D9));

            int bg2 = isConf2 ? (hov2 ? 0xFFDA3633 : 0xFF8A2424) : (hov2 ? 0xFF21262D : 0xFF161B22);
            int border2 = isConf2 ? 0xFFF85149 : (hov2 ? 0xFF58A6FF : 0x22FFFFFF);
            String str2 = isConf2 ? "§c再次点击确认" : text2;

            graphics.fill(x2, y, x2 + halfW, y + h, bg2);
            graphics.renderOutline(x2, y, halfW, h, border2);
            graphics.drawCenteredString(font, str2, x2 + halfW / 2, y + 7, isConf2 ? 0xFFFFFFFF : (hov2 ? 0xFF58A6FF : 0xFFC9D1D9));
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            int halfW = (w - 4) / 2;
            int x1 = x;
            int x2 = x + halfW + 4;
            long now = System.currentTimeMillis();

            if (mouseX >= x1 && mouseX <= x1 + halfW && mouseY >= y && mouseY <= y + h) {
                if (reqConfirm1) {
                    if (UIItem.isConfirmExpired(confirm1Time, now)) {
                        confirm1Time = now;
                        confirm2Time = 0L;
                        if (Minecraft.getInstance().screen instanceof TacticalConsoleScreen sc) {
                            sc.showToast("请在 3 秒内再次点击以确认");
                        }
                        return true;
                    }
                    confirm1Time = 0L;
                }
                action1.run();
                return true;
            }
            if (mouseX >= x2 && mouseX <= x2 + halfW && mouseY >= y && mouseY <= y + h) {
                if (reqConfirm2) {
                    if (UIItem.isConfirmExpired(confirm2Time, now)) {
                        confirm2Time = now;
                        confirm1Time = 0L;
                        if (Minecraft.getInstance().screen instanceof TacticalConsoleScreen sc) {
                            sc.showToast("请在 3 秒内再次点击以确认");
                        }
                        return true;
                    }
                    confirm2Time = 0L;
                }
                action2.run();
                return true;
            }
            return false;
        }
    }

    public static class SortBarItem extends UIItem {
        private final Supplier<String> basisGetter;
        private final Runnable basisToggle;
        private final List<Component> basisTooltip;
        private final Supplier<String> dirGetter;
        private final Runnable dirToggle;
        private final List<Component> dirTooltip;

        SortBarItem(Supplier<String> basisGetter, Runnable basisToggle, List<Component> basisTooltip,
                    Supplier<String> dirGetter, Runnable dirToggle, List<Component> dirTooltip) {
            this.basisGetter = basisGetter;
            this.basisToggle = basisToggle;
            this.basisTooltip = basisTooltip;
            this.dirGetter = dirGetter;
            this.dirToggle = dirToggle;
            this.dirTooltip = dirTooltip;
            this.h = 20;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            int halfW = (w - 4) / 2;
            int btn1X = x;
            int btn2X = x + halfW + 4;

            boolean hov1 = mouseX >= btn1X && mouseX <= btn1X + halfW && mouseContentY >= y && mouseContentY <= y + h;
            boolean hov2 = mouseX >= btn2X && mouseX <= btn2X + halfW && mouseContentY >= y && mouseContentY <= y + h;

            // 依据按钮
            graphics.fill(btn1X, y, btn1X + halfW, y + h, hov1 ? 0xFF1F6FEB : 0xFF161B22);
            graphics.renderOutline(btn1X, y, halfW, h, hov1 ? 0xFF58A6FF : 0x22FFFFFF);
            graphics.drawCenteredString(font, basisGetter.get(), btn1X + halfW / 2, y + 6, hov1 ? 0xFFFFFFFF : 0xFFC9D1D9);

            // 方向按钮
            graphics.fill(btn2X, y, btn2X + halfW, y + h, hov2 ? 0xFF1F6FEB : 0xFF161B22);
            graphics.renderOutline(btn2X, y, halfW, h, hov2 ? 0xFF58A6FF : 0x22FFFFFF);
            graphics.drawCenteredString(font, dirGetter.get(), btn2X + halfW / 2, y + 6, hov2 ? 0xFFFFFFFF : 0xFFC9D1D9);

            if (hov1 && basisTooltip != null) {
                screen.setHoveredTooltip(basisTooltip, mouseX, realMouseY);
            } else if (hov2 && dirTooltip != null) {
                screen.setHoveredTooltip(dirTooltip, mouseX, realMouseY);
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            int halfW = (w - 4) / 2;
            int btn1X = x;
            int btn2X = x + halfW + 4;

            if (mouseX >= btn1X && mouseX <= btn1X + halfW && mouseY >= y && mouseY <= y + h) {
                basisToggle.run();
                return true;
            }
            if (mouseX >= btn2X && mouseX <= btn2X + halfW && mouseY >= y && mouseY <= y + h) {
                dirToggle.run();
                return true;
            }
            return false;
        }
    }

    public static class PaginationBarItem extends UIItem {
        private final int curPage;
        private final int totalPages;
        private final Runnable prevAction;
        private final Runnable nextAction;

        PaginationBarItem(int curPage, int totalPages, Runnable prevAction, Runnable nextAction) {
            this.curPage = curPage;
            this.totalPages = totalPages;
            this.prevAction = prevAction;
            this.nextAction = nextAction;
            this.h = 22;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            int btnW = 50;
            int prevX = x;
            int nextX = x + w - btnW;

            boolean prevHov = mouseX >= prevX && mouseX <= prevX + btnW && mouseContentY >= y && mouseContentY <= y + h;
            boolean nextHov = mouseX >= nextX && mouseX <= nextX + btnW && mouseContentY >= y && mouseContentY <= y + h;

            boolean canPrev = curPage > 1;
            boolean canNext = curPage < totalPages;

            graphics.fill(prevX, y, prevX + btnW, y + h, (canPrev && prevHov) ? 0xFF21262D : 0xFF161B22);
            graphics.renderOutline(prevX, y, btnW, h, (canPrev && prevHov) ? 0xFF58A6FF : 0x22FFFFFF);
            graphics.drawCenteredString(font, "< 上页", prevX + btnW / 2, y + 7, canPrev ? (prevHov ? 0xFFFFFFFF : 0xFFC9D1D9) : 0xFF484F58);

            String pageText = curPage + " / " + totalPages;
            graphics.drawCenteredString(font, pageText, x + w / 2, y + 7, 0xFF58A6FF);

            graphics.fill(nextX, y, nextX + btnW, y + h, (canNext && nextHov) ? 0xFF21262D : 0xFF161B22);
            graphics.renderOutline(nextX, y, btnW, h, (canNext && nextHov) ? 0xFF58A6FF : 0x22FFFFFF);
            graphics.drawCenteredString(font, "下页 >", nextX + btnW / 2, y + 7, canNext ? (nextHov ? 0xFFFFFFFF : 0xFFC9D1D9) : 0xFF484F58);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            int btnW = 50;
            int prevX = x;
            int nextX = x + w - btnW;

            if (mouseX >= prevX && mouseX <= prevX + btnW && mouseY >= y && mouseY <= y + h) {
                if (curPage > 1) {
                    prevAction.run();
                    return true;
                }
            }
            if (mouseX >= nextX && mouseX <= nextX + btnW && mouseY >= y && mouseY <= y + h) {
                if (curPage < totalPages) {
                    nextAction.run();
                    return true;
                }
            }
            return false;
        }
    }

    public static class SegmentedBarItem extends UIItem {
        private final String[] segments;
        private final Supplier<Integer> selectedGetter;
        private final Consumer<Integer> onSelect;

        SegmentedBarItem(String[] segments, Supplier<Integer> selectedGetter, Consumer<Integer> onSelect) {
            this.segments = segments;
            this.selectedGetter = selectedGetter;
            this.onSelect = onSelect;
            this.h = 22;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            graphics.fill(x, y, x + w, y + h, 0xFF161B22);
            graphics.renderOutline(x, y, w, h, 0x22FFFFFF);

            int segW = w / segments.length;
            int curSel = selectedGetter.get();

            for (int i = 0; i < segments.length; i++) {
                int sx = x + i * segW;
                int sw = (i == segments.length - 1) ? (w - i * segW) : segW;
                boolean isSel = (i == curSel);
                boolean hov = mouseX >= sx && mouseX < sx + sw && mouseContentY >= y && mouseContentY < y + h;

                if (isSel) {
                    graphics.fill(sx, y + 1, sx + sw, y + h - 1, 0xFF1F6FEB);
                    graphics.drawCenteredString(font, segments[i], sx + sw / 2, y + 7, 0xFFFFFFFF);
                } else {
                    if (hov) {
                        graphics.fill(sx, y + 1, sx + sw, y + h - 1, 0x1AFFFFFF);
                    }
                    graphics.drawCenteredString(font, segments[i], sx + sw / 2, y + 7, hov ? 0xFFE6EDF3 : 0xFF8B949E);
                }
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            int segW = w / segments.length;
            int clicked = (int) (mouseX - x) / segW;
            if (clicked >= 0 && clicked < segments.length) {
                onSelect.accept(clicked);
                return true;
            }
            return false;
        }
    }

    public static class WeaponBannerCard extends UIItem {
        private final ItemStack held;
        private final ClientEvents.WeaponCategory category;

        WeaponBannerCard(ItemStack held, ClientEvents.WeaponCategory category) {
            this.held = held;
            this.category = category;
            this.h = 34;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            graphics.fill(x, y, x + w, y + h, 0xFF161B22);
            graphics.renderOutline(x, y, w, h, 0x26FFFFFF);

            // 类别高亮左边条
            graphics.fill(x + 1, y + 1, x + 4, y + h - 1, category.getColor());

            int textLeft = x + 8;
            if (!held.isEmpty()) {
                graphics.renderItem(held, x + 8, y + 9);
                textLeft = x + 28;
            }

            String name = held.isEmpty() ? "空手模式" : (category == ClientEvents.WeaponCategory.GUN ? FirearmAdapter.getCleanGunName(held) : held.getHoverName().getString());
            name = name.replaceAll("^\\[.*?\\]\\s*", "").trim();

            String tag;
            String desc;
            if (category == ClientEvents.WeaponCategory.GUN) {
                tag = "§6[枪械] " + name;
                desc = "§7已激活枪械直瞄、Triggerbot 与自动压枪";
            } else if (category == ClientEvents.WeaponCategory.BOW) {
                tag = "§b[弓弩] " + name;
                desc = "§7已激活抛物线微元预判、满蓄放箭与拦截光圈";
            } else if (category == ClientEvents.WeaponCategory.MELEE) {
                tag = "§c[近战] " + name;
                desc = "§7智能攻速 CD 同步，确保 100% 暴击与横扫伤害";
            } else {
                tag = "§7[常规] " + name;
                desc = "§8显示全部全局战术预设，手持武器将自动适配";
            }

            int availW = w - (textLeft - x) - 4;
            graphics.drawString(font, font.plainSubstrByWidth(tag, availW), textLeft, y + 6, 0xFFF0F6FC, false);
            graphics.drawString(font, font.plainSubstrByWidth(desc, availW), textLeft, y + 19, 0xFF8B949E, false);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    public static class HeldMeleeStatusCard extends UIItem {
        private final ItemStack held;

        HeldMeleeStatusCard(ItemStack held) {
            this.held = held;
            this.h = 44;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            graphics.fill(x, y, x + w, y + h, 0xFF161B22);
            graphics.renderOutline(x, y, w, h, 0x33FFFFFF);

            graphics.renderItem(held, x + 4, y + 4);

            String name = held.getHoverName().getString();
            graphics.drawString(font, name, x + 24, y + 5, 0xFFF0F6FC, false);

            graphics.drawString(font, "§c[近战武器]  §e智能出刀蓄力CD已就绪", x + 24, y + 17, 0xFFFFFFFF, false);
            graphics.drawString(font, "§7由近战引擎按攻速 CD 实时同步，无需抛物线弹道档案", x + 24, y + 29, 0xFF8B949E, false);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    public static class HeldWeaponStatusCard extends UIItem {
        private final ItemStack held;
        private final AutoBallisticsTracker.BallisticsProfile profile;

        HeldWeaponStatusCard(ItemStack held, AutoBallisticsTracker.BallisticsProfile profile) {
            this.held = held;
            this.profile = profile;
            this.h = 44;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            graphics.fill(x, y, x + w, y + h, 0xFF161B22);
            graphics.renderOutline(x, y, w, h, 0x33FFFFFF);

            graphics.renderItem(held, x + 4, y + 4);

            String name = held.getHoverName().getString();
            graphics.drawString(font, name, x + 24, y + 5, 0xFFF0F6FC, false);

            if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(held)) {
                com.xdyyj.autoattacker.weapon.FirearmAdapter.GunStatus gun = 
                    com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunStatus(held);
                int maxCap = gun.getEffectiveMaxAmmo();
                String ammoStr = (gun.totalAmmo >= 0) ? 
                    ("弹药: " + gun.totalAmmo + "/" + (maxCap > 0 ? maxCap : "∞")) : "现代枪械";
                String info = String.format(Locale.ROOT, "初速:%.1f (%.0fm/s)  重力:%.3f  %s",
                        profile.speed, gun.bulletSpeedMs, profile.gravity, ammoStr);
                graphics.drawString(font, info, x + 24, y + 17, 0xFF58A6FF, false);

                String modeStr = String.format(Locale.ROOT, "§6[%s]  §c爆头x%.1f  §e射速:%d RPM",
                        gun.typeName, gun.headshotMult, gun.rpm);
                graphics.drawString(font, modeStr, x + 24, y + 29, 0xFFFFFFFF, false);
            } else {
                String info = String.format(Locale.ROOT, "初速:%.2f  重力:%.3f  蓄力:%dt",
                        profile.speed, profile.gravity, profile.minChargeTicks);
                graphics.drawString(font, info, x + 24, y + 17, 0xFF58A6FF, false);

                String modeStr = profile.isHoming ? "§d[自导引追踪]" : (profile.gravity == 0.0 ? "§a[免重力直线]" : "§7[常规抛物线]");
                graphics.drawString(font, modeStr, x + 24, y + 29, 0xFFFFFFFF, false);
            }
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    public static class QuickItemActionCard extends UIItem {
        private final ItemStack held;
        private final boolean alreadyInList;
        private final String listName;
        private final Runnable onToggle;

        QuickItemActionCard(ItemStack held, boolean alreadyInList, String listName, Runnable onToggle) {
            this.held = held;
            this.alreadyInList = alreadyInList;
            this.listName = listName;
            this.onToggle = onToggle;
            this.h = 28;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            graphics.fill(x, y, x + w, y + h, 0xFF161B22);
            graphics.renderOutline(x, y, w, h, 0x22FFFFFF);

            graphics.renderItem(held, x + 4, y + 6);

            String name = held.getHoverName().getString();
            int maxNameW = w - 100;
            String trimmed = font.plainSubstrByWidth(name, maxNameW);
            graphics.drawString(font, trimmed, x + 24, y + 10, 0xFFF0F6FC, false);

            int btnW = 68;
            int btnH = 18;
            int btnX = x + w - btnW - 4;
            int btnY = y + 5;

            boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseContentY >= btnY && mouseContentY <= btnY + btnH;

            int bgCol = alreadyInList ? (hov ? 0xFF8A2424 : 0xFF21262D) : (hov ? 0xFF2EA043 : 0xFF238636);
            int borderCol = alreadyInList ? (hov ? 0xFFC93B3B : 0x33FFFFFF) : (hov ? 0xFF3FB950 : 0x33FFFFFF);

            graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bgCol);
            graphics.renderOutline(btnX, btnY, btnW, btnH, borderCol);

            String btnText = alreadyInList ? "- 移出名单" : "+ 加入名单";
            graphics.drawCenteredString(font, btnText, btnX + btnW / 2, btnY + 5, hov ? 0xFFFFFFFF : 0xFFC9D1D9);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            int btnW = 68;
            int btnH = 18;
            int btnX = x + w - btnW - 4;
            int btnY = y + 5;
            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                onToggle.run();
                return true;
            }
            return false;
        }
    }

    public static class QuickEntityActionCard extends UIItem {
        private final LivingEntity target;
        private final boolean alreadyInList;
        private final Runnable onToggle;

        QuickEntityActionCard(LivingEntity target, boolean alreadyInList, Runnable onToggle) {
            this.target = target;
            this.alreadyInList = alreadyInList;
            this.onToggle = onToggle;
            this.h = 28;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            graphics.fill(x, y, x + w, y + h, 0xFF161B22);
            graphics.renderOutline(x, y, w, h, 0x22FFFFFF);

            String name = target.getName().getString();
            int maxNameW = w - 100;
            String trimmed = font.plainSubstrByWidth("锁定: " + name, maxNameW);
            graphics.drawString(font, trimmed, x + 6, y + 10, 0xFFF0F6FC, false);

            int btnW = 68;
            int btnH = 18;
            int btnX = x + w - btnW - 4;
            int btnY = y + 5;

            boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseContentY >= btnY && mouseContentY <= btnY + btnH;

            int bgCol = alreadyInList ? (hov ? 0xFF8A2424 : 0xFF21262D) : (hov ? 0xFF2EA043 : 0xFF238636);
            int borderCol = alreadyInList ? (hov ? 0xFFC93B3B : 0x33FFFFFF) : (hov ? 0xFF3FB950 : 0x33FFFFFF);

            graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, bgCol);
            graphics.renderOutline(btnX, btnY, btnW, btnH, borderCol);

            String btnText = alreadyInList ? "- 移出排除" : "+ 加入排除";
            graphics.drawCenteredString(font, btnText, btnX + btnW / 2, btnY + 5, hov ? 0xFFFFFFFF : 0xFFC9D1D9);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            int btnW = 68;
            int btnH = 18;
            int btnX = x + w - btnW - 4;
            int btnY = y + 5;
            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
                onToggle.run();
                return true;
            }
            return false;
        }
    }

    public static class ListEntryCard extends UIItem {
        private final String rawId;
        private final boolean isEntity;
        private final Runnable onDelete;
        private final DeleteConfirmButton deleteButton = new DeleteConfirmButton(4);

        ListEntryCard(String rawId, boolean isEntity, Runnable onDelete) {
            this.rawId = rawId;
            this.isEntity = isEntity;
            this.onDelete = onDelete;
            this.h = 24;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            boolean hovered = isHovered(mouseX, mouseContentY);
            drawCardFrame(graphics, hovered);

            String displayName = getReadableEntryName(rawId, isEntity);
            // 与旧实现一致:确认态下按钮展宽,名字可用宽度随之收窄
            int maxNameW = (x + w - deleteButton.currentWidth() - 4) - x - 10;
            String trimmed = font.plainSubstrByWidth(displayName + " §8(" + rawId + ")", maxNameW);
            graphics.drawString(font, trimmed, x + 6, y + 8, 0xFFF0F6FC, false);

            deleteButton.render(graphics, font, mouseX, mouseContentY, x, y, w);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return deleteButton.mouseClicked(mouseX, mouseY, x, y, w, onDelete);
        }
    }

    public static class ProfileArchiveCard extends UIItem {
        private final String sig;
        private final AutoBallisticsTracker.BallisticsProfile profile;
        private final Runnable onDelete;
        private final DeleteConfirmButton deleteButton = new DeleteConfirmButton(6);

        ProfileArchiveCard(String sig, AutoBallisticsTracker.BallisticsProfile profile, Runnable onDelete) {
            this.sig = sig;
            this.profile = profile;
            this.onDelete = onDelete;
            this.h = 28;
        }

        @Override
        void render(GuiGraphics graphics, Font font, int mouseX, int mouseContentY, int realMouseY, TacticalConsoleScreen screen) {
            boolean hovered = isHovered(mouseX, mouseContentY);
            drawCardFrame(graphics, hovered);

            String readableName = getReadableWeaponName(sig);
            String briefStats;
            if (sig.startsWith("gun:") || sig.contains("modern_kinetic_gun")) {
                briefStats = String.format(Locale.ROOT, "直瞄 速:%.1f 零重力", profile.speed);
            } else {
                briefStats = String.format(Locale.ROOT, "速:%.2f 重:%.3f 蓄:%dt",
                        profile.speed, profile.gravity, profile.minChargeTicks);
            }

            int availW = (x + w - deleteButton.currentWidth() - 4) - x - 10;
            String line = readableName + " §8" + briefStats;
            String trimmed = font.plainSubstrByWidth(line, availW);
            graphics.drawString(font, trimmed, x + 6, y + 10, 0xFFF0F6FC, false);

            deleteButton.render(graphics, font, mouseX, mouseContentY, x, y, w);
        }

        @Override
        boolean mouseClicked(double mouseX, double mouseY, int button) {
            return deleteButton.mouseClicked(mouseX, mouseY, x, y, w, onDelete);
        }
    }
}
