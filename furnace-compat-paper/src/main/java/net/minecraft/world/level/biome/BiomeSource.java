package net.minecraft.world.level.biome;

/**
 * 바이옴 원천(Mojang 이름 shim, T-211 2단계) — {@code ChunkGenerator#getBiomeSource()}. 값은 그 월드 생성기의 바이옴 원천을 가리키는 손잡이다:
 * 구조물 생성({@code Structure#generate})이 받은 원천이 그 월드 것인지 확인하는 데 쓴다. 바이옴 읽기(getNoiseBiome·possibleBiomes)는 아직 없다.
 */
public abstract class BiomeSource {

    private final org.domi.furnace.api.world.World world;

    protected BiomeSource(org.domi.furnace.api.world.World world) { this.world = world; }

    /** 이 원천을 가진 월드(호환 모듈 전용 — 플러그인이 직접 만든 원천이면 null). */
    public final org.domi.furnace.api.world.World furnaceWorld() { return world; }
}
