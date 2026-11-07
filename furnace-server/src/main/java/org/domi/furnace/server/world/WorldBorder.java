// 바닐라 WorldBorder — 중심·크기(시간에 따른 선형 보간 포함)·경고·피해 설정. 크기 보간은 바닐라 MovingBorderExtent 처럼
// 벽시계(Util.getMillis = nanoTime/1e6)로 진행하고, 매 틱 tick() 에서 보간이 끝나면 정적 크기로 바뀐다.
// 명령/틱은 전역 스레드, 접속 시 InitializeBorder 는 네트워크 스레드가 읽으므로 모든 접근은 이 객체의 모니터로 직렬화한다.
// 차원마다 하나씩 있다(바닐라 Level.worldBorder). 좌표 비가 1 이 아닌 차원(네더)은 중심만 비로 나눈다(Level 생성자의 익명 하위 클래스) —
// 크기는 그대로다. 오버월드가 아닌 차원은 오버월드 보더의 변경을 Delegate 로 따라간다(MinecraftServer.createLevels).
package org.domi.furnace.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class WorldBorder {

    /** 바닐라 MAX_SIZE = 5.999997E7F (float 리터럴이 double 로 확장된 값 59999968). */
    public static final double MAX_SIZE = 5.999997E7F;
    public static final double MAX_CENTER_COORDINATE = 2.9999984E7;
    public static final int DEFAULT_ABSOLUTE_MAX_SIZE = 29999984;
    /** 바닐라 DEFAULT_SETTINGS(보간 목표 0.0 — 보간 시간이 0 이면 쓰이지 않는다). */
    public static final Settings DEFAULT_SETTINGS = new Settings(0.0, 0.0, 0.2, 5.0, 5, 15, MAX_SIZE, 0L, 0.0);

    /** 바닐라 BorderStatus(경계 색: GROWING 초록 / SHRINKING 빨강 / STATIONARY 파랑). */
    public enum Status {
        GROWING(4259712), SHRINKING(16724016), STATIONARY(2138367);

        private final int color;

        Status(int color) { this.color = color; }

        public int color() { return color; }
    }

    /** 바닐라 BorderChangeListener. 피해량·안전 거리 변경은 PlayerList 리스너가 아무것도 보내지 않는다. */
    public interface Listener {
        default void onSizeSet(WorldBorder border, double size) {}

        default void onSizeLerping(WorldBorder border, double fromSize, double toSize, long timeMs) {}

        default void onCenterSet(WorldBorder border, double x, double z) {}

        default void onWarningTimeSet(WorldBorder border, int seconds) {}

        default void onWarningBlocksSet(WorldBorder border, int blocks) {}

        default void onDamagePerBlockSet(WorldBorder border, double damagePerBlock) {}

        default void onDamageSafeZoneSet(WorldBorder border, double safeZone) {}
    }

    /** 바닐라 WorldBorder.Settings — level.dat 에 저장되는 보더 상태. sizeLerpTime 은 남은 보간 시간(ms). */
    public record Settings(double centerX, double centerZ, double damagePerBlock, double safeZone, int warningBlocks,
                           int warningTime, double size, long sizeLerpTime, double sizeLerpTarget) {

        /** 바닐라 Settings.read: 없는 키는 기본값, 중심은 ±29999984 로 clamp. 숫자가 아닌 태그도 기본값으로 본다. */
        public static Settings read(NbtCompound data, Settings def) {
            double centerX = clamp(num(data, "BorderCenterX", def.centerX).doubleValue(), -MAX_CENTER_COORDINATE, MAX_CENTER_COORDINATE);
            double centerZ = clamp(num(data, "BorderCenterZ", def.centerZ).doubleValue(), -MAX_CENTER_COORDINATE, MAX_CENTER_COORDINATE);
            double size = num(data, "BorderSize", def.size).doubleValue();
            long lerpTime = num(data, "BorderSizeLerpTime", def.sizeLerpTime).longValue();
            double lerpTarget = num(data, "BorderSizeLerpTarget", def.sizeLerpTarget).doubleValue();
            double safeZone = num(data, "BorderSafeZone", def.safeZone).doubleValue();
            double damage = num(data, "BorderDamagePerBlock", def.damagePerBlock).doubleValue();
            int warnBlocks = num(data, "BorderWarningBlocks", def.warningBlocks).intValue();
            int warnTime = num(data, "BorderWarningTime", def.warningTime).intValue();
            return new Settings(centerX, centerZ, damage, safeZone, warnBlocks, warnTime, size, lerpTime, lerpTarget);
        }

        /** 바닐라 Settings.write(경고 거리·시간도 double 로 저장). */
        public void write(NbtCompound out) {
            out.put("BorderCenterX", centerX);
            out.put("BorderCenterZ", centerZ);
            out.put("BorderSize", size);
            out.put("BorderSizeLerpTime", sizeLerpTime);
            out.put("BorderSafeZone", safeZone);
            out.put("BorderDamagePerBlock", damagePerBlock);
            out.put("BorderSizeLerpTarget", sizeLerpTarget);
            out.put("BorderWarningBlocks", (double) warningBlocks);
            out.put("BorderWarningTime", (double) warningTime);
        }

        private static Number num(NbtCompound c, String key, Number def) {
            return c.tags().get(key) instanceof Number n ? n : def;
        }
    }

    private final List<Listener> listeners = new ArrayList<>();
    private final LongSupplier millis;
    private double damagePerBlock = 0.2;
    private double damageSafeZone = 5.0;
    private int warningTime = 15;
    private int warningBlocks = 5;
    private double centerX;
    private double centerZ;
    private int absoluteMaxSize = DEFAULT_ABSOLUTE_MAX_SIZE;
    private Extent extent = new StaticExtent(MAX_SIZE);
    /** 차원 좌표 비(DimensionType.coordinateScale). 1 이 아니면 getCenterX/Z 가 저장된 중심을 이 값으로 나눈다. */
    private final double coordinateScale;

    public WorldBorder() {
        this(() -> System.nanoTime() / 1_000_000L);
    }

    /** 시간원(ms)을 주입한다(바닐라 Util.getMillis 대응) — 테스트용. */
    public WorldBorder(LongSupplier millis) {
        this(millis, 1.0);
    }

    /** 그 좌표 비 차원의 보더(바닐라 Level 생성자: coordinateScale != 1 이면 중심을 나누는 하위 클래스). */
    public WorldBorder(double coordinateScale) {
        this(() -> System.nanoTime() / 1_000_000L, coordinateScale);
    }

    public WorldBorder(LongSupplier millis, double coordinateScale) {
        this.millis = millis;
        this.coordinateScale = coordinateScale;
    }

    public double coordinateScale() { return coordinateScale; }

    // ------------------------------------------------------------------ 크기 범위

    private static double clamp(double v, double min, double max) { return v < min ? min : Math.min(v, max); }

    /** 바닐라 Mth.lerp(delta, start, end). */
    private static double lerp(double delta, double start, double end) { return start + delta * (end - start); }

    public synchronized double getMinX() { return clamp(getCenterX() - extent.size() / 2.0, -absoluteMaxSize, absoluteMaxSize); }

    public synchronized double getMinZ() { return clamp(getCenterZ() - extent.size() / 2.0, -absoluteMaxSize, absoluteMaxSize); }

    public synchronized double getMaxX() { return clamp(getCenterX() + extent.size() / 2.0, -absoluteMaxSize, absoluteMaxSize); }

    public synchronized double getMaxZ() { return clamp(getCenterZ() + extent.size() / 2.0, -absoluteMaxSize, absoluteMaxSize); }

    // ------------------------------------------------------------------ 범위 판정

    public boolean isWithinBounds(int blockX, int blockZ) { return isWithinBounds((double) blockX, (double) blockZ); }

    public boolean isWithinBounds(double x, double z) { return isWithinBounds(x, z, 0.0); }

    public synchronized boolean isWithinBounds(double x, double z, double margin) {
        return x >= getMinX() - margin && x < getMaxX() + margin && z >= getMinZ() - margin && z < getMaxZ() + margin;
    }

    /** 청크 전체가 경계 안인지(청크의 두 모서리 블록이 모두 안). */
    public boolean isWithinBounds(ChunkPos chunk) {
        return isWithinBounds((double) (chunk.x() << 4), (double) (chunk.z() << 4))
                && isWithinBounds((double) ((chunk.x() << 4) + 15), (double) ((chunk.z() << 4) + 15));
    }

    /** 바닐라 isWithinBounds(AABB): 최대 모서리에서 1e-5 를 뺀 뒤 두 모서리가 모두 안이어야 한다. */
    public boolean isWithinBounds(double minX, double minZ, double maxX, double maxZ) {
        return isWithinBounds(minX, minZ) && isWithinBounds(maxX - 1.0E-5F, maxZ - 1.0E-5F);
    }

    /** 바닐라 getDistanceToBorder(x, z): 네 변 중 가장 가까운 변까지의 거리(밖이면 음수). */
    public synchronized double getDistanceToBorder(double x, double z) {
        double d0 = z - getMinZ();
        double d1 = getMaxZ() - z;
        double d2 = x - getMinX();
        double d3 = getMaxX() - x;
        double d4 = Math.min(d2, d3);
        d4 = Math.min(d4, d0);
        return Math.min(d4, d1);
    }

    /**
     * 플레이어에게 줄 보더 피해(바닐라 LivingEntity.baseTick): 몸 상자(minX..maxZ)가 경계 밖이고 안전 거리까지 벗어났을 때
     * max(1, floor(-거리 * 블록당 피해)). 피해가 없으면 0. PlayerVitals.tick 이 outside_border 피해로 준다.
     */
    public synchronized float computePlayerDamage(double minX, double minZ, double maxX, double maxZ, double x, double z) {
        if (isWithinBounds(minX, minZ, maxX, maxZ)) return 0f;
        double distance = getDistanceToBorder(x, z) + damageSafeZone;
        if (distance >= 0.0) return 0f;
        if (damagePerBlock <= 0.0) return 0f;
        return (float) Math.max(1, (int) Math.floor(-distance * damagePerBlock));
    }

    // ------------------------------------------------------------------ 중심·크기

    /** 중심 x. 좌표 비가 1 이 아닌 차원은 저장된 값 / 비(바닐라 Level 의 익명 WorldBorder). */
    public synchronized double getCenterX() { return coordinateScale != 1.0 ? centerX / coordinateScale : centerX; }

    public synchronized double getCenterZ() { return coordinateScale != 1.0 ? centerZ / coordinateScale : centerZ; }

    public synchronized void setCenter(double x, double z) {
        this.centerX = x;
        this.centerZ = z;
        for (Listener l : listeners()) l.onCenterSet(this, x, z);
    }

    /** 현재 크기(보간 중이면 지금 시각의 보간값). */
    public synchronized double getSize() { return extent.size(); }

    /** 보간 목표 크기(정적이면 현재 크기). */
    public synchronized double getLerpTarget() { return extent.lerpTarget(); }

    /** 보간 종료까지 남은 ms(정적이면 0). tick 전에는 음수일 수 있다. */
    public synchronized long getLerpRemainingTime() { return extent.lerpRemaining(); }

    public synchronized double getLerpSpeed() { return extent.lerpSpeed(); }

    public synchronized Status getStatus() { return extent.status(); }

    public synchronized void setSize(double size) {
        this.extent = new StaticExtent(size);
        for (Listener l : listeners()) l.onSizeSet(this, size);
    }

    /** 바닐라 lerpSizeBetween: 같은 크기면 정적, 아니면 지금부터 timeMs 동안 보간. 리스너에는 항상 보간 알림을 보낸다. */
    public synchronized void lerpSizeBetween(double from, double to, long timeMs) {
        this.extent = from == to ? new StaticExtent(to) : new MovingExtent(from, to, timeMs);
        for (Listener l : listeners()) l.onSizeLerping(this, from, to, timeMs);
    }

    public synchronized int getAbsoluteMaxSize() { return absoluteMaxSize; }

    public synchronized void setAbsoluteMaxSize(int size) { this.absoluteMaxSize = size; }

    // ------------------------------------------------------------------ 경고·피해

    public synchronized double getDamageSafeZone() { return damageSafeZone; }

    public synchronized void setDamageSafeZone(double distance) {
        this.damageSafeZone = distance;
        for (Listener l : listeners()) l.onDamageSafeZoneSet(this, distance);
    }

    public synchronized double getDamagePerBlock() { return damagePerBlock; }

    public synchronized void setDamagePerBlock(double damage) {
        this.damagePerBlock = damage;
        for (Listener l : listeners()) l.onDamagePerBlockSet(this, damage);
    }

    public synchronized int getWarningTime() { return warningTime; }

    public synchronized void setWarningTime(int seconds) {
        this.warningTime = seconds;
        for (Listener l : listeners()) l.onWarningTimeSet(this, seconds);
    }

    public synchronized int getWarningBlocks() { return warningBlocks; }

    public synchronized void setWarningBlocks(int blocks) {
        this.warningBlocks = blocks;
        for (Listener l : listeners()) l.onWarningBlocksSet(this, blocks);
    }

    // ------------------------------------------------------------------ 틱·저장

    /** 매 틱: 보간이 끝났으면 정적 크기로 바꾼다(바닐라 WorldBorder.tick). */
    public synchronized void tick() { extent = extent.update(); }

    /** 바닐라 Settings(WorldBorder): 중심은 getCenterX/Z(나눈 값). */
    public synchronized Settings createSettings() {
        return new Settings(getCenterX(), getCenterZ(), damagePerBlock, damageSafeZone, warningBlocks, warningTime, getSize(),
                getLerpRemainingTime(), getLerpTarget());
    }

    /** 바닐라 applySettings: 중심·피해·경고를 적용한 뒤 남은 보간 시간이 있으면 보간, 없으면 크기 고정. */
    public synchronized void applySettings(Settings s) {
        setCenter(s.centerX(), s.centerZ());
        setDamagePerBlock(s.damagePerBlock());
        setDamageSafeZone(s.safeZone());
        setWarningBlocks(s.warningBlocks());
        setWarningTime(s.warningTime());
        if (s.sizeLerpTime() > 0L) lerpSizeBetween(s.size(), s.sizeLerpTarget(), s.sizeLerpTime());
        else setSize(s.size());
    }

    public synchronized void addListener(Listener l) { listeners.add(l); }

    /**
     * 바닐라 BorderChangeListener.DelegateBorderChangeListener: 받은 값을 그대로 다른 보더의 setter 로 넘긴다(중심은 나누기 전 값 —
     * 받는 보더가 자기 좌표 비로 나눈다). 오버월드 보더 → 다른 차원 보더 순서로만 잠그므로 교착이 없다.
     */
    public static final class Delegate implements Listener {
        private final WorldBorder target;

        public Delegate(WorldBorder target) { this.target = target; }

        public WorldBorder target() { return target; }

        @Override public void onSizeSet(WorldBorder border, double size) { target.setSize(size); }

        @Override public void onSizeLerping(WorldBorder border, double fromSize, double toSize, long timeMs) { target.lerpSizeBetween(fromSize, toSize, timeMs); }

        @Override public void onCenterSet(WorldBorder border, double x, double z) { target.setCenter(x, z); }

        @Override public void onWarningTimeSet(WorldBorder border, int seconds) { target.setWarningTime(seconds); }

        @Override public void onWarningBlocksSet(WorldBorder border, int blocks) { target.setWarningBlocks(blocks); }

        @Override public void onDamagePerBlockSet(WorldBorder border, double damagePerBlock) { target.setDamagePerBlock(damagePerBlock); }

        @Override public void onDamageSafeZoneSet(WorldBorder border, double safeZone) { target.setDamageSafeZone(safeZone); }
    }

    /**
     * 이 보더(원본)를 따라가는 차원 보더를 붙인다: 지금 상태를 한 번 넘기고(바닐라는 시작 때 리스너를 먼저 단 뒤 applySettings 로 넘긴다 —
     * 결과가 같다) Delegate 를 단다. 두 일을 이 보더의 잠금 안에서 해 그 사이 변경을 놓치지 않는다. 단 Delegate 를 돌려준다(떼어 낼 때).
     */
    public synchronized Delegate attachFollower(WorldBorder follower) {
        follower.setCenter(centerX, centerZ);
        follower.setDamagePerBlock(damagePerBlock);
        follower.setDamageSafeZone(damageSafeZone);
        follower.setWarningBlocks(warningBlocks);
        follower.setWarningTime(warningTime);
        if (extent instanceof MovingExtent m) follower.followLerp(m);
        else follower.setSize(extent.size());
        Delegate d = new Delegate(follower);
        listeners.add(d);
        return d;
    }

    /** 원본의 보간을 시작 시각까지 그대로 따른다(Delegate 가 같은 순간 받은 lerpSizeBetween 과 같다). */
    private synchronized void followLerp(MovingExtent m) {
        this.extent = new MovingExtent(m.from, m.to, m.lerpBegin, (long) m.lerpDuration);
    }

    public synchronized void removeListener(Listener l) { listeners.remove(l); }

    private List<Listener> listeners() { return new ArrayList<>(listeners); }

    // ------------------------------------------------------------------ 크기 상태(바닐라 BorderExtent)

    private interface Extent {
        double size();

        double lerpSpeed();

        long lerpRemaining();

        double lerpTarget();

        Status status();

        Extent update();
    }

    private static final class StaticExtent implements Extent {
        private final double size;

        StaticExtent(double size) { this.size = size; }

        @Override
        public double size() { return size; }

        @Override
        public double lerpSpeed() { return 0.0; }

        @Override
        public long lerpRemaining() { return 0L; }

        @Override
        public double lerpTarget() { return size; }

        @Override
        public Status status() { return Status.STATIONARY; }

        @Override
        public Extent update() { return this; }
    }

    private final class MovingExtent implements Extent {
        private final double from;
        private final double to;
        private final long lerpEnd;
        private final long lerpBegin;
        private final double lerpDuration;

        MovingExtent(double from, double to, long durationMs) {
            this(from, to, millis.getAsLong(), durationMs);
        }

        MovingExtent(double from, double to, long beginMs, long durationMs) {
            this.from = from;
            this.to = to;
            this.lerpDuration = (double) durationMs;
            this.lerpBegin = beginMs;
            this.lerpEnd = lerpBegin + durationMs;
        }

        @Override
        public double size() {
            double progress = (double) (millis.getAsLong() - lerpBegin) / lerpDuration;
            return progress < 1.0 ? lerp(progress, from, to) : to;
        }

        @Override
        public double lerpSpeed() { return Math.abs(from - to) / (double) (lerpEnd - lerpBegin); }

        @Override
        public long lerpRemaining() { return lerpEnd - millis.getAsLong(); }

        @Override
        public double lerpTarget() { return to; }

        @Override
        public Status status() { return to < from ? Status.SHRINKING : Status.GROWING; }

        @Override
        public Extent update() { return lerpRemaining() <= 0L ? new StaticExtent(to) : this; }
    }
}
