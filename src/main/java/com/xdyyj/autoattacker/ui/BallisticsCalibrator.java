package com.xdyyj.autoattacker.ui;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 科学多样本多距离弹道拟合校准器：
 * - 结合“空中瞬时动力学反解 (v0 = vt / 0.99^t)”与“全程位移解析积分解”，彻底消除距离差异导致的测速漂移；
 * - 支持无限次实弹射击采样，采用分箱中位数滤波 (Median Filtering) 消除抛射散布，发数越多精度越高。
 */
public final class BallisticsCalibrator {

    public static final class ShotSample {
        public final int sampleId;
        public final double distance;      // 水平位移 (格)
        public final double flightTicks;   // 飞行时长 (ticks)
        public final float solvedSpeed;    // 该样本解析出的初速 (m/t)
        public final double solvedGravity; // 该样本解析出的重力加速度
        public final boolean isNear;       // 是否近距离样本 (< 20m)

        public ShotSample(int sampleId, double distance, double flightTicks, float solvedSpeed, double solvedGravity) {
            this.sampleId = sampleId;
            this.distance = distance;
            this.flightTicks = flightTicks;
            this.solvedSpeed = solvedSpeed;
            this.solvedGravity = solvedGravity;
            this.isNear = distance < 20.0;
        }
    }

    public static final class CalibrationResult {
        public final float finalSpeed;
        public final double finalGravity;
        public final double confidence;     // 置信度 (0.0 ~ 1.0)
        public final int totalSamples;

        public CalibrationResult(float finalSpeed, double finalGravity, double confidence, int totalSamples) {
            this.finalSpeed = finalSpeed;
            this.finalGravity = finalGravity;
            this.confidence = confidence;
            this.totalSamples = totalSamples;
        }
    }

    private static volatile boolean isCalibrating = false;
    private static final List<ShotSample> SAMPLES = new ArrayList<>();
    private static final Map<Integer, InFlightRecord> IN_FLIGHT = new ConcurrentHashMap<>();
    private static int sampleCounter = 0;

    public static final class InFlightRecord {
        public final int entityId;
        public final Vec3 launchPos;
        public final float pitchDeg;
        public final float yawDeg;
        public final Vec3 initialDeltaMovement;
        public final long startTick;
        public int age = 0;
        public Vec3 lastPos;
        public Vec3 lastVel;
        public final List<Double> inFlightSpeeds = new ArrayList<>();
        public final List<Double> inFlightGravities = new ArrayList<>();

        public InFlightRecord(int entityId, Vec3 launchPos, float pitchDeg, float yawDeg, Vec3 initialDeltaMovement, long startTick) {
            this.entityId = entityId;
            this.launchPos = launchPos;
            this.pitchDeg = pitchDeg;
            this.yawDeg = yawDeg;
            this.initialDeltaMovement = initialDeltaMovement;
            this.startTick = startTick;
            this.lastPos = launchPos;
            this.lastVel = initialDeltaMovement;
        }
    }

    public static boolean isCalibrating() {
        return isCalibrating;
    }

    public static void startCalibration() {
        isCalibrating = true;
        SAMPLES.clear();
        IN_FLIGHT.clear();
        sampleCounter = 0;
    }

    public static void stopCalibration() {
        isCalibrating = false;
        IN_FLIGHT.clear();
    }

    public static List<ShotSample> getSamples() {
        return Collections.unmodifiableList(new ArrayList<>(SAMPLES));
    }

    public static int getSampleCount() {
        return SAMPLES.size();
    }

    public static Map<Integer, InFlightRecord> getInFlightRecords() {
        return IN_FLIGHT;
    }

    /**
     * 记录刚发射出膛的箭矢
     */
    public static void recordLaunch(int entityId, Vec3 eyePos, float pitchDeg, float yawDeg, Vec3 initialDeltaMovement, long tick) {
        if (!isCalibrating) return;
        IN_FLIGHT.put(entityId, new InFlightRecord(entityId, eyePos, pitchDeg, yawDeg, initialDeltaMovement, tick));
    }

