// 묻힌 보물 — 바닐라 BuriedTreasureStructure + BuriedTreasurePieces.
package org.domi.furnace.server.world.gen.structure;

import java.util.Optional;

public final class BuriedTreasureStructure extends StructureType {

    public BuriedTreasureStructure(StructureRegistry.Def def) {}

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        return onTopOfChunkCenter(c, TerrainAccess.HM_OCEAN_FLOOR_WG, b -> b.addPiece(new Piece(c.chunkX * 16 + 9, 90, c.chunkZ * 16 + 9)));
    }

    static final class Piece extends StructurePiece {
        Piece(int x, int y, int z) {
            super(new BoundingBox(x, y, z, x, y, z), Direction.NORTH);
        }

        @Override
        public String pieceType() { return "btp"; }

        /** 바닐라 BuriedTreasurePiece 는 방향을 정하지 않는다(O = -1). */
        @Override
        protected Direction savedOrientation() { return null; }

        @Override
        public void postProcess(PostCtx c) {
            int h = c.view.getWgOceanFloorHeight(boundingBox.minX(), boundingBox.minZ());
            int x = boundingBox.minX(), y = h, z = boundingBox.minZ();
            org.domi.furnace.server.world.BlockAttrs a = org.domi.furnace.server.world.BlockAttrs.get();
            String[] okBelow = {"minecraft:sandstone", "minecraft:stone", "minecraft:andesite", "minecraft:granite", "minecraft:diorite"};
            int water = c.blocks().defaultStateId("minecraft:water");
            int lava = c.blocks().defaultStateId("minecraft:lava");
            int sand = c.blocks().defaultStateId("minecraft:sand");
            java.util.Set<Integer> ok = new java.util.HashSet<>();
            for (String n : okBelow) ok.add(c.blocks().defaultStateId(n));
            // DOWN, UP, NORTH, SOUTH, WEST, EAST
            int[][] dirs = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
            while (y > -64) {
                int here = c.view.getBlock(x, y, z);
                int below = c.view.getBlock(x, y - 1, z);
                if (ok.contains(below)) {
                    int fill = (!a.isAir(here) && here != water && here != lava) ? here : sand;
                    for (int[] d : dirs) {
                        int px = x + d[0], py = y + d[1], pz = z + d[2];
                        int s = c.view.getBlock(px, py, pz);
                        if (a.isAir(s) || s == water || s == lava) {
                            int sq = c.view.getBlock(px, py - 1, pz);
                            if ((a.isAir(sq) || sq == water || sq == lava) && !(d[1] == 1)) c.view.setBlock(px, py, pz, below);
                            else c.view.setBlock(px, py, pz, fill);
                        }
                    }
                    boundingBox = new BoundingBox(x, y, z, x, y, z);
                    createChestAt(c, c.writableBox, x, y, z, "minecraft:chests/buried_treasure", -1);
                    return;
                }
                y--;
            }
        }
    }
}
