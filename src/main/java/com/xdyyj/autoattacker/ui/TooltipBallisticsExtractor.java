package com.xdyyj.autoattacker.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能 Tooltip 与属性反演解析器：
 * 自动从武器悬停信息 (如 Arch Bows "1.8 Projectile Speed", "0.75 Draw Time") 中提取物理初速与蓄力时间，
 * 实现零试错成本的高精度通用兼容。
 */
public final class TooltipBallisticsExtractor {

    public static final class ExtractedBallistics {
        public final Float speed;          // 提取出的物理初速 (如 1.8f)
        public final Integer drawTicks;    // 提取出的满蓄力 tick (如 15)
        public final Double gravity;       // 提取出的重力 (若明确标注为零重力/能量箭)
        public final Boolean isHoming;     // 提取出的是否自追踪
        public final String rawSpeedInfo;  // 原始匹配到的速度字符串
        public final String rawDrawInfo;   // 原始匹配到的蓄力字符串

        public ExtractedBallistics(Float speed, Integer drawTicks, Double gravity, Boolean isHoming, String rawSpeedInfo, String rawDrawInfo) {
            this.speed = speed;
            this.drawTicks = drawTicks;
            this.gravity = gravity;
            this.isHoming = isHoming;
            this.rawSpeedInfo = rawSpeedInfo;
            this.rawDrawInfo = rawDrawInfo;
        }

        public boolean hasAnyData() {
            return speed != null || drawTicks != null || gravity != null || isHoming != null;
        }
    }

    // 速度匹配正则：兼容 "1.8 Projectile Speed", "Speed: 2.5", "Arrow Velocity: 1.5x", "3.0 速度" 等
    private static final Pattern SPEED_PATTERN_1 = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:Projectile Speed|Arrow Speed|Velocity|射击速度|弹射物速度|箭矢初速|初速)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPEED_PATTERN_2 = Pattern.compile("(?:Projectile Speed|Arrow Speed|Velocity|初速|速度)\\s*[:：=]\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);

    // 蓄力时间匹配正则：兼容 "0.75 Draw Time", "Draw Time: 1.2s", "蓄力时间 0.8秒", "15 Ticks Draw" 等
    private static final Pattern DRAW_PATTERN_1 = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*(?:Draw Time|Charge Time|Pull Time|拉弓耗时|蓄力耗时|蓄力时间)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DRAW_PATTERN_2 = Pattern.compile("(?:Draw Time|Charge Time|Pull Time|蓄力时间|拉弓时间)\\s*[:：=]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:s|秒|t|ticks)?", Pattern.CASE_INSENSITIVE);

    /**
     * 解析指定物品的 Tooltip 文本并提取弹道物理信息
     */
    public static ExtractedBallistics extract(ItemStack stack, Player player) {
        if (stack == null || stack.isEmpty()) return null;

        // 优先从枪械适配器提取高精度物理解算参数 (支持 TACZ 原厂反射)
        if (com.xdyyj.autoattacker.weapon.FirearmAdapter.isGun(stack)) {
            com.xdyyj.autoattacker.weapon.FirearmAdapter.GunStatus gun = 
                com.xdyyj.autoattacker.weapon.FirearmAdapter.getGunStatus(stack);
            String rawSpeed = String.format(Locale.ROOT, "%.0f m/s (%.2f 格/刻)", gun.bulletSpeedMs, gun.bulletSpeed);
            String ammoText = (gun.currentAmmo >= 0) ? (gun.currentAmmo + "/" + (gun.maxAmmo > 0 ? gun.maxAmmo : "∞")) : "装填完毕";
            String rawDraw = gun.rpm + " RPM (" + gun.typeName + " | " + ammoText + ")";
            return new ExtractedBallistics(gun.bulletSpeed, 1, gun.bulletGravity, false, rawSpeed, rawDraw);
        }

        Float speed = null;
        Integer drawTicks = null;
        Double gravity = null;
        Boolean isHoming = null;
        String rawSpeedInfo = null;
        String rawDrawInfo = null;

        try {
            Minecraft mc = Minecraft.getInstance();
            List<Component> tooltipLines = stack.getTooltipLines(player, mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL);

            for (Component lineComp : tooltipLines) {
                String line = lineComp.getString().trim();
                if (line.isEmpty()) continue;
                String lower = line.toLowerCase(Locale.ROOT);

                // 1. 速度提取
                if (speed == null) {
                    Matcher m1 = SPEED_PATTERN_1.matcher(line);
                    if (m1.find()) {
                        speed = Float.parseFloat(m1.group(1));
                        rawSpeedInfo = m1.group(0);
                    } else {
                        Matcher m2 = SPEED_PATTERN_2.matcher(line);
                        if (m2.find()) {
                            speed = Float.parseFloat(m2.group(1));
                            rawSpeedInfo = m2.group(0);
                        }
                    }
                }

                // 2. 蓄力时间提取
                if (drawTicks == null) {
                    Matcher dm1 = DRAW_PATTERN_1.matcher(line);
                    if (dm1.find()) {
                        float val = Float.parseFloat(dm1.group(1));
                        // 若数值小于等于 5.0，通常是以“秒(s)”为单位 (如 0.75 Draw Time -> 15 ticks)；若大于 5 则直接是 ticks (如 15 Draw Time)
                        drawTicks = (val <= 5.0f) ? Math.max(1, Math.round(val * 20.0f)) : Math.max(1, Math.round(val));
                        rawDrawInfo = dm1.group(0);
                    } else {
                        Matcher dm2 = DRAW_PATTERN_2.matcher(line);
                        if (dm2.find()) {
                            float val = Float.parseFloat(dm2.group(1));
                            drawTicks = (val <= 5.0f) ? Math.max(1, Math.round(val * 20.0f)) : Math.max(1, Math.round(val));
                            rawDrawInfo = dm2.group(0);
                        }
                    }
                }

                // 3. 零重力检测
                if (gravity == null) {
                    if (lower.contains("zero gravity") || lower.contains("no gravity") || lower.contains("零重力") || lower.contains("无下坠") || lower.contains("能量箭")) {
                        gravity = 0.0;
                    }
                }

                // 4. 自追踪导引检测
                if (isHoming == null) {
                    if (lower.contains("homing") || lower.contains("tracking") || lower.contains("auto-aim") || lower.contains("自导引") || lower.contains("追踪箭")) {
                        isHoming = true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        ExtractedBallistics result = new ExtractedBallistics(speed, drawTicks, gravity, isHoming, rawSpeedInfo, rawDrawInfo);
        return result.hasAnyData() ? result : null;
    }
}
