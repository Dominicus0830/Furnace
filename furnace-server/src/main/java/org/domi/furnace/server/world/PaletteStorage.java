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
        for (int i = 0; i < paletteSize; i++) {
            if (palette[i] == value) return i;
        }
        if (paletteSize < palette.length) {
            palette[paletteSize] = value;
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
    }

    private void initDirect() {
        this.bits = directBits;
        this.palette = null;
        this.paletteSize = 0;
        this.storage = new BitStorage(directBits, size);
    }

    private int countDistinct(int[] src, int offset) {
        int[] seen = new int[Math.min(size, (1 << maxIndirect) + 1)];
        int n = 0;
        outer:
        for (int i = 0; i < size; i++) {
            int v = src[offset + i];
            for (int j = 0; j < n; j++) {
                if (seen[j] == v) continue outer;
            }
            if (n == seen.length) return seen.length + 1;
            seen[n++] = v;
        }
        return n;
    }
}
