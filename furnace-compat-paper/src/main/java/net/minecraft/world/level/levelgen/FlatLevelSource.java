package net.minecraft.world.level.levelgen;

import net.minecraft.world.level.chunk.ChunkGenerator;

/** 바닐라 평지 생성기(Mojang 이름, T-203 — 패키지는 바닐라 {@code world.level.levelgen.FlatLevelSource}). */
public class FlatLevelSource extends ChunkGenerator {

    /** 호환 모듈 전용. */
    public FlatLevelSource(org.domi.furnace.api.world.World world) { super(world); }
}
