package com.xdyyj.autoattacker.ui;

import com.xdyyj.autoattacker.AutoAttackerConfig;
import com.xdyyj.autoattacker.AutoBallisticsTracker;
import com.xdyyj.autoattacker.ClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * 精巧紧凑型极简现代暗黑悬浮卡片 (Compact Dark Minimalist HUD) 弹道控制面板：
 * - 尺寸紧凑精致 (146 x 140)，彻底清除冗余留白，零遮挡游戏视野；
 * - 像素级对齐网格、分段控制器 Tab、一体化胶囊步进器、跑道形滑动开关、滚轮无级微调。
 */
public class TacticalDebugPanel {

    public static boolean isVisible = true;          // 面板整体是否可见
    public static boolean isControlActive = false;   // 是否处于交互控制态 (解锁光标)

    public static final int PANEL_WIDTH = 146;
    public static final int PANEL_HEIGHT = 168;
    public static final int TITLE_BAR_HEIGHT = 16;
    public static final int TAB_BAR_HEIGHT = 14;

    public static int panelX = 10;
    public static int panelY = 30;
    private static boolean isDragging = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;

    public static void setPosition(int x, int y) {
        panelX = x;
        panelY = y;
        AutoAttackerConfig.OVERLAY_POS_X.set(x);
        AutoAttackerConfig.OVERLAY_POS_Y.set(y);
        AutoAttackerConfig.saveConfig();
    }

    public static int currentTab = 0; // 0: 武器参数, 1: 测距与校准, 2: 视觉与设置

    // 状态反馈提示 (Toast)
    private static String statusMessage = null;
    private static long statusMessageExpiry = 0L;

    public static void setStatus(String msg) {
        statusMessage = msg;
        statusMessageExpiry = System.currentTimeMillis() + 2500L;
        // HUD 小窗的每个设置变更都紧跟着 setStatus，因此在此统一请求落盘。
        // (全屏控制台各处显式调用 saveConfig，而小窗原先只在拖动面板时保存，
        //  导致切换开关/调整数值后退出游戏即丢失。)
        requestConfigSave();
    }

    // --- 配置落盘节流 ---
    // SPEC.save() 是全量写盘，不能在每次点击时直呼。这里限流为最多每 800ms 一次，
    // 并在关闭控制态/退出游戏时强制补一次，确保最终一致。
    private static long lastConfigSaveMs = 0L;
    private static boolean configSavePending = false;
    private static final long CONFIG_SAVE_MIN_INTERVAL_MS = 800L;

    private static void requestConfigSave() {
        long now = System.currentTimeMillis();
        if (now - lastConfigSaveMs >= CONFIG_SAVE_MIN_INTERVAL_MS) {
            lastConfigSaveMs = now;
            configSavePending = false;
            AutoAttackerConfig.saveConfig();
        } else {
            configSavePending = true;
        }
    }

    /** 立即补写未落盘的配置改动 (在关闭控制态/退出时调用) */
    public static void flushPendingConfigSave() {
        if (configSavePending) {
            configSavePending = false;
            lastConfigSaveMs = System.currentTimeMillis();
            AutoAttackerConfig.saveConfig();
        }
    }

    // 二次确认防误触状态戳 (3秒超时自动复原)
    private static long resetConfirmTime = 0L;
    private static long clearAllConfirmTime = 0L;

    // --- 渲染数据快照缓存 (避免每帧走反射链与 tooltip 构建) ---
    // GUI 渲染可达 240fps，而面板显示的是枪支属性/弹药等变化缓慢的数据，
    // 每帧重算 FirearmAdapter.getGunStatus(20+ 次反射) 与
    // TooltipBallisticsExtractor.extract(构建完整 tooltip + 正则匹配) 开销过大。
    // 这里按 HUD_REFRESH_INTERVAL_MS 节流为约 10Hz 刷新一次快照。
    private static final long HUD_REFRESH_INTERVAL_MS = 100L;
    private static long hudSnapshotNanos = 0L;
    private static ItemStack hudCachedWeapon = ItemStack.EMPTY;
    private static com.xdyyj.autoattacker.weapon.FirearmAdapter.GunStatus hudCachedGun = null;
    private static AutoBallisticsTracker.BallisticsProfile hudCachedProfile = null;
    private static TooltipBallisticsExtractor.ExtractedBallistics hudCachedExtracted = null;
    private static boolean hudCachedExtractedValid = false;

