package net.minecraft.world.level.border;

import net.minecraft.core.BlockPos;

/**
 * 월드 경계 shim(Mojang 이름, T-204). 서버 경계를 비춘다(읽기는 한 묶음 스냅샷, 바꾸기는 서버 설정자 — 듣는 이 알림은 서버가 보낸다). 경계 틱은 서버가 돌리므로 tick·applySettings·듣는 이는 아직 없다(부르면 H7 덫).
 * 판정은 바닐라 1.21.1 그대로(isWithinBounds(pos) = isWithinBounds(x, z), 최소 이상·최대 미만). 아무 스레드.
 */
class WorldBorder {

    private final org.domi.furnace.api.world.World world;

    /** 호환 모듈 전용 — 그 월드의 경계. */
    public WorldBorder(org.domi.furnace.api.world.World world) { this.world = world; }

    public boolean isWithinBounds(BlockPos pos) { return isWithinBounds((double) pos.getX(), (double) pos.getZ()); }

    private double[] snap() { return org.domi.furnace.compat.nms.NmsChunks.bridge().borderSnapshot(world); }

    private void set(int op, double a, double b, long c) { org.domi.furnace.compat.nms.NmsChunks.bridge().borderSet(world, op, a, b, c); }

    public double getCenterX() { return snap()[0]; }

    public double getCenterZ() { return snap()[1]; }

    public double getSize() { return snap()[2]; }

    public double getMinX() { return snap()[3]; }

    public double getMinZ() { return snap()[4]; }

    public double getMaxX() { return snap()[5]; }

    public double getMaxZ() { return snap()[6]; }

    public double getLerpTarget() { return snap()[7]; }

    public double getLerpSpeed() { return snap()[8]; }

    public long getLerpRemainingTime() { return (long) snap()[15]; }

    public double getDamagePerBlock() { return snap()[9]; }

    public double getDamageSafeZone() { return snap()[10]; }

    public int getWarningBlocks() { return (int) snap()[11]; }

    public int getWarningTime() { return (int) snap()[12]; }

    public int getAbsoluteMaxSize() { return (int) snap()[14]; }

    public BorderStatus getStatus() { return BorderStatus.values()[(int) snap()[13]]; }

    public double getDistanceToBorder(double x, double z) { return org.domi.furnace.compat.nms.NmsChunks.bridge().borderDistance(world, x, z); }

    public boolean isWithinBounds(double x, double z) { return org.domi.furnace.compat.nms.NmsChunks.bridge().withinBorder(world, x, z); }

    /** 바닐라 isWithinBounds(x, z, margin): 변마다 margin 만큼 넓힌 안. */
    public boolean isWithinBounds(double x, double z, double margin) {
        double[] s = snap();
        return x >= s[3] - margin && x < s[5] + margin && z >= s[4] - margin && z < s[6] + margin;
    }

    public void setCenter(double x, double z) { set(0, x, z, 0L); }

    public void setSize(double size) { set(1, size, 0.0, 0L); }

    public void lerpSizeBetween(double from, double to, long timeMs) { set(2, from, to, timeMs); }

    public void setDamagePerBlock(double damage) { set(3, damage, 0.0, 0L); }

    public void setDamageSafeZone(double distance) { set(4, distance, 0.0, 0L); }

    public void setWarningBlocks(int blocks) { set(5, blocks, 0.0, 0L); }

    public void setWarningTime(int seconds) { set(6, seconds, 0.0, 0L); }

    public void setAbsoluteMaxSize(int size) { set(7, size, 0.0, 0L); }
}
