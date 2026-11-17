package net.minecraft.world.level.chunk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.IdMap;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import org.domi.furnace.api.internal.ChunkInternals;
import org.junit.jupiter.api.Test;

/**
 * T-209: 붙은 보기의 {@code data} 실체화. 칸 번호 판({@code furnaceDataFromIds} — 박싱 없는 색인)은 예전 HashMap 판(아래 {@link #reference})과 같은
 * 디스크 배치(팔레트 = 처음 나온 순서, 비트 수 = calculateBitsForSerialization)를 설정·팔레트 항목·묶인 배열까지 그대로 내고, 서버 저장소 사본 판
 * ({@code furnaceDataFrom(PackedStates)})은 그 꼴을 펼치지 않고 그대로 넘기며 바닐라 설정과 맞지 않으면 디스크 배치로 돌아간다.
 */
class FurnaceDataLayoutTest {

    /** 블록 상태 레지스트리 크기쯤(전역 팔레트 15 비트). 값은 번호마다 하나뿐인 객체. */
    static final class Reg implements IdMap<Object> {
        final Object[] values;
        final Map<Object, Integer> ids = new HashMap<>();

        Reg(int size) {
            values = new Object[size];
            for (int i = 0; i < size; i++) {
                values[i] = "v" + i;
                ids.put(values[i], i);
            }
        }

        @Override public int getId(Object value) { return ids.getOrDefault(value, -1); }

        @Override public Object byId(int id) { return id >= 0 && id < values.length ? values[id] : null; }

        @Override public int size() { return values.length; }

        @Override public Iterator<Object> iterator() { return List.of(values).iterator(); }
    }

    static final Reg BLOCKS = new Reg(26000);
    static final Reg BIOMES = new Reg(64);

    /** 예전(PR #484 까지) 구현 그대로 — 기준. */
    static PalettedContainer.Data<Object> reference(IdMap<Object> registry, PalettedContainer.Strategy strategy, int[] registryIds) {
        int size = strategy.size();
        List<Object> entries = new ArrayList<>();
        Map<Integer, Integer> local = new HashMap<>();
        int[] cells = new int[size];
        for (int i = 0; i < size; i++) {
            int id = registryIds[i];
            Integer k = local.get(id);
            if (k == null) {
                Object v = registry.byId(id);
                if (v == null) throw new IllegalStateException("id missing from the registry: " + id);
                local.put(id, k = entries.size());
                entries.add(v);
            }
            cells[i] = k;
        }
        int bits = strategy.calculateBitsForSerialization(registry, entries.size());
        PalettedContainer.Configuration<Object> c = strategy.getConfiguration(registry, bits);
        BitStorage storage;
        if (bits == 0) {
            storage = new ZeroBitStorage(size);
        } else if (c.factory() == PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY) {
            int[] global = new int[size];
            for (int i = 0; i < size; i++) global[i] = registry.getId(entries.get(cells[i]));
            storage = new SimpleBitStorage(c.bits(), size, global);
        } else {
            storage = new SimpleBitStorage(c.bits(), size, cells);
        }
        return new PalettedContainer.Data<>(c, storage, c.factory().create(c.bits(), registry, null, entries));
    }

    /** 서버 저장소 사본의 대역: 비트 수, 팔레트(null = 직접 저장), 칸별 값(팔레트 번호 또는 상태 번호). */
    static final class FakePacked implements ChunkInternals.PackedStates {
        final int bits;
        final int[] palette;
        final long[] raw;

        FakePacked(int bits, int[] palette, int[] cells) {
            this.bits = bits;
            this.palette = palette;
            this.raw = bits == 0 ? new long[0] : new SimpleBitStorage(bits, cells.length, cells).getRaw();
        }

        @Override public int bits() { return bits; }

        @Override public int paletteSize() { return bits == 0 ? 1 : palette == null ? -1 : palette.length; }

        @Override public int paletteValue(int i) { return palette[i]; }

        @Override public long[] rawCopy() { return raw.clone(); }

