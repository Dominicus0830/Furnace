// 늪 오두막 — 바닐라 SwampHutStructure + SwampHutPiece (마녀·검은 고양이 스폰 포함).
package org.domi.furnace.server.world.gen.structure;

import java.util.Optional;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class SwampHutStructure extends StructureType {

    public SwampHutStructure(StructureRegistry.Def def) {}

    @Override
    public Optional<Stub> findGenerationPoint(GenContext c) {
        // SwampHutStructure 는 SinglePieceStructure 가 아니라 높이 하한 검사가 없다
        return onTopOfChunkCenter(c, TerrainAccess.HM_WORLD_SURFACE_WG, b -> b.addPiece(new Piece(c.random, c.minBlockX(), c.minBlockZ())));
    }

    static final class Piece extends ScatteredPiece {
        private boolean spawnedWitch, spawnedCat;

        Piece(WorldgenRandom random, int x, int z) {
            super(x, 64, z, 7, 7, 9, Direction.randomHorizontal(random));
        }

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox b = c.writableBox;
            if (!updateAverageGroundLevel(c, b, 0)) return;
            StructureEngine e = c.engine;
            int planks = e.state("minecraft:spruce_planks"), log = e.state("minecraft:oak_log"), fence = e.state("minecraft:oak_fence");
            generateBox(c, b, 1, 1, 1, 5, 1, 7, planks, planks, false);
            generateBox(c, b, 1, 4, 2, 5, 4, 7, planks, planks, false);
            generateBox(c, b, 2, 1, 0, 4, 1, 0, planks, planks, false);
            generateBox(c, b, 2, 2, 2, 3, 3, 2, planks, planks, false);
            generateBox(c, b, 1, 2, 3, 1, 3, 6, planks, planks, false);
            generateBox(c, b, 5, 2, 3, 5, 3, 6, planks, planks, false);
            generateBox(c, b, 2, 2, 7, 4, 3, 7, planks, planks, false);
            generateBox(c, b, 1, 0, 2, 1, 3, 2, log, log, false);
            generateBox(c, b, 5, 0, 2, 5, 3, 2, log, log, false);
            generateBox(c, b, 1, 0, 7, 1, 3, 7, log, log, false);
            generateBox(c, b, 5, 0, 7, 5, 3, 7, log, log, false);
            placeBlock(c, fence, 2, 3, 2, b);
            placeBlock(c, fence, 3, 3, 7, b);
            placeBlock(c, 0, 1, 3, 4, b);
            placeBlock(c, 0, 5, 3, 4, b);
            placeBlock(c, 0, 5, 3, 5, b);
            placeBlock(c, e.state("minecraft:potted_red_mushroom"), 1, 3, 5, b);
            placeBlock(c, e.state("minecraft:crafting_table"), 3, 2, 6, b);
            placeBlock(c, e.state("minecraft:cauldron"), 4, 2, 6, b);
            placeBlock(c, fence, 1, 2, 1, b);
            placeBlock(c, fence, 5, 2, 1, b);
            int n = e.state("minecraft:spruce_stairs[facing=north]");
            int ea = e.state("minecraft:spruce_stairs[facing=east]");
            int w = e.state("minecraft:spruce_stairs[facing=west]");
            int s = e.state("minecraft:spruce_stairs[facing=south]");
            generateBox(c, b, 0, 4, 1, 6, 4, 1, n, n, false);
            generateBox(c, b, 0, 4, 2, 0, 4, 7, ea, ea, false);
            generateBox(c, b, 6, 4, 2, 6, 4, 7, w, w, false);
            generateBox(c, b, 0, 4, 8, 6, 4, 8, s, s, false);
            placeBlock(c, c.table().with(n, "shape", "outer_right"), 0, 4, 1, b);
            placeBlock(c, c.table().with(n, "shape", "outer_left"), 6, 4, 1, b);
            placeBlock(c, c.table().with(s, "shape", "outer_left"), 0, 4, 8, b);
            placeBlock(c, c.table().with(s, "shape", "outer_right"), 6, 4, 8, b);
            for (int z = 2; z <= 7; z += 5) {
                for (int x = 1; x <= 5; x += 4) fillColumnDown(c, log, x, -1, z, b);
            }
            int wx = getWorldX(2, 5), wy = getWorldY(2), wz = getWorldZ(2, 5);
            if (!spawnedWitch && b.isInside(wx, wy, wz)) {
                spawnedWitch = true;
                c.view.addEntity("minecraft:witch", wx + 0.5, wy, wz + 0.5, 0.0F, 0.0F).putByte("PersistenceRequired", (byte) 1);
            }
            if (!spawnedCat && b.isInside(wx, wy, wz)) {
                spawnedCat = true;
                org.domi.furnace.server.net.io.nbt.NbtCompound cat = c.view.addEntity("minecraft:cat", wx + 0.5, wy, wz + 0.5, 0.0F, 0.0F);
                cat.putByte("PersistenceRequired", (byte) 1);
                cat.putString("variant", "minecraft:all_black");   // 구조물 스폰(STRUCTURE) 이면 검은 고양이
            }
        }
    }
}
