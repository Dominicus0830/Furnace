package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.chunk.ChunkGenerator;

/** 바닐라 디버그 생성기(모든 블록 상태 격자, Mojang 이름, T-203). */
public class DebugLevelSource extends ChunkGenerator {

    /** 호환 모듈 전용. */
    public DebugLevelSource(org.domi.furnace.api.world.World world) { super(world); }
}
