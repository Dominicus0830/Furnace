package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * T-204 2단계(설계 V1·V2): 쪼갠 setBlock 조각(FAWE NativeAccess 순서)이 서버 {@code Level#setBlock(…, 3)} 와 같은 세계를 만드는가, 그리고 스레드 규칙.
 * 두 자리(A = setBlock, B = 쪼갠 조각)에 같은 무대를 조용히 깔고 같은 블록을 놓은 뒤 몇 틱 돌려 둘레 칸을 서버에서 직접 읽어 비교한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsSideEffectsTest {

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    int baseX, baseZ;
    static final int Y = 150;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Effects");
        server.tick(3);
        var loc = tp.player().location();
        baseX = Math.floorDiv((int) Math.floor(loc.x()), 16) * 16;
        baseZ = Math.floorDiv((int) Math.floor(loc.z()), 16) * 16;
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        // 장면 자리 전부를 올려 두고 틱이 돌게(플레이어 시야와 무관하게)
        server.execute("forceload add " + (baseX - 16) + " " + (baseZ - 48) + " " + (baseX + 47) + " " + (baseZ + 63));
        server.tick(5);
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(BlockPos p, Supplier<T> task) { return server.onRegion(new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ()), task); }

    void run(BlockPos p, Runnable task) { on(p, () -> { task.run(); return null; }); }

    /** 서버 칸(주인 region 에서 직접 — NMS 읽기는 맞춤 지점이라 쓰지 않는다). */
    int truth(int x, int y, int z) {
        return on(new BlockPos(x, y, z), () -> server.internals().world().regionizer().ownerOf(x >> 4, z >> 4).getBlock(x, y, z));
    }

    /** 무대 깔기: 이웃 갱신·모양 갱신 없이(플래그 2|16). */
    void quiet(BlockPos p, BlockState s) { run(p, () -> level.setBlock(p, s, 2 | 16)); }

    /** FAWE WNA 순서로 쪼갠 조각(서버 ChunkLevel.setBlock 과 같은 순서 — 바닐라 Level.setBlock 플래그 3). */
    void split(BlockPos p, BlockState s) {
        run(p, () -> {
            LevelChunk c = level.getChunkAt(p);
            BlockState old = c.getBlockState(p);
            if (c.setBlockState(p, s, false) == null) return;
            BlockState now = level.getBlockState(p);
            if (now != s) return;
            level.sendBlockUpdated(p, old, s, 3);
            level.blockUpdated(p, old.getBlock());
            if (s.hasAnalogOutputSignal()) level.updateNeighbourForOutputSignal(p, s.getBlock());
            old.updateIndirectNeighbourShapes(level, p, 2, 511);
            s.updateNeighbourShapes(level, p, 2, 511);
            s.updateIndirectNeighbourShapes(level, p, 2, 511);
            level.onBlockStateChange(p, old, now);
        });
    }

    void vanilla(BlockPos p, BlockState s) { run(p, () -> level.setBlock(p, s, 3)); }

    /** 원점 둘레 상자(±4, 아래 3·위 4)의 서버 상태 번호. */
    int[] box(BlockPos o) {
        int[] out = new int[9 * 8 * 9];
        for (int dz = -4; dz <= 4; dz++) {
            for (int dx = -4; dx <= 4; dx++) {
                int x = o.getX() + dx, z = o.getZ() + dz, col = (dz + 4) * 9 + dx + 4;
                on(new BlockPos(x, o.getY(), z), () -> {
                    var r = server.internals().world().regionizer().ownerOf(x >> 4, z >> 4);
                    for (int dy = -3; dy <= 4; dy++) out[(dy + 3) * 81 + col] = r.getBlock(x, o.getY() + dy, z);
                    return null;
                });
            }
        }
        return out;
    }

    /** 장면 번호 k 의 두 자리(A, B) 원점 — 서로 떨어져 서로의 물·신호가 닿지 않는다. */
    BlockPos siteA(int k) { return new BlockPos(baseX + 4, Y, baseZ + 8 + 10 * (k - 4)); }

    BlockPos siteB(int k) { return new BlockPos(baseX + 4 + 32, Y, baseZ + 8 + 10 * (k - 4)); }   // A 와 청크 안 위치가 같다

    void platform(BlockPos o) {
        for (int dz = -2; dz <= 2; dz++)
            for (int dx = -2; dx <= 2; dx++) quiet(o.offset(dx, 0, dz), Blocks.STONE.defaultBlockState());
    }

    interface Scene { void build(BlockPos o); }

    /** 같은 장면을 A·B 에 깔고, A 는 setBlock, B 는 쪼갠 조각으로 at 에 s 를 놓은 뒤 ticks 틱 돌려 비교. */
    void compare(int k, Scene scene, BlockPos rel, BlockState s, int ticks) {
        BlockPos a = siteA(k), b = siteB(k);
        scene.build(a);
        scene.build(b);
        server.tick(2);
        assertArrayEquals(box(a), box(b), "깐 무대가 같다");
        vanilla(a.offset(rel.getX(), rel.getY(), rel.getZ()), s);
        split(b.offset(rel.getX(), rel.getY(), rel.getZ()), s);
        assertArrayEquals(box(a), box(b), "놓은 직후");
        server.tick(ticks);
        assertArrayEquals(box(a), box(b), ticks + "틱 뒤");
    }

    /** 레드스톤 블록 옆 선: 이웃 갱신(blockUpdated)으로 선 세기가 같게 오른다. */
    @Test
    void redstonePower() {
        compare(0, o -> {
            platform(o);
            for (int i = 0; i < 3; i++) quiet(o.offset(i, 1, 0), Blocks.REDSTONE_WIRE.defaultBlockState());
        }, new BlockPos(-1, 1, 0), Blocks.REDSTONE_BLOCK.defaultBlockState(), 4);
        assertNotEquals(Blocks.REDSTONE_WIRE.defaultBlockState().furnaceStateId(), truth(siteB(0).getX(), Y + 1, siteB(0).getZ()), "선이 켜졌다");
    }

    /** 물 원천: 놓기 반응(onPlace)이 유체 틱을 예약해 같은 틱에 같은 만큼 흐른다. */
    @Test
    void waterFlows() {
        compare(1, this::platform, new BlockPos(0, 1, 0), Blocks.WATER.defaultBlockState(), 25);
        assertNotEquals(0, truth(siteB(1).getX() + 1, Y + 1, siteB(1).getZ()), "옆으로 흘렀다");
    }

    /** 받침을 치우면 횃불이 모양 갱신(updateNeighbourShapes)으로 떨어진다. */
    @Test
    void torchLosesSupport() {
        compare(2, o -> {
            platform(o);
            quiet(o.offset(0, 1, 0), Blocks.STONE.defaultBlockState());
            quiet(o.offset(0, 2, 0), Blocks.TORCH.defaultBlockState());
        }, new BlockPos(0, 1, 0), Blocks.AIR.defaultBlockState(), 2);
        assertEquals(0, truth(siteB(2).getX(), Y + 2, siteB(2).getZ()), "횃불이 없어졌다");
    }

    /** 관측기 앞 칸이 바뀌면 모양 갱신으로 관측기가 틱을 예약해 깜빡인다. */
    @Test
    void observerFires() {
        compare(3, o -> {
            platform(o);
            quiet(o.offset(0, 1, 0), Blocks.OBSERVER.defaultBlockState());
            quiet(o.offset(0, 1, -1), Blocks.REDSTONE_WIRE.defaultBlockState());
        }, new BlockPos(0, 1, 1), Blocks.STONE.defaultBlockState(), 1);
        server.tick(3);
        assertArrayEquals(box(siteA(3)), box(siteB(3)), "깜빡임이 끝난 뒤");
    }

    /** Paper setBlockState(…, doPlace=false): 놓기 반응이 없어 물이 흐르지 않는다(블록은 놓인다). */
    @Test
    void setBlockStateWithoutPlace() {
        BlockPos o = siteA(4);
        platform(o);
        BlockPos p = o.offset(0, 1, 0);
        run(p, () -> level.getChunkAt(p).setBlockState(p, Blocks.WATER.defaultBlockState(), false, false));
        server.tick(20);
        assertEquals(Blocks.WATER.defaultBlockState().furnaceStateId(), truth(p.getX(), p.getY(), p.getZ()));
        assertEquals(0, truth(p.getX() + 1, p.getY(), p.getZ()), "흐르지 않았다");
    }

    /** blockChanged·sendBlockUpdated: 조용히 바꾼 칸이 틱 끝 변경 방송으로 접속자에게 간다. */
    @Test
    void blockChangedBroadcasts() {
        BlockPos p = siteA(5).offset(0, 1, 0);
        var apiPos = new org.domi.furnace.api.world.BlockPos(p.getX(), p.getY(), p.getZ());
        run(p, () -> level.getChunkAt(p).setBlockState(p, Blocks.STONE.defaultBlockState(), false));
        server.tick(2);
        tp.flushReceived();
        tp.clearReceived();
        server.tick(2);
        tp.flushReceived();
        assertTrue(tp.received(PacketTypes.Clientbound.BLOCK_UPDATE).stream().noneMatch(u -> u.pos().equals(apiPos)), "조용한 쓰기는 보내지 않는다");
        run(p, () -> level.getChunkSource().blockChanged(p));
        server.tick(2);
        tp.awaitPacket(PacketTypes.Clientbound.BLOCK_UPDATE, u -> u.pos().equals(apiPos));
        BlockPos q = p.offset(1, 0, 0);
        var apiQ = new org.domi.furnace.api.world.BlockPos(q.getX(), q.getY(), q.getZ());
        run(q, () -> level.getChunkAt(q).setBlockState(q, Blocks.STONE.defaultBlockState(), false));
        run(q, () -> level.sendBlockUpdated(q, Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), 3));
        server.tick(2);
        tp.awaitPacket(PacketTypes.Clientbound.BLOCK_UPDATE, u -> u.pos().equals(apiQ));
    }

    /** 값 조각: triggerEvent·updateFromNeighbourShapes 는 주인에서 서버 값, 틱 스레드가 아니면 기다려 같은 값, 다른 region 스레드는 예외. */
    @Test
    void valueEffectsAndThreads() throws Exception {
        BlockPos o = siteA(6);
        platform(o);
        BlockPos note = o.offset(0, 1, 0);
        quiet(note, Blocks.NOTE_BLOCK.defaultBlockState());
        assertTrue(on(note, () -> level.getBlockState(note).triggerEvent(level, note, 0, 0)), "음 블록은 블록 이벤트를 처리한다");
        assertFalse(on(note, () -> Blocks.STONE.defaultBlockState().triggerEvent(level, note, 0, 0)), "돌은 처리하지 않는다");

        BlockPos fence = o.offset(1, 1, 0);
        quiet(o.offset(2, 1, 0), Blocks.STONE.defaultBlockState());
        BlockState plain = Blocks.OAK_FENCE.defaultBlockState();
        BlockState shaped = on(fence, () -> Block.updateFromNeighbourShapes(plain, level, fence));
        assertNotEquals(plain, shaped, "옆 돌에 이어진다");
        var rl = server.internals().world().regionizer().ownerOf(fence.getX() >> 4, fence.getZ() >> 4).level();
        int expect = on(fence, () -> org.domi.furnace.server.block.B.updateFromNeighbourShapes(plain.furnaceStateId(), rl,
                new org.domi.furnace.server.block.Pos(fence.getX(), fence.getY(), fence.getZ())));
        assertEquals(expect, shaped.furnaceStateId(), "서버 계산과 같다");

        // 틱 스레드가 아닌 스레드: 주인을 기다려 같은 값
        var got = new Object[1];
        Thread t = new Thread(() -> {
            try {
                got[0] = Block.updateFromNeighbourShapes(plain, level, fence);
            } catch (Throwable e) {
                got[0] = e;
            }
        }, "worker");
        t.start();
        for (int i = 0; i < 200 && t.isAlive(); i++) {
            server.tick();
            t.join(20);
        }
        t.join(5_000);
        assertEquals(shaped, got[0]);

        // 다른 region 스레드: 교착을 피해 예외. 값 없는 조각은 넘기고 돌아온다
        BlockPos far = new BlockPos((baseX + 4000), Y, baseZ);
        server.loadChunk(far.getX() >> 4, far.getZ() >> 4);
        assertThrows(ThreadOwnershipException.class, () -> on(far, () -> Blocks.NOTE_BLOCK.defaultBlockState().triggerEvent(level, note, 0, 0)));
        assertThrows(IllegalStateException.class, () -> on(note, () -> Blocks.NOTE_BLOCK.defaultBlockState().triggerEvent(level, new BlockPos(baseX + 90_000, Y, baseZ), 0, 0)),
                "로드 안 된 원점은 부르는 순간 예외");
    }

    /** 다른 스레드: 청크 쓰기(거울 일지) 뒤 부수 효과(주인 작업)가 그 쓰기 뒤에 돌아 setBlock 과 같은 결과가 된다. */
    @Test
    void offThreadEffectAfterJournaledWrite() throws Exception {
        BlockPos a = siteA(7), b = siteB(7);
        for (BlockPos o : new BlockPos[] {a, b}) {
            platform(o);
            for (int i = 0; i < 3; i++) quiet(o.offset(i, 1, 0), Blocks.REDSTONE_WIRE.defaultBlockState());
        }
        server.tick(2);
        BlockPos pa = a.offset(-1, 1, 0), pb = b.offset(-1, 1, 0);
        vanilla(pa, Blocks.REDSTONE_BLOCK.defaultBlockState());
        LevelChunk chunk = on(pb, () -> level.getChunkAt(pb));
        var err = new Throwable[1];
        Thread t = new Thread(() -> {
            try {
                BlockState old = chunk.getBlockState(pb);
                chunk.setBlockState(pb, Blocks.REDSTONE_BLOCK.defaultBlockState(), false);   // 다른 스레드 — 거울 일지
                level.blockUpdated(pb, old.getBlock());                                          // 주인 작업 — 일지 재생 뒤
                Blocks.REDSTONE_BLOCK.defaultBlockState().updateNeighbourShapes(level, pb, 2, 511);
            } catch (Throwable e) {
                err[0] = e;
            }
        }, "worker");
        t.start();
        t.join(5_000);
        if (err[0] != null) throw new AssertionError(err[0]);
        server.tick(6);
        assertArrayEquals(box(a), box(b), "다른 스레드 조각 = setBlock");
        assertNotEquals(Blocks.REDSTONE_WIRE.defaultBlockState().furnaceStateId(), truth(b.getX(), Y + 1, b.getZ()), "선이 켜졌다");
    }

    /** 두 칸의 청크를 같은 region 이 가졌는가. */
    boolean sameOwner(BlockPos p, BlockPos q) {
        return on(p, () -> server.internals().world().regionizer().ownerOf(p.getX() >> 4, p.getZ() >> 4)
                == server.internals().world().regionizer().ownerOf(q.getX() >> 4, q.getZ() >> 4));
    }

    /** 청크 경계를 사이에 둔 (원천, 등) 두 칸 — 같은 region 의 것. */
    BlockPos[] acrossChunkEdge(int z) {
        BlockPos src = new BlockPos(baseX + 15, Y, z), lamp = src.east();
        if (!sameOwner(src, lamp)) {
            src = new BlockPos(baseX, Y, z);
            lamp = src.west();
        }
        assertTrue(sameOwner(src, lamp), "경계 두 청크가 같은 region");
        return new BlockPos[] {src, lamp};
    }

    /**
     * 맞춤 지점(검토 #472): 주인 스레드가 자기 작업 안에서 다른 스레드의 이웃 청크 쓰기(거울 일지)를 기다린 뒤 바로 이웃 갱신을 부르면, 그 쓰기의 맞춤 작업은
     * 아직 돌지 않았다. 효과 전 둘레 청크 맞춤이 그 쓰기를 먼저 서버에 넣어 옆 청크의 등이 켜진다(맞춤을 빼면 공기에 알려 등이 꺼진 채 남는다).
     */
    @Test
    void neighborChunkSyncedBeforeEffect() throws Exception {
        BlockPos[] e = acrossChunkEdge(baseZ + 48);
        BlockPos src = e[0], lamp = e[1];
        quiet(src, Blocks.REDSTONE_BLOCK.defaultBlockState());
        server.tick(2);
        LevelChunk lampChunk = on(lamp, () -> level.getChunkAt(lamp));
        BlockState unlit = Blocks.REDSTONE_LAMP.defaultBlockState();
        var err = new Throwable[1];
        run(src, () -> {
            Thread t = new Thread(() -> {
                try {
                    lampChunk.setBlockState(lamp, unlit, false);   // 다른 스레드 — 거울 일지(맞춤 작업은 이 주인 작업 뒤에 줄 선다)
                } catch (Throwable ex) {
                    err[0] = ex;
                }
            }, "worker");
            t.start();
            try {
                t.join(5_000);
            } catch (InterruptedException ex) {
                throw new AssertionError(ex);
            }
            assertFalse(t.isAlive(), "다른 스레드 쓰기는 주인을 기다리지 않는다");
            level.updateNeighborsAt(src, Blocks.REDSTONE_BLOCK);   // 주인 스레드 — 바로
        });
        if (err[0] != null) throw new AssertionError(err[0]);
        server.tick(2);
        int got = truth(lamp.getX(), lamp.getY(), lamp.getZ());
        assertEquals(Blocks.REDSTONE_LAMP, Block.stateById(got).getBlock(), "등이 놓였다");
        assertNotEquals(unlit.furnaceStateId(), got, "옆 청크의 등이 켜졌다");
    }

    /** 값 없는 조각의 가장자리 규칙: 로드 안 된 원점은 무시, 가짜 레벨은 UnsupportedNmsCall, 다른 region 스레드는 넘기고 돌아와 주인이 한다, 실패는 한 번 경고. */
    @Test
    void voidEffectEdges() throws Exception {
        // 로드 안 된 원점: 어느 스레드든 아무것도 하지 않는다
        BlockPos unloaded = new BlockPos(baseX + 90_000, Y, baseZ);
        assertDoesNotThrow(() -> level.updateNeighborsAt(unloaded, Blocks.STONE));
        BlockPos o = siteA(6);
        assertDoesNotThrow(() -> run(o, () -> level.updateNeighborsAt(unloaded, Blocks.STONE)));

        // 가짜 레벨(FAWE 의 PaperweightLevelProxy 처럼 생성자 없이 만든 ServerLevel — 월드가 없다)
        var uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        ServerLevel fake = (ServerLevel) ((sun.misc.Unsafe) uf.get(null)).allocateInstance(ServerLevel.class);
        assertThrows(UnsupportedNmsCall.class, () -> fake.updateNeighborsAt(o, Blocks.STONE));
        assertThrows(UnsupportedNmsCall.class, () -> Blocks.NOTE_BLOCK.defaultBlockState().triggerEvent(fake, o, 0, 0));

        // 다른 region 스레드: 기다리지 않고 넘긴다 — 다음 틱에 주인이 이웃 갱신을 해 등이 켜진다
        BlockPos[] e = acrossChunkEdge(baseZ + 58);
        BlockPos src = e[0], lamp = e[1];
        quiet(src, Blocks.REDSTONE_BLOCK.defaultBlockState());
        quiet(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        server.tick(2);
        assertEquals(Blocks.REDSTONE_LAMP.defaultBlockState().furnaceStateId(), truth(lamp.getX(), Y, lamp.getZ()), "조용히 깐 등은 꺼져 있다");
        BlockPos far = new BlockPos(baseX + 4000, Y, baseZ);
        server.loadChunk(far.getX() >> 4, far.getZ() >> 4);
        assertDoesNotThrow(() -> run(far, () -> level.updateNeighborsAt(src, Blocks.REDSTONE_BLOCK)));
        server.tick(2);
        assertNotEquals(Blocks.REDSTONE_LAMP.defaultBlockState().furnaceStateId(), truth(lamp.getX(), Y, lamp.getZ()), "주인이 넘겨받아 등이 켜졌다");

        // 넘긴 작업이 주인에서 실패하면 플러그인·조각마다 한 번 경고
        NmsEffects.resetWarnings();
        BlockPos p = o.offset(0, 1, 0);
        Thread t = new Thread(() -> NmsEffects.run(level, p, NmsEffects.ON_PLACE, -5, 0, 0, 0L, "test#badState"), "worker");
        t.start();
        t.join(5_000);
        for (int i = 0; i < 100 && !NmsEffects.warned("test#badState"); i++) server.tick();
        assertTrue(NmsEffects.warned("test#badState"), "실패 경고");
    }
}
