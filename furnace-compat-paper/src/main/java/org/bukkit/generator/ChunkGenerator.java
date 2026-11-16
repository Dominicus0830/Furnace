package org.bukkit.generator;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Paper 1.21.1 ChunkGenerator(플러그인 월드 생성기)의 자리. Furnace 는 아직 Bukkit 생성기로 월드를 만들지 않는다 — 이 클래스를 넓힌 생성기는
 * 불러올 수 있지만 {@code WorldCreator#generator} 로 createWorld 에 넘기면 {@link org.domi.furnace.compat.paper.UnsupportedFeature} 다
 * (Furnace 생성 훅 {@code WorldCreator.Builder#generationHooks} 는 docs/api/worlds.md). 기본값은 Paper 와 같다.
 */
public abstract class ChunkGenerator {

    /** 이 생성기의 기본 바이옴 공급자(null 이면 바닐라 바이옴). */
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) { return null; }

    public boolean canSpawn(World world, int x, int z) { return true; }

    public Location getFixedSpawnLocation(World world, Random random) { return null; }

    public boolean isParallelCapable() { return false; }

    public boolean shouldGenerateNoise() { return false; }

    public boolean shouldGenerateSurface() { return false; }

    public boolean shouldGenerateCaves() { return false; }

    public boolean shouldGenerateDecorations() { return false; }

    public boolean shouldGenerateMobs() { return false; }

    public boolean shouldGenerateStructures() { return false; }
}
