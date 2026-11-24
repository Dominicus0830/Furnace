package net.minecraft.world.level.levelgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGenerator;

/** 바닐라 노이즈 생성기(Mojang 이름, T-203). */
class NoiseBasedChunkGenerator {

    /**
     * 바닐라 노이즈 설정 칸. FAWE {@code PaperweightRegen} 이 정적 초기화에서 이름으로 찾는다(없으면 그 클래스가 올라오지 않는다). 노이즈 설정 값은
     * 아직 비추지 않아 null 이다.
     */
    private final Holder<Object> settings = null;

    /** 호환 모듈 전용. */
    public NoiseBasedChunkGenerator(org.domi.furnace.api.world.World world) { super(world); }
}
