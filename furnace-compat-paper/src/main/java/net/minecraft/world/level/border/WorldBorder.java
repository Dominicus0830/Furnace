package net.minecraft.world.level.border;

import net.minecraft.core.BlockPos;

/**
 * 월드 경계 shim(Mojang 이름, T-204). 서버 경계를 비추는 판정만 있다 — 크기·중심 바꾸기와 듣는 이는 아직 없다(부르면 H7 덫).
 * 판정은 바닐라 1.21.1 그대로(isWithinBounds(pos) = isWithinBounds(x, z), 최소 이상·최대 미만). 아무 스레드.
 */
public class WorldBorder {

    private final org.domi.furnace.api.world.World world;

    /** 호환 모듈 전용 — 그 월드의 경계. */
    public WorldBorder(org.domi.furnace.api.world.World world) { this.world = world; }

    public boolean isWithinBounds(BlockPos pos) { return isWithinBounds((double) pos.getX(), (double) pos.getZ()); }

    public boolean isWithinBounds(double x, double z) { return org.domi.furnace.compat.nms.NmsChunks.bridge().withinBorder(world, x, z); }
}
