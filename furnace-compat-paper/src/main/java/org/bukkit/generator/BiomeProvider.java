package org.bukkit.generator;

import java.util.List;
import org.bukkit.block.Biome;

/**
 * Paper 1.21.1 BiomeProvider(플러그인이 월드 바이옴을 정한다). Furnace 는 아직 플러그인 바이옴 공급자로 월드를 만들지 않으므로
 * World#getBiomeProvider 는 늘 null(Paper 도 만들 때 주지 않았으면 null)이다.
 */
public abstract class BiomeProvider {

    public abstract Biome getBiome(WorldInfo worldInfo, int x, int y, int z);

    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z, BiomeParameterPoint biomeParameterPoint) {
        return getBiome(worldInfo, x, y, z);
    }

    public abstract List<Biome> getBiomes(WorldInfo worldInfo);
}
