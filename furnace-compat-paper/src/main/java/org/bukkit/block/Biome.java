// 이 파일은 tools/compat/gen-bukkit-values.py 가 만든다 — 손으로 고치지 않는다.
// worldgen/biome 1.21.1 의 64 종 + CUSTOM. 순서는 Bukkit 이 쓰는 옛 번호 순(가정 — 원문 대조 못 함).
package org.bukkit.block;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

public enum Biome implements Keyed {
    OCEAN("ocean"),
    PLAINS("plains"),
    DESERT("desert"),
    WINDSWEPT_HILLS("windswept_hills"),
    FOREST("forest"),
    TAIGA("taiga"),
    SWAMP("swamp"),
    MANGROVE_SWAMP("mangrove_swamp"),
    RIVER("river"),
    NETHER_WASTES("nether_wastes"),
    THE_END("the_end"),
    FROZEN_OCEAN("frozen_ocean"),
    FROZEN_RIVER("frozen_river"),
    SNOWY_PLAINS("snowy_plains"),
    MUSHROOM_FIELDS("mushroom_fields"),
    BEACH("beach"),
    JUNGLE("jungle"),
    SPARSE_JUNGLE("sparse_jungle"),
    DEEP_OCEAN("deep_ocean"),
    STONY_SHORE("stony_shore"),
    SNOWY_BEACH("snowy_beach"),
    BIRCH_FOREST("birch_forest"),
    DARK_FOREST("dark_forest"),
    SNOWY_TAIGA("snowy_taiga"),
    OLD_GROWTH_PINE_TAIGA("old_growth_pine_taiga"),
    WINDSWEPT_FOREST("windswept_forest"),
    SAVANNA("savanna"),
    SAVANNA_PLATEAU("savanna_plateau"),
    BADLANDS("badlands"),
    WOODED_BADLANDS("wooded_badlands"),
    SMALL_END_ISLANDS("small_end_islands"),
    END_MIDLANDS("end_midlands"),
    END_HIGHLANDS("end_highlands"),
    END_BARRENS("end_barrens"),
    WARM_OCEAN("warm_ocean"),
    LUKEWARM_OCEAN("lukewarm_ocean"),
    COLD_OCEAN("cold_ocean"),
    DEEP_LUKEWARM_OCEAN("deep_lukewarm_ocean"),
    DEEP_COLD_OCEAN("deep_cold_ocean"),
    DEEP_FROZEN_OCEAN("deep_frozen_ocean"),
    THE_VOID("the_void"),
    SUNFLOWER_PLAINS("sunflower_plains"),
    WINDSWEPT_GRAVELLY_HILLS("windswept_gravelly_hills"),
    FLOWER_FOREST("flower_forest"),
    ICE_SPIKES("ice_spikes"),
    OLD_GROWTH_BIRCH_FOREST("old_growth_birch_forest"),
    OLD_GROWTH_SPRUCE_TAIGA("old_growth_spruce_taiga"),
    WINDSWEPT_SAVANNA("windswept_savanna"),
    ERODED_BADLANDS("eroded_badlands"),
    BAMBOO_JUNGLE("bamboo_jungle"),
    SOUL_SAND_VALLEY("soul_sand_valley"),
    CRIMSON_FOREST("crimson_forest"),
    WARPED_FOREST("warped_forest"),
    BASALT_DELTAS("basalt_deltas"),
    DRIPSTONE_CAVES("dripstone_caves"),
    LUSH_CAVES("lush_caves"),
    DEEP_DARK("deep_dark"),
    MEADOW("meadow"),
    GROVE("grove"),
    SNOWY_SLOPES("snowy_slopes"),
    FROZEN_PEAKS("frozen_peaks"),
    JAGGED_PEAKS("jagged_peaks"),
    STONY_PEAKS("stony_peaks"),
    CHERRY_GROVE("cherry_grove"),
    /** 플러그인·데이터 팩이 더한 바이옴(Bukkit 과 같다 — Registry.BIOME 에는 없다). */
    CUSTOM("custom");

    private static final Map<NamespacedKey, Biome> BY_KEY = new HashMap<>();

    static {
        for (Biome b : values()) if (b != CUSTOM) BY_KEY.put(b.key, b);
    }

    private final NamespacedKey key;

    Biome(String key) { this.key = NamespacedKey.minecraft(key); }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 호환 모듈 전용 — 바닐라 키로 찾기(없으면 null, CUSTOM 은 키로 찾지 않는다). */
    public static Biome byKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key); }
}