        int[] ids() {
            int[] out = new int[4096];
            if (bits == 0) {
                java.util.Arrays.fill(out, palette[0]);
                return out;
            }
            SimpleBitStorage st = new SimpleBitStorage(bits, 4096, raw.clone());
            for (int i = 0; i < 4096; i++) out[i] = palette == null ? st.get(i) : palette[st.get(i)];
            return out;
        }
    }

    /** 서로 다른 번호 n 개(0 &lt; 번호 &lt; bound). */
    static int[] distinctIds(Random rnd, int n, int bound) {
        return rnd.ints(1, bound).distinct().limit(n).toArray();
    }

    @SuppressWarnings("unchecked")
    static PalettedContainer.Data<Object> build(String method, IdMap<Object> registry, PalettedContainer.Strategy strategy, Object content) throws Throwable {
        Method m = null;
        for (Method x : PalettedContainer.class.getDeclaredMethods()) if (x.getName().equals(method)) m = x;
        m.setAccessible(true);
        try {
            return (PalettedContainer.Data<Object>) m.invoke(null, registry, strategy, content, null);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    static void assertSameData(PalettedContainer.Data<Object> want, PalettedContainer.Data<Object> got, String what) {
        assertEquals(want.configuration(), got.configuration(), what + " 설정");
        assertEquals(want.storage().getSize(), got.storage().getSize(), what + " 칸 수");
        assertArrayEquals(want.storage().getRaw(), got.storage().getRaw(), what + " 묶인 배열");
        assertEquals(want.palette().getSize(), got.palette().getSize(), what + " 팔레트 크기");
        int n = want.configuration().factory() == PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY ? 0 : want.palette().getSize();
        for (int i = 0; i < n; i++) assertSame(want.palette().valueFor(i), got.palette().valueFor(i), what + " 팔레트 " + i);
        for (int i = 0; i < want.storage().getSize(); i += 17) {
            assertSame(want.palette().valueFor(want.storage().get(i)), got.palette().valueFor(got.storage().get(i)), what + " 칸 " + i);
        }
    }

    static int[] ids(Random rnd, int size, int distinct, int bound) {
        int[] pool = rnd.ints(0, bound).distinct().limit(distinct).toArray();
        int[] out = new int[size];
        for (int i = 0; i < size; i++) out[i] = pool[rnd.nextInt(pool.length)];
        if (distinct <= size) for (int k = 0; k < pool.length; k++) out[rnd.nextInt(size)] = pool[k];   // 대개 다 나오게(안 나와도 기준과 같아야 한다)
        return out;
    }

    @Test
    void blockLayoutMatchesReference() throws Throwable {
        Random rnd = new Random(209);
        for (int distinct : new int[] {1, 2, 3, 15, 16, 17, 31, 32, 33, 200, 256, 257, 300, 1000, 4096}) {
            for (int rep = 0; rep < 4; rep++) {
                int[] ids = ids(rnd, 4096, distinct, BLOCKS.size());
                if (rep == 3) java.util.Arrays.sort(ids);   // 긴 같은 값 줄
                var want = reference(BLOCKS, PalettedContainer.Strategy.SECTION_STATES, ids);
                assertSameData(want, build("furnaceDataFromIds", BLOCKS, PalettedContainer.Strategy.SECTION_STATES, ids.clone()), "ids " + distinct);
            }
        }
    }

    /**
     * 서버 저장소 사본은 그 꼴 그대로 data 가 된다 — 설정은 바닐라 getConfiguration(그 비트), 묶인 배열·팔레트 항목 순서(쓰이지 않는 옛 항목 포함)는 사본
     * 그대로, 칸 값은 서버와 같다. 0 비트·4 비트(배열)·5~8 비트(해시)·직접(전역 15 비트).
     */
    @Test
    void packedServerLayoutPassesThrough() throws Throwable {
        Random rnd = new Random(11);
        var S = PalettedContainer.Strategy.SECTION_STATES;
        List<FakePacked> cases = new ArrayList<>();
        cases.add(new FakePacked(0, new int[] {77}, null));
        for (int[] bp : new int[][] {{4, 2}, {4, 6}, {4, 16}, {5, 17}, {6, 40}, {8, 256}}) {
            int bits = bp[0], n = bp[1];
            int[] pal = distinctIds(rnd, n, BLOCKS.size());
            int[] cells = new int[4096];
            for (int i = 0; i < 4096; i++) cells[i] = rnd.nextInt(Math.max(1, n - 1));   // 마지막 항목은 안 쓰인 옛 항목
            cases.add(new FakePacked(bits, pal, cells));
        }
        int[] direct = new int[4096];
        for (int i = 0; i < 4096; i++) direct[i] = 1 + rnd.nextInt(9);   // 직접 저장인데 값은 몇 가지뿐(살아 있는 팔레트는 줄지 않는다)
        cases.add(new FakePacked(15, null, direct));
        for (FakePacked p : cases) {
            String what = "비트 " + p.bits + " 팔레트 " + p.paletteSize();
            var d = build("furnaceDataFrom", BLOCKS, S, p);
            assertEquals(S.getConfiguration(BLOCKS, p.bits), d.configuration(), what);
            assertArrayEquals(p.raw, p.bits == 0 ? new long[0] : d.storage().getRaw(), what);
            if (p.palette != null) {
                assertEquals(p.palette.length, d.palette().getSize(), what);
                for (int i = 0; i < p.palette.length; i++) assertSame(BLOCKS.byId(p.palette[i]), d.palette().valueFor(i), what + " 팔레트 " + i);
            }
            int[] ids = p.ids();
            for (int i = 0; i < 4096; i++) assertSame(BLOCKS.byId(ids[i]), d.palette().valueFor(d.storage().get(i)), what + " 칸 " + i);
        }
    }

    /** 사본의 비트 수가 이 전략의 바닐라 설정과 맞지 않으면(서버 설정이 바뀐 경우) 칸 번호로 디스크 배치를 만든다 — 예전 판과 같다. */
    @Test
    void mismatchedPackedFallsBackToDiskLayout() throws Throwable {
        Random rnd = new Random(3);
        var S = PalettedContainer.Strategy.SECTION_STATES;
        int[] cells = new int[4096];
        for (int i = 0; i < 4096; i++) cells[i] = rnd.nextInt(5);
        int[] direct = new int[4096];
        for (int i = 0; i < 4096; i++) direct[i] = 1 + rnd.nextInt(300);
        for (FakePacked p : new FakePacked[] {new FakePacked(3, distinctIds(rnd, 6, BLOCKS.size()), cells),
                new FakePacked(14, null, direct), new FakePacked(9, distinctIds(rnd, 5, BLOCKS.size()), cells)}) {
            assertSameData(reference(BLOCKS, S, p.ids()), build("furnaceDataFrom", BLOCKS, S, p), "비트 " + p.bits);
        }
    }

    @Test
    void biomeLayoutMatchesReference() throws Throwable {
        Random rnd = new Random(5);
        for (int distinct : new int[] {1, 2, 4, 5, 8, 9, 30, 64}) {
            int[] ids = ids(rnd, 64, Math.min(distinct, BIOMES.size()), BIOMES.size());
            var want = reference(BIOMES, PalettedContainer.Strategy.SECTION_BIOMES, ids);
            assertSameData(want, build("furnaceDataFromIds", BIOMES, PalettedContainer.Strategy.SECTION_BIOMES, ids), "바이옴 " + distinct);
        }
    }

    @Test
    void missingIdFailsLikeBefore() {
        int[] ids = new int[4096];
        ids[100] = -1;
        assertEquals("id missing from the registry: -1",
                assertThrows(IllegalStateException.class, () -> build("furnaceDataFromIds", BLOCKS, PalettedContainer.Strategy.SECTION_STATES, ids)).getMessage());
        ids[100] = BLOCKS.size() + 5;
        assertEquals("id missing from the registry: " + (BLOCKS.size() + 5),
                assertThrows(IllegalStateException.class, () -> build("furnaceDataFromIds", BLOCKS, PalettedContainer.Strategy.SECTION_STATES, ids)).getMessage());
    }
}
