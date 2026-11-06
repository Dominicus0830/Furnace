// 섹션 블록/바이옴용 3-tier(single/indirect/direct) palette 저장소. 값은 int stateId/biomeId 직접.
package org.domi.furnace.server.world;

import java.util.Arrays;

final class PaletteStorage {

    private final int size;
    private final int minIndirect;
    private final int maxIndirect;
    private final int directBits;
    private int bits;
    private int[] palette;
    private int paletteSize;
    private int singleValue;
    private BitStorage storage;
    // 값→팔레트 인덱스 역맵(열린 주소법, 큰 팔레트에서만). lookupVals 는 (인덱스+1), 0 은 빈칸. 팔레트 순서(첫 등장 순)는 그대로다.
    private static final int LINEAR_MAX = 16;
    private int[] lookupKeys;
    private int[] lookupVals;

    PaletteStorage(int size, int minIndirect, int maxIndirect, int directBits, int initialValue) {
        this.size = size;
        this.minIndirect = minIndirect;
        this.maxIndirect = maxIndirect;
        this.directBits = directBits;
        this.bits = 0;
        this.singleValue = initialValue;
    }

    int get(int index) {
        if (bits == 0) return singleValue;
        int raw = storage.get(index);
        return palette == null ? raw : palette[raw];
    }

    /** 단일값 상태면 그 값, 아니면 none. */
    int singleValueOr(int none) {
        return bits == 0 ? singleValue : none;
    }

    void set(int index, int value) {
        if (bits == 0) {
            if (value == singleValue) return;
            growFromSingle();
        }
        int id = idFor(value);   // idFor가 resize 시 this.storage를 교체하므로 id 먼저 계산 후 set
        storage.set(index, id);
    }

    void load(int[] src, int offset) {
        int distinct = countDistinct(src, offset);
        if (distinct == 1) {
            bits = 0;
            singleValue = src[offset];
            palette = null;
            storage = null;
            paletteSize = 0;
            lookupKeys = null;
            lookupVals = null;
            return;
        }
        int needed = 32 - Integer.numberOfLeadingZeros(distinct - 1);
        if (needed <= maxIndirect) {
            initIndirect(Math.max(needed, minIndirect));
            for (int i = 0; i < size; i++) storage.set(i, idFor(src[offset + i]));
        } else {
            initDirect();
            for (int i = 0; i < size; i++) storage.set(i, src[offset + i]);
        }
    }

    /**
     * {@code load(펼친 배열)} 과 똑같은 상태(팔레트 첫 등장 순·비트 수·역맵)를, 팔레트 값과 칸별 팔레트 인덱스(디스크 NBT 꼴)에서 바로 만든다.
     * 칸마다 값 해시를 두 번(서로 다른 값 세기 + idFor) 타던 것을 인덱스 배열 조회로 바꾼다. 팔레트 값이 겹쳐도(모르는 이름 → 공기 둘) 하나로 합친다.
     * 인덱스가 팔레트 밖이면 예전 경로처럼 ArrayIndexOutOfBoundsException. 반환: 공기가 아닌 칸 수.
     */
    int loadIndexed(int[] vals, BitStorage idx) {
        int[] remap = new int[vals.length];
        Arrays.fill(remap, -1);
        int[] cell = CELL.get();
        int[] dv = DISTINCT.get();
        int[] count = COUNT.get();
        int n = 0;
        for (int i = 0; i < size; i++) {
            int k = idx.get(i);
            int r = remap[k];
            if (r < 0) {
                int v = vals[k];
                for (int j = 0; j < n; j++) if (dv[j] == v) { r = j; break; }
                if (r < 0) { count[n] = 0; dv[n] = v; r = n++; }
                remap[k] = r;
            }
            count[r]++;
            cell[i] = r;
        }
        int nonAir = 0;
        for (int j = 0; j < n; j++) if (!ChunkSection.isAir(dv[j])) nonAir += count[j];
        if (n == 1) {
            bits = 0;
            singleValue = dv[0];
            palette = null;
            storage = null;
            paletteSize = 0;
            lookupKeys = null;
            lookupVals = null;
            return nonAir;
        }
        int needed = 32 - Integer.numberOfLeadingZeros(n - 1);
        if (needed <= maxIndirect) {
            initIndirect(Math.max(needed, minIndirect));
            for (int j = 0; j < n; j++) {
                palette[j] = dv[j];
                if (lookupKeys != null) insert(dv[j], j);
            }
            paletteSize = n;
            for (int i = 0; i < size; i++) storage.set(i, cell[i]);
        } else {
            initDirect();
            for (int i = 0; i < size; i++) storage.set(i, dv[cell[i]]);
        }
        return nonAir;
    }

    // loadIndexed 작업 배열(스레드마다 하나, 재진입 없음). 크기는 칸 수 4096 상한.
    private static final ThreadLocal<int[]> CELL = ThreadLocal.withInitial(() -> new int[4096]);
    private static final ThreadLocal<int[]> DISTINCT = ThreadLocal.withInitial(() -> new int[4096]);
    private static final ThreadLocal<int[]> COUNT = ThreadLocal.withInitial(() -> new int[4096]);