    /**
     * 刷新渲染数据快照。按时间间隔节流，并在手持武器变化时立即失效重算。
     * 仅在 render 的可见分支内调用。
     */
    private static void refreshSnapshot(Player player, ItemStack weaponStack) {
        long now = System.nanoTime();
        boolean weaponChanged = !ItemStack.matches(weaponStack, hudCachedWeapon);
        boolean expired = (now - hudSnapshotNanos) >= HUD_REFRESH_INTERVAL_MS * 1_000_000L;
        if (!weaponChanged && !expired) return;

        hudSnapshotNanos = now;
        hudCachedWeapon = weaponStack.copy();
        hudCachedGun = null;
        hudCachedProfile = null;
        hudCachedExtracted = null;
        hudCachedExtractedValid = false;

        if (weaponStack.isEmpty()) return;

        if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(weaponStack)) {
            hudCachedGun = com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunStatus(weaponStack);
        } else {
            hudCachedProfile = AutoBallisticsTracker.getProfile(weaponStack);
            // tooltip 解析仅在弓弩分页需要，且其开销最大 (构建 tooltip + 正则)，故一并纳入节流
            hudCachedExtracted = TooltipBallisticsExtractor.extract(weaponStack, player);
            hudCachedExtractedValid = true;
        }
    }

    public static void toggleControl() {
        Minecraft mc = Minecraft.getInstance();
        if (!isControlActive) {
            if (mc.screen == null) {
                mc.setScreen(new TacticalControlScreen());
                isControlActive = true;
            }
        } else {
            if (mc.screen instanceof TacticalControlScreen) {
                mc.setScreen(null);
            }
            isControlActive = false;
            isDragging = false;
            AutoBallisticsTracker.saveToDiskImmediate();
            flushPendingConfigSave();
        }
    }

    public static void ensurePosition(int screenWidth, int screenHeight) {
        panelX = Math.max(0, Math.min(panelX, screenWidth - PANEL_WIDTH));
        panelY = Math.max(0, Math.min(panelY, screenHeight - PANEL_HEIGHT));
    }

    public static boolean isMouseOverPanel(double mouseX, double mouseY) {
        if (!isVisible) return false;
        return mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH && mouseY >= panelY && mouseY <= panelY + PANEL_HEIGHT;
    }

    // =========================================================================
    // 渲染主入口
    // =========================================================================

    public static void render(GuiGraphics graphics, float partialTick) {
        render(graphics, -1, -1, partialTick);
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        ItemStack activeWeapon = ClientEvents.getActiveWeapon(player);
        ClientEvents.WeaponCategory category = ClientEvents.getWeaponCategory(activeWeapon);
        if (AutoAttackerConfig.HUD_ONLY_WHEN_HOLDING_BOW.get() && (category == ClientEvents.WeaponCategory.OTHER || activeWeapon.isEmpty()) && !isControlActive) {
            return;
        }

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        ensurePosition(screenW, screenH);
        Font font = mc.font;

        // 刷新渲染数据快照 (节流 ~10Hz)：避免每帧走反射链 / 构建 tooltip
        refreshSnapshot(player, activeWeapon);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 400.0F);

        // 1. 双层极简暗黑容器底板与柔光微边框
        int bgColor = isControlActive ? 0xF213151A : 0xBA0F1115;
        int innerBorder = isControlActive ? 0x33FFFFFF : 0x1AFFFFFF;
        
        // 外层柔和微阴影
        graphics.fill(panelX - 1, panelY - 1, panelX + PANEL_WIDTH + 1, panelY + PANEL_HEIGHT + 1, 0x44000000);
        // 主体深黑背板
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, bgColor);
        // 内层高光细描边
        graphics.renderOutline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, innerBorder);

        // 3. 标题栏
        boolean titleHovered = isControlActive && mouseX >= panelX && mouseX <= panelX + PANEL_WIDTH &&
                               mouseY >= panelY && mouseY <= panelY + TITLE_BAR_HEIGHT;
        if (titleHovered) {
            graphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + TITLE_BAR_HEIGHT, 0x14FFFFFF);
        }
        // 标题与按键提示
        String keyName = com.xdyyj.autoattacker.ClientModEvents.DEBUG_PANEL_KEY.getTranslatedKeyMessage().getString();
        graphics.drawString(font, "弹道战况", panelX + 6, panelY + 4, 0xFFF0F2F5, false);
        graphics.drawString(font, "[" + keyName + "]", panelX + 6 + font.width("弹道战况") + 3, panelY + 4, 0xFF7D8392, false);

        // 窗口控制按键 (最小化与关闭)
        renderWindowControls(graphics, font, mouseX, mouseY);

        // 顶栏与内容区分割柔光线
        graphics.fill(panelX + 5, panelY + TITLE_BAR_HEIGHT, panelX + PANEL_WIDTH - 5, panelY + TITLE_BAR_HEIGHT + 1, 0x12FFFFFF);

        // 4. 现代分段控制器 (Segmented Tab Control)
        int tabY = panelY + TITLE_BAR_HEIGHT + 2;
        String[] tabs = {"武器", "遥测", "设置"};
        renderSegmentedTab(graphics, font, mouseX, mouseY, panelX + 5, tabY, PANEL_WIDTH - 10, TAB_BAR_HEIGHT, tabs, currentTab);

        int contentStartY = tabY + TAB_BAR_HEIGHT + 4;

        // 5. 分页内容
        if (currentTab == 0) {
            renderWeaponTab(graphics, font, mouseX, mouseY, contentStartY, player, activeWeapon, category);
        } else if (currentTab == 1) {
            renderTelemetryTab(graphics, font, mouseX, mouseY, contentStartY, player, activeWeapon, category);
        } else {
            renderSettingsTab(graphics, font, mouseX, mouseY, contentStartY, player, activeWeapon, category);
        }

        // 6. 悬浮 Toast 状态反馈胶囊
        if (statusMessage != null) {
            long remaining = statusMessageExpiry - System.currentTimeMillis();
            if (remaining > 0) {
                float alpha = Math.min(1.0F, remaining / 300.0F);
                int alphaBg = (int) (alpha * 0xEB);
                int alphaBorder = (int) (alpha * 0xF0);
                int alphaText = (int) (alpha * 0xFF);

                int textW = font.width(statusMessage);
                int toastW = Math.max(PANEL_WIDTH - 10, textW + 14);
                int toastX = panelX + (PANEL_WIDTH - toastW) / 2;
                int toastY = (panelY + PANEL_HEIGHT + 20 < screenH) ? (panelY + PANEL_HEIGHT + 4) : (panelY - 18);

                int bgCol = (alphaBg << 24) | 0x16181E;
                int borderCol = (alphaBorder << 24) | 0x388BFD;
                int textCol = (alphaText << 24) | 0xF0F6FC;

                graphics.fill(toastX, toastY, toastX + toastW, toastY + 15, bgCol);
                graphics.renderOutline(toastX, toastY, toastW, 15, borderCol);
                graphics.drawCenteredString(font, statusMessage, toastX + toastW / 2, toastY + 3, textCol);
            } else {
                statusMessage = null;
            }
        }

        graphics.pose().popPose();
    }

    private static void renderWindowControls(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        int btnSize = 10;
        int closeX = panelX + PANEL_WIDTH - 14;
        int configX = panelX + PANEL_WIDTH - 26;
        int btnY = panelY + 3;

        // 全屏配置快捷抽屉入口 (三道战术横条，零依赖外部字符集)
        boolean configHov = isControlActive && mouseX >= configX && mouseX <= configX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize;
        graphics.fill(configX, btnY, configX + btnSize, btnY + btnSize, configHov ? 0xFF1D3B60 : 0xFF1C1E24);
        graphics.renderOutline(configX, btnY, btnSize, btnSize, configHov ? 0xFF388BFD : 0x22FFFFFF);
        int barCol = configHov ? 0xFFFFFFFF : 0xFFA0A5B2;
        graphics.fill(configX + 2, btnY + 2, configX + btnSize - 2, btnY + 3, barCol);
        graphics.fill(configX + 2, btnY + 5, configX + btnSize - 2, btnY + 6, barCol);
        graphics.fill(configX + 2, btnY + 8, configX + btnSize - 2, btnY + 9, barCol);

        // 关闭按钮 x
        boolean closeHov = isControlActive && mouseX >= closeX && mouseX <= closeX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize;
        graphics.fill(closeX, btnY, closeX + btnSize, btnY + btnSize, closeHov ? 0xFF8A2424 : 0xFF1C1E24);
        graphics.renderOutline(closeX, btnY, btnSize, btnSize, closeHov ? 0xFFC93B3B : 0x22FFFFFF);
        graphics.drawCenteredString(font, "x", closeX + btnSize / 2, btnY + 1, closeHov ? 0xFFFFFFFF : 0xFFA0A5B2);
    }

    private static void renderSegmentedTab(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, String[] tabs, int activeTab) {
        graphics.fill(x, y, x + w, y + h, 0xFF17191F);
        graphics.renderOutline(x, y, w, h, 0x1AFFFFFF);

        int segW = w / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            int tx = x + i * segW;
            int tw = (i == tabs.length - 1) ? (w - i * segW) : segW;
            boolean hovered = isControlActive && mouseX >= tx && mouseX < tx + tw && mouseY >= y && mouseY < y + h;

            if (i == activeTab) {
                graphics.fill(tx + 1, y + 1, tx + tw - 1, y + h - 1, 0xFF2B2E38);
                graphics.renderOutline(tx + 1, y + 1, tw - 2, h - 2, 0x30FFFFFF);
                graphics.drawCenteredString(font, tabs[i], tx + tw / 2, y + 3, 0xFFFFFFFF);
            } else {
                int textCol = hovered ? 0xFFCED3DC : 0xFF7D8392;
                graphics.drawCenteredString(font, tabs[i], tx + tw / 2, y + 3, textCol);
            }
        }
    }

    // =========================================================================
    // Tab 0: 武器参数与微调 (根据手持武器4态智能自适应)
    // =========================================================================

    private static void renderWeaponTab(GuiGraphics graphics, Font font, int mouseX, int mouseY, int startY,
                                        Player player, ItemStack weaponStack, ClientEvents.WeaponCategory category) {
        String itemName;
        if (weaponStack.isEmpty()) {
            itemName = "空手模式";
        } else if (category == ClientEvents.WeaponCategory.GUN) {
            itemName = com.xdyyj.autoattacker.weapon.FirearmAdapter.getCleanGunName(weaponStack);
        } else {
            itemName = weaponStack.getHoverName().getString();
        }
        itemName = itemName.replaceAll("^\\[.*?\\]\\s*", "").trim();
        if (itemName.length() > 14) itemName = itemName.substring(0, 13) + "…";

        // 武器识别卡片 (紧凑高 14px，带类别彩色指示条)
        renderCard(graphics, panelX + 5, startY, PANEL_WIDTH - 10, 14);
        graphics.fill(panelX + 8, startY + 3, panelX + 10, startY + 11, category.getColor());
        String tagPrefix = (category == ClientEvents.WeaponCategory.GUN) ? "§6[枪] " :
                           (category == ClientEvents.WeaponCategory.BOW) ? "§b[弓] " :
                           (category == ClientEvents.WeaponCategory.MELEE) ? "§c[刃] " : "§7[空] ";
        graphics.drawString(font, tagPrefix + itemName, panelX + 13, startY + 3, 0xFFF0F2F5, false);

        int stepperW = 50;
        int stepperX = panelX + PANEL_WIDTH - 6 - stepperW; // panelX + 90
        int resetBtnX = stepperX - 22;                      // panelX + 68

        if (category == ClientEvents.WeaponCategory.GUN) {
            // === 现代枪械 (TACZ) ===
            int y1 = startY + 16;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y1, "自动开火 (Triggerbot)", AutoAttackerConfig.ENABLE_GUN_TRIGGERBOT.get());

            int y2 = y1 + 13;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y2, "自动压枪 (Anti-Recoil)", AutoAttackerConfig.ENABLE_ANTI_RECOIL.get());

            int y3 = y2 + 13;
            graphics.drawString(font, "压枪", panelX + 6, y3 + 2, 0xFFB0B6C2, false);
            renderTagButton(graphics, font, mouseX, mouseY, resetBtnX, y3, 20, 11, "1.0x", Math.abs(AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get() - 1.0) < 0.01);
            renderModernStepper(graphics, font, mouseX, mouseY, stepperX, y3, stepperW, 11, String.format(Locale.ROOT, "%.2fx", AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get()), false);

            int y4 = y3 + 13;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y4, "机瞄开镜平滑感知", AutoAttackerConfig.ENABLE_ADS_SENSING.get());

            int y5 = y4 + 13;
            graphics.drawString(font, "部位", panelX + 6, y5 + 2, 0xFFB0B6C2, false);
            renderTargetPartCapsule(graphics, font, mouseX, mouseY, resetBtnX - 10, y5, stepperW + 32, 11, AutoAttackerConfig.TARGET_PART.get());

            int y6 = y5 + 14;
            renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, y6, PANEL_WIDTH - 10, 13, "同步枪械原厂直瞄参数", 0xFF1D7846);

            int y7 = y6 + 15;
            com.xdyyj.autoattacker.weapon.FirearmAdapter.GunStatus gun = hudCachedGun;
            if (gun == null) {
                gun = com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunStatus(weaponStack);
            }
            int maxCap = gun.getEffectiveMaxAmmo();
            String ammoDesc = (gun.totalAmmo >= 0) ? 
                (gun.totalAmmo + "/" + (maxCap > 0 ? maxCap : "∞")) : "满装";
            graphics.drawString(font, String.format(Locale.ROOT, "初速:%.0fm/s 弹药:%s 爆头x%.1f", gun.bulletSpeedMs, ammoDesc, gun.headshotMult), panelX + 6, y7, 0xFF7EE787, false);

        } else if (category == ClientEvents.WeaponCategory.BOW) {
            // === 传统弓弩 ===
            AutoBallisticsTracker.BallisticsProfile profile = (hudCachedProfile != null)
                    ? hudCachedProfile
                    : AutoBallisticsTracker.getProfile(weaponStack);

            int y1 = startY + 16;
            graphics.drawString(font, "初速", panelX + 6, y1 + 2, 0xFFB0B6C2, false);
            renderTagButton(graphics, font, mouseX, mouseY, resetBtnX, y1, 20, 11, "3.0", Math.abs(profile.speed - 3.0f) < 0.01f);
            renderModernStepper(graphics, font, mouseX, mouseY, stepperX, y1, stepperW, 11, String.format(Locale.ROOT, "%.2f", profile.speed), false);

            int y2 = y1 + 13;
            graphics.drawString(font, "重力", panelX + 6, y2 + 2, 0xFFB0B6C2, false);
            boolean isZeroG = profile.gravity <= 1.0E-5;
            renderTagButton(graphics, font, mouseX, mouseY, resetBtnX, y2, 20, 11, isZeroG ? "0G" : "0.05", isZeroG);
            String gText = isZeroG ? "0.00" : String.format(Locale.ROOT, "%.3f", profile.gravity);
            renderModernStepper(graphics, font, mouseX, mouseY, stepperX, y2, stepperW, 11, gText, isZeroG);

            int y3 = y2 + 13;
            graphics.drawString(font, "蓄力", panelX + 6, y3 + 2, 0xFFB0B6C2, false);
            renderModernStepper(graphics, font, mouseX, mouseY, stepperX, y3, stepperW, 11, profile.minChargeTicks + "t", false);

            int y4 = y3 + 13;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y4, "满蓄力自动放箭", AutoAttackerConfig.ENABLE_AUTO_SHOOT.get());

            int y5 = y4 + 13;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y5, "自寻的 / 锁定追踪", profile.isHoming);

            int y6 = y5 + 14;
            // 读节流快照；首次尚未填充时(切到弓弩页的当帧)才回退一次实算
            TooltipBallisticsExtractor.ExtractedBallistics extracted;
            if (hudCachedExtractedValid) {
                extracted = hudCachedExtracted;
            } else {
                extracted = TooltipBallisticsExtractor.extract(weaponStack, player);
            }
            boolean hasExtracted = (extracted != null && extracted.hasAnyData());
            String btnText = hasExtracted ? "应用 Tooltip 参数" : "重新扫描 Tooltip 参数";
            renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, y6, PANEL_WIDTH - 10, 13, btnText, hasExtracted ? 0xFF1D5A96 : 0);

            int y7 = y6 + 15;
            if (hasExtracted) {
                String hint = "";
                if (extracted.speed != null) hint += "Spd " + extracted.speed + " ";
                if (extracted.drawTicks != null) hint += "Draw " + extracted.drawTicks + "t";
                graphics.drawString(font, "匹配: " + hint, panelX + 6, y7, 0xFF7EE787, false);
            } else {
                graphics.drawString(font, "无显式数值，建议试错校准", panelX + 6, y7, 0xFF656B7A, false);
            }

        } else if (category == ClientEvents.WeaponCategory.MELEE) {
            // === 近战兵刃 (剑/斧/三叉戟) ===
            int y1 = startY + 16;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y1, "自动近战 (满蓄出刀)", AutoAttackerConfig.ENABLE_AUTO_ATTACK.get());

            int y2 = y1 + 13;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y2, "自瞄吸附 (平滑跟刀)", AutoAttackerConfig.ENABLE_AIM_ASSIST.get());

            int y3 = y2 + 13;
            graphics.drawString(font, "部位", panelX + 6, y3 + 2, 0xFFB0B6C2, false);
            renderTargetPartCapsule(graphics, font, mouseX, mouseY, resetBtnX - 10, y3, stepperW + 32, 11, AutoAttackerConfig.TARGET_PART.get());

            int y4 = y3 + 13;
            graphics.drawString(font, "范围", panelX + 6, y4 + 2, 0xFFB0B6C2, false);
            renderTagButton(graphics, font, mouseX, mouseY, resetBtnX, y4, 20, 11, "4.0m", Math.abs(AutoAttackerConfig.AIM_ASSIST_RANGE.get() - 4.0) < 0.1);
            renderModernStepper(graphics, font, mouseX, mouseY, stepperX, y4, stepperW, 11, String.format(Locale.ROOT, "%.1fm", AutoAttackerConfig.AIM_ASSIST_RANGE.get()), false);

            int y5 = y4 + 13;
            graphics.drawString(font, "速度", panelX + 6, y5 + 2, 0xFFB0B6C2, false);
            renderTagButton(graphics, font, mouseX, mouseY, resetBtnX, y5, 20, 11, "0.25", Math.abs(AutoAttackerConfig.AIM_ASSIST_SPEED.get() - 0.25) < 0.01);
            renderModernStepper(graphics, font, mouseX, mouseY, stepperX, y5, stepperW, 11, String.format(Locale.ROOT, "%.2f", AutoAttackerConfig.AIM_ASSIST_SPEED.get()), false);

            int y6 = y5 + 14;
            renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, y6, PANEL_WIDTH - 10, 13, "⚙ 打开全屏战术抽屉", 0xFF1D5A96);

            int y7 = y6 + 15;
            graphics.drawString(font, "CD满蓄出刀 · 保持横扫暴击", panelX + 6, y7, 0xFFE3B341, false);

        } else {
            // === 常规/空手模式 ===
            int y1 = startY + 16;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y1, "模组核心总开关", AutoAttackerConfig.ENABLE_MOD.get());

            int y2 = y1 + 13;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y2, "自动近战挥击", AutoAttackerConfig.ENABLE_AUTO_ATTACK.get());

            int y3 = y2 + 13;
            renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, y3, "自瞄磁力吸附", AutoAttackerConfig.ENABLE_AIM_ASSIST.get());

            int y4 = y3 + 13;
            graphics.drawString(font, "部位", panelX + 6, y4 + 2, 0xFFB0B6C2, false);
            renderTargetPartCapsule(graphics, font, mouseX, mouseY, resetBtnX - 10, y4, stepperW + 32, 11, AutoAttackerConfig.TARGET_PART.get());

            int y5 = y4 + 13;
            graphics.drawString(font, "范围", panelX + 6, y5 + 2, 0xFFB0B6C2, false);
            renderModernStepper(graphics, font, mouseX, mouseY, stepperX, y5, stepperW, 11, String.format(Locale.ROOT, "%.0fm", AutoAttackerConfig.AIM_ASSIST_RANGE.get()), false);

            int y6 = y5 + 14;
            renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, y6, PANEL_WIDTH - 10, 13, "⚙ 打开全屏战术抽屉", 0xFF1D5A96);

            int y7 = y6 + 15;
            graphics.drawString(font, "手持武器将自动切换专属设置", panelX + 6, y7, 0xFF8B949E, false);
        }
    }

    // =========================================================================
    // Tab 1: 测距遥测与校准向导 (多武器态实时遥测)
    // =========================================================================

    private static void renderTelemetryTab(GuiGraphics graphics, Font font, int mouseX, int mouseY, int startY,
                                          Player player, ItemStack weaponStack, ClientEvents.WeaponCategory category) {
        int curY = startY;

        // 1. 目标精简遥测卡片
        LivingEntity target = ClientEvents.getCurrentTarget();
        if (target != null && target.isAlive()) {
            double dist = player.distanceTo(target);
            String name = target.getDisplayName().getString();
            if (name.length() > 10) name = name.substring(0, 9) + "…";

            renderCard(graphics, panelX + 5, curY, PANEL_WIDTH - 10, 24);
            graphics.drawString(font, name, panelX + 9, curY + 3, 0xFFF0F2F5, false);
            graphics.drawString(font, String.format(Locale.ROOT, "%.1fm", dist), panelX + PANEL_WIDTH - 10 - font.width(String.format(Locale.ROOT, "%.1fm", dist)), curY + 3, 0xFF388BFD, false);

            float targetYaw = ClientEvents.getLastPredictedYaw();
            float yawDelta = Mth.wrapDegrees(targetYaw - player.getYRot());
            String sign = yawDelta >= 0 ? "+" : "";

            if (category == ClientEvents.WeaponCategory.GUN) {
                graphics.drawString(font, String.format(Locale.ROOT, "偏角:%s%.1f° | 直瞄高平无下坠", sign, yawDelta), panelX + 9, curY + 13, 0xFF8E95A5, false);
            } else if (category == ClientEvents.WeaponCategory.BOW) {
                AutoBallisticsTracker.BallisticsProfile profile = !weaponStack.isEmpty() ? AutoBallisticsTracker.getProfile(weaponStack) : null;
                float spd = profile != null ? Math.max(profile.speed, 0.25f) : 3.0f;
                double flightTicks = dist / spd;
                graphics.drawString(font, String.format(Locale.ROOT, "飞行:%.1ft | 偏角:%s%.1f°", flightTicks, sign, yawDelta), panelX + 9, curY + 13, 0xFF8E95A5, false);
            } else if (category == ClientEvents.WeaponCategory.MELEE) {
                boolean inReach = dist <= 3.5;
                String reachStr = inReach ? "§a[在挥砍范围内]" : "§c[超出挥砍距离]";
                graphics.drawString(font, String.format(Locale.ROOT, "%s | HP:%.1f", reachStr, target.getHealth()), panelX + 9, curY + 13, 0xFF8E95A5, false);
            } else {
                graphics.drawString(font, String.format(Locale.ROOT, "目标距离:%.1fm | HP:%.1f", dist, target.getHealth()), panelX + 9, curY + 13, 0xFF8E95A5, false);
            }
            curY += 27;
        } else {
            renderCard(graphics, panelX + 5, curY, PANEL_WIDTH - 10, 16);
            graphics.drawCenteredString(font, "对准生物以开启战术遥测", panelX + PANEL_WIDTH / 2, curY + 4, 0xFF656B7A);
            curY += 19;
        }

        if (category == ClientEvents.WeaponCategory.GUN) {
            renderCard(graphics, panelX + 5, curY, PANEL_WIDTH - 10, 36);
            graphics.drawCenteredString(font, "§6[枪械直瞄系统]", panelX + PANEL_WIDTH / 2, curY + 4, 0xFFFFFFFF);
            graphics.drawCenteredString(font, "高平直射·零重力下坠", panelX + PANEL_WIDTH / 2, curY + 15, 0xFF7EE787);
            graphics.drawCenteredString(font, "无需抛物线校准 (直瞄锁定)", panelX + PANEL_WIDTH / 2, curY + 25, 0xFF8E95A5);

            int btnY = panelY + PANEL_HEIGHT - 17;
            long now = System.currentTimeMillis();
            boolean resetPending = (now - resetConfirmTime < 3000L);
            renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, btnY, PANEL_WIDTH - 10, 13, 
                resetPending ? "§e[二次确认] 再次点击以确认重置" : "重置枪械为原厂直瞄", resetPending ? 0xFF8A5D00 : 0xFF1D5A96);
            return;
        }

        if (category == ClientEvents.WeaponCategory.MELEE) {
            renderCard(graphics, panelX + 5, curY, PANEL_WIDTH - 10, 36);
            graphics.drawCenteredString(font, "§c[近战攻防遥测]", panelX + PANEL_WIDTH / 2, curY + 4, 0xFFFFFFFF);
            float cd = player.getAttackStrengthScale(0.0f);
            int pct = (int) (cd * 100);
            graphics.drawCenteredString(font, "出刀蓄力: " + pct + "% " + (pct >= 100 ? "§a(满额出刀)" : "§e(冷却中)"), panelX + PANEL_WIDTH / 2, curY + 15, 0xFFCED3DC);
            graphics.drawCenteredString(font, "确保 100% 满伤害与横扫暴击", panelX + PANEL_WIDTH / 2, curY + 25, 0xFF8E95A5);

            int btnY = panelY + PANEL_HEIGHT - 17;
            long now = System.currentTimeMillis();
            boolean resetPending = (now - resetConfirmTime < 3000L);
            renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, btnY, PANEL_WIDTH - 10, 13, 
                resetPending ? "§e[二次确认] 再次点击以确认重置" : "重置近战锁定参数为默认", resetPending ? 0xFF8A5D00 : 0xFF1D5A96);
            return;
        }

        if (category == ClientEvents.WeaponCategory.OTHER) {
            renderCard(graphics, panelX + 5, curY, PANEL_WIDTH - 10, 36);
            graphics.drawCenteredString(font, "§7[战术遥测待机]", panelX + PANEL_WIDTH / 2, curY + 4, 0xFFFFFFFF);
            graphics.drawCenteredString(font, "手持武器以激活攻防遥测", panelX + PANEL_WIDTH / 2, curY + 15, 0xFFCED3DC);
            graphics.drawCenteredString(font, "支持现代枪械、弓弩与近战", panelX + PANEL_WIDTH / 2, curY + 25, 0xFF8E95A5);

            int btnY = panelY + PANEL_HEIGHT - 17;
            long now = System.currentTimeMillis();
            boolean resetPending = (now - resetConfirmTime < 3000L);
            renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, btnY, PANEL_WIDTH - 10, 13, 
                resetPending ? "§e[二次确认] 再次点击以确认重置" : "重置全部锁定设置为默认", resetPending ? 0xFF8A5D00 : 0xFF1D5A96);
            return;
        }

        // === 弓弩校准向导 ===
        boolean calibrating = BallisticsCalibrator.isCalibrating();
        renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, curY, PANEL_WIDTH - 10, 13, 
            calibrating ? "停止采样与结算" : "开启射击校准向导", calibrating ? 0xFFA83A3A : 0xFF1D5A96);
        curY += 16;

        int sampleCount = BallisticsCalibrator.getSampleCount();
        graphics.drawString(font, "已采样本: " + sampleCount + " 发 (建议3~10发)", panelX + 6, curY, 0xFFCED3DC, false);
        curY += 10;

        if (calibrating) {
            graphics.drawString(font, "自由满蓄射击即可拟合", panelX + 6, curY, 0xFF7EE787, false);
            curY += 10;
        }

        List<BallisticsCalibrator.ShotSample> samples = BallisticsCalibrator.getSamples();
        if (!samples.isEmpty()) {
            BallisticsCalibrator.ShotSample s = samples.get(samples.size() - 1);
            graphics.drawString(font, String.format(Locale.ROOT, "#%d: %.1fm Spd:%.2f G:%.3f", 
                s.sampleId, s.distance, s.solvedSpeed, s.solvedGravity), panelX + 6, curY, 0xFF8E95A5, false);
            curY += 10;
        }

        if (sampleCount >= 2) {
            BallisticsCalibrator.CalibrationResult res = BallisticsCalibrator.computeFinalResult();
            if (res != null) {
                int btnY = panelY + PANEL_HEIGHT - 17;
                renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, btnY, PANEL_WIDTH - 10, 13, 
                    String.format(Locale.ROOT, "应用: Spd %.2f | G %.3f", res.finalSpeed, res.finalGravity), 0xFF238636);
            }
        }
    }

    // =========================================================================
    // Tab 2: 全局与视觉设置 (紧凑滑动开关)
    // =========================================================================

    private static void renderSettingsTab(GuiGraphics graphics, Font font, int mouseX, int mouseY, int startY,
                                          Player player, ItemStack weaponStack, ClientEvents.WeaponCategory category) {
        int curY = startY;

        // 1. 弹道轨迹预测
        boolean traj = AutoAttackerConfig.ENABLE_TRAJECTORY_PREVIEW.get();
        String trajDesc = (category == ClientEvents.WeaponCategory.GUN || category == ClientEvents.WeaponCategory.MELEE) ? "弹道轨迹预测线 §8(弓弩)" : "弹道轨迹预测线";
        renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, curY, trajDesc, traj);
        curY += 12;

        // 2. 距离显示
        boolean dist = AutoAttackerConfig.SHOW_DISTANCE.get();
        renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, curY, "显示目标距离 (Distance)", dist);
        curY += 12;

        // 3. 血条显示
        boolean hp = AutoAttackerConfig.SHOW_HEALTH_BAR.get();
        renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, curY, "显示目标血条 (Health Bar)", hp);
        curY += 12;

        // 4. 截击光圈
        boolean lead = AutoAttackerConfig.ENABLE_LEAD_INDICATOR.get();
        String leadDesc = (category == ClientEvents.WeaponCategory.GUN || category == ClientEvents.WeaponCategory.MELEE) ? "绘制截击预判光圈 §8(弓弩)" : "绘制截击预判光圈";
        renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, curY, leadDesc, lead);
        curY += 12;

        // 5. 仅手持武器显示
        boolean onlyHold = AutoAttackerConfig.HUD_ONLY_WHEN_HOLDING_BOW.get();
        renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, curY, "仅手持武器时显示", onlyHold);
        curY += 12;

        // 6. 枪械空仓自动换弹
        boolean autoReload = AutoAttackerConfig.ENABLE_GUN_AUTO_RELOAD.get();
        String reloadDesc = (category == ClientEvents.WeaponCategory.GUN) ? "枪械空仓自动换弹" : "枪械空仓自动换弹 §8(枪械)";
        renderToggleSwitch(graphics, font, mouseX, mouseY, panelX + 6, curY, reloadDesc, autoReload);
        curY += 14;

        // 分割线
        graphics.fill(panelX + 5, curY, panelX + PANEL_WIDTH - 5, curY + 1, 0x14FFFFFF);
        curY += 5;

        // 6. 重置当前武器基准
        long now = System.currentTimeMillis();
        boolean resetPending = (now - resetConfirmTime < 3000L);
        String resetText = resetPending ? "§e[二次确认] 再次点击以确认重置" :
                           (category == ClientEvents.WeaponCategory.GUN) ? "重置当前枪械为原厂直瞄" :
                           (category == ClientEvents.WeaponCategory.BOW) ? "重置当前武器为原版基准" :
                           (category == ClientEvents.WeaponCategory.MELEE) ? "重置近战参数为默认值" : "重置锁定设置为默认值";
        renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, curY, PANEL_WIDTH - 10, 13, resetText, resetPending ? 0xFF8A5D00 : 0);
        curY += 15;

        // 7. 清空全部已存弹道库
        boolean clearPending = (now - clearAllConfirmTime < 3000L);
        String clearText = clearPending ? "§c§l[二次确认] 再次点击清空全部!" : "清空全部已存弹道库";
        renderModernButton(graphics, font, mouseX, mouseY, panelX + 5, curY, PANEL_WIDTH - 10, 13, clearText, clearPending ? 0xFFA00000 : 0xFF662020);
    }

    // =========================================================================
    // 现代控件渲染库 (Apple / Modern Minimalist Primitives)
    // =========================================================================

    private static void renderCard(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, 0x4D181A22);
        graphics.renderOutline(x, y, w, h, 0x18FFFFFF);
    }

    private static void renderTagButton(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, String text, boolean active) {
        boolean hovered = isControlActive && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = active ? 0xFF1D283A : (hovered ? 0xFF2A2E3A : 0xFF1C1E26);
        int border = active ? 0xFF388BFD : (hovered ? 0x40FFFFFF : 0x1EFFFFFF);
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.renderOutline(x, y, w, h, border);
        graphics.drawCenteredString(font, text, x + w / 2, y + 2, active ? 0xFF79C0FF : (hovered ? 0xFFFFFFFF : 0xFF8E95A5));
    }

    private static void renderTargetPartCapsule(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, AutoAttackerConfig.TargetPart part) {
        boolean hovered = isControlActive && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = hovered ? 0xFF2D3340 : 0xFF1C2028;
        int border = hovered ? 0xFF58A6FF : 0x30FFFFFF;
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.renderOutline(x, y, w, h, border);

        String text;
        int col;
        if (part == AutoAttackerConfig.TargetPart.HEAD) {
            text = "锁定: 头部优先";
            col = 0xFFFF7B72;
        } else if (part == AutoAttackerConfig.TargetPart.TORSO) {
            text = "锁定: 躯干中心";
            col = 0xFF79C0FF;
        } else {
            text = "锁定: 智能自适应";
            col = 0xFFD2A8FF;
        }
        graphics.drawCenteredString(font, text, x + w / 2, y + 2, col);
    }

    private static void renderModernStepper(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, String valText, boolean valHighlight) {
        int btnW = 11;
        int valW = w - btnW * 2 - 2;

        graphics.fill(x, y, x + w, y + h, 0xFF191B22);
        graphics.renderOutline(x, y, w, h, 0x22FFFFFF);

        // 左 [-] 按钮
        boolean hovMinus = isControlActive && mouseX >= x && mouseX < x + btnW && mouseY >= y && mouseY <= y + h;
        graphics.fill(x + 1, y + 1, x + btnW, y + h - 1, hovMinus ? 0xFF313644 : 0xFF20232D);
        graphics.drawCenteredString(font, "-", x + btnW / 2, y + 1, hovMinus ? 0xFFFFFFFF : 0xFFA0A5B2);

        // 中间数值槽
        int valX = x + btnW + 1;
        graphics.fill(valX, y + 1, valX + valW, y + h - 1, 0xFF12141A);
        graphics.drawCenteredString(font, valText, valX + valW / 2, y + 2, valHighlight ? 0xFF58A6FF : 0xFFF0F2F5);

        // 右 [+] 按钮
        int plusX = x + w - btnW;
        boolean hovPlus = isControlActive && mouseX >= plusX && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        graphics.fill(plusX, y + 1, x + w - 1, y + h - 1, hovPlus ? 0xFF313644 : 0xFF20232D);
        graphics.drawCenteredString(font, "+", plusX + btnW / 2, y + 1, hovPlus ? 0xFFFFFFFF : 0xFFA0A5B2);
    }

    private static void renderToggleSwitch(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, String label, boolean checked) {
        boolean hovered = isControlActive && mouseX >= x && mouseX <= panelX + PANEL_WIDTH - 8 && mouseY >= y - 1 && mouseY <= y + 9;
        int trackW = 14;
        int trackH = 8;
        int trackCol = checked ? 0xFF1F6FEB : (hovered ? 0xFF2D313D : 0xFF21242D);
        int borderCol = checked ? 0xFF388BFD : 0x26FFFFFF;

        graphics.fill(x, y, x + trackW, y + trackH, trackCol);
        graphics.renderOutline(x, y, trackW, trackH, borderCol);

        // 滑块
        int thumbX = checked ? (x + 7) : (x + 1);
        int thumbCol = checked ? 0xFFFFFFFF : (hovered ? 0xFFB0B6C2 : 0xFF7D8392);
        graphics.fill(thumbX, y + 1, thumbX + 6, y + trackH - 1, thumbCol);

        // 标签文字
        graphics.drawString(font, label, x + trackW + 5, y, hovered ? 0xFFFFFFFF : 0xFFCED3DC, false);
    }

    private static void renderModernButton(GuiGraphics graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, String text, int borderTint) {
        boolean hovered = isControlActive && mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int bg = hovered ? 0xFF2B2F3C : 0xFF1A1C24;
        int border = hovered ? 0xFF58A6FF : (borderTint != 0 ? borderTint : 0x26FFFFFF);
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.renderOutline(x, y, w, h, border);
        graphics.drawCenteredString(font, text, x + w / 2, y + 3, hovered ? 0xFFFFFFFF : 0xFFD8DCE5);
    }

    // =========================================================================
    // 鼠标点击与滚轮交互处理 (全武器态深度适配)
    // =========================================================================

    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isVisible || !isControlActive) return false;
        if (!isMouseOverPanel(mouseX, mouseY)) return false;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return false;
        ItemStack weaponStack = ClientEvents.getActiveWeapon(player);
        ClientEvents.WeaponCategory category = ClientEvents.getWeaponCategory(weaponStack);

        // 1. 标题栏交互
        if (mouseY >= panelY && mouseY <= panelY + TITLE_BAR_HEIGHT) {
            int btnSize = 10;
            int closeX = panelX + PANEL_WIDTH - 14;
            int configX = panelX + PANEL_WIDTH - 26;
            int btnY = panelY + 3;

            if (mouseX >= closeX && mouseX <= closeX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                toggleControl();
                return true;
            }
            if (mouseX >= configX && mouseX <= configX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                openFullScreenConfigScreen();
                return true;
            }

            isDragging = true;
            dragOffsetX = (int) mouseX - panelX;
            dragOffsetY = (int) mouseY - panelY;
            return true;
        }

        // 2. Tab 分段控制器切换
        int tabY = panelY + TITLE_BAR_HEIGHT + 2;
        if (mouseY >= tabY && mouseY <= tabY + TAB_BAR_HEIGHT) {
            int tabW = (PANEL_WIDTH - 10) / 3;
            int clickedTab = (int) (mouseX - (panelX + 5)) / tabW;
            if (clickedTab >= 0 && clickedTab <= 2) {
                currentTab = clickedTab;
                return true;
            }
        }

        int contentStartY = tabY + TAB_BAR_HEIGHT + 4;

        // 3. Tab 0: 武器参数交互
        if (currentTab == 0) {
            int stepperW = 50;
            int stepperX = panelX + PANEL_WIDTH - 6 - stepperW; // panelX + 90
            int resetBtnX = stepperX - 22;                      // panelX + 68

            if (category == ClientEvents.WeaponCategory.GUN) {
                // 枪械
                int y1 = contentStartY + 16;
                if (mouseY >= y1 - 1 && mouseY <= y1 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    boolean next = !AutoAttackerConfig.ENABLE_GUN_TRIGGERBOT.get();
                    AutoAttackerConfig.ENABLE_GUN_TRIGGERBOT.set(next);
                    setStatus("枪械自动开火: " + (next ? "开启" : "关闭"));
                    return true;
                }

                int y2 = y1 + 13;
                if (mouseY >= y2 - 1 && mouseY <= y2 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    boolean next = !AutoAttackerConfig.ENABLE_ANTI_RECOIL.get();
                    AutoAttackerConfig.ENABLE_ANTI_RECOIL.set(next);
                    setStatus("自动压枪补偿: " + (next ? "开启" : "关闭"));
                    return true;
                }

                int y3 = y2 + 13;
                if (mouseY >= y3 && mouseY <= y3 + 11) {
                    if (mouseX >= resetBtnX && mouseX <= resetBtnX + 20) {
                        AutoAttackerConfig.ANTI_RECOIL_STRENGTH.set(1.0);
                        setStatus("压枪强度重置为 1.00x");
                        return true;
                    }
                    if (mouseX >= stepperX && mouseX <= stepperX + 11) {
                        double next = Math.max(0.10, AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get() - 0.05);
                        AutoAttackerConfig.ANTI_RECOIL_STRENGTH.set(Math.round(next * 100.0) / 100.0);
                        setStatus("压枪强度: " + String.format(Locale.ROOT, "%.2fx", AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get()));
                        return true;
                    }
                    if (mouseX >= stepperX + stepperW - 11 && mouseX <= stepperX + stepperW) {
                        double next = Math.min(2.50, AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get() + 0.05);
                        AutoAttackerConfig.ANTI_RECOIL_STRENGTH.set(Math.round(next * 100.0) / 100.0);
                        setStatus("压枪强度: " + String.format(Locale.ROOT, "%.2fx", AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get()));
                        return true;
                    }
                }

                int y4 = y3 + 13;
                if (mouseY >= y4 - 1 && mouseY <= y4 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    boolean next = !AutoAttackerConfig.ENABLE_ADS_SENSING.get();
                    AutoAttackerConfig.ENABLE_ADS_SENSING.set(next);
                    setStatus("机瞄感知: " + (next ? "开启" : "关闭"));
                    return true;
                }

                int y5 = y4 + 13;
                if (mouseY >= y5 && mouseY <= y5 + 11 && mouseX >= resetBtnX - 10 && mouseX <= stepperX + stepperW) {
                    cycleTargetPart();
                    return true;
                }

                int y6 = y5 + 14;
                if (mouseY >= y6 && mouseY <= y6 + 13 && mouseX >= panelX + 5 && mouseX <= panelX + PANEL_WIDTH - 5) {
                    AutoBallisticsTracker.resetProfile(weaponStack);
                    setStatus("已同步枪械原厂物理参数");
                    return true;
                }

            } else if (category == ClientEvents.WeaponCategory.BOW) {
                // 传统弓弩
                AutoBallisticsTracker.BallisticsProfile profile = AutoBallisticsTracker.getProfile(weaponStack);

                int y1 = contentStartY + 16;
                if (mouseY >= y1 && mouseY <= y1 + 11) {
                    if (mouseX >= resetBtnX && mouseX <= resetBtnX + 20) {
                        profile.speed = 3.0f;
                        AutoBallisticsTracker.markDirtyAndSave();
                        setStatus("初速重置为原版 3.0");
                        return true;
                    }
                    if (mouseX >= stepperX && mouseX <= stepperX + 11) {
                        profile.speed = Math.max(0.5f, profile.speed - 0.1f);
                        AutoBallisticsTracker.markDirtyAndSave();
                        setStatus("初速: " + String.format(Locale.ROOT, "%.2f", profile.speed));
                        return true;
                    }
                    if (mouseX >= stepperX + stepperW - 11 && mouseX <= stepperX + stepperW) {
                        profile.speed = Math.min(15.0f, profile.speed + 0.1f);
                        AutoBallisticsTracker.markDirtyAndSave();
                        setStatus("初速: " + String.format(Locale.ROOT, "%.2f", profile.speed));
                        return true;
                    }
                }

                int y2 = y1 + 13;
                if (mouseY >= y2 && mouseY <= y2 + 11) {
                    if (mouseX >= resetBtnX && mouseX <= resetBtnX + 20) {
                        profile.gravity = (profile.gravity <= 1.0E-5) ? 0.05 : 0.0;
                        AutoBallisticsTracker.markDirtyAndSave();
                        setStatus(profile.gravity <= 1.0E-5 ? "已切换为零重力" : "已恢复标准重力 0.05");
                        return true;
                    }
                    if (mouseX >= stepperX && mouseX <= stepperX + 11) {
                        profile.gravity = Math.max(0.0, profile.gravity - 0.005);
                        AutoBallisticsTracker.markDirtyAndSave();
                        setStatus("重力: " + String.format(Locale.ROOT, "%.3f", profile.gravity));
                        return true;
                    }
                    if (mouseX >= stepperX + stepperW - 11 && mouseX <= stepperX + stepperW) {
                        profile.gravity = Math.min(0.20, profile.gravity + 0.005);
                        AutoBallisticsTracker.markDirtyAndSave();
                        setStatus("重力: " + String.format(Locale.ROOT, "%.3f", profile.gravity));
                        return true;
                    }
                }

                int y3 = y2 + 13;
                if (mouseY >= y3 && mouseY <= y3 + 11) {
                    if (mouseX >= stepperX && mouseX <= stepperX + 11) {
                        profile.minChargeTicks = Math.max(1, profile.minChargeTicks - 1);
                        AutoBallisticsTracker.markDirtyAndSave();
                        setStatus("蓄力: " + profile.minChargeTicks + "t");
                        return true;
                    }
                    if (mouseX >= stepperX + stepperW - 11 && mouseX <= stepperX + stepperW) {
                        profile.minChargeTicks = Math.min(100, profile.minChargeTicks + 1);
                        AutoBallisticsTracker.markDirtyAndSave();
                        setStatus("蓄力: " + profile.minChargeTicks + "t");
                        return true;
                    }
                }

                int y4 = y3 + 13;
                if (mouseY >= y4 - 1 && mouseY <= y4 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    boolean next = !AutoAttackerConfig.ENABLE_AUTO_SHOOT.get();
                    AutoAttackerConfig.ENABLE_AUTO_SHOOT.set(next);
                    setStatus("满蓄力自动放箭: " + (next ? "开启" : "关闭"));
                    return true;
                }

                int y5 = y4 + 13;
                if (mouseY >= y5 - 1 && mouseY <= y5 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    profile.isHoming = !profile.isHoming;
                    AutoBallisticsTracker.markDirtyAndSave();
                    setStatus(profile.isHoming ? "已开启自寻的模式" : "已切换为抛物线预判");
                    return true;
                }

                int y6 = y5 + 14;
                if (mouseY >= y6 && mouseY <= y6 + 13 && mouseX >= panelX + 5 && mouseX <= panelX + PANEL_WIDTH - 5) {
                    TooltipBallisticsExtractor.ExtractedBallistics ext = TooltipBallisticsExtractor.extract(weaponStack, player);
                    if (ext != null && ext.hasAnyData()) {
                        if (ext.speed != null) profile.speed = ext.speed;
                        if (ext.drawTicks != null) profile.minChargeTicks = ext.drawTicks;
                        if (ext.gravity != null) profile.gravity = ext.gravity;
                        if (ext.isHoming != null) profile.isHoming = ext.isHoming;
                        AutoBallisticsTracker.markDirtyAndSave();
                        setStatus("成功从 Tooltip 载入参数！");
                    } else {
                        setStatus("Tooltip 中未匹配到速度数值");
                    }
                    return true;
                }

            } else if (category == ClientEvents.WeaponCategory.MELEE) {
                // 近战
                int y1 = contentStartY + 16;
                if (mouseY >= y1 - 1 && mouseY <= y1 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    boolean next = !AutoAttackerConfig.ENABLE_AUTO_ATTACK.get();
                    AutoAttackerConfig.ENABLE_AUTO_ATTACK.set(next);
                    setStatus("自动近战出刀: " + (next ? "开启" : "关闭"));
                    return true;
                }

                int y2 = y1 + 13;
                if (mouseY >= y2 - 1 && mouseY <= y2 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    boolean next = !AutoAttackerConfig.ENABLE_AIM_ASSIST.get();
                    AutoAttackerConfig.ENABLE_AIM_ASSIST.set(next);
                    setStatus("自瞄吸附: " + (next ? "开启" : "关闭"));
                    return true;
                }

                int y3 = y2 + 13;
                if (mouseY >= y3 && mouseY <= y3 + 11 && mouseX >= resetBtnX - 10 && mouseX <= stepperX + stepperW) {
                    cycleTargetPart();
                    return true;
                }

                int y4 = y3 + 13;
                if (mouseY >= y4 && mouseY <= y4 + 11) {
                    if (mouseX >= resetBtnX && mouseX <= resetBtnX + 20) {
                        AutoAttackerConfig.AIM_ASSIST_RANGE.set(4.0);
                        setStatus("近战锁定范围: 4.0m");
                        return true;
                    }
                    if (mouseX >= stepperX && mouseX <= stepperX + 11) {
                        double next = Math.max(2.0, AutoAttackerConfig.AIM_ASSIST_RANGE.get() - 0.5);
                        AutoAttackerConfig.AIM_ASSIST_RANGE.set(Math.round(next * 10.0) / 10.0);
                        setStatus("锁定范围: " + String.format(Locale.ROOT, "%.1fm", AutoAttackerConfig.AIM_ASSIST_RANGE.get()));
                        return true;
                    }
                    if (mouseX >= stepperX + stepperW - 11 && mouseX <= stepperX + stepperW) {
                        double next = Math.min(30.0, AutoAttackerConfig.AIM_ASSIST_RANGE.get() + 0.5);
                        AutoAttackerConfig.AIM_ASSIST_RANGE.set(Math.round(next * 10.0) / 10.0);
                        setStatus("锁定范围: " + String.format(Locale.ROOT, "%.1fm", AutoAttackerConfig.AIM_ASSIST_RANGE.get()));
                        return true;
                    }
                }

                int y5 = y4 + 13;
                if (mouseY >= y5 && mouseY <= y5 + 11) {
                    if (mouseX >= resetBtnX && mouseX <= resetBtnX + 20) {
                        AutoAttackerConfig.AIM_ASSIST_SPEED.set(0.25);
                        setStatus("吸附速度重置为 0.25");
                        return true;
                    }
                    if (mouseX >= stepperX && mouseX <= stepperX + 11) {
                        double next = Math.max(0.05, AutoAttackerConfig.AIM_ASSIST_SPEED.get() - 0.05);
                        AutoAttackerConfig.AIM_ASSIST_SPEED.set(Math.round(next * 100.0) / 100.0);
                        setStatus("吸附速度: " + String.format(Locale.ROOT, "%.2f", AutoAttackerConfig.AIM_ASSIST_SPEED.get()));
                        return true;
                    }
                    if (mouseX >= stepperX + stepperW - 11 && mouseX <= stepperX + stepperW) {
                        double next = Math.min(1.00, AutoAttackerConfig.AIM_ASSIST_SPEED.get() + 0.05);
                        AutoAttackerConfig.AIM_ASSIST_SPEED.set(Math.round(next * 100.0) / 100.0);
                        setStatus("吸附速度: " + String.format(Locale.ROOT, "%.2f", AutoAttackerConfig.AIM_ASSIST_SPEED.get()));
                        return true;
                    }
                }

                int y6 = y5 + 14;
                if (mouseY >= y6 && mouseY <= y6 + 13 && mouseX >= panelX + 5 && mouseX <= panelX + PANEL_WIDTH - 5) {
                    openFullScreenConfigScreen();
                    return true;
                }

            } else {
                // 常规/空手模式
                int y1 = contentStartY + 16;
                if (mouseY >= y1 - 1 && mouseY <= y1 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    boolean next = !AutoAttackerConfig.ENABLE_MOD.get();
                    AutoAttackerConfig.ENABLE_MOD.set(next);
                    setStatus("模组总开关: " + (next ? "开启" : "关闭"));
                    return true;
                }

                int y2 = y1 + 13;
                if (mouseY >= y2 - 1 && mouseY <= y2 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    boolean next = !AutoAttackerConfig.ENABLE_AUTO_ATTACK.get();
                    AutoAttackerConfig.ENABLE_AUTO_ATTACK.set(next);
                    setStatus("自动近战挥击: " + (next ? "开启" : "关闭"));
                    return true;
                }

                int y3 = y2 + 13;
                if (mouseY >= y3 - 1 && mouseY <= y3 + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                    boolean next = !AutoAttackerConfig.ENABLE_AIM_ASSIST.get();
                    AutoAttackerConfig.ENABLE_AIM_ASSIST.set(next);
                    setStatus("自瞄吸附: " + (next ? "开启" : "关闭"));
                    return true;
                }

                int y4 = y3 + 13;
                if (mouseY >= y4 && mouseY <= y4 + 11 && mouseX >= resetBtnX - 10 && mouseX <= stepperX + stepperW) {
                    cycleTargetPart();
                    return true;
                }

                int y5 = y4 + 13;
                if (mouseY >= y5 && mouseY <= y5 + 11) {
                    if (mouseX >= stepperX && mouseX <= stepperX + 11) {
                        double next = Math.max(2.0, AutoAttackerConfig.AIM_ASSIST_RANGE.get() - 1.0);
                        AutoAttackerConfig.AIM_ASSIST_RANGE.set(Math.round(next * 10.0) / 10.0);
                        setStatus("锁定范围: " + String.format(Locale.ROOT, "%.0fm", AutoAttackerConfig.AIM_ASSIST_RANGE.get()));
                        return true;
                    }
                    if (mouseX >= stepperX + stepperW - 11 && mouseX <= stepperX + stepperW) {
                        double next = Math.min(60.0, AutoAttackerConfig.AIM_ASSIST_RANGE.get() + 1.0);
                        AutoAttackerConfig.AIM_ASSIST_RANGE.set(Math.round(next * 10.0) / 10.0);
                        setStatus("锁定范围: " + String.format(Locale.ROOT, "%.0fm", AutoAttackerConfig.AIM_ASSIST_RANGE.get()));
                        return true;
                    }
                }

                int y6 = y5 + 14;
                if (mouseY >= y6 && mouseY <= y6 + 13 && mouseX >= panelX + 5 && mouseX <= panelX + PANEL_WIDTH - 5) {
                    openFullScreenConfigScreen();
                    return true;
                }
            }
        }

        // 4. Tab 1: 遥测与校准交互
        if (currentTab == 1) {
            int btnY = panelY + PANEL_HEIGHT - 17;

            if (category == ClientEvents.WeaponCategory.GUN) {
                if (mouseY >= btnY && mouseY <= btnY + 13 && mouseX >= panelX + 5 && mouseX <= panelX + PANEL_WIDTH - 5) {
                    long now = System.currentTimeMillis();
                    if (now - resetConfirmTime > 3000L) {
                        resetConfirmTime = now;
                        setStatus("§e请在 3 秒内再次点击以确认重置");
                        return true;
                    }
                    resetConfirmTime = 0L;
                    AutoBallisticsTracker.resetProfile(weaponStack);
                    setStatus("已恢复枪械原厂直瞄基准");
                    return true;
                }
                return false;
            }

            if (category == ClientEvents.WeaponCategory.MELEE) {
                if (mouseY >= btnY && mouseY <= btnY + 13 && mouseX >= panelX + 5 && mouseX <= panelX + PANEL_WIDTH - 5) {
                    long now = System.currentTimeMillis();
                    if (now - resetConfirmTime > 3000L) {
                        resetConfirmTime = now;
                        setStatus("§e请在 3 秒内再次点击以确认重置");
                        return true;
                    }
                    resetConfirmTime = 0L;
                    AutoAttackerConfig.AIM_ASSIST_RANGE.set(4.0);
                    AutoAttackerConfig.AIM_ASSIST_SPEED.set(0.25);
                    setStatus("已恢复近战锁定参数默认值");
                    return true;
                }
                return false;
            }

            if (category == ClientEvents.WeaponCategory.OTHER) {
                if (mouseY >= btnY && mouseY <= btnY + 13 && mouseX >= panelX + 5 && mouseX <= panelX + PANEL_WIDTH - 5) {
                    long now = System.currentTimeMillis();
                    if (now - resetConfirmTime > 3000L) {
                        resetConfirmTime = now;
                        setStatus("§e请在 3 秒内再次点击以确认重置");
                        return true;
                    }
                    resetConfirmTime = 0L;
                    AutoAttackerConfig.AIM_ASSIST_RANGE.set(64.0);
                    AutoAttackerConfig.AIM_ASSIST_SPEED.set(0.15);
                    setStatus("已恢复常规锁定参数默认值");
                    return true;
                }
                return false;
            }

            // 弓弩校准向导
            LivingEntity target = ClientEvents.getCurrentTarget();
            int calibBtnY = contentStartY + ((target != null && target.isAlive()) ? 27 : 19);
            if (mouseY >= calibBtnY && mouseY <= calibBtnY + 13 && mouseX >= panelX + 5 && mouseX <= panelX + PANEL_WIDTH - 5) {
                if (BallisticsCalibrator.isCalibrating()) {
                    BallisticsCalibrator.stopCalibration();
                    setStatus("已停止校准");
                } else {
                    BallisticsCalibrator.startCalibration();
                    setStatus("已启动校准，请射击 3~10 发");
                }
                return true;
            }

            if (BallisticsCalibrator.getSampleCount() >= 2 && !weaponStack.isEmpty()) {
                if (mouseY >= btnY && mouseY <= btnY + 13 && mouseX >= panelX + 5 && mouseX <= panelX + PANEL_WIDTH - 5) {
                    BallisticsCalibrator.CalibrationResult res = BallisticsCalibrator.computeFinalResult();
                    if (res != null) {
                        AutoBallisticsTracker.BallisticsProfile profile = AutoBallisticsTracker.getProfile(weaponStack);
                        profile.speed = res.finalSpeed;
                        profile.gravity = res.finalGravity;
                        AutoBallisticsTracker.markDirtyAndSave();
                        BallisticsCalibrator.stopCalibration();
                        setStatus(String.format(Locale.ROOT, "拟合生效: Spd %.2f, G %.3f", res.finalSpeed, res.finalGravity));
                    }
                    return true;
                }
            }
        }

        // 5. Tab 2: 设置交互
        if (currentTab == 2) {
            int y = contentStartY;
            // 弹道开关
            if (mouseY >= y - 1 && mouseY <= y + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                boolean next = !AutoAttackerConfig.ENABLE_TRAJECTORY_PREVIEW.get();
                AutoAttackerConfig.ENABLE_TRAJECTORY_PREVIEW.set(next);
                setStatus("弹道预测线: " + (next ? "开启" : "关闭"));
                return true;
            }
            // 距离开关
            y += 12;
            if (mouseY >= y - 1 && mouseY <= y + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                boolean next = !AutoAttackerConfig.SHOW_DISTANCE.get();
                AutoAttackerConfig.SHOW_DISTANCE.set(next);
                setStatus("目标距离显示: " + (next ? "开启" : "关闭"));
                return true;
            }
            // 血条开关
            y += 12;
            if (mouseY >= y - 1 && mouseY <= y + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                boolean next = !AutoAttackerConfig.SHOW_HEALTH_BAR.get();
                AutoAttackerConfig.SHOW_HEALTH_BAR.set(next);
                setStatus("目标血条显示: " + (next ? "开启" : "关闭"));
                return true;
            }
            // 截击光圈
            y += 12;
            if (mouseY >= y - 1 && mouseY <= y + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                AutoAttackerConfig.ENABLE_LEAD_INDICATOR.set(!AutoAttackerConfig.ENABLE_LEAD_INDICATOR.get());
                setStatus("截击预判光圈: " + (AutoAttackerConfig.ENABLE_LEAD_INDICATOR.get() ? "开启" : "关闭"));
                return true;
            }
            // 仅手持显示
            y += 12;
            if (mouseY >= y - 1 && mouseY <= y + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                AutoAttackerConfig.HUD_ONLY_WHEN_HOLDING_BOW.set(!AutoAttackerConfig.HUD_ONLY_WHEN_HOLDING_BOW.get());
                setStatus("仅手持显示: " + (AutoAttackerConfig.HUD_ONLY_WHEN_HOLDING_BOW.get() ? "开启" : "关闭"));
                return true;
            }
            // 空仓自动换弹
            y += 12;
            if (mouseY >= y - 1 && mouseY <= y + 9 && mouseX >= panelX + 6 && mouseX <= panelX + PANEL_WIDTH - 6) {
                boolean next = !AutoAttackerConfig.ENABLE_GUN_AUTO_RELOAD.get();
                AutoAttackerConfig.ENABLE_GUN_AUTO_RELOAD.set(next);
                setStatus("空仓自动换弹: " + (next ? "开启" : "关闭"));
                return true;
            }

            // 重置基准
            int y6 = y + 19;
            if (mouseY >= y6 && mouseY <= y6 + 13) {
                long now = System.currentTimeMillis();
                if (now - resetConfirmTime > 3000L) {
                    resetConfirmTime = now;
                    setStatus("§e[二次确认] 请在 3 秒内再次点击确认重置");
                    return true;
                }
                resetConfirmTime = 0L;
                if (category == ClientEvents.WeaponCategory.GUN) {
                    AutoBallisticsTracker.resetProfile(weaponStack);
                    setStatus("已重置枪械为原厂直瞄基准");
                } else if (category == ClientEvents.WeaponCategory.BOW) {
                    AutoBallisticsTracker.resetProfile(weaponStack);
                    setStatus("已重置当前武器为原版基准");
                } else if (category == ClientEvents.WeaponCategory.MELEE) {
                    AutoAttackerConfig.AIM_ASSIST_RANGE.set(4.0);
                    AutoAttackerConfig.AIM_ASSIST_SPEED.set(0.25);
                    setStatus("已重置近战锁定参数为默认");
                } else {
                    AutoAttackerConfig.AIM_ASSIST_RANGE.set(64.0);
                    AutoAttackerConfig.AIM_ASSIST_SPEED.set(0.15);
                    setStatus("已重置核心锁定设置为默认");
                }
                return true;
            }

            // 清空弹道库
            int y7 = y6 + 15;
            if (mouseY >= y7 && mouseY <= y7 + 13) {
                long now = System.currentTimeMillis();
                if (now - clearAllConfirmTime > 3000L) {
                    clearAllConfirmTime = now;
                    setStatus("§c[二次确认] 请在 3 秒内再次点击清空全部弹道库!");
                    return true;
                }
                clearAllConfirmTime = 0L;
                AutoBallisticsTracker.clearAllCache();
                setStatus("全部弹道库已清空并重置");
                return true;
            }
        }

        return true;
    }

    private static void cycleTargetPart() {
        AutoAttackerConfig.TargetPart cur = AutoAttackerConfig.TARGET_PART.get();
        AutoAttackerConfig.TargetPart next = (cur == AutoAttackerConfig.TargetPart.HEAD)
                ? AutoAttackerConfig.TargetPart.TORSO
                : (cur == AutoAttackerConfig.TargetPart.TORSO)
                ? AutoAttackerConfig.TargetPart.ADAPTIVE
                : AutoAttackerConfig.TargetPart.HEAD;
        AutoAttackerConfig.TARGET_PART.set(next);
        setStatus("锁定部位: " + next.getDisplayName());
    }

    /**
     * 滚轮无级调节支持 (在控制态下，悬停即可滑动滚轮调节数值)
     */
    public static boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isVisible || !isControlActive) return false;
        if (!isMouseOverPanel(mouseX, mouseY)) return false;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return false;
        ItemStack weaponStack = ClientEvents.getActiveWeapon(player);
        ClientEvents.WeaponCategory category = ClientEvents.getWeaponCategory(weaponStack);

        if (currentTab == 0) {
            int tabY = panelY + TITLE_BAR_HEIGHT + 2;
            int contentStartY = tabY + TAB_BAR_HEIGHT + 4;

            if (category == ClientEvents.WeaponCategory.GUN) {
                int y3 = contentStartY + 16 + 13 + 13;
                if (mouseY >= y3 - 2 && mouseY <= y3 + 12) {
                    double next = Mth.clamp(AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get() + delta * 0.05, 0.10, 2.50);
                    AutoAttackerConfig.ANTI_RECOIL_STRENGTH.set(Math.round(next * 100.0) / 100.0);
                    setStatus("压枪强度: " + String.format(Locale.ROOT, "%.2fx", AutoAttackerConfig.ANTI_RECOIL_STRENGTH.get()));
                    return true;
                }
            } else if (category == ClientEvents.WeaponCategory.BOW) {
                AutoBallisticsTracker.BallisticsProfile profile = AutoBallisticsTracker.getProfile(weaponStack);
                int y1 = contentStartY + 16;
                int y2 = y1 + 13;
                int y3 = y2 + 13;

                if (mouseY >= y1 - 2 && mouseY <= y1 + 12) {
                    profile.speed = Mth.clamp(profile.speed + (float) delta * 0.1f, 0.5f, 15.0f);
                    AutoBallisticsTracker.markDirtyAndSave();
                    setStatus("初速: " + String.format(Locale.ROOT, "%.2f", profile.speed));
                    return true;
                }
                if (mouseY >= y2 - 2 && mouseY <= y2 + 12) {
                    profile.gravity = Mth.clamp(profile.gravity + delta * 0.005, 0.0, 0.20);
                    AutoBallisticsTracker.markDirtyAndSave();
                    setStatus("重力: " + String.format(Locale.ROOT, "%.3f", profile.gravity));
                    return true;
                }
                if (mouseY >= y3 - 2 && mouseY <= y3 + 12) {
                    profile.minChargeTicks = Mth.clamp(profile.minChargeTicks + (int) Math.signum(delta), 1, 100);
                    AutoBallisticsTracker.markDirtyAndSave();
                    setStatus("蓄力: " + profile.minChargeTicks + "t");
                    return true;
                }
            } else if (category == ClientEvents.WeaponCategory.MELEE) {
                int y4 = contentStartY + 16 + 13 + 13 + 13;
                int y5 = y4 + 13;
                if (mouseY >= y4 - 2 && mouseY <= y4 + 12) {
                    double next = Mth.clamp(AutoAttackerConfig.AIM_ASSIST_RANGE.get() + delta * 0.5, 2.0, 30.0);
                    AutoAttackerConfig.AIM_ASSIST_RANGE.set(Math.round(next * 10.0) / 10.0);
                    setStatus("锁定范围: " + String.format(Locale.ROOT, "%.1fm", AutoAttackerConfig.AIM_ASSIST_RANGE.get()));
                    return true;
                }
                if (mouseY >= y5 - 2 && mouseY <= y5 + 12) {
                    double next = Mth.clamp(AutoAttackerConfig.AIM_ASSIST_SPEED.get() + delta * 0.05, 0.05, 1.00);
                    AutoAttackerConfig.AIM_ASSIST_SPEED.set(Math.round(next * 100.0) / 100.0);
                    setStatus("吸附速度: " + String.format(Locale.ROOT, "%.2f", AutoAttackerConfig.AIM_ASSIST_SPEED.get()));
                    return true;
                }
            } else {
                int y5 = contentStartY + 16 + 13 + 13 + 13 + 13;
                if (mouseY >= y5 - 2 && mouseY <= y5 + 12) {
                    double next = Mth.clamp(AutoAttackerConfig.AIM_ASSIST_RANGE.get() + delta * 1.0, 2.0, 60.0);
                    AutoAttackerConfig.AIM_ASSIST_RANGE.set(Math.round(next * 10.0) / 10.0);
                    setStatus("锁定范围: " + String.format(Locale.ROOT, "%.0fm", AutoAttackerConfig.AIM_ASSIST_RANGE.get()));
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (isDragging) {
            isDragging = false;
            AutoAttackerConfig.OVERLAY_POS_X.set(panelX);
            AutoAttackerConfig.OVERLAY_POS_Y.set(panelY);
            return true;
        }
        return false;
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging && isControlActive) {
            Minecraft mc = Minecraft.getInstance();
            panelX = (int) mouseX - dragOffsetX;
            panelY = (int) mouseY - dragOffsetY;
            ensurePosition(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            return true;
        }
        return false;
    }

    /**
     * 快捷调起侧边战术抽屉配置终端 (一体化非暂停配置中心)
     */
    public static void openFullScreenConfigScreen() {
        Minecraft mc = Minecraft.getInstance();
        toggleControl(); // 关闭悬浮面板控制态
        mc.setScreen(new TacticalConsoleScreen(null));
    }
    // =========================================================================
    // 透明鼠标捕获 Screen
    // =========================================================================

    public static final class TacticalControlScreen extends Screen {
        public TacticalControlScreen() {
            super(Component.literal("Tactical Debug Control"));
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public void removed() {
            AutoBallisticsTracker.saveToDiskImmediate();
            super.removed();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            TacticalDebugPanel.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (TacticalDebugPanel.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (TacticalDebugPanel.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (TacticalDebugPanel.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (TacticalDebugPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || com.xdyyj.autoattacker.ClientModEvents.DEBUG_PANEL_KEY.matches(keyCode, scanCode)) {
                TacticalDebugPanel.toggleControl();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
