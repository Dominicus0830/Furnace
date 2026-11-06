// 바닐라 StructureProcessor — 템플릿 블록 하나(또는 전체 목록)를 배치 직전에 바꾼다.
package org.domi.furnace.server.world.gen.structure.template;

import java.util.List;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;

public abstract class Processor {

    /** processBlock에 필요한 월드 접근. */
    public static final class Ctx {
        public final FeatureWorldView view;
        public final BlockStateTable table;
        public Ctx(FeatureWorldView view, BlockStateTable table) {
            this.view = view;
            this.table = table;
        }
        public long worldSeed() { return view.worldSeed(); }

        /** Level.getHeight(type, x, z) — 첫 빈 칸 y(= 최상단 블록 y + 1). */
        public int heightFirstFree(String type, int x, int z) {
            return switch (type) {
                case "world_surface_wg" -> view.getWorldSurfaceWgY(x, z) + 1;
                case "ocean_floor_wg" -> view.getWgOceanFloorHeight(x, z);
                case "ocean_floor" -> view.getOceanFloorY(x, z) + 1;
                case "motion_blocking", "motion_blocking_no_leaves" -> view.getMotionBlockingY(x, z) + 1;
                default -> view.getSurfaceY(x, z) + 1;   // world_surface
            };
        }

        /** BlockStateParser: "minecraft:name[prop=value,...]" → state id (실패 시 -1). */
        public int parseBlockState(String text) {
            String name = text;
            java.util.Map<String, String> props = new java.util.TreeMap<>();
            int br = text.indexOf('[');
            if (br >= 0) {
                name = text.substring(0, br);
                String body = text.substring(br + 1, text.lastIndexOf(']'));
                if (!body.isBlank()) for (String kv : body.split(",")) {
                    int eq = kv.indexOf('=');
                    props.put(kv.substring(0, eq).strip(), kv.substring(eq + 1).strip());
                }
            }
            if (!name.contains(":")) name = "minecraft:" + name;
            int id = table.registry().exactStateId(org.domi.furnace.server.world.BlockStateKey.of(name, props));
            if (id >= 0) return id;
            // BlockStateParser: 지정하지 않은 속성은 기본값 — 기본 상태에서 지정 속성만 바꾼다
            int st;
            try { st = table.registry().defaultStateId(name); } catch (IllegalArgumentException e) { return -1; }
            for (java.util.Map.Entry<String, String> p : props.entrySet()) st = table.with(st, p.getKey(), p.getValue());
            return st;
        }

        /** Block.isShapeFullBlock(state.getCollisionShape) — 6면 모두 가득인 충돌 모양. */
        public boolean isFullCollisionBlock(int state) {
            org.domi.furnace.server.world.BlockAttrs a = org.domi.furnace.server.world.BlockAttrs.get();
            for (int d = 0; d < 6; d++) if (!a.isCollisionFaceFull(state, d)) return false;
            return true;
        }
    }

    /** null을 돌려주면 그 블록은 배치하지 않는다. offset=템플릿 배치 위치, pivot=배치 피벗(placeInWorld의 두 번째 pos). */
    public StructureTemplate.BlockInfo processBlock(Ctx ctx, int offX, int offY, int offZ, int pivX, int pivY, int pivZ,
                                                    StructureTemplate.BlockInfo original, StructureTemplate.BlockInfo current, PlaceSettings settings) {
        return current;
    }

    public List<StructureTemplate.BlockInfo> finalizeProcessing(Ctx ctx, int offX, int offY, int offZ, int pivX, int pivY, int pivZ,
                                                                List<StructureTemplate.BlockInfo> originals, List<StructureTemplate.BlockInfo> processed,
                                                                PlaceSettings settings) {
        return processed;
    }
}
