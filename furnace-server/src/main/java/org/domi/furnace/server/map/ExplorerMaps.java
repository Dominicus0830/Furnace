// 탐험 지도(제도사 거래의 바다·삼림 탐험 지도, 시련 지도 / 전리품 explore_map) 만들기 — 바닐라 VillagerTrades.TreasureMapForEmeralds.getOffer 의 지도 부분:
// level.findNearestMapStructure → MapItem.create → renderBiomePreviewMap → addTargetDecoration("+") → item_name.
package org.domi.furnace.server.map;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;

public final class ExplorerMaps {
    private ExplorerMaps() { }

    /** 서버 쪽 지원(구조물 찾기·바이옴·차원) — 월드 생성기가 있을 때만 붙는다. */
    public interface Backend {
        /**
         * ServerLevel.findNearestMapStructure(tag, pos, radius, skipKnownStructures=true) 의 대응: 가장 가까운 구조물의 {x, z}(없으면 null).
         * TODO(지도, A06-06): skipKnownStructures(이미 지도가 가리킨 구조물 건너뛰기)는 구조물 시작점의 references 를 청크에 저장하지 않아 못 한다.
         */
        int[] findNearestMapStructure(String structureTag, int x, int y, int z, int searchRadius);

        /**
         * findNearestMapStructure 의 {x, y, z}(StructurePlacement.getLocatePos — y 는 locate_offset 포함, 대개 0). skipKnownStructures=false 쪽.
         * 기본은 {x, 0, z}.
         */
        default int[] findNearestStructurePos(String structureTag, int x, int y, int z, int searchRadius) {
            int[] f = findNearestMapStructure(structureTag, x, y, z, searchRadius);
            return f == null ? null : new int[] {f[0], 0, f[1]};
        }

        /** 그 칸(블록 x, z — Level.getBiome 의 확대 지터 포함, y = 0)의 바이옴이 #water_on_map_outlines 인가. */
        boolean waterOnMapOutlines(int blockX, int blockZ);

        String dimension();
    }

    /**
     * 탐험 지도 한 장(바닐라 배율 2, 위치 추적·무제한 추적). 찾지 못하면 null. 이름은 번역 키(item_name).
     * 서 있는 곳(x, y, z)에서 반경 100(청크 격자 단위)으로 찾는다.
     */
    public static ItemStack create(Backend backend, int x, int y, int z, String structureTag, MapDecorationType type, String nameKey) {
        int[] found = backend.findNearestMapStructure(structureTag, x, y, z, 100);
        if (found == null) return null;
        ItemStack map = createAt(backend, found[0], found[1], (byte) 2, type);
        return ItemTools.with(map, "minecraft:item_name", "{\"translate\":\"" + nameKey + "\"}");
    }

    /**
     * 찾은 구조물 (x, z) 를 가리키는 지도 한 장: MapItem.create(위치 추적·무제한 추적) → renderBiomePreviewMap
     * → addTargetDecoration("+"). 거래(배율 2)와 전리품 exploration_map(배율 = zoom)이 함께 쓴다.
     */
    public static ItemStack createAt(Backend backend, int x, int z, byte scale, MapDecorationType type) {
        ItemStack map = MapItems.create(backend.dimension(), x, z, scale, true, true);
        MapRenderer.renderBiomePreview(MapStore.of(map), backend::waterOnMapOutlines);
        return MapItems.addTargetDecoration(map, x, z, "+", type);
    }
}
