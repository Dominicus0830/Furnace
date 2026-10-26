// 바닐라 DimensionType(1.21.1 내장 overworld / the_nether / the_end)과 차원 레벨 키·저장 폴더. 값은 data/minecraft/dimension_type/*.json 과 같다.
package org.domi.furnace.server.world;

import java.util.List;
import org.domi.furnace.server.world.gen.NoiseWorldSource;

/**
 * @param levelKey       레벨(차원) 키 — Respawn·LoginPlay 의 dimension name
 * @param typeKey        minecraft:dimension_type 레지스트리 키
 * @param fixedTime      고정 시각(없으면 -1) — 클라이언트가 하늘을 그릴 때만 쓴다(서버 시계는 오버월드와 공유)
 * @param coordinateScale DimensionType.coordinateScale — 포털 좌표 비(오버월드 1, 네더 8)
 */
public record DimensionType(String levelKey, String typeKey, int minY, int height, int logicalHeight, boolean hasSkyLight,
                            boolean hasCeiling, boolean ultraWarm, boolean natural, double coordinateScale, long fixedTime,
                            boolean bedWorks, boolean respawnAnchorWorks, boolean piglinSafe, NoiseWorldSource.Dimension gen) {

    public static final DimensionType OVERWORLD = new DimensionType("minecraft:overworld", "minecraft:overworld", -64, 384, 384,
            true, false, false, true, 1.0, -1L, true, false, false, NoiseWorldSource.Dimension.OVERWORLD);
    public static final DimensionType NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", 0, 256, 128,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);
    public static final DimensionType END = new DimensionType("minecraft:the_end", "minecraft:the_end", 0, 256, 256,
            false, false, false, false, 1.0, 6000L, false, false, false, NoiseWorldSource.Dimension.END);

    /** 서버가 띄우는 차원(바닐라 createLevels 순서: 오버월드 → 네더 → 엔드 — LevelStem 레지스트리 순서). */
    public static final List<DimensionType> ALL = List.of(OVERWORLD, NETHER, END);

    public static DimensionType byLevelKey(String key) {
        for (DimensionType t : ALL) if (t.levelKey.equals(key)) return t;
        return null;
    }

    /** 블록 높이 상한(배타). */
    public int maxY() { return minY + height; }

    /** 섹션 수. */
    public int sectionCount() { return height >> 4; }

    /** 저장 폴더(오버월드 = 월드 폴더, 네더 = DIM-1, 엔드 = DIM1). */
    public java.nio.file.Path storageDir(java.nio.file.Path worldDir) { return gen.storageDir(worldDir); }

    /**
     * DimensionType.getTeleportationScale(from, to): from.coordinateScale / to.coordinateScale.
     */
    public static double teleportationScale(DimensionType from, DimensionType to) {
        return from.coordinateScale / to.coordinateScale;
    }
}
