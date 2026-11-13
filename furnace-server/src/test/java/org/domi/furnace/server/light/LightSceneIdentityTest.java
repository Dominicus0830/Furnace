// T-012 빛 묶음 증명 장면: 청크 올리기(생성 풀 미리 계산 + 가장자리 맞추기), 가장자리를 걸친 편집, 저장·내리기·다시 올리기(이웃이 내려간
// 동안 편집 — 빛을 줄이는 편집 포함), "저장 뒤 이웃이 올라와 번졌는데 다시 저장 안 된 청크" 를 지나며 빛 배열·하늘빛 원천·빛 갱신 패킷을
// 해시해 금 파일과 비교한다. 금 파일은 T-012 이전(기준 커밋 dec5d2c7, 디스크 빛을 버리고 늘 다시 계산)에서 만들었다.
//
// 해시하는 시점은 "올라온 청크들의 빛이 블록과 맞는 상태"뿐이다. 내려간 이웃이 있는 채로 다시 올린 청크의 중간 상태는 해시하지 않는다 —
// 그 순간은 옛 경로(고립 계산: 내려간 이웃 몫이 빠짐)와 저장 빛 경로(이웃 몫이 들어 있음, 바닐라와 같음)가 다르게 보이는 것이 맞다.
package org.domi.furnace.server.light;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.identity.IdentityHash;
import org.domi.furnace.server.identity.SceneHash;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.LightUpdate;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSnapshot;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.Test;

class LightSceneIdentityTest {

    static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");

    /** 올라온 청크만 보는 레벨. 내리기는 Region.evictIdleChunks 와 같이 dirty 면 저장하고, 다시 올리기는 Region.loadChunk 순서를 흉내 낸다. */
    static class SceneLevel extends ChunkLevel {
        final Map<Long, ChunkColumn> loaded = new HashMap<>();
        final Map<Long, byte[]> disk = new HashMap<>();
        final Set<Long> dirty = new HashSet<>();
        /** 클라이언트가 가진 빛(청크마다 빛 섹션 [하늘 count개, 블록 count개]) — 청크를 받을 때 통째로 담고 빛 갱신 패킷으로 고친다. */
        final Map<Long, byte[][]> client = new HashMap<>();
        final int lightBase, count;
        final SuperflatWorldSource flat = new SuperflatWorldSource(REG, 0);
        final AnvilChunkCodec codec = new AnvilChunkCodec(REG, 0);
        final long seed;

        SceneLevel(long seed) {
            super(1000000);
            this.seed = seed;
            lightBase = (minBuildHeight() >> 4) - 1 - ChunkLight.MIN_SECTION;
            count = ((maxBuildHeight() - minBuildHeight()) >> 4) + 2;
        }

        static long key(int x, int z) { return (long) x << 32 | (z & 0xFFFFFFFFL); }

        @Override protected ChunkColumn chunk(int x, int z) { return loaded.get(key(x, z)); }

        @Override protected void markDirty(int x, int z) { dirty.add(key(x, z)); }

        @Override protected void broadcastToChunk(int x, int z, Packet p) {}

        /** 빛 갱신 패킷을 그 청크를 가진 클라이언트에 적용한다(청크가 없으면 보는 사람이 없다 — 버린다). */
        @Override protected void broadcastLightToChunk(int x, int z, Packet p) {
            LightUpdate u = (LightUpdate) p;
            byte[][] c = client.get(key(x, z));
            if (c == null) return;
            int si = 0, bi = 0;
            for (int i = 0; i < count; i++) {
                if ((u.skyMask() & 1L << i) != 0) c[i] = u.skyData()[si++].clone();
                if ((u.emptySkyMask() & 1L << i) != 0) c[i] = new byte[2048];
                if ((u.blockMask() & 1L << i) != 0) c[count + i] = u.blockData()[bi++].clone();
                if ((u.emptyBlockMask() & 1L << i) != 0) c[count + i] = new byte[2048];
            }
            if (si != u.skyData().length || bi != u.blockData().length) throw new AssertionError("빛 패킷 배열 수가 마스크와 다르다");
        }

