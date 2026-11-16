package org.bukkit.generator;

import java.util.UUID;
import org.bukkit.World;

/** Paper 1.21.1 WorldInfo(월드 생성 중에도 쓸 수 있는 월드 정보). World 가 이것을 넓힌다. */
public interface WorldInfo {

    String getName();

    UUID getUID();

    World.Environment getEnvironment();

    long getSeed();

    int getMinHeight();

    int getMaxHeight();

    /**
     * Paper 1.21.1(Expose-vanilla-BiomeProvider-from-WorldInfo): 이 월드의 바닐라 바이옴 공급자. Furnace 바이옴 소스를 Bukkit BiomeProvider 로
     * 감싸는 것이 아직 없어 UnsupportedFeature.
     */
    default BiomeProvider vanillaBiomeProvider() {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("WorldInfo#vanillaBiomeProvider", "there is no way yet to wrap a Furnace biome source as a BiomeProvider");
    }
}
