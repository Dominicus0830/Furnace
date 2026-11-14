// N2b 필드 전수 시험(설계 3.4·R1·R9): 플러그인이 섹션을 통째로 바꾸면(N2b 들여오기) 블록에서 파생된 Furnace 전용 캐시를 하나도 빠짐없이
// 버려야 한다. ChunkColumn·ChunkSection·ChunkLight 의 필드를 리플렉션으로 모두 나열해 아래 분류표와 맞춘다 — 새 필드가 생기면
// 이 시험이 실패하므로, 만든 사람이 "블록에서 파생되는가, 들여오기가 다루는가"를 정해 표에 넣어야 한다. 표에만 있고 사라진 필드도 실패.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.server.light.ChunkLight;
import org.junit.jupiter.api.Test;

class ChunkFieldCensusTest {

    /** 들여오기(N2b-4 importSection)에서의 처리. */
    enum Kind {
        /** 상수·정적 표·모양(높이·섹션 수) — 블록 내용과 무관. */
        FIXED,
        /** 블록·바이옴 내용 자체 — 들여오기가 거울에서 옮겨 쓴다. */
        CONTENT,
        /** Furnace 전용 블록 파생 캐시 — 들여오기가 반드시 버리거나 다시 만든다(빠뜨리면 바닐라와 조용히 다름, R1). */
        FURNACE_CACHE,
        /** 하이트맵 — 바닐라는 원시 쓰기 뒤 낡은 값을 둔다. 고정 하이트맵 모드(설계 3.5)가 다룬다. */
        HEIGHTMAP,
        /** 바닐라에도 있는 파생 상태(빛·하늘빛 원천·블록 엔티티) — 바닐라처럼 플러그인 몫, 들여오기가 고치지 않는다(설계 2장). */
        VANILLA_DERIVED,
        /** 블록에서 파생되지 않는 상태(저장 표시·월드젠 단계·엔티티·POI 원본·보내기 대기 등). */
        UNRELATED,
    }

    private static final Map<String, Kind> COLUMN = new TreeMap<>(Map.ofEntries(
        // 상수·정적 표
        e("SECTION_COUNT", Kind.FIXED), e("MIN_Y", Kind.FIXED), e("MAX_Y", Kind.FIXED), e("HEIGHTMAP_BITS", Kind.FIXED),
        e("DEFAULT_BIOME_DIRECT_BITS", Kind.FIXED), e("UNSET_HEIGHT", Kind.FIXED), e("CAVE_AIR_STATE", Kind.FIXED),
        e("VOID_AIR_STATE", Kind.FIXED), e("HAS_BE", Kind.FIXED), e("TOP_MB_NO_LEAVES", Kind.FIXED), e("TOP_MB", Kind.FIXED),
        e("TOP_OCEAN_FLOOR", Kind.FIXED), e("leavesTable", Kind.FIXED), e("motionBlockingTable", Kind.FIXED),
        e("blocksMotionTable", Kind.FIXED), e("hasFluidTable", Kind.FIXED), e("fluidRegistry", Kind.FIXED),
        e("minY", Kind.FIXED), e("sectionCount", Kind.FIXED), e("heightmapBits", Kind.FIXED),
        // 내용
        e("sections", Kind.CONTENT),
        // 하이트맵(surface·motionBlocking 는 라이브, oceanFloor·motionBlocking 은 칸별 UNSET 지연 — 고정 모드 대상)
        e("surface", Kind.HEIGHTMAP), e("oceanFloor", Kind.HEIGHTMAP), e("motionBlocking", Kind.HEIGHTMAP),
        // 월드젠 하이트맵(얼린 뒤엔 바뀌지 않음 — 바닐라 WG 하이트맵도 생성 뒤 갱신 안 함)
        e("surfaceWg", Kind.UNRELATED), e("wgOceanFloor", Kind.UNRELATED), e("wgHeightmapsFrozen", Kind.UNRELATED),
        // Furnace 전용 캐시 — touch() 가 버리는 것들과 섹션 공유·무작위 틱 마스크
        e("modCount", Kind.FURNACE_CACHE), e("cachedSerializedBody", Kind.FURNACE_CACHE),
        e("inflightSerializedBody", Kind.FURNACE_CACHE), e("cachedMotionBlockingHeightmap", Kind.FURNACE_CACHE),
        e("cachedWorldSurfaceHeightmap", Kind.FURNACE_CACHE), e("cachedSnapshot", Kind.FURNACE_CACHE),
        e("sharedSections", Kind.FURNACE_CACHE), e("randomTickMask", Kind.FURNACE_CACHE),
        // 바닐라에도 있는 파생 상태
        e("blockEntities", Kind.VANILLA_DERIVED), e("light", Kind.VANILLA_DERIVED),
        // 블록과 무관
        e("dirty", Kind.UNRELATED), e("generatedBlockEntities", Kind.UNRELATED), e("postProcessMarks", Kind.UNRELATED),
        e("generatedFluidTicks", Kind.UNRELATED), e("generatedEntities", Kind.UNRELATED), e("freshlyGenerated", Kind.UNRELATED),
        e("newlyCreated", Kind.UNRELATED), e("bodyWaits", Kind.UNRELATED), e("lightWaits", Kind.UNRELATED),
        e("lightWaitTick", Kind.UNRELATED), e("lightPrecomputed", Kind.UNRELATED), e("lightStored", Kind.UNRELATED),
        e("lightStaleSeq", Kind.UNRELATED), e("listenerRegistries", Kind.UNRELATED), e("extraNbt", Kind.UNRELATED),
        e("inhabitedTime", Kind.UNRELATED), e("poiNbt", Kind.UNRELATED), e("poi", Kind.UNRELATED),
        e("structures", Kind.UNRELATED), e("foreignEntities", Kind.UNRELATED), e("entitiesOnDisk", Kind.UNRELATED),
        e("entitiesLoaded", Kind.UNRELATED), e("pendingEntityLoad", Kind.UNRELATED)));

