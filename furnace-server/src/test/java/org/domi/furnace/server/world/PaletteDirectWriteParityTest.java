// T-015: 섹션을 int[4096] 으로 펼치지 않고 묶인 팔레트에서 바로 쓰는 전송·저장 경로가 옛 경로(펼친 배열 → Palette.write /
// blockStatesFromArray)와 바이트가 같은지. 옛 경로는 운영 코드에 그대로 남아 있다(펼친 배열만 가진 Section 이 탄다) — 그것을 오라클로 쓴다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.List;
import java.util.SplittableRandom;
import org.domi.furnace.server.identity.Differential;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.protocol.play.ChunkSerializer;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PaletteDirectWriteParityTest {

    static BlockRegistry reg;
    static AnvilChunkCodec codec;
    static int plains;
    static int states;
    static int biomeCount;
    static final int BIOME_DIRECT = ChunkColumn.DEFAULT_BIOME_DIRECT_BITS;

    @BeforeAll
    static void load() throws Exception {
        reg = BlockRegistry.load("blocks/1.21.1.txt");
        List<String> biomes = RegistrySet.load("registries/1.21.1.txt").entries("minecraft:worldgen/biome");
        plains = biomes.indexOf("minecraft:plains");
        biomeCount = biomes.size();
        states = reg.totalStateCount();
        codec = new AnvilChunkCodec(reg, ItemRegistry.load("items/1.21.1.txt"), biomes, plains, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
    }

    /** 섹션을 만드는 길: 펼친 배열 load, NBT 인덱스 load, 빈 섹션에 setBlock 쌓기(팔레트 성장·옛 항목), load 뒤 편집, scratch. */
    enum Mode { LOAD, INDEXED, GROW, LOAD_THEN_EDIT, UNIFORM_AGAIN, SCRATCH_OPEN, SCRATCH_COMPACT }

    record Input(ChunkColumn col, String summary) {}

    private static final int[] DISTINCT = {1, 2, 3, 4, 15, 16, 17, 31, 32, 33, 100, 255, 256, 257, 600, 4096};

    private static int[] pool(SplittableRandom r, int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) p[i] = r.nextInt(5) == 0 ? r.nextInt(3) : r.nextInt(states);   // 공기 섞기
        return p;
    }

    private static int[] cells(SplittableRandom r, int[] pool, boolean runs) {
        int[] c = new int[4096];
        int cur = pool[0];
        for (int i = 0; i < 4096; i++) {
            if (pool.length == 4096) cur = pool[i];
            else if (!runs || r.nextInt(8) == 0) cur = pool[r.nextInt(pool.length)];
            c[i] = cur;
        }
        return c;
    }

    private static void edit(SplittableRandom r, ChunkColumn col, int sec, int[] pool, int n) {
        int baseY = ChunkColumn.MIN_Y + sec * 16;
        for (int k = 0; k < n; k++) col.setBlock(r.nextInt(16), baseY + r.nextInt(16), r.nextInt(16), pool[r.nextInt(pool.length)]);
    }

    static Input generate(SplittableRandom r) {
        ChunkColumn col = new ChunkColumn(plains, reg.directBits(), BIOME_DIRECT, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
        StringBuilder sb = new StringBuilder();
        int touched = 1 + r.nextInt(4);
        for (int t = 0; t < touched; t++) {
            int sec = r.nextInt(ChunkColumn.SECTION_COUNT);
            Mode m = Mode.values()[r.nextInt(Mode.values().length)];
            int d = DISTINCT[r.nextInt(DISTINCT.length)];
            int[] pool = pool(r, d);
            boolean runs = r.nextBoolean();
            ChunkSection s = col.sections()[sec];
            switch (m) {
                case LOAD -> col.loadSectionBlocks(sec, cells(r, pool, runs));
                case INDEXED -> {
                    // NBT 꼴: 값이 겹치는 팔레트(모르는 이름 → 공기 둘), 안 쓰는 뒤 항목
                    int[] vals = pool.clone();
                    if (vals.length > 1 && r.nextBoolean()) vals[vals.length - 1] = vals[0];
                    int used = 1 + r.nextInt(vals.length);
                    int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(Math.max(1, vals.length - 1)));
                    BitStorage idx = new BitStorage(bits, 4096);
                    int cur = r.nextInt(used);
                    for (int i = 0; i < 4096; i++) {
                        if (!runs || r.nextInt(8) == 0) cur = r.nextInt(used);
                        idx.set(i, cur);
                    }
                    col.loadSectionIndexed(sec, vals, idx);
                }
                case GROW -> edit(r, col, sec, pool, 1 + r.nextInt(r.nextBoolean() ? 64 : 6000));
                case LOAD_THEN_EDIT -> {
                    col.loadSectionBlocks(sec, cells(r, pool, runs));
                    edit(r, col, sec, pool(r, 1 + r.nextInt(40)), 1 + r.nextInt(r.nextBoolean() ? 8 : 3000));
                }
                case UNIFORM_AGAIN -> {
                    // 팔레트가 자란 뒤 모든 칸이 한 값으로 돌아감 → 저장소는 간접인데 서로 다른 값 1개(옛 항목만 남음)
                    col.loadSectionBlocks(sec, cells(r, pool, runs));
                    int v = r.nextInt(states);
                    int baseY = ChunkColumn.MIN_Y + sec * 16;
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                        if (r.nextInt(50) != 0 || t == 0) col.setBlock(x, baseY + y, z, v);
                    }
                }
                case SCRATCH_OPEN -> {
                    col.loadSectionBlocks(sec, cells(r, pool, runs));
                    int[] sc = s.openScratch();
                    sc[r.nextInt(4096)] = pool[r.nextInt(pool.length)];
                }
                case SCRATCH_COMPACT -> {
                    col.loadSectionBlocks(sec, cells(r, pool, runs));
                    int[] sc = s.openScratch();
                    for (int k = r.nextInt(500); k > 0; k--) sc[r.nextInt(4096)] = pool[r.nextInt(pool.length)];
                    s.compact();
                }
            }
            // 바이옴: 채우기, 칸별(9종 이상이면 직접 저장), 펼친 배열 load
            int bd = 1 + r.nextInt(r.nextInt(4) == 0 ? 14 : 8);
            int[] bpool = new int[bd];
            for (int i = 0; i < bd; i++) bpool[i] = r.nextInt(biomeCount);
            switch (r.nextInt(3)) {
                case 0 -> s.fillBiome(bpool[0]);
                case 1 -> { for (int k = r.nextInt(200); k > 0; k--) s.setBiome(r.nextInt(4), r.nextInt(4), r.nextInt(4), bpool[r.nextInt(bd)]); }
                default -> {
                    int[] b = new int[64];
                    for (int i = 0; i < 64; i++) b[i] = bpool[r.nextInt(bd)];
                    s.replaceBiomes(b, 0);
                }
            }
            sb.append(sec).append(':').append(m).append(" d=").append(d).append(" bd=").append(bd).append(runs ? " runs" : "").append("; ");
        }
        return new Input(col, sb.toString());
    }

    /** 같은 스냅샷을 펼친 배열만 가진 섹션으로 바꾼다 — 직렬화가 옛 경로(Palette.write·blockStatesFromArray)를 탄다. */
    static ChunkSnapshot flattened(ChunkSnapshot snap) {
        ChunkSnapshot.Section[] src = snap.sections(), out = new ChunkSnapshot.Section[src.length];
        for (int i = 0; i < src.length; i++) out[i] = new ChunkSnapshot.Section(src[i].nonAirCount(), src[i].blocks().clone(), src[i].biomes().clone());
        return new ChunkSnapshot(snap.chunkX(), snap.chunkZ(), snap.heightmap(), snap.worldSurfaceHeightmap(), out,
                snap.blockEntities(), snap.save(), snap.light());
    }

    static byte[] wire(ChunkSnapshot snap) {
        ByteBuf b = Unpooled.buffer();
        ChunkSerializer.serialize(b, snap, reg.directBits(), BIOME_DIRECT);
        byte[] out = ByteBufUtil.getBytes(b);
        b.release();
        return out;
    }

    static byte[] disk(ChunkSnapshot snap) {
        try {
            return NbtIo.toBytes(codec.toNbt(snap), "");
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void wireBytesMatchFlattenedPath() {
        Differential.<Input, byte[]>of("T-015 전송 팔레트", PaletteDirectWriteParityTest::generate)
                .seed(0x7015_0001L).iterations(600).describe(Input::summary)
                .check(in -> wire(flattened(ChunkSnapshot.of(3, -7, in.col()))), in -> wire(ChunkSnapshot.of(3, -7, in.col())));
    }

    @Test
    void diskNbtBytesMatchFlattenedPath() {
        Differential.<Input, byte[]>of("T-015 저장 팔레트", PaletteDirectWriteParityTest::generate)
                .seed(0x7015_0002L).iterations(400).describe(Input::summary)
                .check(in -> disk(flattened(ChunkSnapshot.forSave(3, -7, in.col()))), in -> disk(ChunkSnapshot.forSave(3, -7, in.col())));
    }

    // ------------------------------------------------------------------ 변이: 저장소 팔레트를 그대로 내보내면(첫 등장 순으로 안 고치면) 잡는가

    private static Object field(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 일부러 틀린 전송: 간접 저장소의 팔레트·묶인 배열을 그대로(옛 항목·저장 순서 포함). T-015 가 피해야 하는 바로 그 지름길. */
    static byte[] rawStorageWire(ChunkColumn col) {
        ByteBuf b = Unpooled.buffer();
        for (ChunkSection s : col.sections()) {
            b.writeShort(s.nonAirCount());
            writeRaw(b, (PaletteStorage) field(s, "blocks"), s.blocks(), reg.directBits(), 4, 8);
            writeRaw(b, (PaletteStorage) field(s, "biomes"), s.biomes(), BIOME_DIRECT, 1, 3);
        }
        byte[] out = ByteBufUtil.getBytes(b);
        b.release();
        return out;
    }

    private static void writeRaw(ByteBuf b, PaletteStorage st, int[] flat, int direct, int min, int max) {
        int bits = (int) field(st, "bits");
        int[] pal = (int[]) field(st, "palette");
        if (bits == 0 || pal == null) { Palette.write(b, flat, direct, min, max); return; }
        int n = (int) field(st, "paletteSize");
        b.writeByte(bits);
        Types.writeVarInt(b, n);
        for (int i = 0; i < n; i++) Types.writeVarInt(b, pal[i]);
        long[] data = ((BitStorage) field(st, "storage")).data();
        Types.writeVarInt(b, data.length);
        for (long l : data) b.writeLong(l);
    }

    static byte[] sectionsWire(ChunkSnapshot snap) {
        ByteBuf b = Unpooled.buffer();
        for (ChunkSnapshot.Section s : snap.sections()) {
            b.writeShort(s.nonAirCount());
            Palette.write(b, s.blocks(), reg.directBits(), 4, 8);
            Palette.write(b, s.biomes(), BIOME_DIRECT, 1, 3);
        }
        byte[] out = ByteBufUtil.getBytes(b);
        b.release();
        return out;
    }

    @Test
    void mutantRawStoragePaletteIsDetected() {
        Differential.<Input, byte[]>of("T-015 변이: 저장소 팔레트 그대로", PaletteDirectWriteParityTest::generate)
                .seed(0x7015_0001L).iterations(600).describe(Input::summary)
                .assertDetects(in -> sectionsWire(ChunkSnapshot.of(0, 0, in.col())), in -> rawStorageWire(in.col()));
    }

    // ------------------------------------------------------------------ 생성기가 모든 갈래를 지나가는가

    @Test
    void generatorCoversEveryBranch() {
        int single = 0, identityShared = 0, remapped = 0, direct = 0, scratch = 0, staleDropped = 0;
        for (int k = 0; k < 600; k++) {
            ChunkSnapshot snap = ChunkSnapshot.of(0, 0, generate(Differential.randomFor(0x7015_0001L, k)).col());
            for (ChunkSnapshot.Section s : snap.sections()) {
                PaletteStorage src = (PaletteStorage) field(s, "blockSrc");
                if (src == null) { scratch++; continue; }
                FirstSeenIndex ix = s.blockIndex();
                if (ix == null) { direct++; continue; }
                if (ix.size() == 1) { single++; continue; }
                int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(ix.size() - 1));
                long[] packed = ix.pack(bits);
                if (packed == ((BitStorage) field(src, "storage")).data()) identityShared++;
                else remapped++;
                if (ix.size() < (int) field(src, "paletteSize")) staleDropped++;
            }
        }
        String msg = "single=" + single + " shared=" + identityShared + " remapped=" + remapped + " direct=" + direct
                + " scratch=" + scratch + " stale=" + staleDropped;
        System.out.println("[T-015] 갈래 " + msg);
        assertTrue(single > 0 && identityShared > 50 && remapped > 50 && direct > 0 && scratch > 0 && staleDropped > 10, msg);
    }

    /** pack 이 BitStorage 와 같은 배치인지(모든 원본·대상 비트 조합, 남는 비트 0). */
    @Test
    void packMatchesBitStorageForAllBitWidths() {
        SplittableRandom r = new SplittableRandom(0x7015_0003L);
        for (int size : new int[]{64, 4096}) {
            for (int srcBits = 1; srcBits <= 8; srcBits++) {
                for (int it = 0; it < 30; it++) {
                    int palLen = 1 << srcBits;
                    int[] palette = new int[palLen];
                    // 저장소 팔레트는 값이 겹치지 않는다(idFor·loadIndexed 가 합침) — 서로 다른 값으로
                    java.util.LinkedHashSet<Integer> uniq = new java.util.LinkedHashSet<>();
                    while (uniq.size() < palLen) uniq.add(r.nextInt(states));
                    int pi = 0;
                    for (int v : uniq) palette[pi++] = v;
                    int used = Math.min(palLen, 2 + r.nextInt(palLen));
                    BitStorage st = new BitStorage(srcBits, size);
                    int[] flat = new int[size];
                    for (int i = 0; i < size; i++) { int k = r.nextInt(used); st.set(i, k); flat[i] = palette[k]; }
                    FirstSeenIndex ix = FirstSeenIndex.forBlocks();
                    ix.indirect(palette, st.data(), srcBits, size);
                    if (ix.size() < 2) continue;
                    // 기대: 펼친 값의 첫 등장 순
                    java.util.LinkedHashMap<Integer, Integer> order = new java.util.LinkedHashMap<>();
                    for (int v : flat) order.putIfAbsent(v, order.size());
                    assertEquals(order.size(), ix.size());
                    int need = 32 - Integer.numberOfLeadingZeros(ix.size() - 1);
                    for (int bits = need; bits <= 15; bits++) {
                        BitStorage want = new BitStorage(bits, size);
                        for (int i = 0; i < size; i++) want.set(i, order.get(flat[i]));
                        assertArrayEquals(want.data(), ix.pack(bits), "size=" + size + " src=" + srcBits + " bits=" + bits);
                    }
                }
            }
        }
    }
}
