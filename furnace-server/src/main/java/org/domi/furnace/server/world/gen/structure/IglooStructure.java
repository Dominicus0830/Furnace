// 이글루 — 바닐라 IglooStructure/IglooPieces 이식 (top + 선택적 middle*N + bottom 지하실).
package org.domi.furnace.server.world.gen.structure;

import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.domi.furnace.server.world.gen.structure.template.PlaceSettings;
import org.domi.furnace.server.world.gen.structure.template.Processors;
import org.domi.furnace.server.world.gen.structure.template.StructureTemplate;

public final class IglooStructure extends StructureType {

    static final String TOP = "minecraft:igloo/top";
    static final String MIDDLE = "minecraft:igloo/middle";
    static final String BOTTOM = "minecraft:igloo/bottom";
    static final Map<String, int[]> PIVOTS = Map.of(TOP, new int[]{3, 5, 5}, MIDDLE, new int[]{1, 3, 1}, BOTTOM, new int[]{3, 6, 7});
    static final Map<String, int[]> OFFSETS = Map.of(TOP, new int[]{0, 0, 0}, MIDDLE, new int[]{2, -3, 4}, BOTTOM, new int[]{0, -3, -2});

    private final StructureRegistry.Def def;

    public IglooStructure(StructureRegistry.Def def) {
        this.def = def;
    }

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        return onTopOfChunkCenter(c, TerrainAccess.HM_WORLD_SURFACE_WG, b -> {
            WorldgenRandom random = c.random;
            int rotation = random.nextInt(4);   // Rotation.getRandom
            addPieces(c.engine, c.minBlockX(), 90, c.minBlockZ(), rotation, b, random);
        });
    }

    static void addPieces(StructureEngine e, int x, int y, int z, int rotation, PiecesBuilder b, WorldgenRandom random) {
        if (random.nextDouble() < 0.5) {
            int n = random.nextInt(8) + 4;
            b.addPiece(new Piece(e, BOTTOM, x, y, z, rotation, n * 3));
            for (int i = 0; i < n - 1; i++) b.addPiece(new Piece(e, MIDDLE, x, y, z, rotation, i * 3));
        }
        b.addPiece(new Piece(e, TOP, x, y, z, rotation, 0));
    }

    static PlaceSettings makeSettings(int rotation, String loc) {
        PlaceSettings st = new PlaceSettings();
        st.rotation = rotation;
        st.mirror = BlockStateTable.NONE;
        int[] pv = PIVOTS.get(loc);
        st.pivotX = pv[0];
        st.pivotY = pv[1];
        st.pivotZ = pv[2];
        st.processors.add(Processors.BlockIgnore.structureBlock());
        st.keepLiquids = false;   // LiquidSettings.IGNORE_WATERLOGGING
        return st;
    }

    static final class Piece extends TemplatePiece {
        Piece(StructureEngine e, String loc, int x, int y, int z, int rotation, int depth) {
            super(loc, e.template(loc), makeSettings(rotation, loc),
                x + OFFSETS.get(loc)[0], y + OFFSETS.get(loc)[1] - depth, z + OFFSETS.get(loc)[2]);
        }

        @Override
        protected void handleDataMarker(String marker, int x, int y, int z, PostCtx c) {
            if ("chest".equals(marker)) {
                c.view.setBlock(x, y, z, 0);
                setChestLoot(c, x, y - 1, z, "minecraft:chests/igloo_chest");
            }
        }

        @Override
        public void postProcess(PostCtx c) {
            PlaceSettings st = makeSettings(settings.rotation, templateName);
            int[] off = OFFSETS.get(templateName);
            int[] rel = StructureTemplate.transform(3 - off[0], 0, -off[2], st.mirror, st.rotation, st.pivotX, st.pivotZ);
            int px = tx + rel[0], pz = tz + rel[2];
            int h = c.view.getWorldSurfaceWgY(px, pz) + 1;
            int oy = ty;
            ty = ty + (h - 90 - 1);
            super.postProcess(c);
            if (templateName.equals(TOP)) {
                int[] r2 = StructureTemplate.transform(3, 0, 5, st.mirror, st.rotation, st.pivotX, st.pivotZ);
                int x = tx + r2[0], y = ty + r2[1], z = tz + r2[2];
                int below = c.view.getBlock(x, y - 1, z);
                String nm = c.table().name(below);
                if (!nm.equals("minecraft:air") && !nm.equals("minecraft:cave_air") && !nm.equals("minecraft:void_air") && !nm.equals("minecraft:ladder")) {
                    c.view.setBlock(x, y, z, c.blocks().defaultStateId("minecraft:snow_block"));
                }
            }
            ty = oy;
        }
    }
}
