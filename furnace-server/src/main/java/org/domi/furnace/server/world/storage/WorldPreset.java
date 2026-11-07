// server.properties level-type → 바닐라 월드 프리셋(WorldPresets). 새 월드를 만들 때만 쓰인다.
package org.domi.furnace.server.world.storage;

import java.util.Map;

public enum WorldPreset {
    NORMAL("minecraft:normal", "minecraft:overworld"),
    FLAT("minecraft:flat", null),
    LARGE_BIOMES("minecraft:large_biomes", "minecraft:large_biomes"),
    AMPLIFIED("minecraft:amplified", "minecraft:amplified"),
    /** 오버월드 노이즈 설정 + FixedBiomeSource(plains) — 바이옴 원천이 다르다(LevelData.useSingleBiomeOverworld). */
    SINGLE_BIOME_SURFACE("minecraft:single_biome_surface", null),
    /** 모든 블록 상태 격자(DebugWorldSource) — LevelData.useDebugOverworld. */
    DEBUG_ALL_BLOCK_STATES("minecraft:debug_all_block_states", null);

    /** 프리셋 id. */
    public final String id;
    /** 오버월드 노이즈 생성기의 noise_settings(노이즈 생성기가 아니면 null). */
    public final String noiseSettings;

    WorldPreset(String id, String noiseSettings) {
        this.id = id;
        this.noiseSettings = noiseSettings;
    }

    /** 바닐라 DedicatedServerProperties.WorldDimensionData.LEGACY_PRESET_NAMES — id 로 읽히지 않는 문자열에만 쓰인다. */
    private static final Map<String, WorldPreset> LEGACY = Map.of("default", NORMAL, "largebiomes", LARGE_BIOMES);

    /**
     * 바닐라 WorldDimensionData.create: ResourceLocation.tryParse(levelType) 로 프리셋을 찾고, id 로 못 읽으면 옛 이름표,
     * 그래도 없으면 null(호출자가 "Failed to parse level-type" 경고 뒤 normal). levelType 은 이미 소문자다.
     * "largebiomes"·"default" 는 올바른 id(minecraft:largebiomes)로 읽혀 옛 이름표까지 가지 않는다 — 바닐라와 같다.
     */
    public static WorldPreset resolve(String levelType) {
        String id = normalizeId(levelType);
        if (id == null) return LEGACY.get(levelType);
        for (WorldPreset p : values()) if (p.id.equals(id)) return p;
        return null;
    }

    /** ResourceLocation.tryParse: 네임스페이스 생략은 minecraft, 허용 문자 밖이면 null. */
    static String normalizeId(String s) {
        int colon = s.indexOf(':');
        String ns = colon >= 0 ? s.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? s.substring(colon + 1) : s;
        if (ns.isEmpty()) ns = "minecraft";
        for (int i = 0; i < ns.length(); i++) {
            char c = ns.charAt(i);
            if (!(c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.')) return null;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!(c == '_' || c == '-' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '/' || c == '.')) return null;
        }
        return ns + ":" + path;
    }
}
