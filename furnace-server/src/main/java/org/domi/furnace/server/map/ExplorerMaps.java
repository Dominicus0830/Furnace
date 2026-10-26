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
         * TODO(지도): skipKnownStructures(이미 지도가 가리킨 구조물 건너뛰기 — 시작점 references)는 구조물 시작점 저장이 없어 못 한다.
         */
        int[] findNearestMapStructure(String structureTag, int x, int y, int z, int searchRadius);

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
        ItemStack map = MapItems.create(backend.dimension(), found[0], found[1], (byte) 2, true, true);
        MapRenderer.renderBiomePreview(MapStore.of(map), backend::waterOnMapOutlines);
        map = MapItems.addTargetDecoration(map, found[0], found[1], "+", type);
        return ItemTools.with(map, "minecraft:item_name", "{\"translate\":\"" + nameKey + "\"}");
    }
}