    int[] unpack() {
        int[] out = new int[size];
        if (bits == 0) {
            Arrays.fill(out, singleValue);
        } else if (palette == null) {
            for (int i = 0; i < size; i++) out[i] = storage.get(i);
        } else {
            for (int i = 0; i < size; i++) out[i] = palette[storage.get(i)];
        }
        return out;
    }

    private int idFor(int value) {
        if (palette == null) return value;
        if (lookupKeys == null) {
            for (int i = 0; i < paletteSize; i++) {
                if (palette[i] == value) return i;
            }
        } else {
            int found = lookup(value);
            if (found >= 0) return found;
        }
        if (paletteSize < palette.length) {
            palette[paletteSize] = value;
            if (lookupKeys != null) insert(value, paletteSize);
            return paletteSize++;
        }
        if (bits < maxIndirect) growTo(bits + 1);
        else growToDirect();
        return idFor(value);
    }

    private void growFromSingle() {
        int prev = singleValue;
        initIndirect(minIndirect);
        idFor(prev);
    }

    private void growTo(int newBits) {
        int[] oldPalette = palette;
        int oldPaletteSize = paletteSize;
        BitStorage old = storage;
        initIndirect(newBits);
        for (int i = 0; i < oldPaletteSize; i++) idFor(oldPalette[i]);
        for (int i = 0; i < size; i++) storage.set(i, old.get(i));
    }

    private void growToDirect() {
        int[] oldPalette = palette;
        BitStorage old = storage;
        initDirect();
        for (int i = 0; i < size; i++) storage.set(i, oldPalette[old.get(i)]);
    }

    private void initIndirect(int newBits) {
        this.bits = newBits;
        this.palette = new int[1 << newBits];
        this.paletteSize = 0;
        this.storage = new BitStorage(newBits, size);
        if (palette.length > LINEAR_MAX) {
            lookupKeys = new int[palette.length * 2];
            lookupVals = new int[palette.length * 2];
        } else {
            lookupKeys = null;
            lookupVals = null;
        }
    }

    private static int slot(int value, int mask) {
        int h = value * 0x9E3779B9;
        return (h ^ (h >>> 16)) & mask;
    }

    private int lookup(int value) {
        int mask = lookupKeys.length - 1;
        for (int i = slot(value, mask); ; i = (i + 1) & mask) {
            int v = lookupVals[i];
            if (v == 0) return -1;
            if (lookupKeys[i] == value) return v - 1;
        }
    }

    private void insert(int value, int id) {
        int mask = lookupKeys.length - 1;
        int i = slot(value, mask);
        while (lookupVals[i] != 0) i = (i + 1) & mask;
        lookupKeys[i] = value;
        lookupVals[i] = id + 1;
    }

    private void initDirect() {
        this.bits = directBits;
        this.palette = null;
        this.paletteSize = 0;
        this.storage = new BitStorage(directBits, size);
        this.lookupKeys = null;
        this.lookupVals = null;
    }

    /** 독립된 backing 배열을 가진 깊은 복사본(pristine 청크 캐시용, ChunkPipeline 참조). */
    PaletteStorage copy() {
        PaletteStorage c = new PaletteStorage(size, minIndirect, maxIndirect, directBits, singleValue);
        c.bits = bits;
        c.palette = palette == null ? null : palette.clone();
        c.paletteSize = paletteSize;
        c.storage = storage == null ? null : storage.copy();
        c.lookupKeys = lookupKeys == null ? null : lookupKeys.clone();
        c.lookupVals = lookupVals == null ? null : lookupVals.clone();
        return c;
    }

    /**
     * 읽기 전용 사본(C-5): 팔레트·비트 저장소만 복사하고 역맵은 비운다. {@link #get}·{@link #unpack} 만 부른다 —
     * set·load 를 부르면 역맵이 없는 큰 팔레트에서 선형 탐색으로 떨어질 뿐 결과는 같지만, 스냅샷은 고치지 않는다.
     */
    PaletteStorage readCopy() {
        PaletteStorage c = new PaletteStorage(size, minIndirect, maxIndirect, directBits, singleValue);
        c.bits = bits;
        c.palette = palette == null ? null : palette.clone();
        c.paletteSize = paletteSize;
        c.storage = storage == null ? null : storage.copy();
        return c;
    }

    // 서로 다른 값 수(상한 cap 을 넘으면 cap+1). 해시 집합으로 세고 결과는 예전 선형 탐색과 같다.
    private int countDistinct(int[] src, int offset) {
        int cap = Math.min(size, (1 << maxIndirect) + 1);
        int[] keys = new int[Integer.highestOneBit(Math.max(cap, 1) * 2 - 1) << 1];
        boolean[] used = new boolean[keys.length];
        int mask = keys.length - 1;
        int n = 0;
        for (int i = 0; i < size; i++) {
            int v = src[offset + i];
            int s = slot(v, mask);
            while (true) {
                if (!used[s]) {
                    if (n == cap) return cap + 1;
                    used[s] = true;
                    keys[s] = v;
                    n++;
                    break;
                }
                if (keys[s] == v) break;
                s = (s + 1) & mask;
            }
        }
        return n;
    }
}
