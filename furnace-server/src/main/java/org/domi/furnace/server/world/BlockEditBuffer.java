// 빠른 블록 쓰기(E7)의 쓰기 목록 — 청크 → 섹션별 칸 배열. 부른 스레드에서 채우고, 적용은 BlockEdits 가 소유 region 스레드에서 한다.
// 채우는 동안 월드를 읽지 않는다. 다 채운 뒤에는 읽기만 하므로(적용 스레드로 넘길 때 작업 큐가 happens-before) 잠금이 없다.
package org.domi.furnace.server.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class BlockEditBuffer {

    /** 한 청크의 쓰기: 섹션마다 4096칸, 값은 상태 번호 + 1(0 = 적지 않음). 칸 번호 = y<<8 | z<<4 | x(바닐라 섹션 순서). */
    public static final class ChunkEdit {
        final int cx, cz;
        final char[][] sections;
        int count;

        ChunkEdit(int cx, int cz, int sectionCount) {
            this.cx = cx;
            this.cz = cz;
            this.sections = new char[sectionCount][];
        }

        public int cx() { return cx; }

        public int cz() { return cz; }

        /** 적은 칸 수. */
        public int count() { return count; }
    }

    private final int minY, maxY;
    /** 넣은 순서대로(적용 순서가 결정적이게). */
    private final HashMap<Long, ChunkEdit> chunks = new HashMap<>();
    private final List<ChunkEdit> order = new ArrayList<>();
    private ChunkEdit last;
    private int size, outOfHeight;

    /** minY 이상 maxY 미만이 월드 높이. 상태 번호는 0..65534 여야 한다(1.21.1 은 약 2.6만). */
    public BlockEditBuffer(int minY, int maxY) {
        if (((maxY - minY) & 15) != 0) throw new IllegalArgumentException("높이가 16의 배수가 아니다: " + minY + ".." + maxY);
        this.minY = minY;
        this.maxY = maxY;
    }

    public int minY() { return minY; }

    /** 그 칸을 state 로 적는다. 높이 밖이면 세기만 하고 버린다. */
    public void set(int x, int y, int z, int state) {
        if (state < 0 || state >= 0xFFFF) throw new IllegalArgumentException("상태 번호 범위 밖: " + state);
        if (y < minY || y >= maxY) {
            outOfHeight++;
            return;
        }
        int cx = x >> 4, cz = z >> 4;
        ChunkEdit e = last;
        if (e == null || e.cx != cx || e.cz != cz) {
            long key = Regionizer.chunkKey(cx, cz);
            e = chunks.get(key);
            if (e == null) {
                e = new ChunkEdit(cx, cz, (maxY - minY) >> 4);
                chunks.put(key, e);
                order.add(e);
            }
            last = e;
        }
        int ry = y - minY;
        char[] sec = e.sections[ry >> 4];
        if (sec == null) e.sections[ry >> 4] = sec = new char[4096];
        int idx = (ry & 15) << 8 | (z & 15) << 4 | x & 15;
        if (sec[idx] == 0) {
            e.count++;
            size++;
        }
        sec[idx] = (char) (state + 1);
    }

    /** 적은 칸 수(같은 칸은 한 번, 높이 밖은 빼고). */
    public int size() { return size; }

    /** 높이 밖이라 버린 칸 수(같은 칸을 여러 번 적었으면 여러 번). */
    public int outOfHeight() { return outOfHeight; }

    /** 청크별 쓰기, 처음 적은 순서. */
    public List<ChunkEdit> chunks() { return order; }
}