    private static final Map<String, Kind> SECTION = new TreeMap<>(Map.ofEntries(
        e("AIR", Kind.FIXED), e("VOID_AIR", Kind.FIXED), e("CAVE_AIR", Kind.FIXED), e("DEFAULT_DIRECT_BITS", Kind.FIXED),
        e("DEFAULT_BIOME_DIRECT_BITS", Kind.FIXED), e("randomTicking", Kind.FIXED),
        e("blocks", Kind.CONTENT), e("biomes", Kind.CONTENT),
        // 개수·평평한 사본은 블록에서 파생 — 들여오기가 다시 센다(nonAirCount 는 바닐라 개수와 다른 Furnace 정의다, VanillaSectionOracleTest)
        e("nonAirCount", Kind.FURNACE_CACHE), e("tickingCount", Kind.FURNACE_CACHE), e("scratch", Kind.FURNACE_CACHE),
        // 컬럼 소속·빛 작업 얼림 — 섹션을 갈아 끼울 때 attach/detach·ownSection 규칙을 따라야 한다
        e("owner", Kind.FURNACE_CACHE), e("ownerIndex", Kind.FURNACE_CACHE), e("lightFrozen", Kind.FURNACE_CACHE)));

    private static final Map<String, Kind> LIGHT = new TreeMap<>(Map.ofEntries(
        e("MIN_SECTION", Kind.FIXED), e("SECTIONS", Kind.FIXED), e("MIN_Y", Kind.FIXED), e("MAX_Y", Kind.FIXED),
        e("NO_BLOCKER", Kind.FIXED), e("LAYER_BYTES", Kind.FIXED), e("ZERO", Kind.FIXED), e("FULL", Kind.FIXED),
        // 빛 배열·하늘빛 원천은 바닐라 DataLayer·ChunkSkyLightSources 와 같은 파생 상태 — 바닐라처럼 낡은 채로 둔다(D2 추천 A)
        e("sky", Kind.VANILLA_DERIVED), e("block", Kind.VANILLA_DERIVED), e("lowestSource", Kind.VANILLA_DERIVED),
        e("lightEnabled", Kind.UNRELATED),
        // 빛 작업 쓰기 사본 관리(T-064)
        e("base", Kind.UNRELATED), e("ownSky", Kind.UNRELATED), e("ownBlock", Kind.UNRELATED)));

    private static Map.Entry<String, Kind> e(String k, Kind v) { return Map.entry(k, v); }

    @Test
    void chunkColumnFieldsAreClassified() { check(ChunkColumn.class, COLUMN); }

    @Test
    void chunkSectionFieldsAreClassified() { check(ChunkSection.class, SECTION); }

    @Test
    void chunkLightFieldsAreClassified() { check(ChunkLight.class, LIGHT); }

    private static void check(Class<?> c, Map<String, Kind> table) {
        List<String> unknown = new ArrayList<>(), gone = new ArrayList<>();
        Map<String, Field> actual = new TreeMap<>();
        for (Field f : c.getDeclaredFields()) {
            if (f.isSynthetic()) continue;   // $assertionsDisabled·자코코 등
            actual.put(f.getName(), f);
        }
        for (String name : actual.keySet()) if (!table.containsKey(name)) unknown.add(name);
        for (String name : table.keySet()) if (!actual.containsKey(name)) gone.add(name);
        assertTrue(unknown.isEmpty(), c.getSimpleName() + " 에 분류 안 된 필드 " + unknown
            + " — 블록 내용에서 파생되면 N2b 들여오기(importSection)가 버리거나 다시 만들게 하고 ChunkFieldCensusTest 표에 넣을 것");
        assertTrue(gone.isEmpty(), c.getSimpleName() + " 에서 사라진 필드 " + gone + " — 표에서 지울 것");
    }

    @Test
    void cacheListIsNotEmpty() {
        // 분류표가 형식만 남지 않게: 지금 확인된 Furnace 전용 캐시가 표에 있는가
        assertEquals(Kind.FURNACE_CACHE, COLUMN.get("cachedSerializedBody"));
        assertEquals(Kind.FURNACE_CACHE, SECTION.get("tickingCount"));
    }
}
