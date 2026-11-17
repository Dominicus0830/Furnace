package org.bukkit.generator;

import java.util.List;
import org.bukkit.block.Biome;

/**
 * Paper 1.21.1 BiomeProvider(플러그인이 월드 바이옴을 정한다). {@code WorldCreator#biomeProvider} 로 만든 월드(T-210)와 FAWE {@code //regen <바이옴>}
 * 의 임시 월드(T-203)가 쓴다. World#getBiomeProvider 는 만들 때 준 공급자(주지 않았으면 null — Paper 와 같다).
 */
public abstract class BiomeProvider {

    public abstract Biome getBiome(WorldInfo worldInfo, int x, int y, int z);

    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z, BiomeParameterPoint biomeParameterPoint) {
        return getBiome(worldInfo, x, y, z);
    }

    public abstract List<Biome> getBiomes(WorldInfo worldInfo);
}
