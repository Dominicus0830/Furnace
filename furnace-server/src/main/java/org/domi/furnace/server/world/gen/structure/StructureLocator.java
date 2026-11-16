// 가장 가까운 구조물 찾기 — 바닐라 ChunkGenerator.findNearestMapStructure(/locate structure, 탐험 지도).
package org.domi.furnace.server.world.gen.structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.world.ChunkPos;

public final class StructureLocator {

    /** 찾은 위치(StructurePlacement.getLocatePos: 시작 청크 모서리 + locate_offset)와 구조물 id. */
    public record Found(int x, int y, int z, String structureId) {}

    private final StructureEngine engine;
    private final TerrainAccess terrain;

    public StructureLocator(StructureEngine engine, TerrainAccess terrain) {
        this.engine = engine;
        this.terrain = terrain;
    }

    /** findNearestMapStructure(..., skipKnownStructures=false) — /locate, 엔더의 눈, 돌고래. */
    public Found findNearest(List<String> structures, int ox, int oy, int oz, int searchRadius) {
        return findNearest(structures, ox, oy, oz, searchRadius, false);
    }

    /**
     * findNearestMapStructure(level, structures, pos, searchRadius, skipKnownStructures).
     * skipKnown(제도사 거래, 전리품 exploration_map 의 skip_existing_chunks)이면 후보마다 바닐라 tryAddReference 를 한다:
     * references 가 1 이상인 시작점은 건너뛰고, 아니면 1 올리고 고른다. 바닐라처럼 동심원 배치는 더 가까운 후보를 찾을 때마다,
     * 무작위 분산 배치는 배치마다 처음 찾은 것에 올린다(가장 가까운 것이 아니어도).
     * 구조물들을 배치별로 묶어, 동심원 배치는 링 위치 전부를, 무작위 분산 배치는 반경 0..searchRadius 의 격자 테두리를
     * 안쪽부터 훑어 처음 찾은 테두리에서 가장 가까운 것을 돌려준다. 없으면 null.
     */
    public Found findNearest(List<String> structures, int ox, int oy, int oz, int searchRadius, boolean skipKnown) {
        Map<StructureRegistry.SetDef, Set<String>> byPlacement = new LinkedHashMap<>();
        for (String id : structures) {
            for (StructureRegistry.SetDef set : engine.placementsFor(id)) byPlacement.computeIfAbsent(set, k -> new LinkedHashSet<>()).add(id);
        }
        if (byPlacement.isEmpty()) return null;
        Found best = null;
        double bestDist = Double.MAX_VALUE;
        List<Map.Entry<StructureRegistry.SetDef, Set<String>>> spread = new ArrayList<>();
        for (var e : byPlacement.entrySet()) {
            StructureRegistry.SetDef set = e.getKey();
            if (set.rings() != null) {
                Found f = nearestInRings(e.getValue(), set, ox, oy, oz, skipKnown);
                if (f != null) {
                    double d = distSqr(ox, oy, oz, f);
                    if (d < bestDist) { bestDist = d; best = f; }
                }
            } else if (set.spread() != null) {
                spread.add(e);
            }
        }
        if (!spread.isEmpty()) {
            int cx = ox >> 4, cz = oz >> 4;
            for (int r = 0; r <= searchRadius; r++) {
                boolean found = false;
                for (var e : spread) {
                    Found f = nearestInSpreadRing(e.getValue(), e.getKey(), cx, cz, r, skipKnown);
                    if (f != null) {
                        found = true;
                        double d = distSqr(ox, oy, oz, f);
                        if (d < bestDist) { bestDist = d; best = f; }
                    }
                }
                if (found) return best;
            }
        }
        return best;
    }

    private Found nearestInRings(Set<String> ids, StructureRegistry.SetDef set, int ox, int oy, int oz, boolean skipKnown) {
        Found best = null;
        double bestDist = Double.MAX_VALUE;
        for (long key : engine.ringPositions(set)) {
            int rcx = (int) (key >> 32), rcz = (int) key;
            // 링 위치는 청크 가운데·y 32 로 거리를 잰다
            double dx = (rcx << 4) + 8 - ox, dy = 32 - oy, dz = (rcz << 4) + 8 - oz;
            double d = dx * dx + dy * dy + dz * dz;
            if (best == null || d < bestDist) {
                Found f = structureAt(ids, set, rcx, rcz, skipKnown);
                if (f != null) { best = f; bestDist = d; }
            }
        }
        return best;
    }

    private Found nearestInSpreadRing(Set<String> ids, StructureRegistry.SetDef set, int cx, int cz, int r, boolean skipKnown) {
        RandomSpreadStructurePlacement p = set.spread();
        int spacing = p.spacing();
        for (int dx = -r; dx <= r; dx++) {
            boolean edgeX = dx == -r || dx == r;
            for (int dz = -r; dz <= r; dz++) {
                boolean edgeZ = dz == -r || dz == r;
                if (!edgeX && !edgeZ) continue;
                ChunkPos c = p.getPotentialStructureChunk(engine.seed(), cx + spacing * dx, cz + spacing * dz);
                Found f = structureAt(ids, set, c.x(), c.z(), skipKnown);
                if (f != null) return f;
            }
        }
        return null;
    }

    /**
     * getStructureGeneratingAt: 그 청크에 후보 구조물 중 하나의 시작점이 생기면 위치.
     * skipKnown 이면 바닐라 StructureCheck(references != 0 이면 START_NOT_PRESENT) + tryAddReference.
     */
    private Found structureAt(Set<String> ids, StructureRegistry.SetDef set, int cx, int cz, boolean skipKnown) {
        String id = engine.startOf(set, cx, cz, terrain);
        if (id == null || !ids.contains(id)) return null;
        if (skipKnown && !engine.references().tryAddReference(id, cx, cz)) return null;
        int[] off = engine.registry().locateOffset(set.id());
        return new Found((cx << 4) + off[0], off[1], (cz << 4) + off[2], id);
    }

    private static double distSqr(int ox, int oy, int oz, Found f) {
        double dx = f.x() - ox, dy = f.y() - oy, dz = f.z() - oz;
        return dx * dx + dy * dy + dz * dz;
    }
}
