// 정글 신전 — 바닐라 JungleTempleStructure + JungleTemplePiece(JunglePyramidPiece).
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class JungleTempleStructure {
    private JungleTempleStructure() {}

    public static SinglePieceStructure type() {
        return new SinglePieceStructure((e, r, x, z) -> new Piece(r, x, z), 12, 15);
    }

    static final class Piece extends ScatteredPiece {
        private boolean placedMainChest, placedHiddenChest, placedTrap1, placedTrap2;

        Piece(WorldgenRandom random, int x, int z) {
            super(x, 64, z, 12, 10, 15, Direction.randomHorizontal(random));
        }

        private final BlockSelector selector = new BlockSelector() {
            private int state;
            private final int cobble = -1;
            @Override
            public void next(LegacyRandom r, int x, int y, int z, boolean edge) {
                state = r.nextFloat() < 0.4F ? COBBLE : MOSSY;
            }
            @Override
            public int state() { return state; }
        };

        // 정적 상태 id(엔진 생성 후 첫 postProcess 에서 채움)
        private int COBBLE, MOSSY;

        @Override
        public void postProcess(PostCtx c) {
            BoundingBox b = c.writableBox;
            if (!updateAverageGroundLevel(c, b, 0)) return;
            StructureEngine e = c.engine;
            COBBLE = e.state("minecraft:cobblestone");
            MOSSY = e.state("minecraft:mossy_cobblestone");
            LegacyRandom r = c.random;
            int air = 0;
            generateBox(c, b, 0, -4, 0, width - 1, 0, depth - 1, false, r, selector);
            generateBox(c, b, 2, 1, 2, 9, 2, 2, false, r, selector);
            generateBox(c, b, 2, 1, 12, 9, 2, 12, false, r, selector);
            generateBox(c, b, 2, 1, 3, 2, 2, 11, false, r, selector);
            generateBox(c, b, 9, 1, 3, 9, 2, 11, false, r, selector);
            generateBox(c, b, 1, 3, 1, 10, 6, 1, false, r, selector);
            generateBox(c, b, 1, 3, 13, 10, 6, 13, false, r, selector);
            generateBox(c, b, 1, 3, 2, 1, 6, 12, false, r, selector);
            generateBox(c, b, 10, 3, 2, 10, 6, 12, false, r, selector);
            generateBox(c, b, 2, 3, 2, 9, 3, 12, false, r, selector);
            generateBox(c, b, 2, 6, 2, 9, 6, 12, false, r, selector);
            generateBox(c, b, 3, 7, 3, 8, 7, 11, false, r, selector);
            generateBox(c, b, 4, 8, 4, 7, 8, 10, false, r, selector);
            generateAirBox(c, b, 3, 1, 3, 8, 2, 11);
            generateAirBox(c, b, 4, 3, 6, 7, 3, 9);
            generateAirBox(c, b, 2, 4, 2, 9, 5, 12);
            generateAirBox(c, b, 4, 6, 5, 7, 6, 9);
            generateAirBox(c, b, 5, 7, 6, 6, 7, 8);
            generateAirBox(c, b, 5, 1, 2, 6, 2, 2);
            generateAirBox(c, b, 5, 2, 12, 6, 2, 12);
            generateAirBox(c, b, 5, 5, 1, 6, 5, 1);
            generateAirBox(c, b, 5, 5, 13, 6, 5, 13);
            placeBlock(c, air, 1, 5, 5, b);
            placeBlock(c, air, 10, 5, 5, b);
            placeBlock(c, air, 1, 5, 9, b);
            placeBlock(c, air, 10, 5, 9, b);
            for (int z = 0; z <= 14; z += 14) {
                generateBox(c, b, 2, 4, z, 2, 5, z, false, r, selector);
                generateBox(c, b, 4, 4, z, 4, 5, z, false, r, selector);
                generateBox(c, b, 7, 4, z, 7, 5, z, false, r, selector);
                generateBox(c, b, 9, 4, z, 9, 5, z, false, r, selector);
            }
            generateBox(c, b, 5, 6, 0, 6, 6, 0, false, r, selector);
            for (int x = 0; x <= 11; x += 11) {
                for (int z = 2; z <= 12; z += 2) generateBox(c, b, x, 4, z, x, 5, z, false, r, selector);
                generateBox(c, b, x, 6, 5, x, 6, 5, false, r, selector);
                generateBox(c, b, x, 6, 9, x, 6, 9, false, r, selector);
            }
            generateBox(c, b, 2, 7, 2, 2, 9, 2, false, r, selector);
            generateBox(c, b, 9, 7, 2, 9, 9, 2, false, r, selector);
            generateBox(c, b, 2, 7, 12, 2, 9, 12, false, r, selector);
            generateBox(c, b, 9, 7, 12, 9, 9, 12, false, r, selector);
            generateBox(c, b, 4, 9, 4, 4, 9, 4, false, r, selector);
            generateBox(c, b, 7, 9, 4, 7, 9, 4, false, r, selector);
            generateBox(c, b, 4, 9, 10, 4, 9, 10, false, r, selector);
            generateBox(c, b, 7, 9, 10, 7, 9, 10, false, r, selector);
            generateBox(c, b, 5, 9, 7, 6, 9, 7, false, r, selector);
            int stE = e.state("minecraft:cobblestone_stairs[facing=east]");
            int stW = e.state("minecraft:cobblestone_stairs[facing=west]");
            int stS = e.state("minecraft:cobblestone_stairs[facing=south]");
            int stN = e.state("minecraft:cobblestone_stairs[facing=north]");
            placeBlock(c, stN, 5, 9, 6, b);
            placeBlock(c, stN, 6, 9, 6, b);
            placeBlock(c, stS, 5, 9, 8, b);
            placeBlock(c, stS, 6, 9, 8, b);
            placeBlock(c, stN, 4, 0, 0, b);
            placeBlock(c, stN, 5, 0, 0, b);
            placeBlock(c, stN, 6, 0, 0, b);
            placeBlock(c, stN, 7, 0, 0, b);
            placeBlock(c, stN, 4, 1, 8, b);
            placeBlock(c, stN, 4, 2, 9, b);
            placeBlock(c, stN, 4, 3, 10, b);
            placeBlock(c, stN, 7, 1, 8, b);
            placeBlock(c, stN, 7, 2, 9, b);
            placeBlock(c, stN, 7, 3, 10, b);
            generateBox(c, b, 4, 1, 9, 4, 1, 9, false, r, selector);
            generateBox(c, b, 7, 1, 9, 7, 1, 9, false, r, selector);
            generateBox(c, b, 4, 1, 10, 7, 2, 10, false, r, selector);
            generateBox(c, b, 5, 4, 5, 6, 4, 5, false, r, selector);
            placeBlock(c, stE, 4, 4, 5, b);
            placeBlock(c, stW, 7, 4, 5, b);
            for (int i = 0; i < 4; i++) {
                placeBlock(c, stS, 5, -i, 6 + i, b);
                placeBlock(c, stS, 6, -i, 6 + i, b);
                generateAirBox(c, b, 5, -i, 7 + i, 6, -i, 9 + i);
            }
            generateAirBox(c, b, 1, -3, 12, 10, -1, 13);
            generateAirBox(c, b, 1, -3, 1, 3, -1, 13);
            generateAirBox(c, b, 1, -3, 1, 9, -1, 5);
            for (int z = 1; z <= 13; z += 2) generateBox(c, b, 1, -3, z, 1, -2, z, false, r, selector);
            for (int z = 2; z <= 12; z += 2) generateBox(c, b, 1, -1, z, 3, -1, z, false, r, selector);
            generateBox(c, b, 2, -2, 1, 5, -2, 1, false, r, selector);
            generateBox(c, b, 7, -2, 1, 9, -2, 1, false, r, selector);
            generateBox(c, b, 6, -3, 1, 6, -3, 1, false, r, selector);
            generateBox(c, b, 6, -1, 1, 6, -1, 1, false, r, selector);
            placeBlock(c, e.state("minecraft:tripwire_hook[facing=east,attached=true]"), 1, -3, 8, b);
            placeBlock(c, e.state("minecraft:tripwire_hook[facing=west,attached=true]"), 4, -3, 8, b);
            int tripEW = e.state("minecraft:tripwire[east=true,west=true,attached=true]");
            placeBlock(c, tripEW, 2, -3, 8, b);
            placeBlock(c, tripEW, 3, -3, 8, b);
            int wireNS = e.state("minecraft:redstone_wire[north=side,south=side]");
            placeBlock(c, wireNS, 5, -3, 7, b);
            placeBlock(c, wireNS, 5, -3, 6, b);
            placeBlock(c, wireNS, 5, -3, 5, b);
            placeBlock(c, wireNS, 5, -3, 4, b);
            placeBlock(c, wireNS, 5, -3, 3, b);
            placeBlock(c, wireNS, 5, -3, 2, b);
            placeBlock(c, e.state("minecraft:redstone_wire[north=side,west=side]"), 5, -3, 1, b);
            placeBlock(c, e.state("minecraft:redstone_wire[east=side,west=side]"), 4, -3, 1, b);
            placeBlock(c, MOSSY, 3, -3, 1, b);
            if (!placedTrap1) placedTrap1 = createDispenser(c, b, 3, -2, 1, "north", "minecraft:chests/jungle_temple_dispenser");
            placeBlock(c, e.state("minecraft:vine[south=true]"), 3, -2, 2, b);
            placeBlock(c, e.state("minecraft:tripwire_hook[facing=north,attached=true]"), 7, -3, 1, b);
            placeBlock(c, e.state("minecraft:tripwire_hook[facing=south,attached=true]"), 7, -3, 5, b);
            int tripNS = e.state("minecraft:tripwire[north=true,south=true,attached=true]");
            placeBlock(c, tripNS, 7, -3, 2, b);
            placeBlock(c, tripNS, 7, -3, 3, b);
            placeBlock(c, tripNS, 7, -3, 4, b);
            placeBlock(c, e.state("minecraft:redstone_wire[east=side,west=side]"), 8, -3, 6, b);
            placeBlock(c, e.state("minecraft:redstone_wire[west=side,south=side]"), 9, -3, 6, b);
            placeBlock(c, e.state("minecraft:redstone_wire[north=side,south=up]"), 9, -3, 5, b);
            placeBlock(c, MOSSY, 9, -3, 4, b);
            placeBlock(c, wireNS, 9, -2, 4, b);
            if (!placedTrap2) placedTrap2 = createDispenser(c, b, 9, -2, 3, "west", "minecraft:chests/jungle_temple_dispenser");
            placeBlock(c, e.state("minecraft:vine[east=true]"), 8, -1, 3, b);
            placeBlock(c, e.state("minecraft:vine[east=true]"), 8, -2, 3, b);
            if (!placedMainChest) placedMainChest = createChestLocalLoot(c, b, 8, -3, 3, "minecraft:chests/jungle_temple");
            placeBlock(c, MOSSY, 9, -3, 2, b);
            placeBlock(c, MOSSY, 8, -3, 1, b);
            placeBlock(c, MOSSY, 4, -3, 5, b);
            placeBlock(c, MOSSY, 5, -2, 5, b);
            placeBlock(c, MOSSY, 5, -1, 5, b);
            placeBlock(c, MOSSY, 6, -3, 5, b);
            placeBlock(c, MOSSY, 7, -2, 5, b);
            placeBlock(c, MOSSY, 7, -1, 5, b);
            placeBlock(c, MOSSY, 8, -3, 5, b);
            generateBox(c, b, 9, -1, 1, 9, -1, 5, false, r, selector);
            generateAirBox(c, b, 8, -3, 8, 10, -1, 10);
            int chisel = e.state("minecraft:chiseled_stone_bricks");
            placeBlock(c, chisel, 8, -2, 11, b);
            placeBlock(c, chisel, 9, -2, 11, b);
            placeBlock(c, chisel, 10, -2, 11, b);
            int lever = e.state("minecraft:lever[facing=north,face=wall]");
            placeBlock(c, lever, 8, -2, 12, b);
            placeBlock(c, lever, 9, -2, 12, b);
            placeBlock(c, lever, 10, -2, 12, b);
            generateBox(c, b, 8, -3, 8, 8, -3, 10, false, r, selector);
            generateBox(c, b, 10, -3, 8, 10, -3, 10, false, r, selector);
            placeBlock(c, MOSSY, 10, -2, 9, b);
            placeBlock(c, wireNS, 8, -2, 9, b);
            placeBlock(c, wireNS, 8, -2, 10, b);
            placeBlock(c, e.state("minecraft:redstone_wire[north=side,south=side,east=side,west=side]"), 10, -1, 9, b);
            placeBlock(c, e.state("minecraft:sticky_piston[facing=up]"), 9, -2, 8, b);
            placeBlock(c, e.state("minecraft:sticky_piston[facing=west]"), 10, -2, 8, b);
            placeBlock(c, e.state("minecraft:sticky_piston[facing=west]"), 10, -1, 8, b);
            placeBlock(c, e.state("minecraft:repeater[facing=north]"), 10, -2, 10, b);
            if (!placedHiddenChest) placedHiddenChest = createChestLocalLoot(c, b, 9, -3, 10, "minecraft:chests/jungle_temple");
        }

        private boolean createChestLocalLoot(PostCtx c, BoundingBox b, int x, int y, int z, String loot) {
            return createChestAt(c, b, getWorldX(x, z), getWorldY(y), getWorldZ(x, z), loot, -1);
        }

        /** 바닐라 StructurePiece.createDispenser(level, box, random, x, y, z, facing, loot). */
        private boolean createDispenser(PostCtx c, BoundingBox b, int x, int y, int z, String facing, String loot) {
            int wx = getWorldX(x, z), wy = getWorldY(y), wz = getWorldZ(x, z);
            if (!b.isInside(wx, wy, wz) || c.table().name(c.view.getBlock(wx, wy, wz)).equals("minecraft:dispenser")) return false;
            placeBlock(c, c.engine.state("minecraft:dispenser[facing=" + facing + "]"), x, y, z, b);
            long seed = c.random.nextLong();
            org.domi.furnace.server.net.io.nbt.NbtCompound nbt = new org.domi.furnace.server.net.io.nbt.NbtCompound();
            nbt.putString("id", "minecraft:dispenser");
            nbt.putInt("x", wx); nbt.putInt("y", wy); nbt.putInt("z", wz);
            nbt.putString("LootTable", loot);
            nbt.putLong("LootTableSeed", seed);
            c.view.putGeneratedBlockEntity(wx, wy, wz, nbt);
            return true;
        }
    }
}
