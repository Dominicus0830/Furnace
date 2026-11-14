package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SplittableRandom;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * N2b-1(V1): 떨어진 섹션 객체(LevelChunkSection·PalettedContainer·팔레트 4종·비트 저장소)가 바닐라 1.21.1 과 같은가.
 * 정답은 바닐라 jar 로 계산한 표 두 개 — 같은 입력(시드로 다시 만든다)에 바닐라가 낸 숫자뿐이다.
 * <ul>
 * <li>{@code n2b/palette-oracle-1.21.1.txt.gz}({@code tools/vanilla-data/ExportPaletteOracle}): 컨테이너 240개(기본 생성자·5인자 생성자·
 *     설정과 저장소 비트가 어긋난 FAWE 모양·섹션)의 쓰기 뒤 직렬화 바이트 해시, 크기, maybeHas, count 합, copy·recreate 직렬화, 섹션 개수 3개,
 *     바닐라가 예외를 낸 쓰기 번호와 종류. 바이옴 레지스트리 크기.</li>
 * <li>{@code n2b/section-oracle-1.21.1.txt.gz}(N2b-0, 서버 시험 자원): 상태별 개수 깃발 26,684개, 섹션 300개의 개수 3개(하나씩 놓기 →
 *     다시 세기 → 다시 센 뒤 하나씩 바꾸기 — 유체 이중 셈·물 tickingFluid·음수 포함).</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NmsDetachedSectionOracleTest {

    FurnaceTestServer server;

    @BeforeAll
    void start() { server = FurnaceTestServer.builder().failFast(false).start(); }

    @AfterAll
    void stop() { server.close(); }

    static List<String[]> lines(String resource) throws Exception {
        try (InputStream in = NmsDetachedSectionOracleTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "정답표 없음 " + resource);
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
            List<String[]> out = new ArrayList<>();
            for (String line; (line = r.readLine()) != null; ) out.add(line.split(" "));
            return out;
        }
    }

    static String value(List<String[]> lines, String key) {
        for (String[] p : lines) if (p[0].equals(key)) return p[1];
        throw new AssertionError("줄 없음 " + key);
    }

    // ------------------------------------------------------------------ 팔레트 정답기

    @Test
    void paletteContainersMatchVanillaOracle() throws Exception {
        List<String[]> lines = lines("/n2b/palette-oracle-1.21.1.txt.gz");
        assertEquals("1", value(lines, "version"));
        int n = Integer.parseInt(value(lines, "states"));
        assertEquals(n, Block.BLOCK_STATE_REGISTRY.size(), "상태 수");
        int cases = 0, errors = 0;
        for (String[] p : lines) {
            if (!p[0].equals("case")) continue;
            long seed = Long.parseLong(p[1]);
            Cases c = Cases.make(seed, n);
            assertEquals(Integer.parseInt(p[2]), c.kind, "종류 seed=" + seed);
            assertEquals(Long.parseLong(p[3]), c.hash(), "생성기가 도구와 다름 seed=" + seed);
            String[] mine = run(c);
            String[] want = Arrays.copyOfRange(p, 4, p.length);
            assertArrayEquals(want, mine, "seed=" + seed + " kind=" + c.kind);
            cases++;
            if (!p[4].equals("-")) errors++;
        }
        assertEquals(Cases.COUNT, cases);
        assertTrue(errors > 0, "정답표에 바닐라 예외 사례가 없음(설정·저장소 어긋남)");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static String[] run(Cases c) throws Exception {
        IdMap idmap;
        Object[] values;
        if (c.generic) {
            Integer[] ints = new Integer[c.registrySize];
            for (int i = 0; i < ints.length; i++) ints[i] = Integer.valueOf(i);
            values = ints;
            idmap = new IntIds(ints);
        } else {
            values = null;
            idmap = Block.BLOCK_STATE_REGISTRY;
        }
        Object[] vals = values;
        java.util.function.IntFunction<Object> val = i -> vals != null ? vals[i] : Block.BLOCK_STATE_REGISTRY.byId(i);
        PalettedContainer.Strategy strategy = c.biomeStrategy ? PalettedContainer.Strategy.SECTION_BIOMES : PalettedContainer.Strategy.SECTION_STATES;
        PalettedContainer pc;
        LevelChunkSection section = null;
        if (c.kind == Cases.DEFAULT || c.kind == Cases.SECTION) {
            pc = new PalettedContainer(idmap, val.apply(c.pal[0]), strategy);
        } else {
            PalettedContainer.Strategy confFrom = c.kind == Cases.CTOR_MISMATCH ? PalettedContainer.Strategy.SECTION_STATES : strategy;
            PalettedContainer.Configuration conf = confFrom.getConfiguration(idmap, c.ctorBits);
            BitStorage storage = c.ctorBits == 0 ? new ZeroBitStorage(strategy.size()) : new SimpleBitStorage(c.ctorBits, strategy.size(), c.ctorCells);
            List list = new ArrayList();
            for (int v : c.ctorList) list.add(val.apply(v));
            pc = new PalettedContainer(idmap, strategy, conf, storage, list);
        }
        if (c.kind == Cases.SECTION) {
            Integer[] bi = new Integer[70];
            for (int i = 0; i < bi.length; i++) bi[i] = Integer.valueOf(i);
            section = new LevelChunkSection(pc, (PalettedContainerRO) new PalettedContainer(new IntIds(bi), bi[c.biome], PalettedContainer.Strategy.SECTION_BIOMES));
        }
        int size = strategy.size(), side = c.biomeStrategy ? 4 : 16, sh = c.biomeStrategy ? 2 : 4;
        String err = "-";
        for (int k = 0; k < c.opIdx.length; k++) {
            int i = c.opIdx[k] % size;
            int x = i & (side - 1), y = i >> (sh * 2), z = (i >> sh) & (side - 1);
            Object v = val.apply(c.opVal[k]);
            try {
                if (section != null) section.setBlockState(x, y, z, (BlockState) v, c.opKind[k] != 1);
                else if (c.opKind[k] == 0) pc.getAndSet(x, y, z, v);
                else if (c.opKind[k] == 1) pc.getAndSetUnchecked(x, y, z, v);
                else pc.set(x, y, z, v);
            } catch (RuntimeException e) {
                err = k + ":" + kindOf(e);
                break;
            }
        }
        Object probe = val.apply(c.pal[0]);
        LevelChunkSection s = section;
        PalettedContainer fpc = pc;
        List<String> out = new ArrayList<>();
        out.add(err);
        out.add(safe(() -> hash(fpc::write)));
        out.add(safe(() -> "" + fpc.getSerializedSize()));
        out.add(safe(() -> fpc.maybeHas(o -> o == probe) ? "1" : "0"));
        out.add(safe(() -> {
            int[] sum = new int[1];
            fpc.count((o, cnt) -> sum[0] += cnt);
            return "" + sum[0];
        }));
        out.add(safe(() -> hash(fpc.copy()::write)));
        out.add(safe(() -> hash(fpc.recreate()::write)));
        if (s != null) {
            short[] counts = counts(s);
            out.add("" + counts[0]);
            out.add("" + counts[1]);
            out.add("" + counts[2]);
            out.add(safe(() -> hash(s::write)));
        }
        return out.toArray(String[]::new);
    }

    interface Writes { void to(FriendlyByteBuf buf) throws Exception; }

    interface Text { String get() throws Exception; }

    static String safe(Text t) {
        try {
            return t.get();
        } catch (Exception e) {
            return "x:" + kindOf(e);
        }
    }

    /** 도구와 같은 분류. */
    static String kindOf(Exception e) {
        String m = e.getMessage();
        if (m != null && m.startsWith("Missing Palette entry for index")) return "MPE";
        if (e instanceof IllegalArgumentException) return "IAE";
        if (e instanceof IllegalStateException) return "ISE";
        if (e instanceof ArrayIndexOutOfBoundsException) return "AIOOBE";
        return e.getClass().getName();
    }

    static String hash(Writes w) throws Exception {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        w.to(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(0, bytes);
        byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 8; i++) s.append(String.format("%02x", d[i]));
        return s.toString();
    }

    static final Field NON_EMPTY, TICKING_BLOCK, TICKING_FLUID;

    static {
        try {
            NON_EMPTY = LevelChunkSection.class.getDeclaredField("nonEmptyBlockCount");
            TICKING_BLOCK = LevelChunkSection.class.getDeclaredField("tickingBlockCount");
            TICKING_FLUID = LevelChunkSection.class.getDeclaredField("tickingFluidCount");
            for (Field f : new Field[] {NON_EMPTY, TICKING_BLOCK, TICKING_FLUID}) f.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 리플렉션으로 읽는다(FAWE 처럼 — 필드 이름·형이 바닐라와 같아야 한다). */
    static short[] counts(LevelChunkSection s) throws Exception {
        return new short[] {NON_EMPTY.getShort(s), TICKING_BLOCK.getShort(s), TICKING_FLUID.getShort(s)};
    }

    /** 0..n-1 의 정수 객체 표(도구와 같다). */
    static final class IntIds implements IdMap<Integer> {
        final Integer[] v;

        IntIds(Integer[] v) { this.v = v; }

        @Override
        public int getId(Integer o) {
            for (int i = 0; i < v.length; i++) if (v[i] == o) return i;
            return -1;
        }

        @Override
        public Integer byId(int i) { return i >= 0 && i < v.length ? v[i] : null; }

        @Override
        public int size() { return v.length; }

        @Override
        public Iterator<Integer> iterator() { return Arrays.asList(v).iterator(); }
    }

    // ------------------------------------------------------------------ N2b-0 섹션 정답기(개수 규칙)

    @Test
    void stateFlagsThroughShimMatchVanilla() throws Exception {
        List<String[]> lines = lines("/n2b/section-oracle-1.21.1.txt.gz");
        String flags = value(lines, "flags");
        List<String> bad = new ArrayList<>();
        for (int s = 0; s < flags.length(); s++) {
            BlockState st = Block.BLOCK_STATE_REGISTRY.byId(s);
            var fluid = st.getFluidState();
            int mine = (st.isAir() ? 0 : 1) | (st.isRandomlyTicking() ? 2 : 0) | (fluid.isEmpty() ? 0 : 4) | (fluid.isRandomlyTicking() ? 8 : 0);
            int want = Character.digit(flags.charAt(s), 16);
            if (mine != want && bad.size() < 20) bad.add(s + ":" + st + " 바닐라=" + want + " shim=" + mine);
        }
        assertTrue(bad.isEmpty(), "상태 깃발 다름: " + bad);
        assertEquals(flags.length(), Block.BLOCK_STATE_REGISTRY.size());
    }

    /** 하나씩 놓기(A) → 그 블록으로 새 섹션 = 다시 세기(B) → B 에 하나씩 바꾸기(C). 떨어진 LevelChunkSection 의 개수 3개가 바닐라 숫자와 같다. */
    @Test
    void detachedSectionCountsMatchVanillaOracle() throws Exception {
        List<String[]> lines = lines("/n2b/section-oracle-1.21.1.txt.gz");
        String flagText = value(lines, "flags");
        int[] flags = new int[flagText.length()];
        for (int i = 0; i < flags.length; i++) flags[i] = Character.digit(flagText.charAt(i), 16);
        int[] pool = SectionCase.pool(flags);
        var reg = Block.BLOCK_STATE_REGISTRY;
        BlockState air = Blocks.AIR.defaultBlockState();
        int sections = 0, negative = 0, quirk = 0;
        for (String[] p : lines) {
            if (!p[0].equals("section")) continue;
            long seed = Long.parseLong(p[1]);
            SectionCase sc = SectionCase.make(seed, flags.length, pool);
            assertEquals(Long.parseLong(p[2]), sc.hash(), "생성기가 도구와 다름 seed=" + seed);
            LevelChunkSection a = new LevelChunkSection(new PalettedContainer<>(reg, air, PalettedContainer.Strategy.SECTION_STATES), biomes());
            PalettedContainer<BlockState> raw = new PalettedContainer<>(reg, air, PalettedContainer.Strategy.SECTION_STATES);
            for (int i = 0; i < 4096; i++) {
                a.setBlockState(i & 15, i >> 8, (i >> 4) & 15, reg.byId(sc.cells[i]));
                raw.set(i & 15, i >> 8, (i >> 4) & 15, reg.byId(sc.cells[i]));
            }
            assertCounts(p, 3, a, "하나씩 놓기 seed=" + seed);
            LevelChunkSection b = new LevelChunkSection(raw, biomes());
            assertCounts(p, 6, b, "다시 세기 seed=" + seed);
            for (int k = 0; k < sc.opIdx.length; k++) {
                int i = sc.opIdx[k];
                b.setBlockState(i & 15, i >> 8, (i >> 4) & 15, reg.byId(sc.opState[k]));
            }
            assertCounts(p, 9, b, "다시 센 뒤 하나씩 바꾸기 seed=" + seed);
            short[] c = counts(b);
            if (c[2] < 0) negative++;
            assertEquals(c[1] > 0 || c[2] > 0, b.isRandomlyTicking(), "isRandomlyTicking seed=" + seed);
            assertEquals(c[0] == 0, b.hasOnlyAir(), "hasOnlyAir seed=" + seed);
            LevelChunkSection fresh = new LevelChunkSection(b.getStates().copy(), biomes());
            if (fresh.isRandomlyTicking() != b.isRandomlyTicking()) quirk++;
            sections++;
        }
        assertEquals(300, sections);
        assertEquals(90, negative, "다시 센 뒤 바꾼 섹션의 tickingFluid 음수 수(N2b-0 정답표와 같아야 한다)");
        assertTrue(quirk > 0, "같은 블록인데 무작위 틱 판정이 갈리는 바닐라 특이점이 재현되지 않음");
    }

    static PalettedContainerRO<Holder<Biome>> biomes() {
        return new PalettedContainer<>(NmsChunks.BIOME_IDS, NmsChunks.biome("minecraft:plains"), PalettedContainer.Strategy.SECTION_BIOMES);
    }

    static void assertCounts(String[] p, int from, LevelChunkSection s, String what) throws Exception {
        short[] c = counts(s);
        int[] want = {Integer.parseInt(p[from]), Integer.parseInt(p[from + 1]), Integer.parseInt(p[from + 2])};
        assertArrayEquals(want, new int[] {c[0], c[1], c[2]}, what);
    }

    // ------------------------------------------------------------------ 모양(플러그인이 리플렉션으로 보는 것)

    @Test
    void biomeRegistrySizeMatchesVanilla() throws Exception {
        int want = Integer.parseInt(value(lines("/n2b/palette-oracle-1.21.1.txt.gz"), "biomes"));
        assertEquals(want, NmsChunks.BIOME_IDS.size(), "바이옴 레지스트리 크기(전역 팔레트 비트 수가 여기서 정해진다)");
        Holder<Biome> plains = NmsChunks.biome("minecraft:plains");
        assertSame(plains, NmsChunks.BIOME_IDS.byId(NmsChunks.BIOME_IDS.getId(plains)));
        assertEquals(-1, NmsChunks.BIOME_IDS.getId(null));
    }

    @Test
    void fieldsHaveVanillaNamesAndTypes() throws Exception {
        Field data = PalettedContainer.class.getDeclaredField("data");
        assertTrue(Modifier.isVolatile(data.getModifiers()), "data 는 volatile(바닐라)");
        assertSame(PalettedContainer.Data.class, data.getType());
        assertSame(net.minecraft.core.IdMap.class, PalettedContainer.class.getDeclaredField("registry").getType());
        assertSame(PalettedContainer.Strategy.class, PalettedContainer.class.getDeclaredField("strategy").getType());
        var comps = PalettedContainer.Data.class.getRecordComponents();
        assertEquals(List.of("configuration", "storage", "palette"), Arrays.stream(comps).map(rc -> rc.getName()).toList());
        assertEquals(List.of("factory", "bits"), Arrays.stream(PalettedContainer.Configuration.class.getRecordComponents()).map(rc -> rc.getName()).toList());
        for (Field f : new Field[] {NON_EMPTY, TICKING_BLOCK, TICKING_FLUID}) assertSame(short.class, f.getType(), f.getName());
        Field states = LevelChunkSection.class.getDeclaredField("states"), biomes = LevelChunkSection.class.getDeclaredField("biomes");
        assertSame(PalettedContainer.class, states.getType());
        assertTrue(Modifier.isFinal(states.getModifiers()));
        assertSame(PalettedContainerRO.class, biomes.getType());
        assertFalse(Modifier.isFinal(biomes.getModifiers()), "biomes 는 바닐라처럼 대입된다(readBiomes·FAWE)");

        // FAWE 처럼: 필드 대입과 개수 0 대입(clearCounts)
        LevelChunkSection s = new LevelChunkSection(new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY, Blocks.LAVA.defaultBlockState(),
                PalettedContainer.Strategy.SECTION_STATES), biomes());
        assertTrue(s.isRandomlyTickingBlocks(), "용암 블록 자체도 무작위 틱(바닐라 LiquidBlock — 유체 쪽 판정)");
        assertTrue(s.isRandomlyTickingFluids());
        TICKING_BLOCK.setShort(s, (short) 0);
        TICKING_FLUID.setShort(s, (short) 0);
        assertFalse(s.isRandomlyTicking(), "개수 대입은 그 값 그대로(FAWE clearCounts)");
        assertFalse(s.hasOnlyAir());
        biomes.setAccessible(true);
        var other = biomes();
        biomes.set(s, other);
        assertSame(other, s.getBiomes());
        PalettedContainer<BlockState> pc = s.getStates();
        data.setAccessible(true);
        Object d = data.get(pc);
        assertNotNull(d);
        Field storage = PalettedContainer.Data.class.getDeclaredField("storage");
        storage.setAccessible(true);
        assertTrue(storage.get(d) instanceof ZeroBitStorage, "값 하나 = 0 비트 저장소");
    }

    @Test
    void copyIsIndependentAndRecreateUsesFirstPaletteEntry() {
        var reg = Block.BLOCK_STATE_REGISTRY;
        PalettedContainer<BlockState> pc = new PalettedContainer<>(reg, Blocks.STONE.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
        pc.set(1, 2, 3, Blocks.DIRT.defaultBlockState());
        PalettedContainer<BlockState> copy = pc.copy();
        copy.set(1, 2, 3, Blocks.GOLD_BLOCK.defaultBlockState());
        assertSame(Blocks.DIRT.defaultBlockState(), pc.get(1, 2, 3));
        assertSame(Blocks.GOLD_BLOCK.defaultBlockState(), copy.get(1, 2, 3));
        PalettedContainer<BlockState> re = pc.recreate();
        assertSame(Blocks.STONE.defaultBlockState(), re.get(1, 2, 3));
        assertFalse(re.maybeHas(st -> st == Blocks.DIRT.defaultBlockState()));
        // 바닐라: 팔레트는 줄지 않는다 — 지운 값도 maybeHas 에 남는다
        pc.set(1, 2, 3, Blocks.STONE.defaultBlockState());
        assertTrue(pc.maybeHas(st -> st == Blocks.DIRT.defaultBlockState()));
    }

    // ---- 아래 생성기는 tools/vanilla-data/ExportPaletteOracle.java 와 글자 그대로 같아야 한다(해시로 확인) ----

    static final class Cases {
        static final int COUNT = 240;
        static final int DEFAULT = 0, CTOR = 1, CTOR_MISMATCH = 2, SECTION = 3;
        static final int[] STATE_K = {1, 2, 3, 4, 5, 9, 16, 17, 40, 256, 257, 600};
        static final int[] GENERIC_SIZE = {4, 9, 64, 65, 200};
        static final int[] CTOR_BITS = {0, 1, 2, 3, 4, 5, 6, 8, 15};

        int kind, registrySize, ctorBits, biome;
        boolean generic, biomeStrategy;
        int[] pal, ctorCells, ctorList, opIdx, opVal, opKind;

        static Cases make(long seed, int nStates) {
            SplittableRandom r = new SplittableRandom(seed * 0x9E3779B97F4A7C15L);
            Cases c = new Cases();
            c.kind = (int) (seed % 4);
            c.biomeStrategy = c.kind != SECTION && r.nextInt(3) == 0;
            c.generic = c.biomeStrategy;
            c.registrySize = c.generic ? GENERIC_SIZE[r.nextInt(GENERIC_SIZE.length)] : nStates;
            int k = c.generic ? 1 + r.nextInt(c.registrySize) : STATE_K[r.nextInt(STATE_K.length)];
            LinkedHashSet<Integer> pal = new LinkedHashSet<>();
            while (pal.size() < k) pal.add(r.nextInt(c.registrySize));
            c.pal = pal.stream().mapToInt(Integer::intValue).toArray();
            int size = c.biomeStrategy ? 64 : 4096;
            if (c.kind == CTOR || c.kind == CTOR_MISMATCH) {
                c.ctorBits = c.generic ? 1 + r.nextInt(8) : CTOR_BITS[r.nextInt(CTOR_BITS.length)];
                if (c.ctorBits == 15) c.ctorBits = 32 - Integer.numberOfLeadingZeros(nStates - 1);
                boolean global = c.ctorBits > 8 || (c.biomeStrategy && c.kind == CTOR && c.ctorBits > 3);
                int listLen = c.ctorBits == 0 ? 1 : global ? 0 : Math.min(c.pal.length, 1 << c.ctorBits);
                if (!global && listLen == 0) listLen = 1;
                c.ctorList = Arrays.copyOf(c.pal, Math.min(listLen, c.pal.length));
                c.ctorCells = new int[size];
                if (c.ctorBits > 0) {
                    for (int i = 0; i < size; i++) {
                        int p = c.pal[r.nextInt(c.pal.length)];
                        c.ctorCells[i] = global ? (p < (1 << c.ctorBits) ? p : 0) : r.nextInt(c.ctorList.length);
                    }
                }
            } else {
                c.ctorList = new int[0];
                c.ctorCells = new int[0];
            }
            c.biome = r.nextInt(70);
            int m = r.nextInt(c.biomeStrategy ? 300 : 6000);
            c.opIdx = new int[m];
            c.opVal = new int[m];
            c.opKind = new int[m];
            for (int i = 0; i < m; i++) {
                c.opIdx[i] = r.nextInt(size);
                c.opVal[i] = r.nextInt(10) == 0 ? 0 : c.pal[r.nextInt(c.pal.length)];
                c.opKind[i] = r.nextInt(3);
            }
            return c;
        }

        long hash() {
            long h = 1125899906842597L;
            h = 31 * h + kind; h = 31 * h + registrySize; h = 31 * h + ctorBits; h = 31 * h + biome;
            h = 31 * h + (generic ? 1 : 0); h = 31 * h + (biomeStrategy ? 1 : 0);
            for (int[] arr : new int[][]{pal, ctorCells, ctorList, opIdx, opVal, opKind}) for (int v : arr) h = 31 * h + v;
            return h;
        }
    }

    // ---- 아래 생성기는 tools/vanilla-data/ExportSectionOracle.java(·서버 VanillaSectionOracleTest) 와 글자 그대로 같아야 한다 ----

    static final class SectionCase {
        int[] cells = new int[4096];
        int[] opIdx, opState;

        static int[] pool(int[] flags) {
            int c = 0;
            for (int f : flags) if (f != 1) c++;
            int[] p = new int[c];
            c = 0;
            for (int s = 0; s < flags.length; s++) if (flags[s] != 1) p[c++] = s;
            return p;
        }

        static int pick(SplittableRandom r, int n, int[] pool) {
            return r.nextInt(10) < 7 ? pool[r.nextInt(pool.length)] : r.nextInt(n);
        }

        static SectionCase make(long seed, int n, int[] pool) {
            SplittableRandom r = new SplittableRandom(seed);
            SectionCase c = new SectionCase();
            int k = 1 + r.nextInt(24);
            int[] pal = new int[k];
            for (int i = 0; i < k; i++) pal[i] = pick(r, n, pool);
            int airPct = r.nextInt(101);
            for (int i = 0; i < 4096; i++) c.cells[i] = r.nextInt(100) < airPct ? 0 : pal[r.nextInt(k)];
            int m = r.nextInt(400);
            c.opIdx = new int[m];
            c.opState = new int[m];
            for (int i = 0; i < m; i++) {
                c.opIdx[i] = r.nextInt(4096);
                int q = r.nextInt(3);
                c.opState[i] = q == 0 ? 0 : q == 1 ? pal[r.nextInt(k)] : pick(r, n, pool);
            }
            return c;
        }

        long hash() {
            long h = 1125899906842597L;
            for (int v : cells) h = 31 * h + v;
            for (int v : opIdx) h = 31 * h + v;
            for (int v : opState) h = 31 * h + v;
            return h;
        }
    }
}