    /**
     * 箭矢每 tick 更新，并在真正命中/落地时完成采样计算
     */
    public static void updateInFlight(int entityId, Vec3 currentPos, Vec3 currentVel, boolean isStopped) {
        if (!isCalibrating) return;
        InFlightRecord rec = IN_FLIGHT.get(entityId);
        if (rec == null) return;

        rec.age++;

        // 1. 空中瞬时动力学采样 (只在空中高速飞行阶段记录)
        double currentSpeed = currentVel.length();
        if (!isStopped && currentSpeed > 0.4 && rec.age >= 1 && rec.age <= 30) {
            // MC 离散阻力反解初速: v_0 = v_t / 0.99^t (完全不受飞行距离限制，近远结果绝对一致)
            double backCalculatedV0 = currentSpeed / Math.pow(0.99, rec.age);
            if (backCalculatedV0 >= 0.5 && backCalculatedV0 <= 15.0) {
                rec.inFlightSpeeds.add(backCalculatedV0);
            }

            // 垂直加速度观测: g = vy_{t-1} - vy_{t} / 0.99
            if (rec.lastVel != null && rec.lastVel.length() > 0.4) {
                double gObserved = rec.lastVel.y - (currentVel.y / 0.99);
                if (gObserved >= -0.005 && gObserved <= 0.25) {
                    rec.inFlightGravities.add(Math.max(0.0, gObserved));
                }
            }
        }

        rec.lastPos = currentPos;
        rec.lastVel = currentVel;

        double dx = currentPos.x - rec.launchPos.x;
        double dz = currentPos.z - rec.launchPos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        double dy = currentPos.y - rec.launchPos.y;

        // 2. 判定采样结束条件：真正停滞命中方块/生物，或者飞行超过 100 ticks
        // 彻底移除旧版错误的 (rec.age >= 15) 提前截断！
        if (isStopped || rec.age > 100) {
            IN_FLIGHT.remove(entityId);
            if (rec.age >= 2 && horizDist >= 3.0) {
                processSample(rec, horizDist, dy, rec.age);
            }
        }
    }

    /**
     * 核心样本计算处理
     */
    private static void processSample(InFlightRecord rec, double horizDist, double dy, int t) {
        float solvedSpeed;
        double solvedGravity;

        // 优先采用空中连续采样的中位数初速 (杜绝落地阻挡碰撞对位移积分的吸收误差)
        if (!rec.inFlightSpeeds.isEmpty()) {
            Collections.sort(rec.inFlightSpeeds);
            solvedSpeed = rec.inFlightSpeeds.get(rec.inFlightSpeeds.size() / 2).floatValue();
        } else {
            // 兜底：总位移解析积分解
            double elevRad = Math.toRadians(-rec.pitchDeg);
            double cosElev = Math.max(0.08, Math.cos(elevRad));
            double horizCoeff = (1.0 - Math.pow(0.99, t)) / 0.01;
            double v_h0 = horizDist / Math.max(horizCoeff, 1.0E-5);
            solvedSpeed = (float) (v_h0 / cosElev);
        }

        if (solvedSpeed < 0.5f || solvedSpeed > 15.0f) return;

        // 重力解算
        if (!rec.inFlightGravities.isEmpty()) {
            Collections.sort(rec.inFlightGravities);
            solvedGravity = rec.inFlightGravities.get(rec.inFlightGravities.size() / 2);
        } else {
            double elevRad = Math.toRadians(-rec.pitchDeg);
            double v_y0 = solvedSpeed * Math.sin(elevRad);
            double curVy = v_y0;
            double expectedYNoG = 0.0;
            double gCoeffSum = 0.0;
            double curGCumulative = 0.0;

            for (int step = 0; step < t; step++) {
                expectedYNoG += curVy;
                curVy *= 0.99;
                curGCumulative = (curGCumulative + 1.0) * 0.99;
                gCoeffSum += curGCumulative;
            }

            solvedGravity = (expectedYNoG - dy) / Math.max(gCoeffSum, 1.0E-5);
        }

        if (solvedGravity < 0.005) solvedGravity = 0.0;
        solvedGravity = Mth.clamp(solvedGravity, 0.0, 0.20);

        sampleCounter++;
        ShotSample sample = new ShotSample(sampleCounter, horizDist, t, solvedSpeed, solvedGravity);
        SAMPLES.add(sample);
    }

    /**
     * 结算最终拟合结果 (多样本中位数滤波，支持 5~100+ 发超级抗噪拟合)
     */
    public static CalibrationResult computeFinalResult() {
        if (SAMPLES.isEmpty()) return null;

        List<Float> speeds = new ArrayList<>();
        List<Double> gravities = new ArrayList<>();
        boolean hasNear = false;
        boolean hasFar = false;

        for (ShotSample s : SAMPLES) {
            speeds.add(s.solvedSpeed);
            gravities.add(s.solvedGravity);
            if (s.isNear) hasNear = true;
            else hasFar = true;
        }

        Collections.sort(speeds);
        Collections.sort(gravities);

        float medianSpeed = speeds.get(speeds.size() / 2);
        double medianGravity = gravities.get(gravities.size() / 2);

        // 若重力极微小 (< 0.005)，判定为物理零重力武器 (如百中弓、水晶弓)
        if (medianGravity < 0.005) {
            medianGravity = 0.0;
        }

        // 置信度计算：发数越多，置信度越高；多发抗噪能力呈对数级增加
        int count = SAMPLES.size();
        double countFactor = 1.0 - Math.exp(-count / 5.0); // 3发~45%, 10发~86%, 20发~98%
        double confidence = Mth.clamp(0.50 + countFactor * 0.40 + (hasNear && hasFar ? 0.10 : 0.0), 0.50, 0.999);

        return new CalibrationResult(medianSpeed, medianGravity, confidence, count);
    }
}
