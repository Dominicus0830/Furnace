package org.bukkit.generator;

import java.util.Random;
import org.bukkit.Chunk;
import org.bukkit.World;

/**
 * Paper 1.21.1 BlockPopulator. {@link #populate(WorldInfo, Random, int, int, LimitedRegion)} 는 그 청크의 바닐라 장식 뒤 생성 스레드에서 불린다
 * (월드의 {@code getPopulators()} 순서, 모든 월드). 옛 {@link #populate(World, Random, Chunk)} 는 Paper 에선 청크가 처음 올라갈 때 불리지만
 * Furnace 는 아직 부르지 않는다(T-218).
 */
public abstract class BlockPopulator {

    @Deprecated(since = "1.17.1")
    public void populate(World world, Random random, Chunk source) {}

    public void populate(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, LimitedRegion limitedRegion) {}
}