        /** 청크 데이터 패킷(빛 포함)을 보낸 것처럼 클라이언트 빛을 지금 서버 빛으로 채운다. */
        void sendChunk(int cx, int cz) {
            ChunkLight l = loaded.get(key(cx, cz)).light();
            byte[][] c = new byte[2 * count][];
            for (int i = 0; i < count; i++) {
                c[i] = l.sky[lightBase + i].clone();
                c[count + i] = l.block[lightBase + i].clone();
            }
            client.put(key(cx, cz), c);
        }

        @Override protected LevelServices services() { return LevelServices.DEFAULT; }

        /** 새 청크(디스크에 없으면 생성) 또는 디스크 청크를 생성 풀처럼 준비해 올린다. */
        void load(int cx, int cz) {
            byte[] bytes = disk.get(key(cx, cz));
            ChunkColumn col;
            if (bytes != null) {
                try {
                    col = codec.fromNbt(NbtIo.read(bytes));
                    col.clearDirty();   // AnvilWorldSource 처럼 디스크에서 읽은 청크는 깨끗하다
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                col = terrain(cx, cz);
            }
            ChunkLevel.precomputeLight(col, cx, cz, lightBedrockState(), true);   // 생성 풀
            loaded.put(key(cx, cz), col);                                         // 설치(Region.finishLoad)
            lightNewChunk(cx, cz);
            runLightUpdates();
            sendChunk(cx, cz);                                                    // 시야 안 플레이어에게 청크 전송
        }

        /** Region.evictIdleChunks: dirty 면 저장하고 내린다. */
        void unload(int cx, int cz) {
            ChunkColumn col = loaded.get(key(cx, cz));
            if (col.dirty() || dirty.contains(key(cx, cz))) {
                try {
                    disk.put(key(cx, cz), NbtIo.toBytes(codec.toNbt(ChunkSnapshot.forSave(cx, cz, col)), ""));
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                col.clearDirty();
            }
            dirty.remove(key(cx, cz));
            loaded.remove(key(cx, cz));
            client.remove(key(cx, cz));   // 내리는 청크는 구독이 없다(클라이언트도 버렸다)
        }

        /** 자동 저장(Region.autosave): dirty 청크를 저장만 한다. */
        void saveAll() {
            for (var e : new TreeMap<>(loaded).entrySet()) {
                long k = e.getKey();
                ChunkColumn col = e.getValue();
                if (!col.dirty() && !dirty.contains(k)) continue;
                int cx = (int) (k >> 32), cz = (int) k;
                try {
                    disk.put(k, NbtIo.toBytes(codec.toNbt(ChunkSnapshot.forSave(cx, cz, col)), ""));
                } catch (java.io.IOException ex) {
                    throw new RuntimeException(ex);
                }
                col.clearDirty();
                dirty.remove(k);
            }
        }

        /** 칸마다 정해지는 지형: 언덕·동굴·반 블록 지붕·잎·유리·물·발광 블록이 청크 가장자리를 가로지른다. */
        ChunkColumn terrain(int cx, int cz) {
            ChunkColumn col = flat.generate(cx, cz);
            int stone = REG.defaultStateId("minecraft:stone"), air = REG.defaultStateId("minecraft:air");
            int slabTop = REG.stateIdOrAir("minecraft:stone_slab", Map.of("type", "top", "waterlogged", "false"));
            int slabBottom = REG.stateIdOrAir("minecraft:stone_slab", Map.of("type", "bottom", "waterlogged", "false"));
            int leaves = REG.defaultStateId("minecraft:oak_leaves"), glass = REG.defaultStateId("minecraft:glass");
            int water = REG.defaultStateId("minecraft:water"), glow = REG.defaultStateId("minecraft:glowstone");
            int torch = REG.defaultStateId("minecraft:torch"), lava = REG.defaultStateId("minecraft:lava");
            int lantern = REG.defaultStateId("minecraft:sea_lantern"), rtorch = REG.defaultStateId("minecraft:redstone_torch");
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                long hc = mix(seed, (x >> 2) * 31L + (z >> 2));
                int h = -50 + (int) ((hc >>> 7) & 31);
                for (int y = -60; y <= h; y++) col.setBlock(lx, y, lz, stone);
                long hx = mix(seed ^ 0x55, x * 7919L + z);
                if ((hx & 15) == 0) for (int y = h + 1; y <= h + 4; y++) col.setBlock(lx, y, lz, leaves);
                else if ((hx & 31) == 1) for (int y = h + 1; y <= h + 6; y++) col.setBlock(lx, y, lz, glass);
                // 반 블록 지붕(대각선 띠) — 하늘빛 그림자와 모양 가림이 청크를 가로지른다
                if (Math.floorMod((x >> 3) + (z >> 3), 3) == 0) col.setBlock(lx, -5, lz, (x + z & 1) == 0 ? slabTop : slabBottom);
                if (Math.floorMod((x >> 2) - (z >> 3), 5) == 0) col.setBlock(lx, 20, lz, stone);
                if (h < -40 && (hc & 3) == 0) col.setBlock(lx, h + 1, lz, water);
            }
            Random r = new Random(mix(seed, key(cx, cz)));
            // 동굴: 땅속 공기 덩어리(가장자리 근처를 자주)
            for (int n = 0; n < 6; n++) {
                int ox = edgeBiased(r), oz = edgeBiased(r), oy = -58 + r.nextInt(18), rad = 2 + r.nextInt(3);
                for (int dx = -rad; dx <= rad; dx++) for (int dy = -rad; dy <= rad; dy++) for (int dz = -rad; dz <= rad; dz++) {
                    int lx = ox + dx, lz = oz + dz, y = oy + dy;
                    if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || y < -60 || dx * dx + dy * dy + dz * dz > rad * rad) continue;
                    col.setBlock(lx, y, lz, air);
                }
            }
            int[] emit = {glow, torch, lava, lantern, rtorch};
            for (int n = 0; n < 14; n++) col.setBlock(edgeBiased(r), -58 + r.nextInt(40), edgeBiased(r), emit[r.nextInt(emit.length)]);
            return col;
        }
    }

