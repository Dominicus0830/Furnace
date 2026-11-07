// 바닐라 DimensionType(1.21.1 내장 overworld / the_nether / the_end)과 차원 레벨 키·저장 폴더. 값은 data/minecraft/dimension_type/*.json 과 같다.
package org.domi.furnace.server.world;

import java.util.List;
import org.domi.furnace.server.world.gen.NoiseWorldSource;

/**
 * @param levelKey       레벨(차원) 키 — Respawn·LoginPlay 의 dimension name
 * @param typeKey        minecraft:dimension_type 레지스트리 키
 * @param fixedTime      고정 시각(없으면 -1) — 클라이언트가 하늘을 그릴 때만 쓴다(서버 시계는 오버월드와 공유)
 * @param coordinateScale DimensionType.coordinateScale — 포털 좌표 비(오버월드 1, 네더 8)
 * @param gen            생성기 계열 — 저장 폴더(오버월드 = 월드 폴더, 네더 DIM-1, 엔드 DIM1)와 노이즈 생성기의 원래 높이
 * @param infiniburn     영원히 타는 블록 태그(# 없이)
 * @param monsterSpawnLightMin monster_spawn_light_level 균등 범위의 아래(같으면 고정값 — 난수를 쓰지 않는다)
 */
public record DimensionType(String levelKey, String typeKey, int minY, int height, int logicalHeight, boolean hasSkyLight,
                            boolean hasCeiling, boolean ultraWarm, boolean natural, double coordinateScale, long fixedTime,
                            boolean bedWorks, boolean respawnAnchorWorks, boolean piglinSafe, NoiseWorldSource.Dimension gen,
                            String infiniburn, boolean hasRaids, int monsterSpawnLightMin, int monsterSpawnLightMax,
                            int monsterSpawnBlockLightLimit) {

    /** 바닐라 세 타입의 나머지 값은 typeKey 로 채운다(네더: 습격 없음·몬스터 빛 7 고정·블록 빛 15, 그 밖: 습격·0~7·0). */
    public DimensionType(String levelKey, String typeKey, int minY, int height, int logicalHeight, boolean hasSkyLight,
                         boolean hasCeiling, boolean ultraWarm, boolean natural, double coordinateScale, long fixedTime,
                         boolean bedWorks, boolean respawnAnchorWorks, boolean piglinSafe, NoiseWorldSource.Dimension gen) {
        this(levelKey, typeKey, minY, height, logicalHeight, hasSkyLight, hasCeiling, ultraWarm, natural, coordinateScale, fixedTime,
                bedWorks, respawnAnchorWorks, piglinSafe, gen,
                switch (typeKey) {
                    case "minecraft:the_nether" -> "minecraft:infiniburn_nether";
                    case "minecraft:the_end" -> "minecraft:infiniburn_end";
                    default -> "minecraft:infiniburn_overworld";
                },
                !"minecraft:the_nether".equals(typeKey),
                "minecraft:the_nether".equals(typeKey) ? 7 : 0, 7,
                "minecraft:the_nether".equals(typeKey) ? 15 : 0);
    }

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

    /** 바닐라 minecraft:overworld_caves(오버월드 + 천장). 레벨 키는 쓰는 월드가 정한다. */
    private static final DimensionType OVERWORLD_CAVES = new DimensionType("minecraft:overworld", "minecraft:overworld_caves", -64, 384, 384,
            true, true, false, true, 1.0, -1L, true, false, false, NoiseWorldSource.Dimension.OVERWORLD);

    /** 바닐라 차원 타입 키(minecraft:overworld · the_nether · the_end · overworld_caves)의 값, 모르면 null. */
    public static DimensionType vanillaType(String typeKey) {
        for (DimensionType t : ALL) if (t.typeKey.equals(typeKey)) return t;
        return OVERWORLD_CAVES.typeKey.equals(typeKey) ? OVERWORLD_CAVES : null;
    }

    /** 같은 타입 값을 다른 레벨 키·생성기 계열로(추가 월드 — 바닐라 LevelStem 이 차원 타입을 가리키는 것과 같다). */
    public DimensionType forLevel(String levelKey, NoiseWorldSource.Dimension gen) {
        return new DimensionType(levelKey, typeKey, minY, height, logicalHeight, hasSkyLight, hasCeiling, ultraWarm, natural, coordinateScale,
                fixedTime, bedWorks, respawnAnchorWorks, piglinSafe, gen, infiniburn, hasRaids, monsterSpawnLightMin, monsterSpawnLightMax,
                monsterSpawnBlockLightLimit);
    }

    /** 플러그인이 등록한 차원 타입 정의(W2)로 서버 값을 만든다. */
    public static DimensionType fromDefinition(String levelKey, String typeKey, org.domi.furnace.api.worldgen.DimensionTypeDefinition d,
                                               NoiseWorldSource.Dimension gen) {
        return new DimensionType(levelKey, typeKey, d.minY(), d.height(), d.logicalHeight(), d.hasSkyLight(), d.hasCeiling(), d.ultraWarm(),
                d.natural(), d.coordinateScale(), d.fixedTime().orElse(-1L), d.bedWorks(), d.respawnAnchorWorks(), d.piglinSafe(), gen,
                d.infiniburn(), d.hasRaids(), d.monsterSpawnLightMin(), d.monsterSpawnLightMax(), d.monsterSpawnBlockLightLimit());
    }

    /** 습격 파일 이름(바닐라 Raids.getFileId: 엔드 타입이면 raids_end). */
    public String raidsFileId() { return "minecraft:the_end".equals(typeKey) ? "raids_end" : "raids"; }

    /** DimensionType.monsterSpawnLightTest().sample(random): 범위가 한 값이면 난수를 쓰지 않는다(ConstantInt), 아니면 UniformInt. */
    public int sampleMonsterSpawnLight(java.util.function.IntUnaryOperator nextInt) {
        if (monsterSpawnLightMin == monsterSpawnLightMax) return monsterSpawnLightMin;
        return monsterSpawnLightMin + nextInt.applyAsInt(monsterSpawnLightMax - monsterSpawnLightMin + 1);
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
