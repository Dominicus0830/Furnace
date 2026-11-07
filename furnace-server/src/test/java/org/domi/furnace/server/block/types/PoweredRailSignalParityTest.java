// X1: 전동 레일 줄 신호 탐색(값 번호 판)이 예전 문자열 판과 무작위 배치 수천 곳에서 같은 답을 내는지.
// 예전 판은 이 시험 안에 그대로 옮겨 두었다(변경 전 코드).
package org.domi.furnace.server.block.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class PoweredRailSignalParityTest {

    static class Lvl extends ChunkLevel {
        final Map<Long, ChunkColumn> chunks = new HashMap<>();
        final SuperflatWorldSource gen = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);

        Lvl() { super(1000000); }

        @Override protected ChunkColumn chunk(int x, int z) {
            if (Math.abs(x) > 2 || Math.abs(z) > 2) return null;
            return chunks.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> gen.generate(x, z));
        }

        @Override protected void markDirty(int x, int z) {}
        @Override protected void broadcastToChunk(int x, int z, Packet p) {}
        @Override protected LevelServices services() { return LevelServices.DEFAULT; }

        /** onPlace·이웃 갱신 없이 상태만 둔다. */
        void put(Pos p, int state) { chunk(p.x() >> 4, p.z() >> 4).setBlock(p.x() & 15, p.y(), p.z() & 15, state); }
    }

    private static final Prop SHAPE = B.prop("shape"), POWERED = B.prop("powered");

    // ---- 변경 전 구현(문자열 판) ----
    private static String shape(int s) { return SHAPE.valueName(s); }

    private static boolean refFind(BlockLevel level, int block, Pos pos, int state, boolean forward, int depth) {
        if (depth >= 8) return false;
        int x = pos.x(), y = pos.y(), z = pos.z();
        boolean checkBelow = true;
        String shape = shape(state);
        switch (shape) {
            case "north_south" -> z += forward ? 1 : -1;
            case "east_west" -> x += forward ? -1 : 1;
            case "ascending_east" -> {
                if (forward) x--;
                else { x++; y++; checkBelow = false; }
                shape = "east_west";
            }
            case "ascending_west" -> {
                if (forward) { x--; y++; checkBelow = false; }
                else x++;
                shape = "east_west";
            }
            case "ascending_north" -> {
                if (forward) z++;
                else { z--; y++; checkBelow = false; }
                shape = "north_south";
            }
            case "ascending_south" -> {
                if (forward) { z++; y++; checkBelow = false; }
                else z--;
                shape = "north_south";
            }
            default -> {}
        }
        return refSame(level, block, new Pos(x, y, z), forward, depth, shape)
                || checkBelow && refSame(level, block, new Pos(x, y - 1, z), forward, depth, shape);
    }

    private static boolean refSame(BlockLevel level, int block, Pos pos, boolean forward, int depth, String shape) {
        int s = level.getBlockState(pos);
        if (!B.is(s, block)) return false;
        String other = shape(s);
        if ("east_west".equals(shape) && ("north_south".equals(other) || "ascending_north".equals(other) || "ascending_south".equals(other))) return false;
        if ("north_south".equals(shape) && ("east_west".equals(other) || "ascending_east".equals(other) || "ascending_west".equals(other))) return false;
        if (!POWERED.is(s)) return false;
        return level.hasNeighborSignal(pos) || refFind(level, block, pos, s, forward, depth + 1);
    }

    @Test
    void newSearchMatchesOldSearchOnRandomRailLayouts() {
        BlockBehaviors.ofBlock(0);
        int powered = B.id("powered_rail"), activator = B.id("activator_rail");
        String[] shapes = {"north_south", "east_west", "ascending_east", "ascending_west", "ascending_north", "ascending_south"};
        Random rnd = new Random(42);
        int redstoneBlock = B.defaultState("redstone_block"), stone = B.defaultState("stone"), air = B.defaultState("air");
        long compared = 0, trueCount = 0;
        for (int layout = 0; layout < 60; layout++) {
            Lvl l = new Lvl();
            int y0 = -50;
            // 12×12×3 칸에 레일(전동·활성화)·레드스톤 블록·돌·공기를 섞어 둔다
            for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) for (int y = 0; y < 3; y++) {
                int r = rnd.nextInt(10);
                int st;
                if (r < 6) {
                    int b = rnd.nextInt(5) == 0 ? activator : powered;
                    st = B.defaultState(b);
                    st = SHAPE.with(st, shapes[rnd.nextInt(shapes.length)]);
                    st = POWERED.with(st, rnd.nextInt(3) != 0);
                } else if (r == 6) st = redstoneBlock;
                else if (r == 7) st = stone;
                else st = air;
                l.put(new Pos(x, y0 + y, z), st);
            }
            for (int x = 0; x < 12; x++) for (int z = 0; z < 12; z++) for (int y = 0; y < 3; y++) {
                Pos p = new Pos(x, y0 + y, z);
                int st = l.getBlockState(p);
                int blk = B.block(st);
                if (blk != powered && blk != activator) continue;
                PoweredRailBlock impl = (PoweredRailBlock) BlockBehaviors.ofBlock(blk);
                for (boolean forward : new boolean[] {true, false}) {
                    boolean expected = refFind(l, blk, p, st, forward, 0);
                    assertEquals(expected, impl.findPoweredRailSignal(l, p, st, forward, 0), "layout " + layout + " " + p + " fwd=" + forward);
                    compared++;
                    if (expected) trueCount++;
                }
            }
        }
        assertTrue(compared > 5000, "비교 수 " + compared);
        assertTrue(trueCount > 100 && trueCount < compared - 100, "참/거짓이 고루 나와야 한다: " + trueCount + "/" + compared);
    }
}
