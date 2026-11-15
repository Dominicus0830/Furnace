package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.MirrorTestSeam;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * N2b-4 거울의 경쟁 틈(PR #396 검토 R1~R3·R6). Paper 에서는 섹션 배열에 넣은 섹션의 내용·그 섹션에 쓴 칸이 그대로 월드에 있고, 배열에서 빠진 옛 섹션에
 * 쓴 칸은 월드에 없다. 장벽 호출은 변환된 플러그인 코드와 같은 정적 입구(NmsArrays·NmsReflect)를 시험 스레드(주인이 아닌 스레드)에서 바로 부른다.
 * <p>참값은 늘 <b>서버를 바로</b> 읽는다(Region.getBlock·ChunkColumn) — 주인 스레드의 NMS·Bukkit 읽기는 그 자체가 맞춤 지점이라 빠진 알림·맞추기를 가린다.
 * 틈은 두 가지로 결정적으로 만든다: 주인 작업 안에서 다른 스레드를 돌려 기다리기(그동안 주인은 맞추지 않는다), 시험 이음매(계획 뒤·들여오기 전).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsMirrorRaceTest {

    static final VarHandle OBJ = MethodHandles.arrayElementVarHandle(Object[].class);
    static final Field DATA = field(PalettedContainer.class, "data");
    static final Field BIOMES = field(LevelChunkSection.class, "biomes");
    static final Field NON_EMPTY = field(LevelChunkSection.class, "nonEmptyBlockCount");

    static Field field(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    int cx, cz;

    @BeforeAll
    void start() {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("MirrorRace");
        server.tick(3);
        var loc = tp.player().location();
        cx = Math.floorDiv((int) Math.floor(loc.x()), 16);
        cz = Math.floorDiv((int) Math.floor(loc.z()), 16);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) server.loadChunk(cx + dx, cz + dz);
        server.tick(2);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        AIR = Blocks.AIR.defaultBlockState();
        STONE = Blocks.STONE.defaultBlockState();
        GLASS = Blocks.GLASS.defaultBlockState();
        GOLD = Blocks.GOLD_BLOCK.defaultBlockState();
        DIAMOND = Blocks.DIAMOND_BLOCK.defaultBlockState();
    }

    @AfterAll
    void stop() {
        MirrorTestSeam.afterPlan(null);
        server.close();
    }

    @AfterEach
    void clearSeam() { MirrorTestSeam.afterPlan(null); }

    // ------------------------------------------------------------------ 도움

    <T> T on(int chunkX, int chunkZ, Supplier<T> task) {
        return server.onRegion(new org.domi.furnace.api.world.BlockPos(chunkX * 16, 64, chunkZ * 16), task);
    }

    Region region(int chunkX, int chunkZ) { return server.internals().world().regionizer().ownerOf(chunkX, chunkZ); }

    ChunkColumn column(int chunkX, int chunkZ) { return region(chunkX, chunkZ).loadedOwnedChunk(chunkX, chunkZ); }

    int y0(int si) { return level.getMinBuildHeight() + si * 16; }

    /** 서버 칸(주인 스레드에서 서버를 바로 — 맞춤 지점이 아니다). */
    int truth(int x, int y, int z) { return on(x >> 4, z >> 4, () -> region(x >> 4, z >> 4).getBlock(x, y, z)); }

    /** 이미 주인 스레드일 때 서버 칸. */
    int truthHere(int x, int y, int z) { return region(x >> 4, z >> 4).getBlock(x, y, z); }

    /** 섹션 하나의 서버 칸 4096개. */
    int[] truthSection(int qx, int qz, int si) {
        int y = y0(si);
        return on(qx, qz, () -> {
            var r = region(qx, qz);
            int[] out = new int[4096];
            for (int c = 0; c < 4096; c++) out[c] = r.getBlock(qx * 16 + (c & 15), y + (c >> 8), qz * 16 + ((c >> 4) & 15));
            return out;
        });
    }

    /** 서버 섹션의 바닐라 개수(맞춤 지점이 아니다). */
    int[] serverCounts(int qx, int qz, int si) { return on(qx, qz, () -> column(qx, qz).sectionVanillaCounts(si)); }

    /** 틱을 돌리지 않고 기다린다(주인 region 은 틱 사이에도 받은 작업을 돈다). */
    static void eventually(String what, BooleanSupplier cond) {
        long end = System.nanoTime() + 5_000_000_000L;
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > end) fail(what + " — 틱 없이 5초 안에 안 됨");
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    /** 다른 스레드(주인이 아닌)에서 돌리고 끝까지 기다린다 — 주인 작업 안에서 부르면 그동안 주인은 맞추지 않는다. */
    static <T> T offThread(Supplier<T> task) {
        AtomicReference<T> out = new AtomicReference<>();
        AtomicReference<Throwable> err = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                out.set(task.get());
            } catch (Throwable e) {
                err.set(e);
            }
        }, "mirror-race-writer");
        t.start();
        try {
            t.join(10_000);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
        if (t.isAlive()) fail("다른 스레드가 10초 안에 끝나지 않았다(교착?)");
        if (err.get() != null) throw new AssertionError("다른 스레드 실패", err.get());
        return out.get();
    }

    static void offThread(Runnable task) {
        offThread(() -> {
            task.run();
            return null;
        });
    }

    /** 그 청크의 다음 맞추기 하나(when 이 참인 첫 번)의 "계획 뒤·들여오기 전" 에 task 를 끼운다. 불렸으면 fired 가 참. */
    static AtomicBoolean seamOnce(LevelChunk c, BooleanSupplier when, Runnable task) {
        AtomicBoolean fired = new AtomicBoolean();
        MirrorTestSeam.afterPlan(chunk -> {
            if (chunk == c && !fired.get() && when.getAsBoolean() && fired.compareAndSet(false, true)) task.run();
        });
        return fired;
    }

    static LevelChunkSection emptySection(LevelChunkSection biomeDonor) {
        return new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES),
                biomeDonor.getBiomes());
    }

    /** FAWE ReflectionUtils.compareAndSet 과 같은 길(변환기가 돌린 정적 입구). */
    static boolean cas(LevelChunk c, int si, LevelChunkSection expected, LevelChunkSection fresh) {
        return NmsArrays.compareAndSet(OBJ, c.getSections(), si, expected, fresh);
    }

    /** 변환된 Field.get 과 같은 길(장벽 → 읽기). */
    static Object dataOf(PalettedContainer<?> c) {
        try {
            NmsReflect.beforeFieldGet(DATA, c);
            return DATA.get(c);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /** 변환된 Field.set 과 같은 길(쓰기 → 장벽). */
    static void setField(Field f, Object target, Object value) {
        try {
            f.set(target, value);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
        NmsReflect.afterFieldSet(f, target);
    }

    @SuppressWarnings("unchecked")
    static BlockState dataStateAt(PalettedContainer<BlockState> c, int x, int y, int z) {
        PalettedContainer.Data<BlockState> d = (PalettedContainer.Data<BlockState>) dataOf(c);
        return d.palette().valueFor(d.storage().get((y << 8) | (z << 4) | x));
    }

    static int[] countFields(LevelChunkSection s) {
        try {
            int[] out = new int[3];
            String[] names = {"nonEmptyBlockCount", "tickingBlockCount", "tickingFluidCount"};
            for (int i = 0; i < 3; i++) out[i] = field(LevelChunkSection.class, names[i]).getShort(s);
            return out;
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /** 블록 상태는 서버가 뜬 뒤에 얻는다(Blocks 초기화가 서버 레지스트리를 본다 — 클래스 초기화 때 얻으면 같은 JVM 의 다른 시험까지 깨진다). */
    BlockState AIR, STONE, GLASS, GOLD, DIAMOND;

    // ------------------------------------------------------------------ 검토 재현(R1~R3, 스트레스)

    /** R1: 다른 스레드가 새 섹션을 CAS 로 넣고 주인이 들여온 뒤(틱 전), 그 스레드가 자기가 만든 섹션을 다시 읽으면 넣은 내용이다(Paper). */
    @Test
    void r1_readYourOwnImportedSectionBeforeTick() {
        int si = 23;
        LevelChunk c = level.getChunk(cx, cz);
        int y = y0(si) + 5, x = cx * 16 + 4, z = cz * 16 + 11;
        server.tick(1);
        LevelChunkSection existing = c.getSections()[si];
        LevelChunkSection fresh = emptySection(existing);
        fresh.setBlockState(4, 5, 11, STONE);
        assertTrue(cas(c, si, existing, fresh));
        eventually("들여오기", () -> truth(x, y, z) == STONE.furnaceStateId());
        BlockState viaSection = fresh.getBlockState(4, 5, 11);
        BlockState viaChunk = c.getBlockState(new BlockPos(x, y, z));
        BlockState viaData = dataStateAt(fresh.getStates(), 4, 5, 11);
        server.tick(1);
        BlockState afterTick = fresh.getBlockState(4, 5, 11);
        assertTrue(cas(c, si, fresh, emptySection(fresh)));
        server.tick(1);
        String msg = "들여온 뒤 틱 전: section=" + viaSection + " chunk=" + viaChunk + " data=" + viaData + " / 틱 뒤 section=" + afterTick;
        assertSame(STONE, viaSection, msg);
        assertSame(STONE, viaChunk, msg);
        assertSame(STONE, viaData, msg);
        assertSame(STONE, afterTick, msg);
    }

    /** R2: 다른 스레드가 옛 섹션에 쓰는 동안 새 섹션으로 교체 — 교체 뒤 월드 칸 = 새 섹션 내용(옛 섹션에 쓴 칸은 배열 밖 객체에만). */
    @Test
    void r2_writesToReplacedSectionDoNotReachWorld() throws Exception {
        int qx = cx - 1, qz = cz + 1, si = 22;
        LevelChunk c = level.getChunk(qx, qz);
        int goldId = GOLD.furnaceStateId();
        int leakedTotal = 0;
        for (int trial = 0; trial < 6; trial++) {
            server.tick(1);
            LevelChunkSection existing = c.getSections()[si];
            AtomicBoolean stop = new AtomicBoolean();
            AtomicLong n = new AtomicLong();
            Thread w = new Thread(() -> {
                long k = 0;
                while (!stop.get() && k < 400_000) {
                    int cell = 2048 + (int) (k % 2048);   // 위쪽 절반(y 8..15)
                    existing.setBlockState(cell & 15, cell >> 8, (cell >> 4) & 15, GOLD);
                    n.set(++k);
                }
            }, "mirror-race-r2");
            w.start();
            while (n.get() < 3000) Thread.onSpinWait();
            LevelChunkSection fresh = emptySection(existing);
            fresh.setBlockState(1, 1, 1, STONE);   // 아래쪽 절반에 표지 하나
            assertTrue(cas(c, si, existing, fresh));
            eventually("들여오기", () -> truth(qx * 16 + 1, y0(si) + 1, qz * 16 + 1) == STONE.furnaceStateId());
            Thread.sleep(30);
            stop.set(true);
            w.join();
            server.tick(2);
            int leaked = 0;
            for (int v : truthSection(qx, qz, si)) if (v == goldId) leaked++;
            leakedTotal += leaked;
            LevelChunkSection cur = c.getSections()[si];
            assertTrue(cas(c, si, cur, emptySection(cur)));
            server.tick(2);
        }
        assertEquals(0, leakedTotal, "교체된 옛 섹션에 쓴 칸이 새 섹션 자리(월드)에 들어왔다");
    }

    /** R3: 다른 스레드가 새 섹션을 CAS 로 넣고 곧바로 그 섹션에 칸을 계속 쓴다 — 모든 쓰기가 월드에 있다(Paper). */
    @Test
    void r3_writesRightAfterPlacingSectionAreKept() {
        int qx = cx + 1, qz = cz + 1, si = 21;
        LevelChunk c = level.getChunk(qx, qz);
        BlockState[] vals = {GLASS, STONE};
        int lostTotal = 0;
        for (int trial = 0; trial < 40; trial++) {
            server.tick(1);
            BlockState v = vals[trial & 1];
            LevelChunkSection existing = c.getSections()[si];
            LevelChunkSection fresh = emptySection(existing);
            assertTrue(cas(c, si, existing, fresh));
            for (int cell = 0; cell < 4096; cell++) {
                fresh.setBlockState(cell & 15, cell >> 8, (cell >> 4) & 15, v);
                for (int s = 0; s < 40; s++) Thread.onSpinWait();
            }
            server.tick(2);
            for (int x : truthSection(qx, qz, si)) if (x != v.furnaceStateId()) lostTotal++;
            assertArrayEquals(countFields(fresh), serverCounts(qx, qz, si), "개수 필드 = 서버 바닐라 개수");
        }
        assertEquals(0, lostTotal, "넣은 섹션에 곧바로 쓴 칸을 잃었다");
    }

    // ------------------------------------------------------------------ 이음매로 결정적인 틈(R2·R3·M3)

    /** R2 결정적: 교체를 계획한 뒤 옛 섹션 떼어 내기 전에 그 옛 섹션에 쓴 칸 — 옛 객체에 남고 새 섹션 자리(월드)에는 없다(다음 맞추기에서도). */
    @Test
    void seamWriteToReplacedSectionStaysInOldObject() {
        int qx = cx, qz = cz + 1, si = 20;
        LevelChunk c = level.getChunk(qx, qz);
        server.tick(1);
        LevelChunkSection existing = c.getSections()[si];
        LevelChunkSection fresh = emptySection(existing);
        fresh.setBlockState(1, 1, 1, STONE);
        AtomicBoolean fired = seamOnce(c, () -> c.getSections()[si] == fresh, () -> offThread(() -> existing.setBlockState(3, 3, 3, GOLD)));
        assertTrue(cas(c, si, existing, fresh));
        eventually("들여오기", () -> truth(qx * 16 + 1, y0(si) + 1, qz * 16 + 1) == STONE.furnaceStateId());
        assertTrue(fired.get(), "이음매가 교체 맞추기에서 불렸다");
        server.tick(2);
        assertEquals(0, truth(qx * 16 + 3, y0(si) + 3, qz * 16 + 3), "옛 섹션에 쓴 칸은 새 섹션 자리에 없다");
        assertSame(GOLD, existing.getBlockState(3, 3, 3), "떼어 낸 옛 객체에는 남는다");
        assertSame(AIR, fresh.getBlockState(3, 3, 3));
        assertTrue(cas(c, si, fresh, emptySection(fresh)));
        server.tick(1);
    }

    /** R3 결정적: 교체를 계획한 뒤(새 섹션을 잡은 뒤) 들여오기 전에 새 섹션에 쓴 칸 — 같은 들여오기에 들어가고 개수도 맞는다. 붙은 뒤 쓰기는 일지로. */
    @Test
    void seamWriteToClaimedSectionIsImported() {
        int qx = cx + 1, qz = cz, si = 20;
        LevelChunk c = level.getChunk(qx, qz);
        server.tick(1);
        LevelChunkSection existing = c.getSections()[si];
        LevelChunkSection fresh = emptySection(existing);
        fresh.setBlockState(1, 1, 1, STONE);
        AtomicBoolean fired = seamOnce(c, () -> c.getSections()[si] == fresh, () -> offThread(() -> {
            for (int k = 0; k < 16; k++) fresh.setBlockState(k, 2, 0, GLASS);
        }));
        assertTrue(cas(c, si, existing, fresh));
        eventually("들여오기", () -> truth(qx * 16 + 1, y0(si) + 1, qz * 16 + 1) == STONE.furnaceStateId());
        assertTrue(fired.get(), "이음매가 교체 맞추기에서 불렸다");
        int[] row = on(qx, qz, () -> {
            int[] out = new int[16];
            for (int k = 0; k < 16; k++) out[k] = truthHere(qx * 16 + k, y0(si) + 2, qz * 16);
            return out;
        });
        for (int k = 0; k < 16; k++) assertEquals(GLASS.furnaceStateId(), row[k], "틈에 쓴 칸 " + k + " 이 같은 들여오기에 들었다(틱 없이)");
        assertArrayEquals(new int[] {17, 0, 0}, serverCounts(qx, qz, si), "개수 = 그 순간 섹션 필드");
        assertArrayEquals(new int[] {17, 0, 0}, countFields(fresh));
        fresh.setBlockState(5, 5, 5, GLASS);   // 이제 붙은 보기 — 일지
        eventually("붙은 뒤 쓰기", () -> truth(qx * 16 + 5, y0(si) + 5, qz * 16 + 5) == GLASS.furnaceStateId());
        assertTrue(cas(c, si, fresh, emptySection(fresh)));
        server.tick(1);
    }

    /**
     * M3: 개수 필드 대입을 계획한 뒤 붙이기 전에 그 섹션에 칸 쓰기(일지 ±1)가 끼고, 그다음 서버가 같은 섹션을 바꿔도 개수가 맞는다
     * (기준을 대입량만큼만 옮겨야 다음 맞추기가 일지 ±1 을 플러그인 대입으로 오인해 서버 개수를 덮지 않는다). Paper: 같은 섹션 객체라 셋 다 더해진다.
     */
    @Test
    void seamJournalWriteDuringCountsAssignmentKeepsServerCounts() {
        int qx = cx - 1, qz = cz, si = 19;
        LevelChunk c = level.getChunk(qx, qz);
        server.tick(1);
        LevelChunkSection sec = c.getSections()[si];
        assertArrayEquals(new int[] {0, 0, 0}, countFields(sec), "시험 전제: 빈 섹션");
        AtomicBoolean fired = seamOnce(c, () -> true, () -> offThread(() -> sec.setBlockState(1, 1, 1, GLASS)));
        on(qx, qz, () -> {
            offThread(() -> {
                try {
                    NON_EMPTY.setShort(sec, (short) 5);   // 알림 없는 개수 대입(플러그인)
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            });
            // 맞추기 한 번(맞춤 지점의 몸통 — NMS 읽기 진입은 그 뒤 다리 읽기에서 한 번 더 맞춰 틈을 메운다): 대입을 계획하고, 이음매에서 칸 쓰기가 낀다
            c.furnaceMirror().sync();
            assertTrue(fired.get(), "이음매가 개수 맞추기에서 불렸다");
            assertArrayEquals(new int[] {5, 0, 0}, column(qx, qz).sectionVanillaCounts(si), "대입한 개수를 들여왔다(칸 쓰기는 아직 일지)");
            column(qx, qz).setBlock(2, y0(si) + 2, 2, STONE.furnaceStateId());   // 서버(게임)가 같은 섹션을 바꾼다
            return null;
        });
        server.tick(1);   // 남은 일지 재생(틱 끝 맞추기)·공개
        assertEquals(GLASS.furnaceStateId(), truth(qx * 16 + 1, y0(si) + 1, qz * 16 + 1), "일지 재생");
        assertArrayEquals(new int[] {7, 0, 0}, serverCounts(qx, qz, si), "대입 5 + 칸 쓰기 1 + 서버 쓰기 1");
        assertArrayEquals(new int[] {7, 0, 0}, countFields(sec), "공개 뒤 필드 = 서버 개수");
    }

    // ------------------------------------------------------------------ 맞추기 순서·공개·대기(M1·M4·M6·M8·M18)

    /** M1: 옛 섹션에 쓴 칸이 일지에 있는 채 교체되고 둘이 한 맞추기에 들면, 재생이 먼저라 그 자리 = 새 섹션 내용(옛 쓰기가 새 섹션 위에 남지 않는다). */
    @Test
    void journalAndReplaceInOneSyncReplayFirst() {
        int qx = cx + 1, qz = cz - 1, si = 22;
        LevelChunk c = level.getChunk(qx, qz);
        server.tick(1);
        on(qx, qz, () -> {
            LevelChunkSection existing = c.getSections()[si];
            offThread(() -> {
                existing.setBlockState(3, 3, 3, GOLD);   // 일지
                LevelChunkSection fresh = emptySection(existing);
                fresh.setBlockState(1, 1, 1, STONE);
                assertTrue(cas(c, si, existing, fresh));   // 같은 맞추기에 든다(주인은 이 작업 안)
            });
            c.getBlockState(new BlockPos(qx * 16 + 1, y0(si) + 1, qz * 16 + 1));   // 맞춤 지점
            assertEquals(STONE.furnaceStateId(), truthHere(qx * 16 + 1, y0(si) + 1, qz * 16 + 1), "새 섹션을 들여왔다");
            assertEquals(0, truthHere(qx * 16 + 3, y0(si) + 3, qz * 16 + 3), "교체 전 옛 섹션 쓰기는 새 섹션이 덮는다");
            return null;
        });
        LevelChunkSection cur = c.getSections()[si];
        assertTrue(cas(c, si, cur, emptySection(cur)));
        server.tick(1);
    }

    /** M4: 서버가 붙은 섹션의 칸을 바꾸면(플러그인 대입 없음) 공개 뒤 섹션 개수 필드가 새 바닐라 개수를 따른다. */
    @Test
    void countFieldsFollowServerAfterPublish() {
        int qx = cx, qz = cz - 1, si = 21;
        LevelChunk c = level.getChunk(qx, qz);
        LevelChunkSection sec = c.getSections()[si];
        server.tick(1);
        assertArrayEquals(new int[] {0, 0, 0}, countFields(sec), "시험 전제: 빈 섹션");
        on(qx, qz, () -> {
            column(qx, qz).setBlock(1, y0(si) + 1, 1, STONE.furnaceStateId());
            column(qx, qz).setBlock(2, y0(si) + 1, 1, GLASS.furnaceStateId());
            return null;
        });
        server.tick(1);
        assertArrayEquals(new int[] {2, 0, 0}, countFields(sec), "공개 뒤 필드 = 서버 개수");
        on(qx, qz, () -> {
            column(qx, qz).setBlock(1, y0(si) + 1, 1, 0);
            column(qx, qz).setBlock(2, y0(si) + 1, 1, 0);
            return null;
        });
        server.tick(1);
        assertArrayEquals(new int[] {0, 0, 0}, countFields(sec));
    }

    /** M6: 공개 직전 일지에 든(아직 재생 안 한) 쓰기는 공개 뒤에도 쓴 스레드에게 보인다 — 공개는 재생한 순번까지만 덧칠을 지운다. */
    @Test
    void unreplayedOverlaySurvivesPublish() {
        int qx = cx - 1, qz = cz - 1, si = 20;
        LevelChunk c = level.getChunk(qx, qz);
        LevelChunkSection sec = c.getSections()[si];
        server.tick(1);
        BlockState[] seen = on(qx, qz, () -> {
            offThread(() -> sec.setBlockState(1, 1, 1, GLASS));   // J1
            c.getBlockState(new BlockPos(qx * 16 + 1, y0(si) + 1, qz * 16 + 1));   // 맞춤 지점 — J1 재생
            assertEquals(GLASS.furnaceStateId(), truthHere(qx * 16 + 1, y0(si) + 1, qz * 16 + 1));
            offThread(() -> sec.setBlockState(2, 2, 2, STONE));   // J2 — 재생 전
            region(qx, qz).publishReadViewNow(qx, qz);
            column(qx, qz).mirrorPublished();   // 공개 알림(덧칠 정리)
            assertEquals(0, truthHere(qx * 16 + 2, y0(si) + 2, qz * 16 + 2), "J2 는 아직 서버에 없다");
            return offThread(() -> new BlockState[] {sec.getBlockState(1, 1, 1), sec.getBlockState(2, 2, 2)});
        });
        assertSame(GLASS, seen[0], "재생한 쓰기는 공개 판에서");
        assertSame(STONE, seen[1], "재생 안 한 쓰기는 덧칠로 계속 보인다");
        eventually("J2 재생", () -> truth(qx * 16 + 2, y0(si) + 2, qz * 16 + 2) == STONE.furnaceStateId());
        sec.setBlockState(1, 1, 1, AIR);
        sec.setBlockState(2, 2, 2, AIR);
        eventually("되돌림", () -> truth(qx * 16 + 1, y0(si) + 1, qz * 16 + 1) == 0 && truth(qx * 16 + 2, y0(si) + 2, qz * 16 + 2) == 0);
        server.tick(1);
    }

    /** M8·M18: 주인이 바쁜 동안 다른 스레드가 여러 번 써도 깨우기는 한 번, 맞춘 뒤 월드의 대기 청크 수는 0(Bukkit 진입 맞춤 지점이 다시 공짜). */
    @Test
    void pendingCountReturnsToZero() {
        int qx = cx + 1, qz = cz, si = 17;
        LevelChunk c = level.getChunk(qx, qz);
        LevelChunkSection sec = c.getSections()[si];
        var world = server.internals().world();
        server.tick(1);
        assertEquals(0, world.mirrorPendingChunks.get(), "시험 전제: 대기 없음");
        on(qx, qz, () -> {
            offThread(() -> {
                for (int k = 0; k < 5; k++) sec.setBlockState(k, 0, 0, GLASS);
            });
            assertEquals(1, world.mirrorPendingChunks.get(), "대기 청크 하나(쓰기 다섯 번이어도)");
            return null;
        });
        eventually("재생", () -> truth(qx * 16 + 4, y0(si), qz * 16) == GLASS.furnaceStateId());
        server.tick(1);
        assertEquals(0, world.mirrorPendingChunks.get(), "맞춘 뒤 대기 청크 수 0");
        assertFalse(on(qx, qz, () -> column(qx, qz).mirrorPending()));
        for (int k = 0; k < 5; k++) sec.setBlockState(k, 0, 0, AIR);
        eventually("되돌림", () -> truth(qx * 16 + 4, y0(si), qz * 16) == 0);
        server.tick(1);
        assertEquals(0, world.mirrorPendingChunks.get());
    }

    // ------------------------------------------------------------------ 맞춤 지점·장벽(M11·M15·M16)

    /** M11: 다른 스레드의 NMS 쓰기가 들여오기를 기다리는 동안 주인 스레드의 Bukkit 읽기는 먼저 맞추고 읽는다(같은 작업 안, 틱 없이). */
    @Test
    void bukkitReadOnOwnerSyncsPendingWrite() {
        int qx = cx - 1, qz = cz, si = 17;
        LevelChunk c = level.getChunk(qx, qz);
        LevelChunkSection sec = c.getSections()[si];
        org.bukkit.World bw = Compat.player(tp.player()).getWorld();
        int x = qx * 16 + 4, y = y0(si) + 4, z = qz * 16 + 4;
        server.tick(1);
        on(qx, qz, () -> {
            offThread(() -> sec.setBlockState(4, 4, 4, STONE));
            assertEquals(0, truthHere(x, y, z), "아직 일지");
            assertEquals(Material.STONE, bw.getBlockAt(x, y, z).getType(), "Bukkit 읽기 진입이 맞춤 지점");
            return null;
        });
        sec.setBlockState(4, 4, 4, AIR);
        eventually("되돌림", () -> truth(x, y, z) == 0);
    }

    /** M15: 다른 스레드의 Field.set(data·biomes) 뒤 장벽 알림이 주인을 깨운다 — 틱 없이 서버에 들어온다(서버를 바로 읽어 확인). */
    @Test
    @SuppressWarnings("unchecked")
    void fieldSetBarrierWakesOwnerWithoutTick() {
        int qx = cx, qz = cz + 1, si = 17, bsi = 16;
        LevelChunk c = level.getChunk(qx, qz);
        server.tick(1);
        // data 대입
        PalettedContainer<BlockState> states = c.getSections()[si].getStates();
        PalettedContainer<BlockState> donor = new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, AIR, PalettedContainer.Strategy.SECTION_STATES);
        donor.set(6, 6, 6, DIAMOND);
        setField(DATA, states, dataOf(donor));
        eventually("data 대입 들여오기(틱 없이)", () -> truth(qx * 16 + 6, y0(si) + 6, qz * 16 + 6) == DIAMOND.furnaceStateId());
        assertSame(DIAMOND, states.get(6, 6, 6));
        // biomes 대입
        LevelChunkSection bsec = c.getSections()[bsi];
        var oldBiomes = (PalettedContainer<Holder<Biome>>) bsec.getBiomes();
        int wasId = NmsChunks.BIOME_IDS.getId(oldBiomes.get(1, 2, 3));
        int otherId = wasId == 0 ? 1 : 0;
        PalettedContainer<Holder<Biome>> fresh = oldBiomes.recreate();
        for (int i = 0; i < 64; i++) fresh.set(i & 3, i >> 4, (i >> 2) & 3, oldBiomes.get(i & 3, i >> 4, (i >> 2) & 3));
        fresh.set(1, 2, 3, NmsChunks.BIOME_IDS.byId(otherId));
        setField(BIOMES, bsec, fresh);
        eventually("biomes 대입 들여오기(틱 없이)", () -> on(qx, qz, () -> column(qx, qz).sectionForRead(bsi).getBiome(1, 2, 3)) == otherId);
        // 되돌림
        c.getSections()[si].setBlockState(6, 6, 6, AIR);
        setField(BIOMES, bsec, oldBiomes);
        eventually("되돌림", () -> truth(qx * 16 + 6, y0(si) + 6, qz * 16 + 6) == 0
                && on(qx, qz, () -> column(qx, qz).sectionForRead(bsi).getBiome(1, 2, 3)) == wasId);
        server.tick(1);
    }

    /**
     * M16: Field.get 으로 실체화한 data 의 팔레트를 넘치게 하면(idFor) 바닐라처럼 <b>그 컨테이너의</b> data 가 커진 새 data 로 바뀐다 — 플러그인이 그 data 에
     * 쓴 칸은 틱 끝 훑기가 들여온다(FAWE 가 섹션 data 를 읽고 고치는 길).
     */
    @Test
    @SuppressWarnings("unchecked")
    void materializedDataResizeReplacesContainerData() {
        int qx = cx, qz = cz - 1, si = 18;
        LevelChunk c = level.getChunk(qx, qz);
        LevelChunkSection sec = c.getSections()[si];
        PalettedContainer<BlockState> states = sec.getStates();
        server.tick(1);
        var d = (PalettedContainer.Data<BlockState>) dataOf(states);
        int id = d.palette().idFor(STONE);   // 공기 하나 팔레트 → 크기 바꿈(컨테이너의 onResize)
        var d2 = (PalettedContainer.Data<BlockState>) dataOf(states);
        assertNotSame(d, d2, "크기 바꿈은 그 컨테이너의 data 를 바꾼다(바닐라 onResize)");
        assertSame(STONE, d2.palette().valueFor(id));
        d2.storage().set((3 << 8) | (3 << 4) | 3, id);   // 플러그인이 그 data 에 칸을 쓴다
        server.tick(1);
        assertEquals(STONE.furnaceStateId(), truth(qx * 16 + 3, y0(si) + 3, qz * 16 + 3), "틱 끝 훑기가 들여온다");
        sec.setBlockState(3, 3, 3, AIR);
        eventually("되돌림", () -> truth(qx * 16 + 3, y0(si) + 3, qz * 16 + 3) == 0);
        server.tick(1);
    }
}
