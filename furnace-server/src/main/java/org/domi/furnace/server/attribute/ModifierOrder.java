// 바닐라 AttributeInstance 는 연산마다 Object2ObjectOpenHashMap<ResourceLocation, AttributeModifier> 에 수정자를 담고, 값 계산은 그 맵의 값 순서대로 더하고 곱한다.
// 부동소수는 합산 순서에 따라 마지막 비트가 달라지므로, fastutil 열린 주소 해시의 순회 순서(칸 번호 큰 쪽부터)를 그대로 재현해 같은 순서로 계산한다.
// 불변 값이다: 넣기·빼기는 새 표를 돌려준다(속성 읽기는 다른 region 스레드에서도 일어난다).
package org.domi.furnace.server.attribute;

import java.util.function.Consumer;

final class ModifierOrder {
    private static final double LOAD_FACTOR = 0.75;
    /** fastutil Object2ObjectOpenHashMap() 기본: arraySize(16, 0.75) = 32 칸, 이 아래로는 줄지 않는다. */
    private static final int MIN_N = 32;
    static final ModifierOrder EMPTY = new ModifierOrder(new String[MIN_N], 0);

    private final String[] key;   // 길이 n(2 의 거듭제곱). 빈 칸은 null.
    private final int size;

    private ModifierOrder(String[] key, int size) {
        this.key = key;
        this.size = size;
    }

    int size() { return size; }

    /** ResourceLocation.hashCode: 31 × namespace.hashCode() + path.hashCode(). 이름공간이 없으면 minecraft. */
    static int idHash(String id) {
        int c = id.indexOf(':');
        String ns = c < 0 ? "minecraft" : id.substring(0, c);
        String path = c < 0 ? id : id.substring(c + 1);
        return 31 * ns.hashCode() + path.hashCode();
    }

    /** fastutil HashCommon.mix(int). */
    private static int mix(int x) {
        int h = x * 0x9E3779B9;
        return h ^ (h >>> 16);
    }

    private static int slotOf(String id, int mask) { return mix(idHash(id)) & mask; }

    private static int maxFill(int n) { return Math.min((int) Math.ceil(n * LOAD_FACTOR), n - 1); }

    private static int arraySize(long expected) {
        long s = Math.max(2, Long.highestOneBit(Math.max(1, (long) Math.ceil(expected / LOAD_FACTOR)) - 1) << 1);
        return (int) s;
    }

    private int find(String id) {
        int mask = key.length - 1;
        int pos = slotOf(id, mask);
        String curr;
        while ((curr = key[pos]) != null) {
            if (curr.equals(id)) return pos;
            pos = (pos + 1) & mask;
        }
        return -(pos + 1);
    }

    boolean contains(String id) { return find(id) >= 0; }

    /** 이미 있으면 같은 칸 그대로(this), 없으면 fastutil 과 같은 칸에 넣고 가득 차면 키운다. */
    ModifierOrder put(String id) {
        int pos = find(id);
        if (pos >= 0) return this;
        String[] k = key.clone();
        pos = -pos - 1;
        k[pos] = id;
        int newSize = size + 1;
        ModifierOrder r = new ModifierOrder(k, newSize);
        // fastutil insert: if (size++ >= maxFill) rehash(arraySize(size + 1, f)) — 비교는 넣기 전 size.
        if (size >= maxFill(key.length)) return r.rehash(arraySize(newSize + 1L));
        return r;
    }

    /** 없으면 this. 있으면 fastutil shiftKeys 로 뒤 칸을 당기고, 많이 비면 절반으로 줄인다. */
    ModifierOrder remove(String id) {
        int pos = find(id);
        if (pos < 0) return this;
        String[] k = key.clone();
        int mask = k.length - 1;
        int last, slot;
        String curr;
        for (;;) {
            pos = ((last = pos) + 1) & mask;
            for (;;) {
                if ((curr = k[pos]) == null) {
                    k[last] = null;
                    break;
                }
                slot = slotOf(curr, mask);
                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
                pos = (pos + 1) & mask;
            }
            if (curr == null) break;
            k[last] = curr;
        }
        int newSize = size - 1;
        ModifierOrder r = new ModifierOrder(k, newSize);
        int n = k.length;
        if (n > MIN_N && newSize < maxFill(n) / 4 && n > 16) return r.rehash(n / 2);
        return r;
    }

    /** fastutil rehash(newN): 옛 표를 큰 칸 번호부터 훑어 새 표에 선형 탐사로 넣는다. */
    private ModifierOrder rehash(int newN) {
        int mask = newN - 1;
        String[] nk = new String[newN];
        int i = key.length;
        for (int j = size; j-- != 0;) {
            while (key[--i] == null) { /* 빈 칸 건너뛰기 */ }
            int pos = slotOf(key[i], mask);
            while (nk[pos] != null) pos = (pos + 1) & mask;
            nk[pos] = key[i];
        }
        return new ModifierOrder(nk, size);
    }

    /** 바닐라 values() 순회 순서: 칸 번호가 큰 쪽부터. */
    void forEachId(Consumer<String> c) {
        for (int i = key.length; i-- != 0;) if (key[i] != null) c.accept(key[i]);
    }
}
