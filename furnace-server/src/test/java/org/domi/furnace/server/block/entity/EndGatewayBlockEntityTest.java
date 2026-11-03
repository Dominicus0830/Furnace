// 엔드 관문(바닐라 TheEndGatewayBlockEntity·EndGatewayBlock): 저장, 나이·쿨다운 틱, 출구 찾기(walk·엔드 돌 칸·섬 만들기·출구 관문),
// 엔티티·엔더 진주 순간이동.
package org.domi.furnace.server.block.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Pig;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.ThrownEnderpearl;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.DimensionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndGatewayBlockEntityTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    /** 엔드 높이(0~255)의 빈(공기) 청크가 어디에나 있는 시험 레벨. */
    static final class EndLevel extends ChunkLevel {
        final Map<Long, ChunkColumn> chunks = new HashMap<>();
        String dimension = EndGatewayBlockEntity.THE_END;
        long time;

        EndLevel() { super(1000000); }

        @Override
        protected ChunkColumn chunk(int x, int z) {
            return chunks.computeIfAbsent((long) x << 32 | (z & 0xFFFFFFFFL),
                    k -> new ChunkColumn(0, REG.directBits(), ChunkColumn.DEFAULT_BIOME_DIRECT_BITS, 0, 16));
        }

        @Override protected void markDirty(int x, int z) {}

        @Override protected void broadcastToChunk(int x, int z, Packet p) {}

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public String dimension() { return dimension; }
                @Override public DimensionType dimensionType() { return DimensionType.END; }
                @Override public long gameTime() { return time; }
                @Override public void forEachPlayer(Consumer<Player> c) {}
            };
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
            int s = B.defaultState(block);
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) setBlock(new Pos(x, y, z), s, 2);
        }

        void tick(int n) {
            for (int t = 0; t < n; t++) {
                time++;
                tickBlockEntities(k -> true);
                for (Entity e : entities().tickSnapshot()) {
                    if (e.isRemoved()) continue;
                    e.setOldPosAndRot();
                    e.tickCount++;
                    e.tick();
                }
                runBlockEvents();
            }
        }
    }

    private static EndGatewayBlockEntity placeGateway(ChunkLevel level, Pos p) {
        EndGatewayBlockEntity.placeGatewayPortal(level, p, null, false);
        return (EndGatewayBlockEntity) level.getBlockEntity(p);
    }

    @Test
    void saveAndLoad() {
        var be = new EndGatewayBlockEntity(new Pos(1, 2, 3), B.defaultState("minecraft:end_gateway"));
        NbtCompound tag = new NbtCompound();
        tag.putLong("Age", 1234L);
        tag.putIntArray("exit_portal", new int[] {10, 70, -20});
        tag.putBoolean("ExactTeleport", true);
        be.load(tag);
        assertEquals(1234L, be.age());
        assertEquals(new Pos(10, 70, -20), be.exitPortal());
        assertTrue(be.exactTeleport());
        NbtCompound out = be.saveCustomOnly();
        assertEquals(1234L, out.getLong("Age"));
        assertArrayEquals(new int[] {10, 70, -20}, out.getIntArray("exit_portal"));
        assertTrue(out.getBoolean("ExactTeleport"));
        assertTrue(org.domi.furnace.server.net.io.nbt.NbtTags.equal(out, be.updateTag()), "청크·갱신 패킷은 saveCustomOnly");

        var fresh = new EndGatewayBlockEntity(Pos.ZERO, B.defaultState("minecraft:end_gateway"));
        NbtCompound empty = fresh.saveCustomOnly();
        assertEquals(0L, empty.getLong("Age"));
        assertFalse(empty.tags().containsKey("exit_portal"), "출구가 없으면 쓰지 않는다");
        assertFalse(empty.tags().containsKey("ExactTeleport"), "거짓이면 쓰지 않는다");
    }

    @Test
    void outOfBoundsExitIsIgnored() {
        var be = new EndGatewayBlockEntity(Pos.ZERO, B.defaultState("minecraft:end_gateway"));
        NbtCompound tag = new NbtCompound();
        tag.putIntArray("exit_portal", new int[] {30000000, 70, 0});
        be.load(tag);
        assertNull(be.exitPortal(), "isInSpawnableBounds 밖");
    }

    @Test
    void worldgenNbtLoadsAsBlockEntity() {
        NbtCompound tag = new NbtCompound();
        tag.putString("id", "minecraft:end_gateway");
        tag.putLong("Age", 0L);
        tag.put("exit_portal", new int[] {5, 6, 7});
        tag.putByte("ExactTeleport", (byte) 1);
        var be = BlockEntities.loadStatic(new Pos(0, 80, 0), B.defaultState("minecraft:end_gateway"), tag);
        assertInstanceOf(EndGatewayBlockEntity.class, be);
        assertEquals(new Pos(5, 6, 7), ((EndGatewayBlockEntity) be).exitPortal());
        assertTrue(((EndGatewayBlockEntity) be).exactTeleport());
    }

    @Test
    void ageAndAttentionCooldown() {
        var level = new EndLevel();
        var g = placeGateway(level, new Pos(0, 80, 0));
        assertTrue(g.isSpawning());
        level.tick(200);
        assertEquals(200L, g.age());
        assertFalse(g.isSpawning(), "200틱 뒤 빔이 다 솟았다");
        assertFalse(g.isCoolingDown());
        level.tick(2200);   // 나이 2400 — 주의 빔
        assertEquals(2400L, g.age());
        assertEquals(EndGatewayBlockEntity.COOLDOWN_TIME, g.teleportCooldown());
        level.tick(1);
        assertEquals(EndGatewayBlockEntity.COOLDOWN_TIME - 1, g.teleportCooldown(), "쿨다운이 한 틱 줄었다");
    }

    @Test
    void exitWalkStepsBackOutToFilledChunk() {
        var level = new EndLevel();
        Pos gateway = new Pos(96, 75, 0);
        // 1024 청크만 채움: 1단계에서 안쪽 1008(빔)로 갔다가 2단계에서 바깥 1024(채움)에서 멈춘다
        level.fill(1024, 50, 0, 1039, 50, 0, "minecraft:end_stone");
        var walk = new EndGatewayBlockEntity.ExitWalk(gateway);
        while (!walk.feed(EndGatewayBlockEntity.isChunkEmpty(level, walk.chunkX(), walk.chunkZ()))) { }
        assertEquals(1024.0, walk.x(), 1e-9);
        assertEquals(64, walk.chunkX());
    }

    @Test
    void exitWalkGoesOutwardWhileEmpty() {
        var level = new EndLevel();
        // 모두 비면 1024 에서 바깥으로 16번(1280) — 대각선 방향도 단위 벡터로
        var walk = new EndGatewayBlockEntity.ExitWalk(new Pos(96, 75, 0));
        while (!walk.feed(EndGatewayBlockEntity.isChunkEmpty(level, walk.chunkX(), walk.chunkZ()))) { }
        assertEquals(1280.0, walk.x(), 1e-9);
        var diag = new EndGatewayBlockEntity.ExitWalk(new Pos(-50, 75, 50));
        double s = 1.0 / Math.sqrt(2.0);
        assertEquals(-1024.0 * s, diag.x(), 1e-9);
        assertEquals(1024.0 * s, diag.z(), 1e-9);
        assertEquals((int) Math.floor(-1024.0 * s / 16.0), diag.chunkX());
    }

    @Test
    void exitWalkStepsInwardPastFilledChunks() {
        var level = new EndLevel();
        Pos gateway = new Pos(96, 75, 0);
        // 1024 부근 x 960~1039 를 모두 채움: 1단계에서 안쪽으로 1024 → 1008 → 992 → 976 → 960 → 944(빔), 2단계는 빈 칸에서 시작하므로
        // 바깥으로 한 번 960(채움)에서 멈춘다.
        level.fill(960, 50, 0, 1039, 50, 0, "minecraft:end_stone");
        var walk = new EndGatewayBlockEntity.ExitWalk(gateway);
        while (!walk.feed(EndGatewayBlockEntity.isChunkEmpty(level, walk.chunkX(), walk.chunkZ()))) { }
        assertEquals(960.0, walk.x(), 1e-9);
        assertEquals(60, walk.chunkX());
    }

    @Test
    void validSpawnPrefersEndStoneClosestToOrigin() {
        var level = new EndLevel();
        level.fill(960, 50, 0, 975, 50, 15, "minecraft:end_stone");
        level.fill(960, 51, 0, 960, 51, 15, "minecraft:stone");   // x 960 줄은 위가 막혀 안 된다
        Pos p = EndGatewayBlockEntity.findValidSpawnInChunk(level, 60, 0);
        assertEquals(new Pos(961, 50, 0), p, "(0,0,0) 에서 가장 가까운 칸");
        assertNull(EndGatewayBlockEntity.findValidSpawnInChunk(level, 61, 0), "엔드 돌이 없는 청크");
    }

    @Test
    void tallestBlockSkipsBedrockUnlessAllowed() {
        var level = new EndLevel();
        level.fill(-5, 60, -5, 5, 60, 5, "minecraft:end_stone");
        level.setBlock(new Pos(2, 90, 0), B.defaultState("minecraft:bedrock"), 2);
        level.setBlock(new Pos(-1, 70, 0), B.defaultState("minecraft:end_stone"), 2);
        assertEquals(new Pos(-1, 70, 0), EndGatewayBlockEntity.findTallestBlock(level, Pos.ZERO, 5, false));
        assertEquals(new Pos(2, 90, 0), EndGatewayBlockEntity.findTallestBlock(level, Pos.ZERO, 5, true));
        assertEquals(new Pos(0, 3, 0), EndGatewayBlockEntity.findTallestBlock(level, new Pos(0, 3, 0), 0, false), "가운데만이고 거짓이면 그대로");
    }

    @Test
    void firstUseBuildsIslandAndExitGatewayAndTeleportsPig() {
        var level = new EndLevel();
        Pos gwPos = new Pos(96, 75, 0);
        var g = placeGateway(level, gwPos);
        level.tick(1);
        Pig pig = new Pig(level, level.nextEntityId());
        pig.moveTo(96.5, 75.0, 0.5, 30.0F, 0.0F);
        level.addFreshEntity(pig);
        level.tick(1);   // entityInside → 대기 + 쿨다운
        assertTrue(g.isCoolingDown());
        level.tick(1);   // handlePortal → 이동
        assertNotNull(g.exitPortal(), "출구를 기록했다");
        Pos exit = g.exitPortal();
        // 바깥이 모두 비어 1024 에서 바깥으로 16번(1280) 간 뒤 그 청크에 섬을 만든다(엔드 돌 칸이 없다)
        assertEquals(1280.0, exit.x(), 7.0, "섬(반지름 ≤ 7) 위");
        assertEquals(0.0, exit.z(), 7.0);
        assertEquals(85, exit.y(), "섬 꼭대기 75 + 10");
        assertTrue(B.is(level.getBlockState(exit), "minecraft:end_gateway"), "출구 관문");
        assertTrue(B.is(level.getBlockState(exit.above(2)), "minecraft:bedrock"));
        assertTrue(B.is(level.getBlockState(exit.below(2)), "minecraft:bedrock"));
        var back = (EndGatewayBlockEntity) level.getBlockEntity(exit);
        assertEquals(gwPos, back.exitPortal(), "출구 관문은 처음 관문으로 돌아간다");
        assertFalse(back.exactTeleport());
        assertTrue(B.is(level.getBlockState(new Pos(1280, 75, 0)), "minecraft:end_stone"), "섬 가운데");
        assertTrue(pig.isOnPortalCooldown());
        assertTrue(Math.abs(pig.x() - exit.x() - 0.5) <= 5.0 && Math.abs(pig.z() - exit.z() - 0.5) <= 5.0, "출구 둘레 5 안: " + pig.x() + "," + pig.z());
        assertTrue(pig.y() > 75.0, "섬 위");
        assertEquals(30.0F, pig.yRot(), 1e-6, "회전은 그대로");
    }

    @Test
    void knownExitAndExactTeleport() {
        var level = new EndLevel();
        Pos gwPos = new Pos(5, 80, 5);
        var g = placeGateway(level, gwPos);
        g.setExitPosition(level, new Pos(40, 100, 40), true);
        level.setBlock(new Pos(40, 99, 40), B.defaultState("minecraft:stone"), 2);
        Pig pig = new Pig(level, level.nextEntityId());
        pig.moveTo(5.5, 80.0, 5.5, 0.0F, 0.0F);
        level.addFreshEntity(pig);
        level.tick(3);
        assertEquals(40.5, pig.x(), 1e-9);
        assertEquals(100.0, pig.y(), 1e-9, "정확 이동: 그 칸 바닥 가운데");
        assertEquals(40.5, pig.z(), 1e-9);
    }

    @Test
    void notEndAndNoExitDoesNothing() {
        var level = new EndLevel();
        level.dimension = "minecraft:overworld";
        Pos gwPos = new Pos(5, 80, 5);
        var g = placeGateway(level, gwPos);
        Pig pig = new Pig(level, level.nextEntityId());
        pig.moveTo(5.5, 80.0, 5.5, 0.0F, 0.0F);
        level.addFreshEntity(pig);
        level.tick(3);
        assertNull(g.exitPortal());
        assertEquals(5.5, pig.x(), 1e-9, "출구가 없으면 그대로");
        assertTrue(g.isCoolingDown(), "쿨다운은 걸린다");
    }

    @Test
    void cooldownBlocksSecondEntity() {
        var level = new EndLevel();
        Pos gwPos = new Pos(5, 80, 5);
        var g = placeGateway(level, gwPos);
        g.setExitPosition(level, new Pos(40, 100, 40), true);
        Pig a = new Pig(level, level.nextEntityId());
        a.moveTo(5.5, 80.0, 5.5, 0.0F, 0.0F);
        Pig b = new Pig(level, level.nextEntityId());
        b.moveTo(5.5, 80.0, 5.5, 0.0F, 0.0F);
        level.addFreshEntity(a);
        level.addFreshEntity(b);
        level.tick(3);
        boolean aMoved = a.x() > 30, bMoved = b.x() > 30;
        assertTrue(aMoved ^ bMoved, "같은 틱에는 하나만(쿨다운 40)");
    }

    @Test
    void enderPearlExitsDownward() {
        var level = new EndLevel();
        Pos gwPos = new Pos(5, 80, 5);
        var g = placeGateway(level, gwPos);
        g.setExitPosition(level, new Pos(40, 100, 40), true);
        ThrownEnderpearl pearl = new ThrownEnderpearl(level, level.nextEntityId());
        pearl.moveTo(3.5, 80.4, 5.5, 0.0F, 0.0F);
        pearl.setDeltaMovement(1.0, 0.0, 0.0);
        level.addFreshEntity(pearl);
        for (int i = 0; i < 5 && pearl.x() < 30; i++) level.tick(1);
        assertEquals(40.5, pearl.x(), 0.6, "관문을 지나 출구로");
        assertEquals(40.5, pearl.z(), 1e-9);
        assertTrue(pearl.vy() < 0 && Math.abs(pearl.vx()) < 1e-9, "진주는 아래로 (0, -1, 0)");
    }
}
