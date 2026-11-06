// 네더 화석 — 바닐라 NetherFossilStructure + NetherFossilPieces (1.21.1).
package org.domi.furnace.server.world.gen.structure;

import java.util.Optional;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.gen.carver.HeightProvider;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processors;

public final class NetherFossilStructure extends StructureType {

    /** NetherFossilPieces.FOSSILS — fossil_1..fossil_14 (순서가 난수 인덱스다). */
    private static final String[] FOSSILS = new String[14];

    static {
        for (int i = 0; i < FOSSILS.length; i++) FOSSILS[i] = "minecraft:nether_fossils/fossil_" + (i + 1);
    }

    private final HeightProvider height;

    public NetherFossilStructure(StructureRegistry.Def def) {
        this.height = HeightProvider.fromJson(def.json().getAsJsonObject("height"));
    }

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        var random = c.random;
        int x = c.minBlockX() + random.nextInt(16);
        int z = c.minBlockZ() + random.nextInt(16);
        int seaLevel = c.terrain.seaLevel();
        // WorldGenerationContext(minGenY = max(차원 min, 생성기 min), genDepth) — 네더는 0 / 128
        int y = height.sample(random, c.terrain.minY(), c.terrain.genDepth());
        BlockAttrs a = BlockAttrs.get();
        // getBaseColumn(x, z) 를 위에서 아래로: 위 칸이 공기이고 아래 칸이 영혼 모래이거나 윗면이 단단하면 멈춘다
        while (y > seaLevel) {
            int top = c.terrain.baseBlock(x, y, z);
            int below = c.terrain.baseBlock(x, --y, z);
            if (a.isAir(top) && (c.engine.table().name(below).equals("minecraft:soul_sand") || a.isFaceSturdy(below, BlockAttrs.UP))) {
                break;
            }
        }
        if (y <= seaLevel) return Optional.empty();
        int fx = x, fy = y, fz = z;
        return Optional.of(new Stub(fx, fy, fz, b -> {
            // NetherFossilPieces.addPieces: Rotation.getRandom 이 템플릿 선택보다 먼저
            int rotation = random.nextInt(4);
            String name = FOSSILS[random.nextInt(FOSSILS.length)];
            b.addPiece(new Piece(c.engine, name, fx, fy, fz, rotation));
        }));
    }

    static final class Piece extends TemplatePiece {
        Piece(StructureEngine e, String name, int x, int y, int z, int rotation) {
            super(name, e.template(name), settings(rotation), x, y, z);
        }

        private static PlaceSettings settings(int rotation) {
            PlaceSettings st = new PlaceSettings();
            st.rotation = rotation;
            st.mirror = BlockStateTable.NONE;
            st.processors.add(Processors.BlockIgnore.structureAndAir());
            return st;
        }

        @Override
        protected void handleDataMarker(String marker, int x, int y, int z, PostCtx c) {}

        @Override
        public void postProcess(PostCtx c) {
            // 바닐라: 쓰기 상자를 템플릿 전체 상자로 넓힌다(청크 경계로 잘리지 않는다)
            BoundingBox b = boundingBox;
            c.writableBox.encapsulate(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ());
            super.postProcess(c);
        }
    }
}
