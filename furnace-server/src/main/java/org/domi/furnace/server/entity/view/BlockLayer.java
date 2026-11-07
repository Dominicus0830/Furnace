// 한 플레이어(연결)에게만 덮어쓰는 가상 블록·바이옴 층. 서버 상태는 건드리지 않고, 그 사람에게 나가는 청크·블록 변경·바이옴 패킷에 덧입힌다
// (ViewRewriter → ChunkBodyRewriter). 청크를 다시 보내도(재접속·시야 밖에 갔다 옴·차원 이동) 층이 남아 있으면 같은 모습이 된다.
// 쓰기는 아무 스레드(락 하나, 청크 단위 복사 후 교체), 읽기는 연결 이벤트 루프·청크 전송 스레드에서 락 없이 한다.
// 층이 하나도 없으면 active 가 거짓이라 읽는 쪽은 volatile 읽기 하나로 끝난다.
package org.domi.furnace.server.entity.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockLayer {

    /** 한 칸의 덮어쓰기: 건 플러그인(주인)과 값(블록 상태 번호 또는 바이옴 번호). */
    public record Entry(Object owner, int value) {}

    /** 월드의 세로 모양과 와이어 직접 팰릿 비트 수(층을 처음 쓸 때 월드에서 읽어 둔다 — 월드마다 불변). */
    public record Dim(int minY, int height, int blockBits, int biomeBits) {
        public int sections() { return height >> 4; }
    }

    /**
     * 한 청크의 덮어쓰기(불변). 키는 청크 안 위치: 블록 = ((y-minY)<<8)|(z<<4)|x, 바이옴 칸 = (((y-minY)>>2)<<4)|((z>>2)<<2)|(x>>2).
     */
    public record Chunk(Map<Integer, Entry> blocks, Map<Integer, Entry> biomes) {
        static final Chunk EMPTY = new Chunk(Map.of(), Map.of());

        public boolean isEmpty() { return blocks.isEmpty() && biomes.isEmpty(); }
    }

    /** 걷은 것: 블록 칸(월드 좌표 x,y,z)과 바이옴이 바뀐 청크 키들. */
    public record Removed(Dim dim, List<int[]> blocks, Set<Long> biomeChunks) {
        public boolean isEmpty() { return blocks.isEmpty() && biomeChunks.isEmpty(); }
    }

    private static final class WorldLayer {
        final Dim dim;
        final ConcurrentHashMap<Long, Chunk> chunks = new ConcurrentHashMap<>();

        WorldLayer(Dim dim) { this.dim = dim; }
    }

    private final ConcurrentHashMap<String, WorldLayer> worlds = new ConcurrentHashMap<>();
    private volatile boolean active;

    public boolean active() { return active; }

    private void updateActive() {
        boolean any = false;
        for (WorldLayer w : worlds.values()) if (!w.chunks.isEmpty()) { any = true; break; }
        active = any;
    }

    public static long chunkKey(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }

    public static int blockKey(Dim d, int x, int y, int z) { return ((y - d.minY) << 8) | ((z & 15) << 4) | (x & 15); }

    public static int biomeKey(Dim d, int x, int y, int z) { return (((y - d.minY) >> 2) << 4) | (((z & 15) >> 2) << 2) | ((x & 15) >> 2); }

    // ------------------------------------------------------------------ 읽기

    public Dim dim(String dimension) {
        WorldLayer w = worlds.get(dimension);
        return w == null ? null : w.dim;
    }

    /** 그 청크의 덮어쓰기, 없으면 null. */
    public Chunk chunk(String dimension, int cx, int cz) {
        WorldLayer w = worlds.get(dimension);
        return w == null ? null : w.chunks.get(chunkKey(cx, cz));
    }

    /** 그 칸의 블록 덮어쓰기, 없으면 null. */
    public Entry block(String dimension, int x, int y, int z) {
        WorldLayer w = worlds.get(dimension);
        if (w == null || y < w.dim.minY || y >= w.dim.minY + w.dim.height) return null;
        Chunk c = w.chunks.get(chunkKey(x >> 4, z >> 4));
        return c == null ? null : c.blocks.get(blockKey(w.dim, x, y, z));
    }

    /** 그 칸이 든 바이옴 칸의 덮어쓰기, 없으면 null. */
    public Entry biome(String dimension, int x, int y, int z) {
        WorldLayer w = worlds.get(dimension);
        if (w == null || y < w.dim.minY || y >= w.dim.minY + w.dim.height) return null;
        Chunk c = w.chunks.get(chunkKey(x >> 4, z >> 4));
        return c == null ? null : c.biomes.get(biomeKey(w.dim, x, y, z));
    }

    // ------------------------------------------------------------------ 쓰기

    /** 칸마다 {x, y, z, 상태 번호}. 높이 밖인 칸은 건너뛴다. 바뀐 칸 수를 돌려준다. */
    public synchronized int putBlocks(Object owner, String dimension, Dim dim, List<int[]> cells) {
        WorldLayer w = worlds.computeIfAbsent(dimension, k -> new WorldLayer(dim));
        Map<Long, Map<Integer, Entry>> byChunk = new HashMap<>();
        int n = 0;
        for (int[] c : cells) {
            if (c[1] < w.dim.minY || c[1] >= w.dim.minY + w.dim.height) continue;
            byChunk.computeIfAbsent(chunkKey(c[0] >> 4, c[2] >> 4), k -> new HashMap<>()).put(blockKey(w.dim, c[0], c[1], c[2]), new Entry(owner, c[3]));
            n++;
        }
        for (var e : byChunk.entrySet()) {
            Chunk old = w.chunks.getOrDefault(e.getKey(), Chunk.EMPTY);
            Map<Integer, Entry> m = new HashMap<>(old.blocks);
            m.putAll(e.getValue());
            w.chunks.put(e.getKey(), new Chunk(Map.copyOf(m), old.biomes));
        }
        updateActive();
        return n;
    }

    /** 칸마다 {x, y, z, 바이옴 번호} — 그 칸이 든 바이옴 칸 전체에 걸린다. 바뀐 청크 키들을 돌려준다. */
    public synchronized Set<Long> putBiomes(Object owner, String dimension, Dim dim, List<int[]> cells) {
        WorldLayer w = worlds.computeIfAbsent(dimension, k -> new WorldLayer(dim));
        Map<Long, Map<Integer, Entry>> byChunk = new HashMap<>();
        for (int[] c : cells) {
            if (c[1] < w.dim.minY || c[1] >= w.dim.minY + w.dim.height) continue;
            byChunk.computeIfAbsent(chunkKey(c[0] >> 4, c[2] >> 4), k -> new HashMap<>()).put(biomeKey(w.dim, c[0], c[1], c[2]), new Entry(owner, c[3]));
        }
        for (var e : byChunk.entrySet()) {
            Chunk old = w.chunks.getOrDefault(e.getKey(), Chunk.EMPTY);
            Map<Integer, Entry> m = new HashMap<>(old.biomes);
            m.putAll(e.getValue());
            w.chunks.put(e.getKey(), new Chunk(old.blocks, Map.copyOf(m)));
        }
        updateActive();
        return new HashSet<>(byChunk.keySet());
    }

    /** owner 가 건 블록 층 중 positions({x,y,z})에 해당하는 것을 걷는다. */
    public synchronized Removed removeBlocks(Object owner, String dimension, List<int[]> positions) {
        WorldLayer w = worlds.get(dimension);
        if (w == null) return new Removed(null, List.of(), Set.of());
        List<int[]> removed = new ArrayList<>();
        for (int[] p : positions) {
            long ck = chunkKey(p[0] >> 4, p[2] >> 4);
            Chunk c = w.chunks.get(ck);
            if (c == null) continue;
            int key = blockKey(w.dim, p[0], p[1], p[2]);
            Entry e = c.blocks.get(key);
            if (e == null || !e.owner.equals(owner)) continue;
            Map<Integer, Entry> m = new HashMap<>(c.blocks);
            m.remove(key);
            store(w, ck, new Chunk(Map.copyOf(m), c.biomes));
            removed.add(new int[] {p[0], p[1], p[2]});
        }
        updateActive();
        return new Removed(w.dim, removed, Set.of());
    }

    /** owner 가 건 바이옴 칸 중 positions 가 든 것을 걷는다. */
    public synchronized Removed removeBiomes(Object owner, String dimension, List<int[]> positions) {
        WorldLayer w = worlds.get(dimension);
        if (w == null) return new Removed(null, List.of(), Set.of());
        Set<Long> touched = new HashSet<>();
        for (int[] p : positions) {
            long ck = chunkKey(p[0] >> 4, p[2] >> 4);
            Chunk c = w.chunks.get(ck);
            if (c == null) continue;
            int key = biomeKey(w.dim, p[0], p[1], p[2]);
            Entry e = c.biomes.get(key);
            if (e == null || !e.owner.equals(owner)) continue;
            Map<Integer, Entry> m = new HashMap<>(c.biomes);
            m.remove(key);
            store(w, ck, new Chunk(c.blocks, Map.copyOf(m)));
            touched.add(ck);
        }
        updateActive();
        return new Removed(w.dim, List.of(), touched);
    }

    /** owner 가 그 월드(dimension)에 건 층을 모두 걷는다. */
    public synchronized Removed removeWorld(Object owner, String dimension) {
        WorldLayer w = worlds.get(dimension);
        if (w == null) return new Removed(null, List.of(), Set.of());
        return removeWorldLocked(owner, w);
    }

    /** owner 가 건 층을 모든 월드에서 걷는다(월드 키 → 걷은 것). */
    public synchronized Map<String, Removed> removeAll(Object owner) {
        Map<String, Removed> out = new HashMap<>();
        for (var e : worlds.entrySet()) {
            Removed r = removeWorldLocked(owner, e.getValue());
            if (!r.isEmpty()) out.put(e.getKey(), r);
        }
        return out;
    }

    private Removed removeWorldLocked(Object owner, WorldLayer w) {
        List<int[]> blocks = new ArrayList<>();
        Set<Long> biomeChunks = new HashSet<>();
        for (Iterator<Map.Entry<Long, Chunk>> it = w.chunks.entrySet().iterator(); it.hasNext(); ) {
            var ce = it.next();
            long ck = ce.getKey();
            Chunk c = ce.getValue();
            Map<Integer, Entry> nb = new HashMap<>(), nbi = new HashMap<>();
            int cx = (int) (ck >> 32), cz = (int) ck;
            for (var b : c.blocks.entrySet()) {
                if (b.getValue().owner.equals(owner)) {
                    int k = b.getKey();
                    blocks.add(new int[] {(cx << 4) | (k & 15), w.dim.minY + (k >> 8), (cz << 4) | ((k >> 4) & 15)});
                } else {
                    nb.put(b.getKey(), b.getValue());
                }
            }
            boolean biomeChanged = false;
            for (var b : c.biomes.entrySet()) {
                if (b.getValue().owner.equals(owner)) biomeChanged = true; else nbi.put(b.getKey(), b.getValue());
            }
            if (biomeChanged) biomeChunks.add(ck);
            if (nb.size() == c.blocks.size() && nbi.size() == c.biomes.size()) continue;
            Chunk n = new Chunk(Map.copyOf(nb), Map.copyOf(nbi));
            if (n.isEmpty()) it.remove(); else ce.setValue(n);
        }
        updateActive();
        return new Removed(w.dim, blocks, biomeChunks);
    }

    private void store(WorldLayer w, long ck, Chunk c) {
        if (c.isEmpty()) w.chunks.remove(ck); else w.chunks.put(ck, c);
    }
}