    static long mix(long a, long b) {
        long z = a * 0x9E3779B97F4A7C15L + b;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 절반은 가장자리 두 칸 안. */
    static int edgeBiased(Random r) {
        if (r.nextBoolean()) return r.nextInt(16);
        int e = r.nextInt(4);
        return e < 2 ? e : 14 + (e - 2);
    }

    static int[] palette() {
        String[] names = {"minecraft:air", "minecraft:air", "minecraft:air", "minecraft:stone", "minecraft:stone", "minecraft:glowstone",
                "minecraft:torch", "minecraft:oak_leaves", "minecraft:glass", "minecraft:water", "minecraft:lava", "minecraft:sea_lantern",
                "minecraft:redstone_torch", "minecraft:ice", "minecraft:snow"};
        List<Integer> out = new ArrayList<>();
        for (String n : names) out.add(REG.defaultStateId(n));
        out.add(REG.stateIdOrAir("minecraft:stone_slab", Map.of("type", "top", "waterlogged", "false")));
        out.add(REG.stateIdOrAir("minecraft:stone_slab", Map.of("type", "bottom", "waterlogged", "false")));
        out.add(REG.stateIdOrAir("minecraft:oak_stairs", Map.of("facing", "east", "half", "bottom", "shape", "straight", "waterlogged", "false")));
        out.add(REG.stateIdOrAir("minecraft:snow", Map.of("layers", "3")));
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** 칸 무작위 편집: 대부분 청크 가장자리 두 칸 안, 땅 근처 높이. flags 2|16 — 이웃 갱신 없이 블록만(결정적). */
    static void edits(SceneLevel lv, Random r, int n, int[] pal, int lo, int hi) {
        for (int i = 0; i < n; i++) {
            int cx = lo + r.nextInt(hi - lo + 1), cz = lo + r.nextInt(hi - lo + 1);
            if (!lv.loaded.containsKey(SceneLevel.key(cx, cz))) continue;
            int x = cx * 16 + edgeBiased(r), z = cz * 16 + edgeBiased(r), y = -58 + r.nextInt(84);
            lv.setBlock(new Pos(x, y, z), pal[r.nextInt(pal.length)], 2 | 16);
        }
    }

    /** 칸 하나 편집(그 청크가 올라와 있을 때만). */
    static void put(SceneLevel lv, int x, int y, int z, int state) {
        if (lv.loaded.containsKey(SceneLevel.key(x >> 4, z >> 4))) lv.setBlock(new Pos(x, y, z), state, 2 | 16);
    }

    /** 올라온 청크 전부의 빛(26층 × 하늘·블록)·하늘빛 원천·켜짐을 키 순서로. */
    static IdentityHash lightHash(SceneLevel lv) {
        IdentityHash h = new IdentityHash();
        for (var e : new TreeMap<>(lv.loaded).entrySet()) {
            h.add(e.getKey());
            chunkHash(h, e.getValue().light());
        }
        return h;
    }

    static void chunkHash(IdentityHash h, ChunkLight l) {
        h.add(l.lightEnabled);
        for (int i = 0; i < ChunkLight.SECTIONS; i++) {
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int wy = ChunkLight.MIN_Y + i * 16 + y;
                h.add(l.skyAt(x, wy, z) | l.blockAt(x, wy, z) << 4);
            }
        }
        h.add(l.lowestSource);
    }

    /**
     * 틱 끝(빛 계산) → 방송 → 클라이언트가 가진 빛(관측 결과). 패킷 바이트·개수가 아니라 클라이언트에 남는 빛을 해시한다 — 값이 같은
     * 섹션을 다시 보내는 중복 갱신은 보이지 않고, 빠뜨린 갱신(서버 빛과 클라이언트 빛이 갈림)은 잡힌다.
     */
    static IdentityHash flush(SceneLevel lv) {
        lv.runLightUpdates();
        lv.broadcastChanges();
        IdentityHash h = new IdentityHash();
        h.add(lv.client.size());
        for (var e : new TreeMap<>(lv.client).entrySet()) {
            h.add(e.getKey());
            for (byte[] layer : e.getValue()) h.add(layer);
        }
        return h;
    }

    static void scene(SceneHash.Recorder rec, long seed) {
        SceneLevel lv = new SceneLevel(seed);
        int[] pal = palette();
        Random r = new Random(seed);
        int glow = REG.defaultStateId("minecraft:glowstone"), stone = REG.defaultStateId("minecraft:stone"), air = REG.defaultStateId("minecraft:air");
        String s = "seed " + seed + " ";

        // P1 7×7 청크를 섞인 순서로 올린다(생성 → 미리 계산 → 가장자리 맞추기)
        List<int[]> order = new ArrayList<>();
        for (int cx = -3; cx <= 3; cx++) for (int cz = -3; cz <= 3; cz++) order.add(new int[]{cx, cz});
        java.util.Collections.shuffle(order, new Random(seed * 7 + 1));
        for (int[] c : order) lv.load(c[0], c[1]);
        rec.line(s + "P1 load light", lightHash(lv));
        rec.line(s + "P1 load packets", flush(lv));

        // P2 가장자리를 걸친 편집 다섯 묶음(묶음마다 빛 계산·방송)
        for (int b = 0; b < 5; b++) {
            edits(lv, r, 120, pal, -3, 3);
            rec.line(s + "P2 batch " + b + " packets", flush(lv));
            rec.line(s + "P2 batch " + b + " light", lightHash(lv));
        }

        // P3 서로 떨어진 청크 셋을 저장·내림 → 그 둘레 편집(빛을 줄이는 편집 포함: 광원 치우기·막기) → 다시 올림
        int[][] apart = {{-2, -2}, {1, 0}, {-2, 2}};
        lv.saveAll();
        for (int[] c : apart) {   // 내리기 전에 그 가장자리 바로 바깥(이웃 청크 쪽)에 광원을 둬서 저장 빛에 이웃 몫이 들어가게
            int bx = c[0] * 16 - 1, bz = c[1] * 16 + 8;
            put(lv, bx, -30, bz, glow);
            put(lv, bx - 1, -30, bz, air);
        }
        rec.line(s + "P3 pre packets", flush(lv));
        for (int[] c : apart) lv.unload(c[0], c[1]);
        for (int[] c : apart) {
            int bx = c[0] * 16 - 1, bz = c[1] * 16 + 8;
            put(lv, bx, -30, bz, air);                      // 광원 치움 → 줄어드는 빛이 내려간 청크 쪽으로 가려다 막힌다
            put(lv, c[0] * 16 + 16, -20, c[1] * 16 + 3, glow);   // 반대쪽 이웃에 새 광원(늘어나는 빛)
            put(lv, c[0] * 16 + 7, 25, c[1] * 16 - 1, stone);    // 북쪽 이웃 칸을 막는다(하늘빛 감소)
        }
        edits(lv, r, 150, pal, -3, 3);
        rec.line(s + "P3 unloaded-edit packets", flush(lv));
        rec.line(s + "P3 unloaded-edit light", lightHash(lv));
        for (int[] c : apart) lv.load(c[0], c[1]);
        rec.line(s + "P3 reload packets", flush(lv));
        rec.line(s + "P3 reload light", lightHash(lv));

        // P4 이웃끼리 같이 내리고, 하나만 먼저 다시 올린 뒤(중간 상태는 해시 안 함) 나머지를 올린다
        int[][] pair = {{0, 0}, {0, 1}, {1, 1}};
        for (int[] c : pair) lv.unload(c[0], c[1]);
        put(lv, 15, -40, -1, air);
        put(lv, -1, -35, 20, glow);
        edits(lv, r, 80, pal, -3, 3);
        lv.runLightUpdates();
        lv.load(0, 0);
        lv.runLightUpdates();
        lv.load(1, 1);
        lv.load(0, 1);
        lv.runLightUpdates();
        lv.broadcastChanges();
        rec.line(s + "P4 staggered reload light", lightHash(lv));

        // P5 "저장 뒤 이웃이 올라와 번졌는데 다시 저장 안 된 청크": A(3,3) 저장 → 이웃 B(4,3) 새로 올라와 A 로 번짐 → A 내림(옛 경로는
        // 빛 변경을 dirty 로 안 쳐서 저장 안 됨) → B 에서 광원 치움 → A 다시 올림
        lv.saveAll();
        put(lv, 63, -45, 50, glow);
        flush(lv);
        lv.saveAll();
        lv.load(4, 3);
        put(lv, 64, -45, 52, glow);   // B 의 서쪽 가장자리 광원 → A 로 번진다
        flush(lv);
        lv.unload(3, 3);
        put(lv, 64, -45, 52, air);    // A 가 내려간 동안 치움
        put(lv, 64, -44, 55, stone);
        flush(lv);
        lv.load(3, 3);
        rec.line(s + "P5 reload packets", flush(lv));
        rec.line(s + "P5 reload light", lightHash(lv));

        // P6 다시 올린 청크 둘레 편집이 지금처럼 이어지는가
        for (int b = 0; b < 3; b++) {
            edits(lv, r, 100, pal, -3, 4);
            rec.line(s + "P6 batch " + b + " packets", flush(lv));
        }
        // P7 올라온 이웃의 빛이 줄어 이 청크 빛도 줄었는데(블록은 그대로) 그대로 내렸다가 다시 올림 — 바닐라는 빛이 바뀐 청크를
        // 저장 대상으로 친다(sectionLightChanged → setUnsaved). 디스크 빛을 읽는다면 이 저장이 빠지면 지운 빛이 되살아난다.
        put(lv, -17, -50, -40, glow);   // (-2,-3) 동쪽 끝 → (-1,-3) 로 번짐
        put(lv, -17, -49, -40, air);
        flush(lv);
        for (int x = -16; x <= -12; x++) put(lv, x, -50, -40, air);   // (-1,-3) 쪽으로 굴 → 빛이 반드시 번지고, 이 편집으로 번진 빛을 담은 채 저장된다
        flush(lv);
        lv.saveAll();
        put(lv, -17, -50, -40, air);
        flush(lv);
        lv.unload(-1, -3);
        lv.load(-1, -3);
        rec.line(s + "P7 reload packets", flush(lv));
        rec.line(s + "P7 reload light", lightHash(lv));

        // 끝: 청크별
        for (var e : new TreeMap<>(lv.loaded).entrySet()) {
            IdentityHash h = new IdentityHash();
            chunkHash(h, e.getValue().light());
            rec.line(s + "end chunk " + (int) (e.getKey() >> 32) + "," + (int) (long) e.getKey(), h);
        }
    }

    @Test
    void lightBatchScene() {
        SceneHash.assertGolden("light-batch-scene", rec -> {
            scene(rec, 1);
            scene(rec, 2);
        });
    }
}
