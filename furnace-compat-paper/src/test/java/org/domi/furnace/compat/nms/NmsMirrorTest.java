package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.levelgen.Heightmap;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.test.FurnaceTestServer;
import org.domi.furnace.test.TestPlayer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * N2b-4 거울·들여오기: 주인이 아닌 스레드의 NMS 청크 쓰기가 바닐라·Paper 와 같은 결과로 서버 청크에 들어가는가.
 * <ul>
 *   <li>칸 일지(D1): 섹션 setBlockState·LevelChunk.setBlockState 는 쓴 스레드가 곧바로 자기 쓰기를 읽고, 주인이 틱을 기다리지 않고 재생한다.</li>
 *   <li>FAWE 꼴(2.16.2 PaperweightGetBlocks): 개수 필드 setShort 0 → 새 섹션 → Object[] VarHandle CAS(이름 힌트 없는 클래스) → biomes 필드 대입,
 *       PalettedContainer.data 리플렉션 읽기. 변환한 플러그인 클래스를 실제로 돌린다.</li>
 *   <li>알림 없는 쓰기(장벽 밖 AASTORE)는 틱 끝 훑기가, 주인 스레드의 AASTORE 는 맞춤 지점(setUnsaved·그 칸 읽기)이 잡는다.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsMirrorTest {

    /** ReflectionUtils(worldedit-core 꼴): "minecraft" 문자열이 없는 클래스의 Object[] VarHandle CAS. */
    private static final String CORE = """
            package mirrory.core;
            import java.lang.invoke.MethodHandles;
            import java.lang.invoke.VarHandle;
            public final class ReflectionUtils {
                private static final VarHandle REFERENCE_ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);
                public static <T> boolean compareAndSet(T[] array, T expectedValue, T newValue, int index) {
                    return REFERENCE_ARRAY_HANDLE.compareAndSet(array, index, expectedValue, newValue);
                }
            }
            """;

    /** PaperweightPlatformAdapter 꼴: 개수 필드·biomes 필드·data 필드 리플렉션, 새 섹션 만들기, NMS 원소 형 VarHandle. */
    private static final String ADAPTER = """
            package mirrory.adapter;
            import java.lang.invoke.MethodHandles;
            import java.lang.invoke.VarHandle;
            import java.lang.reflect.Field;
            import net.minecraft.world.level.block.state.BlockState;
            import net.minecraft.world.level.chunk.LevelChunk;
            import net.minecraft.world.level.chunk.LevelChunkSection;
            import net.minecraft.world.level.chunk.PalettedContainer;
            import net.minecraft.world.level.chunk.PalettedContainerRO;
            public final class Adapter {
                private static final Field NON_EMPTY, TICKING_BLOCK, TICKING_FLUID, BIOMES, DATA;
                private static final VarHandle TYPED = MethodHandles.arrayElementVarHandle(LevelChunkSection[].class);
                static {
                    try {
                        NON_EMPTY = field(LevelChunkSection.class, "nonEmptyBlockCount");
                        TICKING_BLOCK = field(LevelChunkSection.class, "tickingBlockCount");
                        TICKING_FLUID = field(LevelChunkSection.class, "tickingFluidCount");
                        BIOMES = field(LevelChunkSection.class, "biomes");
                        DATA = field(PalettedContainer.class, "data");
                    } catch (ReflectiveOperationException e) {
                        throw new ExceptionInInitializerError(e);
                    }
                }
                private static Field field(Class<?> c, String name) throws NoSuchFieldException {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                }
                public static void clearCounts(LevelChunkSection s) throws IllegalAccessException {
                    NON_EMPTY.setShort(s, (short) 0);
                    TICKING_BLOCK.setShort(s, (short) 0);
                    TICKING_FLUID.setShort(s, (short) 0);
                }
                public static int[] counts(LevelChunkSection s) throws IllegalAccessException {
                    return new int[] {NON_EMPTY.getShort(s), TICKING_BLOCK.getShort(s), TICKING_FLUID.getShort(s)};
                }
                public static LevelChunkSection newSection(LevelChunkSection existing, int x, int y, int z, BlockState state) {
                    PalettedContainer<BlockState> states = existing.getStates().copy();
                    states.set(x, y, z, state);
                    return new LevelChunkSection(states, existing.getBiomes());
                }
                public static boolean replace(LevelChunk chunk, int index, LevelChunkSection expected, LevelChunkSection fresh) {
                    return mirrory.core.ReflectionUtils.compareAndSet(chunk.getSections(), expected, fresh, index);
                }
                public static LevelChunkSection exchangeTyped(LevelChunk chunk, int index, LevelChunkSection expected, LevelChunkSection fresh) {
                    return (LevelChunkSection) TYPED.compareAndExchange(chunk.getSections(), index, expected, fresh);
                }
                @SuppressWarnings("unchecked")
                public static int[] dataStates(PalettedContainer<BlockState> c) throws IllegalAccessException {
                    PalettedContainer.Data<BlockState> d = (PalettedContainer.Data<BlockState>) DATA.get(c);
                    int[] out = new int[4096];
                    for (int i = 0; i < 4096; i++) out[i] = net.minecraft.world.level.block.Block.getId(d.palette().valueFor(d.storage().get(i)));
                    return out;
                }
                public static void setBiomes(LevelChunkSection s, PalettedContainerRO<?> biomes) throws IllegalAccessException {
                    BIOMES.set(s, biomes);
                }
                public static Object data(PalettedContainer<?> c) throws IllegalAccessException {
                    return DATA.get(c);
                }
                @SuppressWarnings("unchecked")
                public static BlockState dataStateAt(PalettedContainer<BlockState> c, int x, int y, int z) throws IllegalAccessException {
                    PalettedContainer.Data<BlockState> d = (PalettedContainer.Data<BlockState>) DATA.get(c);
                    return d.palette().valueFor(d.storage().get((y << 8) | (z << 4) | x));
                }
            }
            """;

    @TempDir
    static Path tmp;

    FurnaceTestServer server;
    TestPlayer tp;
    ServerLevel level;
    org.domi.furnace.api.world.BlockPos at;
    int cx, cz;
    Map<String, byte[]> pluginClasses;
    Map<String, byte[]> transformed = new java.util.concurrent.ConcurrentHashMap<>();
    Class<?> adapter;

    @BeforeAll
    void start() throws Exception {
        server = FurnaceTestServer.builder().failFast(false).start();
        Bukkit.setServer(CraftServer.INSTANCE);
        tp = server.join("Mirror");
        server.tick(3);
        var loc = tp.player().location();
        at = new org.domi.furnace.api.world.BlockPos((int) Math.floor(loc.x()), (int) Math.floor(loc.y()), (int) Math.floor(loc.z()));
        cx = Math.floorDiv(at.x(), 16);
        cz = Math.floorDiv(at.z(), 16);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) server.loadChunk(cx + dx, cz + dz);
        server.tick(2);
        level = ((CraftWorld) Compat.player(tp.player()).getWorld()).getHandle();
        Path jar = JarCompiler.jar(tmp.resolve("mirrory.jar"), "name: Mirrory\nversion: '1'\nmain: mirrory.adapter.Adapter\n",
                Map.of("mirrory.core.ReflectionUtils", CORE, "mirrory.adapter.Adapter", ADAPTER));
        pluginClasses = NmsLinkerTest.read(jar);
        NmsClassTransformer t = new NmsClassTransformer("Mirrory", Set.of(), NmsClassTransformer.Options.DEFAULT, NmsSymbols.RUNTIME);
        ClassLoader loader = new ClassLoader(NmsMirrorTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    if (c == null && pluginClasses.containsKey(name)) {
                        byte[] b = pluginClasses.get(name);
                        byte[] x = t.transform(name, b);
                        if (x != null) {
                            transformed.put(name, x);
                            b = x;
                        }
                        c = defineClass(name, b, 0, b.length);
                    }
                    return c != null ? c : super.loadClass(name, resolve);
                }
            }
        };
        adapter = loader.loadClass("mirrory.adapter.Adapter");
        loader.loadClass("mirrory.core.ReflectionUtils");
    }

    @AfterAll
    void stop() { server.close(); }

    <T> T on(int chunkX, int chunkZ, Supplier<T> task) {
        return server.onRegion(new org.domi.furnace.api.world.BlockPos(chunkX * 16, 64, chunkZ * 16), task);
    }

    ChunkColumn column(int chunkX, int chunkZ) {
        return server.internals().world().regionizer().ownerOf(chunkX, chunkZ).loadedOwnedChunk(chunkX, chunkZ);
    }

    int truth(int x, int y, int z) {
        return on(x >> 4, z >> 4, () -> server.internals().world().regionizer().ownerOf(x >> 4, z >> 4).getBlock(x, y, z));
    }

    /** 틱을 돌리지 않고 기다린다(주인 region 은 틱 사이에도 받은 작업을 돈다). */
    static void eventually(String what, BooleanSupplier cond) {
        long end = System.nanoTime() + 5_000_000_000L;
        while (!cond.getAsBoolean()) {
            if (System.nanoTime() > end) fail(what + " — 틱 없이 5초 안에 들어오지 않았다");
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    Object call(String name, Object... args) {
        for (var m : adapter.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    return m.invoke(null, args);
                } catch (InvocationTargetException e) {
                    throw new AssertionError(name + " 실패", e.getCause());
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
        throw new AssertionError("no method " + name);
    }

    static int[] countFields(LevelChunkSection s) {
        try {
            int[] out = new int[3];
            String[] names = {"nonEmptyBlockCount", "tickingBlockCount", "tickingFluidCount"};
            for (int i = 0; i < 3; i++) {
                Field f = LevelChunkSection.class.getDeclaredField(names[i]);
                f.setAccessible(true);
                out[i] = f.getShort(s);
            }
            return out;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 다른 스레드의 섹션 setBlockState(바닐라: 칸 + 개수 ±1): 쓴 스레드는 곧바로 읽고, 개수 필드도 바로, 주인은 틱 없이 재생한다. 저장 표시는 세우지 않는다. */
    @Test
    void offThreadSectionSetIsJournaledAndReplayedWithoutTick() {
        int qx = cx + 1, qz = cz;
        LevelChunk c = level.getChunk(qx, qz);   // 시험 스레드 = 주인이 아닌 스레드
        int x = qx * 16 + 6, z = qz * 16 + 9;
        int y = on(qx, qz, () -> level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)) + 2;
        int si = level.getSectionIndex(y);
        on(qx, qz, () -> {
            column(qx, qz).clearDirty();
            return null;
        });
        LevelChunkSection sec = c.getSection(si);
        BlockState glass = Blocks.GLASS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        int[] before = countFields(sec);
        assertArrayEquals(before, on(qx, qz, () -> level.getChunk(qx, qz).furnaceSectionCounts(si)), "개수 필드는 서버 바닐라 개수에서 시작한다");
        BlockState old = sec.setBlockState(x & 15, y & 15, z & 15, glass);
        assertSame(air, old, "옛 상태(바닐라 반환값)");
        assertSame(glass, sec.getBlockState(x & 15, y & 15, z & 15), "자기 쓰기를 곧바로 읽는다");
        assertSame(glass, c.getBlockState(new BlockPos(x, y, z)), "LevelChunk 읽기도");
        assertEquals(before[0] + 1, countFields(sec)[0], "개수 필드 +1(바닐라 setBlockState)");
        eventually("주인 재생", () -> truth(x, y, z) == glass.furnaceStateId());
        assertArrayEquals(countFields(sec), on(qx, qz, () -> level.getChunk(qx, qz).furnaceSectionCounts(si)), "서버 개수도 +1");
        assertFalse(on(qx, qz, () -> column(qx, qz).dirty()), "섹션 쓰기는 저장 표시를 세우지 않는다(바닐라)");
        assertTrue(on(qx, qz, () -> column(qx, qz).importedUnsaved()), "저장 표시 없는 변경으로 기록(D3 경고 대상)");
        assertSame(glass, on(qx, qz, () -> level.getBlockState(new BlockPos(x, y, z))));

        assertSame(glass, sec.setBlockState(x & 15, y & 15, z & 15, air));
        eventually("되돌림 재생", () -> truth(x, y, z) == 0);
        assertArrayEquals(before, countFields(sec));
        server.tick(1);   // 공개 뒤 덧칠이 지워져도 같은 값
        assertSame(air, sec.getBlockState(x & 15, y & 15, z & 15));
        assertArrayEquals(before, on(qx, qz, () -> level.getChunk(qx, qz).furnaceSectionCounts(si)));
    }

    /** 다른 스레드의 LevelChunk.setBlockState: 주인이 바닐라 LevelChunk.setBlockState 를 그대로(하이트맵·저장 표시까지). */
    @Test
    void offThreadChunkSetBlockStateReplaysVanillaChunkWrite() {
        int qx = cx, qz = cz + 1;
        LevelChunk c = level.getChunk(qx, qz);
        int x = qx * 16 + 3, z = qz * 16 + 12;
        int h = on(qx, qz, () -> level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
        BlockPos p = new BlockPos(x, h + 1, z);
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        on(qx, qz, () -> {
            column(qx, qz).clearDirty();
            return null;
        });
        try {
            assertSame(air, c.setBlockState(p, stone, false), "옛 상태");
            assertSame(stone, c.getBlockState(p), "자기 쓰기를 곧바로 읽는다");
            assertSame(null, c.setBlockState(p, stone, false), "같은 상태면 null(바닐라)");
            eventually("주인 재생", () -> truth(x, h + 1, z) == stone.furnaceStateId());
            assertEquals(h + 2, (int) on(qx, qz, () -> level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)), "바닐라 LevelChunk.setBlockState 는 하이트맵을 고친다");
            assertTrue(on(qx, qz, () -> column(qx, qz).dirty()), "바닐라 LevelChunk.setBlockState 는 저장 표시를 세운다");
        } finally {
            on(qx, qz, () -> level.setBlock(p, air, 2 | 16));
        }
    }

    /**
     * FAWE 2.16.2 꼴 섹션 교체(다른 스레드): 개수 setShort 0 → 기존 섹션 복사로 새 섹션 → ReflectionUtils CAS. 장벽 알림으로 틱 없이 들어오고,
     * 새 섹션은 그 자리의 보기가 되며(정체성 유지 — 다음 CAS 의 기대값), 빠진 옛 섹션은 떨어진다(그 뒤 쓰기는 월드에 닿지 않는다).
     */
    @Test
    void faweStyleSectionReplaceIsImported() {
        int si = 20;   // 공기뿐인 높은 섹션
        LevelChunk c = level.getChunk(cx, cz);
        int y = level.getMinBuildHeight() + si * 16 + 5, x = cx * 16 + 4, z = cz * 16 + 11;
        BlockState stone = Blocks.STONE.defaultBlockState(), glass = Blocks.GLASS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        LevelChunkSection existing = c.getSections()[si];
        assertSame(existing, c.getSections()[si], "같은 배열·같은 섹션(정체성)");
        call("clearCounts", existing);
        assertArrayEquals(new int[] {0, 0, 0}, (int[]) call("counts", existing));
        LevelChunkSection fresh = (LevelChunkSection) call("newSection", existing, 4, 5, 11, stone);
        assertArrayEquals(new int[] {1, 0, 0}, countFields(fresh), "새 섹션은 만들 때 센다(바닐라)");
        assertTrue((boolean) call("replace", c, si, existing, fresh));
        assertSame(fresh, c.getSections()[si]);
        eventually("장벽 알림으로 들여오기", () -> truth(x, y, z) == stone.furnaceStateId());
        assertArrayEquals(new int[] {1, 0, 0}, on(cx, cz, () -> level.getChunk(cx, cz).furnaceSectionCounts(si)), "개수는 새 섹션의 필드");
        assertFalse(on(cx, cz, () -> level.getChunk(cx, cz).getSection(si).hasOnlyAir()));
        assertSame(fresh, c.getSections()[si], "들인 뒤에도 그 객체(정체성)");
        assertSame(fresh, on(cx, cz, () -> level.getChunk(cx, cz).getSections()[si]), "주인에게도 같은 객체");
        // 새 섹션은 서버 칸의 보기: 주인이 다른 칸을 바꾸면 공개 뒤 읽힌다
        on(cx, cz, () -> level.setBlock(new BlockPos(x + 1, y, z), glass, 2 | 16));
        server.tick(1);
        assertSame(glass, fresh.getBlockState(5, 5, 11));
        // 떨어진 옛 섹션: 그 순간 내용, 쓰기는 월드에 닿지 않는다
        assertSame(air, existing.getBlockState(4, 5, 11));
        existing.setBlockState(1, 1, 1, glass);
        server.tick(1);
        assertEquals(0, truth(cx * 16 + 1, level.getMinBuildHeight() + si * 16 + 1, cz * 16 + 1), "떨어진 섹션 쓰기는 월드에 없다");
        // 되돌림: 같은 길로 빈 섹션
        LevelChunkSection empty = (LevelChunkSection) call("newSection", fresh, 4, 5, 11, air);
        empty.getStates().set(5, 5, 11, air);
        empty.recalcBlockCounts();
        assertTrue((boolean) call("replace", c, si, fresh, empty));
        eventually("되돌림 들여오기", () -> truth(x, y, z) == 0 && truth(x + 1, y, z) == 0);
        assertTrue(on(cx, cz, () -> level.getChunk(cx, cz).getSection(si).hasOnlyAir()));
    }

    /** NMS 원소 형 VarHandle(compareAndExchange, 반환 형 변환)도 변환되어 돈다. */
    @Test
    void typedVarHandleExchangeIsImported() {
        int si = 21;
        LevelChunk c = level.getChunk(cx, cz);
        int y = level.getMinBuildHeight() + si * 16 + 2, x = cx * 16 + 2, z = cz * 16 + 2;
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        LevelChunkSection existing = c.getSections()[si];
        LevelChunkSection fresh = (LevelChunkSection) call("newSection", existing, 2, 2, 2, gold);
        assertSame(existing, call("exchangeTyped", c, si, existing, fresh), "compareAndExchange 는 옛 값을 돌려준다");
        eventually("들여오기", () -> truth(x, y, z) == gold.furnaceStateId());
        LevelChunkSection empty = (LevelChunkSection) call("newSection", fresh, 2, 2, 2, air);
        assertSame(fresh, call("exchangeTyped", c, si, fresh, empty));
        eventually("되돌림", () -> truth(x, y, z) == 0);
    }

    /** biomes 필드 대입(FAWE setBiomesToChunkSection): 들여와 서버 바이옴이 바뀌고, 대입한 컨테이너가 그 자리의 보기가 된다. */
    @Test
    @SuppressWarnings("unchecked")
    void biomesFieldAssignmentIsImported() {
        int si = 19;
        LevelChunk c = level.getChunk(cx, cz);
        LevelChunkSection sec = c.getSections()[si];
        var oldBiomes = sec.getBiomes();
        Holder<Biome> was = oldBiomes.get(1, 2, 3);
        int wasId = NmsChunks.BIOME_IDS.getId(was);
        Holder<Biome> other = NmsChunks.BIOME_IDS.byId(wasId == 0 ? 1 : 0);
        PalettedContainer<Holder<Biome>> fresh = oldBiomes.recreate();
        for (int i = 0; i < 64; i++) fresh.set(i & 3, i >> 4, (i >> 2) & 3, oldBiomes.get(i & 3, i >> 4, (i >> 2) & 3));
        fresh.set(1, 2, 3, other);
        int qy = (level.getChunk(cx, cz).getSectionYFromSectionIndex(si) << 2) + 2;
        call("setBiomes", sec, fresh);
        assertSame(fresh, sec.getBiomes());
        eventually("바이옴 들여오기", () -> on(cx, cz, () -> level.getChunk(cx, cz).getNoiseBiome(cx * 4 + 1, qy, cz * 4 + 3)) == other);
        assertTrue(on(cx, cz, () -> column(cx, cz).importedUnsaved()));
        // 대입한 컨테이너는 이제 서버 칸의 보기
        fresh.set(1, 2, 3, was);
        eventually("보기 쓰기 재생", () -> on(cx, cz, () -> level.getChunk(cx, cz).getNoiseBiome(cx * 4 + 1, qy, cz * 4 + 3)) == was);
        call("setBiomes", sec, oldBiomes);
        server.tick(1);
        assertSame(was, sec.getBiomes().get(1, 2, 3));
    }

    /** PalettedContainer.data 리플렉션 읽기(FAWE 의 섹션 읽기): 붙은 보기도 바닐라 모양 data 를 주고, 서버가 바뀐 뒤에는 새 내용. */
    @Test
    void dataFieldReadMaterializesAndRefreshes() {
        int si = 22;
        LevelChunk c = level.getChunk(cx, cz);
        int y = level.getMinBuildHeight() + si * 16 + 7, x = cx * 16 + 8, z = cz * 16 + 1;
        PalettedContainer<BlockState> states = c.getSections()[si].getStates();
        BlockState air = Blocks.AIR.defaultBlockState(), diamond = Blocks.DIAMOND_BLOCK.defaultBlockState();
        Object d1 = call("data", states);
        assertNotNull(d1, "변환된 Field.get 은 data 를 만들어 준다");
        assertSame(air, call("dataStateAt", states, 8, 7, 1));
        assertSame(d1, call("data", states), "바뀐 것이 없으면 같은 data");
        try {
            on(cx, cz, () -> level.setBlock(new BlockPos(x, y, z), diamond, 2 | 16));
            server.tick(1);
            assertSame(diamond, states.get(8, 7, 1));
            Object d2 = call("data", states);
            assertNotSame(d1, d2, "서버가 바뀌면 새 data");
            assertSame(diamond, call("dataStateAt", states, 8, 7, 1));
        } finally {
            on(cx, cz, () -> level.setBlock(new BlockPos(x, y, z), air, 2 | 16));
            server.tick(1);
        }
    }

    /** 알림 없는 쓰기(장벽 밖 배열 칸 대입 — 변환되지 않은 코드): 주인의 다른 작업은 맞추지 않고, 틱 끝 훑기가 들여온다(정식 경로). */
    @Test
    void unnotifiedArrayStoreIsImportedAtTickEnd() {
        int qx = cx - 1, qz = cz, si = 20;
        LevelChunk c = level.getChunk(qx, qz);
        int y = level.getMinBuildHeight() + si * 16 + 3, x = qx * 16 + 7, z = qz * 16 + 7;
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        LevelChunkSection existing = c.getSections()[si];
        LevelChunkSection fresh = (LevelChunkSection) call("newSection", existing, 7, 3, 7, stone);
        c.getSections()[si] = fresh;   // AASTORE(이 시험 클래스는 변환되지 않는다)
        assertEquals(0, truth(x, y, z), "알림이 없으니 아직(주인 작업 한 바퀴 뒤에도)");
        server.tick(1);
        assertEquals(stone.furnaceStateId(), truth(x, y, z), "틱 끝 훑기");
        c.getSections()[si] = (LevelChunkSection) call("newSection", fresh, 7, 3, 7, air);
        server.tick(1);
        assertEquals(0, truth(x, y, z));
    }

    /** 주인 스레드의 AASTORE: 그 칸을 읽기 전·setUnsaved 에서 맞춘다(같은 작업 안). */
    @Test
    void ownerArrayStoreSyncsAtSyncPoints() {
        int qx = cx + 1, qz = cz + 1, si = 21;
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        int y = level.getMinBuildHeight() + si * 16 + 9, x = qx * 16 + 1, z = qz * 16 + 14;
        on(qx, qz, () -> {
            LevelChunk c = level.getChunk(qx, qz);
            column(qx, qz).clearDirty();
            LevelChunkSection existing = c.getSections()[si];
            c.getSections()[si] = (LevelChunkSection) call("newSection", existing, 1, 9, 14, stone);
            assertSame(stone, c.getBlockState(new BlockPos(x, y, z)), "그 칸 읽기 전에 맞춘다");
            assertEquals(stone.furnaceStateId(), server.internals().world().regionizer().ownerOf(qx, qz).getBlock(x, y, z));
            LevelChunkSection cur = c.getSections()[si];
            c.getSections()[si] = (LevelChunkSection) call("newSection", cur, 1, 9, 14, air);
            c.setUnsaved(true);   // 바닐라 의미가 있는 맞춤 지점
            assertEquals(0, server.internals().world().regionizer().ownerOf(qx, qz).getBlock(x, y, z));
            assertTrue(column(qx, qz).dirty());
            return null;
        });
    }

    /** 내린 청크의 거울: 섹션이 그 순간 내용으로 굳고, 쓰기는 월드에 닿지 않는다. 다른 스레드는 거울을 맞추거나 내릴 수 없다. */
    @Test
    void unloadedMirrorDetachesSections() {
        int qx = cx - 1, qz = cz - 1, si = 18;
        LevelChunk c = level.getChunk(qx, qz);
        assertFalse(c.furnaceMirror().sync(), "주인이 아닌 스레드의 sync 는 아무것도 안 한다");
        c.furnaceMirror().unloaded();   // 주인이 아니면 아무것도 안 한다
        LevelChunkSection sec = c.getSections()[si];
        BlockState glass = Blocks.GLASS.defaultBlockState();
        on(qx, qz, () -> {
            level.getChunk(qx, qz).furnaceMirror().unloaded();
            return null;
        });
        sec.setBlockState(0, 0, 0, glass);
        assertSame(glass, sec.getBlockState(0, 0, 0), "떨어진 섹션은 자기 내용");
        server.tick(2);
        assertEquals(0, truth(qx * 16, level.getMinBuildHeight() + si * 16, qz * 16), "내린 거울의 쓰기는 월드에 닿지 않는다");
    }

    /** 여러 스레드가 같은 섹션의 다른 칸에 동시에 쓰는 사이 주인이 틱을 돈다: 교착·예외 없이 모두 들어오고, 개수 필드와 서버 바닐라 개수가 같다. */
    @Test
    void concurrentJournalWritesStayConsistent() throws Exception {
        int qx = cx + 1, qz = cz - 1, si = 17, threads = 4, per = 256;
        LevelChunk c = level.getChunk(qx, qz);
        LevelChunkSection sec = c.getSection(si);
        int[] before = countFields(sec);
        assertEquals(0, before[0], "시험 전제: 빈 섹션");
        BlockState glass = Blocks.GLASS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int base = t * per;
                fs.add(pool.submit(() -> {
                    for (int k = 0; k < per; k++) {
                        int cell = base + k;
                        sec.setBlockState(cell & 15, cell >> 8, (cell >> 4) & 15, glass);
                    }
                }));
            }
            for (int i = 0; i < 4; i++) server.tick(1);   // 쓰는 사이 공개·개수 새로 고침
            for (var f : fs) f.get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(threads * per, countFields(sec)[0], "개수 필드 +1 씩");
            eventually("모두 재생", () -> on(qx, qz, () -> level.getChunk(qx, qz).furnaceSectionCounts(si))[0] == threads * per);
            server.tick(1);
            assertArrayEquals(countFields(sec), on(qx, qz, () -> level.getChunk(qx, qz).furnaceSectionCounts(si)));
            int y0 = level.getMinBuildHeight() + si * 16;
            for (int cell = 0; cell < threads * per; cell += 97) {
                int cc = cell;
                assertEquals(glass.furnaceStateId(), truth(qx * 16 + (cc & 15), y0 + (cc >> 8), qz * 16 + ((cc >> 4) & 15)), "칸 " + cc);
            }
        } finally {
            pool.shutdownNow();
            for (int cell = 0; cell < threads * per; cell++) sec.setBlockState(cell & 15, cell >> 8, (cell >> 4) & 15, air);
            eventually("되돌림", () -> on(qx, qz, () -> level.getChunk(qx, qz).furnaceSectionCounts(si))[0] == 0);
        }
        server.tick(1);
        assertArrayEquals(before, countFields(sec));
    }

    /**
     * FAWE GET 첫 읽기 꼴(PaperweightGetBlocks.update — 섹션 배열 사본 → getStates → data 리플렉션 → 저장소·팔레트): 주인이 아닌 여러 스레드가 서버가 막 바꾼
     * 섹션의 data 를 동시에 처음 읽어도 모두 그 판 내용 그대로다(찢김·낡은 판 없음). T-204 후속(//fixblocks 뒤 레드스톤 한 칸이 실행마다 갈림)에서 Furnace 쪽
     * 읽기를 원인에서 뺀 시험 — 원인은 FAWE CharBlocks 의 겹친 첫 읽기가 배열을 제자리에서 다시 채우는 경쟁이다(context-notes 2026-11-17-t204-redstone-flaky).
     */
    @Test
    void concurrentFirstDataReadsAgree() throws Exception {
        int qx = cx - 1, qz = cz + 1, si = 18, threads = 6, rounds = 30;
        LevelChunk c = level.getChunk(qx, qz);
        int y0 = level.getMinBuildHeight() + si * 16;
        BlockState[] mix = {Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.REDSTONE_WIRE.defaultBlockState(),
            Blocks.OAK_STAIRS.defaultBlockState(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Blocks.GLASS_PANE.defaultBlockState()};
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            for (int r = 0; r < rounds; r++) {
                int rr = r;
                int[] expected = on(qx, qz, () -> {
                    var rnd = new java.util.Random(rr);
                    for (int k = 0; k < 600; k++) {
                        int cell = rnd.nextInt(4096);
                        level.setBlock(new BlockPos(qx * 16 + (cell & 15), y0 + (cell >> 8), qz * 16 + ((cell >> 4) & 15)), mix[rnd.nextInt(mix.length)], 2 | 16);
                    }
                    int[] e = new int[4096];
                    for (int cell = 0; cell < 4096; cell++) {
                        e[cell] = level.getBlockState(new BlockPos(qx * 16 + (cell & 15), y0 + (cell >> 8), qz * 16 + ((cell >> 4) & 15))).furnaceStateId();
                    }
                    return e;
                });
                server.tick(1);   // 공개
                var go = new java.util.concurrent.CountDownLatch(1);
                List<java.util.concurrent.Future<int[]>> fs = new ArrayList<>();
                for (int t = 0; t < threads; t++) {
                    fs.add(pool.submit(() -> {
                        go.await();
                        LevelChunkSection[] secs = c.getSections().clone();
                        return (int[]) call("dataStates", secs[si].getStates());
                    }));
                }
                go.countDown();
                for (int t = 0; t < threads; t++) assertArrayEquals(expected, fs.get(t).get(30, java.util.concurrent.TimeUnit.SECONDS), "판 " + r + " 스레드 " + t);
            }
        } finally {
            pool.shutdownNow();
            on(qx, qz, () -> {
                for (int cell = 0; cell < 4096; cell++) {
                    level.setBlock(new BlockPos(qx * 16 + (cell & 15), y0 + (cell >> 8), qz * 16 + ((cell >> 4) & 15)), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
                return null;
            });
            server.tick(1);
        }
    }

    /**
     * T-209: {@code data} 실체화가 서버 섹션 저장소 꼴을 그대로 넘긴다(칸을 펼치지 않음) — 서버 섹션이 간접 저장일 때와, 편집이 쌓여 직접 저장으로 커진
     * 뒤일 때(값은 몇 가지뿐) 모두: 설정 비트·묶인 배열·팔레트 항목 순서가 공개 스냅샷의 저장소와 같고, 칸 값은 서버와 같다. 주인 스레드가 먼저 읽어도
     * 같은 꼴이다. 내용이 그대로면 다시 읽어도 같은 data 객체, 바뀌면 새 data.
     */
    @Test
    void dataPassesServerSectionLayoutThrough() throws Exception {
        int qx = cx + 1, qz = cz - 1, si = 17;
        LevelChunk c = level.getChunk(qx, qz);
        int y0 = level.getMinBuildHeight() + si * 16;
        BlockState[] simple = {Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
            Blocks.GLASS.defaultBlockState(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
        int wire = net.minecraft.world.level.block.Block.getId(Blocks.REDSTONE_WIRE.defaultBlockState());
        try {
            for (String phase : new String[] {"indirect", "direct", "owner-first"}) {
                int[] expected = on(qx, qz, () -> {
                    if (phase.equals("direct")) {   // 서로 다른 상태 수백 개를 지나가게 해 팔레트를 직접 저장으로 키운다(살아 있는 팔레트는 줄지 않는다)
                        List<BlockState> wires = new ArrayList<>();
                        for (int id = wire - 1300; id <= wire + 1300; id++) {
                            BlockState s = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.byId(id);
                            if (s != null && s.getBlock() == Blocks.REDSTONE_WIRE) wires.add(s);
                        }
                        for (int cell = 0; cell < 4096; cell++) {
                            level.setBlock(new BlockPos(qx * 16 + (cell & 15), y0 + (cell >> 8), qz * 16 + ((cell >> 4) & 15)), wires.get(cell % wires.size()), 2 | 16);
                        }
                    }
                    var rnd = new java.util.Random(phase.length());
                    for (int cell = 0; cell < 4096; cell++) {
                        level.setBlock(new BlockPos(qx * 16 + (cell & 15), y0 + (cell >> 8), qz * 16 + ((cell >> 4) & 15)), simple[rnd.nextInt(simple.length)], 2 | 16);
                    }
                    int[] e = new int[4096];
                    for (int cell = 0; cell < 4096; cell++) {
                        e[cell] = level.getBlockState(new BlockPos(qx * 16 + (cell & 15), y0 + (cell >> 8), qz * 16 + ((cell >> 4) & 15))).furnaceStateId();
                    }
                    return e;
                });
                Object ownerData = phase.equals("owner-first")
                        ? on(qx, qz, () -> {   // 주인 스레드의 읽기(살아 있는 섹션 저장소 사본)
                            PalettedContainer<BlockState> mine = level.getChunk(qx, qz).getSections()[si].getStates();
                            assertArrayEquals(expected, (int[]) call("dataStates", mine), "주인 읽기 칸");
                            return call("data", mine);
                        })
                        : null;
                server.tick(1);   // 공개
                var packed = c.furnaceForeignView("시험").sectionStatesPacked(si);
                assertEquals(!phase.equals("indirect"), packed.paletteSize() < 0, phase + ": 시험 전제 — 서버 저장 방식(직접 저장은 줄지 않는다)");
                PalettedContainer<BlockState> st = c.getSections().clone()[si].getStates();
                var g = (PalettedContainer.Data<?>) call("data", st);
                if (ownerData != null) {   // 주인(살아 있는 저장소 사본)과 다른 스레드(공개 스냅샷)가 같은 꼴
                    var o = (PalettedContainer.Data<?>) ownerData;
                    assertEquals(o.configuration(), g.configuration());
                    assertArrayEquals(o.storage().getRaw(), g.storage().getRaw(), "주인 읽기와 같은 묶인 배열");
                }
                assertEquals(packed.bits(), g.storage().getBits(), phase);
                assertArrayEquals(packed.rawCopy(), g.storage().getRaw(), phase);
                if (packed.paletteSize() > 0) {
                    assertEquals(packed.paletteSize(), g.palette().getSize(), phase);
                    for (int i = 0; i < packed.paletteSize(); i++) {
                        assertEquals(packed.paletteValue(i), net.minecraft.world.level.block.Block.getId((BlockState) g.palette().valueFor(i)), phase + " 팔레트 " + i);
                    }
                }
                assertArrayEquals(expected, (int[]) call("dataStates", st), phase);
                assertSame(g, call("data", st), phase + ": 판이 그대로면 같은 data");
            }
            on(qx, qz, () -> {
                level.setBlock(new BlockPos(qx * 16, y0, qz * 16), Blocks.GOLD_BLOCK.defaultBlockState(), 2 | 16);
                return null;
            });
            server.tick(1);
            PalettedContainer<BlockState> st = c.getSections().clone()[si].getStates();
            Object before = call("data", st);
            on(qx, qz, () -> {
                level.setBlock(new BlockPos(qx * 16 + 1, y0, qz * 16), Blocks.GOLD_BLOCK.defaultBlockState(), 2 | 16);
                return null;
            });
            server.tick(1);
            Object after = call("data", st);
            assertNotSame(before, after, "판이 바뀌면 새 data");
            assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), call("dataStateAt", st, 1, 0, 0));
        } finally {
            on(qx, qz, () -> {
                for (int cell = 0; cell < 4096; cell++) {
                    level.setBlock(new BlockPos(qx * 16 + (cell & 15), y0 + (cell >> 8), qz * 16 + ((cell >> 4) & 15)), Blocks.AIR.defaultBlockState(), 2 | 16);
                }
                return null;
            });
            server.tick(1);
        }
    }

    /** 변환기: 이름 힌트 없는 클래스의 Object[] VarHandle 쓰기와, 힌트 있는 클래스의 Field.get·Field.set*·NMS 원소 형 VarHandle 을 장벽으로 돌린다. */
    @Test
    void transformerInstallsBarriers() {
        assertTrue(transformed.containsKey("mirrory.core.ReflectionUtils"), "VarHandle 장벽은 이름 힌트 게이트 밖");
        assertFalse(new String(pluginClasses.get("mirrory.core.ReflectionUtils"), java.nio.charset.StandardCharsets.ISO_8859_1).contains("minecraft"),
                "시험 전제: 그 클래스에 minecraft 문자열이 없다");
        assertEquals(List.of("org/domi/furnace/compat/nms/NmsArrays.compareAndSet"), calls("mirrory.core.ReflectionUtils", "org/domi/furnace/compat/nms/"));
        List<String> a = calls("mirrory.adapter.Adapter", "org/domi/furnace/compat/nms/");
        assertTrue(a.contains("org/domi/furnace/compat/nms/NmsArrays.compareAndExchange"), a.toString());
        assertTrue(a.contains("org/domi/furnace/compat/nms/NmsReflect.beforeFieldGet"), a.toString());
        assertEquals(4, a.stream().filter(s -> s.endsWith("NmsReflect.afterFieldSet")).count(), "setShort 셋 + set 하나: " + a);
        assertTrue(NmsArrays.normalize("([Lnet/minecraft/world/level/chunk/LevelChunkSection;ILnet/minecraft/world/level/chunk/LevelChunkSection;)V") != null);
        assertEquals(null, NmsArrays.normalize("([Ljava/lang/String;ILjava/lang/String;)V"), "다른 원소 형은 그대로(정확 호출 핸들 보호)");
        assertEquals(null, NmsArrays.normalize("([IIII)Z"), "기본형 배열은 아님");
    }

    private List<String> calls(String cls, String prefix) {
        List<String> out = new ArrayList<>();
        new ClassReader(transformed.get(cls)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int op, String owner, String n, String d, boolean itf) {
                        if (owner.startsWith(prefix)) out.add(owner + "." + n);
                    }
                };
            }
        }, 0);
        return out;
    }
}
