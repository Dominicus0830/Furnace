package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 설치 경로(useItemOn → BlockItem.place)와 setBlock 의 이웃 알림·방송 모으기. */
public class BlockPlacementTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 슈퍼플랫(지면 y=-61 잔디) 몇 청크를 가진 레벨. */
    public static class TestLevel extends ChunkLevel {
        final Map<Long, ChunkColumn> chunks = new HashMap<>();
        final List<Packet> broadcasts = new ArrayList<>();
        final SuperflatWorldSource gen = new SuperflatWorldSource(BlockRegistry.load("blocks/1.21.1.txt"), 0);

        public TestLevel() { super(1000000); }

        @Override
        protected ChunkColumn chunk(int x, int z) {
            if (Math.abs(x) > 2 || Math.abs(z) > 2) return null;
            return chunks.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL), k -> gen.generate(x, z));
        }

        /**
         * 시험용: 유한 바닥(청크 ±2)의 둘레에 벽을 둘러 몹이 난수로 걸어 나가 허공에서 죽지 않게 한다.
         * 벽은 x,z = -30 / 45, 높이 -60..-49.
         */
        public void wallOffArena() {
            int stone = org.domi.furnace.server.block.B.defaultState(org.domi.furnace.server.block.B.id("minecraft:stone"));
            for (int a = -30; a <= 45; a++) for (int y = -60; y <= -49; y++) {
                setBlock(new Pos(a, y, -30), stone, 2);
                setBlock(new Pos(a, y, 45), stone, 2);
                setBlock(new Pos(-30, y, a), stone, 2);
                setBlock(new Pos(45, y, a), stone, 2);
            }
        }

        /** 시험용: 그 청크(없으면 만든다). */
        public ChunkColumn column(int x, int z) { return chunk(x, z); }

        @Override protected void markDirty(int x, int z) {}
        @Override protected void broadcastToChunk(int x, int z, Packet p) { broadcasts.add(p); }
        @Override protected LevelServices services() { return LevelServices.DEFAULT; }
    }

    static class Actor implements BlockActor {
        final ItemStack[] hands = {ItemStack.EMPTY, ItemStack.EMPTY};
        boolean creative, sneaking;
        double x = 0.5, y = -60, z = 0.5;
        float yRot, xRot;
        final List<String> triggers = new ArrayList<>();

        public float yRot() { return yRot; }
        public float xRot() { return xRot; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public double eyeHeight() { return sneaking ? 1.27 : 1.62; }
        public boolean isSecondaryUseActive() { return sneaking; }
        public boolean mayBuild() { return true; }
        public boolean instabuild() { return creative; }
        public boolean isSpectator() { return false; }
        public boolean isCreative() { return creative; }
        public int permissionLevel() { return 0; }
        public ItemStack itemInHand(int hand) { return hands[hand]; }
        public void setItemInHand(int hand, ItemStack s) { hands[hand] = s; }
        public Object handle() { return this; }
        @Override public void trigger(String c, Pos pos, ItemStack s) { triggers.add(c); }
    }

    static int item(String name) { return ItemContext.id("minecraft:item", name); }

    static final Blocks B = Blocks.get();

    @Test
    void placeOnTopFace_setsBlock_consumesOne_andTriggers() {
        TestLevel level = new TestLevel();
        Actor a = new Actor();
        a.x = 3.5; a.z = 3.5;
        a.hands[0] = new ItemStack(item("minecraft:stone"), 5);
        Pos ground = new Pos(1, -61, 1);
        InteractionResult r = BlockPlacement.useItemOn(level, a, 0, new Hit(ground, Dir.UP, 1.5, -60, 1.5, false));
        assertEquals(InteractionResult.CONSUME, r);
        assertEquals(B.defaultState("minecraft:stone"), level.getBlockState(ground.above()));
        assertEquals(4, a.hands[0].count());
        assertEquals(List.of("minecraft:placed_block", "minecraft:item_used_on_block"), a.triggers);
        // 변경은 틱 끝에 모아서 — 한 칸이면 블록 갱신 하나
        assertTrue(level.broadcasts.isEmpty());
        level.broadcastChanges();
        assertEquals(1, level.broadcasts.size());
    }

    @Test
    void creative_doesNotConsume() {
        TestLevel level = new TestLevel();
        Actor a = new Actor();
        a.creative = true;
        a.x = 3.5; a.z = 3.5;
        a.hands[0] = new ItemStack(item("minecraft:stone"), 5);
        BlockPlacement.useItemOn(level, a, 0, new Hit(new Pos(1, -61, 1), Dir.UP, 1.5, -60, 1.5, false));
        assertEquals(5, a.hands[0].count());
    }

    @Test
    void replaceableClickedBlock_isReplacedInPlace() {
        TestLevel level = new TestLevel();
        Pos grass = new Pos(1, -60, 1);
        level.setBlock(grass, B.defaultState("minecraft:short_grass"), 3);
        Actor a = new Actor();
        a.x = 3.5; a.z = 3.5;
        a.hands[0] = new ItemStack(item("minecraft:stone"), 1);
        BlockPlacement.useItemOn(level, a, 0, new Hit(grass, Dir.NORTH, 1.5, -59.5, 1.0, false));
        assertEquals(B.defaultState("minecraft:stone"), level.getBlockState(grass));
        assertEquals(B.defaultState("minecraft:air"), level.getBlockState(grass.north()));
        assertTrue(a.hands[0].isEmpty());
    }

    @Test
    void blockedByPlayerBox_fails() {
        TestLevel level = new TestLevel() {
            @Override public boolean isUnobstructed(Shape s) { return !s.intersects(0, -60, 0, 0.6, -58.2, 0.6); }
        };
        Actor a = new Actor();
        a.hands[0] = new ItemStack(item("minecraft:stone"), 1);
        InteractionResult r = BlockPlacement.useItemOn(level, a, 0, new Hit(new Pos(0, -61, 0), Dir.UP, 0.5, -60, 0.5, false));
        assertEquals(InteractionResult.FAIL, r);
        assertEquals(B.defaultState("minecraft:air"), level.getBlockState(new Pos(0, -60, 0)));
    }

    @Test
    void outsideBuildHeight_fails() {
        TestLevel level = new TestLevel();
        Actor a = new Actor();
        a.hands[0] = new ItemStack(item("minecraft:stone"), 1);
        level.setBlock(new Pos(0, 319, 0), B.defaultState("minecraft:stone"), 3);
        InteractionResult r = BlockPlacement.useItemOn(level, a, 0, new Hit(new Pos(0, 319, 0), Dir.UP, 0.5, 320, 0.5, false));
        assertEquals(InteractionResult.FAIL, r);
    }

    @Test
    void neighborsAreNotifiedInUpdateOrder_withOldBlockAsSource() {
        List<String> seen = new ArrayList<>();
        // MultiNeighborUpdate 는 실행 때 상태를 읽어 neighborChanged 를 부른다 — 위치 순서를 행동 대신 기록
        Pos c = new Pos(0, -55, 0);
        NeighborUpdater u = new NeighborUpdater(new TestLevel() {
            @Override public int getBlockState(Pos p) {
                if (!p.equals(c)) seen.add(Dir.NAME[dirOf(c, p)]);
                return super.getBlockState(p);
            }
        }, 100);
        u.updateNeighborsAtExceptFromFacing(c, B.block("minecraft:air"), -1);
        assertEquals(List.of("west", "east", "down", "up", "north", "south"), seen);
    }

    private static int dirOf(Pos from, Pos to) {
        for (int d = 0; d < 6; d++) if (from.relative(d).equals(to)) return d;
        return -1;
    }
}
