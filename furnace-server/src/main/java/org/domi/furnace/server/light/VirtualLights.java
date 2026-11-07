// 한 차원의 가짜 광원(플러그인 빛 API, W6): 칸마다 주인(플러그인 객체, 같음 비교)별 밝기. 빛 엔진은 블록 발광과 이 값 중 큰 쪽을 발광으로 본다.
// 청크가 내려가도 남는다(다시 올라오면 lightNewChunk 가 다시 건다). 디스크에는 쓰지 않는다 — 저장할 때 근처에 광원이 있으면
// 빛 배열을 빼고 isLightOn=0 으로 써서 바닐라가 열면 가짜 빛 없이 다시 계산하게 한다.
// 쓰기는 그 칸을 가진 region 스레드에서, 읽기는 아무 스레드(저장 IO 스레드가 affects 를 본다) — 청크 단위로 잠근다.
package org.domi.furnace.server.light;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class VirtualLights {

    /** 한 칸: 주인 → 밝기(1..15). */
    private static final class Cell {
        final LinkedHashMap<Object, Integer> byOwner = new LinkedHashMap<>(2);
        int effective;

        void recompute() {
            int m = 0;
            for (int v : byOwner.values()) m = Math.max(m, v);
            effective = m;
        }
    }

    /** 청크 키 → (칸 좌표 → 칸). 칸 맵은 그 객체로 잠근다. */
    private final ConcurrentHashMap<Long, HashMap<Long, Cell>> chunks = new ConcurrentHashMap<>();
    /** 광원 칸 수(빠른 "하나도 없음" 검사 — 빛 엔진이 칸마다 묻는다). */
    private final AtomicInteger cells = new AtomicInteger();

    public static long chunkKey(int cx, int cz) { return (long) cx << 32 | (cz & 0xFFFFFFFFL); }

    public boolean isEmpty() { return cells.get() == 0; }

    /** 그 칸의 실제 가짜 발광(주인들 중 가장 밝은 값, 없으면 0). */
    public int emission(int x, int y, int z) {
        if (cells.get() == 0) return 0;
        HashMap<Long, Cell> m = chunks.get(chunkKey(x >> 4, z >> 4));
        if (m == null) return 0;
        synchronized (m) {
            Cell c = m.get(LightEngine.pack(x, y, z));
            return c == null ? 0 : c.effective;
        }
    }

    /** 그 주인이 그 칸에 둔 밝기(없으면 0). */
    public int ownLevel(Object owner, int x, int y, int z) {
        HashMap<Long, Cell> m = chunks.get(chunkKey(x >> 4, z >> 4));
        if (m == null) return 0;
        synchronized (m) {
            Cell c = m.get(LightEngine.pack(x, y, z));
            Integer v = c == null ? null : c.byOwner.get(owner);
            return v == null ? 0 : v;
        }
    }

    /**
     * 주인의 밝기를 level 로 둔다(0 이면 지운다). 실제 발광 값이 바뀌었으면 true — 그때 호출자가 빛 엔진에 검사를 건다.
     * level 은 0..15 로 자른다.
     */
    public boolean set(Object owner, int x, int y, int z, int level) {
        int lvl = Math.max(0, Math.min(15, level));
        long ck = chunkKey(x >> 4, z >> 4), pk = LightEngine.pack(x, y, z);
        while (true) {
            HashMap<Long, Cell> m = lvl > 0 ? chunks.computeIfAbsent(ck, k -> new HashMap<>()) : chunks.get(ck);
            if (m == null) return false;
            synchronized (m) {
                if (chunks.get(ck) != m) continue;   // 그 사이 빈 맵이 빠졌다 — 다시
                Cell c = m.get(pk);
                int before = c == null ? 0 : c.effective;
                if (lvl > 0) {
                    if (c == null) {
                        c = new Cell();
                        m.put(pk, c);
                        cells.incrementAndGet();
                    }
                    c.byOwner.put(owner, lvl);
                } else if (c == null || c.byOwner.remove(owner) == null) {
                    return false;
                }
                c.recompute();
                if (c.byOwner.isEmpty()) {
                    m.remove(pk);
                    cells.decrementAndGet();
                    if (m.isEmpty()) chunks.remove(ck, m);
                }
                return c.effective != before;
            }
        }
    }

    /**
     * 로드되지 않은 칸의 광원을 지울 때 쓰는 주인. 그 청크가 내려가 있으면 빛을 다시 계산할 수 없어 로드된 이웃 청크에 번진 빛이
     * 낡은 채 남고, 다시 올라올 때 그 빛이 거꾸로 밀려 들어와 영영 안 지워진다. 그래서 그 몫을 이 주인에게 넘겨 두었다가,
     * 청크가 다시 올라와 빛을 다 맞춘 뒤 지운다(감소 전파가 이웃까지 정리한다).
     */
    public static final Object RETIRED = new Object() {
        @Override public String toString() { return "retired"; }
    };

    /** owner 의 몫을 RETIRED 로 넘긴다(실제 발광은 그대로). 넘긴 것이 있으면 true. */
    public boolean retire(Object owner, int x, int y, int z) {
        HashMap<Long, Cell> m = chunks.get(chunkKey(x >> 4, z >> 4));
        if (m == null) return false;
        synchronized (m) {
            Cell c = m.get(LightEngine.pack(x, y, z));
            Integer v = c == null ? null : c.byOwner.remove(owner);
            if (v == null) return false;
            c.byOwner.merge(RETIRED, v, Math::max);
            return true;
        }
    }

    /** 그 청크 안 RETIRED 몫이 있는 칸(묶은 좌표). */
    public long[] retiredIn(int cx, int cz) {
        HashMap<Long, Cell> m = chunks.get(chunkKey(cx, cz));
        if (m == null) return new long[0];
        synchronized (m) {
            return m.entrySet().stream().filter(e -> e.getValue().byOwner.containsKey(RETIRED)).mapToLong(Map.Entry::getKey).toArray();
        }
    }

    /** 그 청크 안 광원 칸(묶은 좌표). */
    public long[] sourcesIn(int cx, int cz) {
        HashMap<Long, Cell> m = chunks.get(chunkKey(cx, cz));
        if (m == null) return new long[0];
        synchronized (m) {
            long[] out = new long[m.size()];
            int i = 0;
            for (long p : m.keySet()) out[i++] = p;
            return out;
        }
    }

    /** 그 주인이 둔 칸 모두(묶은 좌표). */
    public List<Long> sourcesOf(Object owner) {
        List<Long> out = new ArrayList<>();
        for (HashMap<Long, Cell> m : chunks.values()) {
            synchronized (m) {
                for (Map.Entry<Long, Cell> e : m.entrySet()) if (e.getValue().byOwner.containsKey(owner)) out.add(e.getKey());
            }
        }
        return out;
    }

    /**
     * 그 청크의 빛이 가짜 광원의 영향을 받을 수 있는가 — 빛은 14칸까지 번지므로 자기와 맞닿은 8청크까지 본다.
     * 저장할 때 빛 배열을 뺄지 정한다.
     */
    public boolean affects(int cx, int cz) {
        if (cells.get() == 0) return false;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (chunks.containsKey(chunkKey(cx + dx, cz + dz))) return true;
        return false;
    }
}
