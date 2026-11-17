package net.minecraft.world.level.chunk;

/**
 * T-209: 칸별 레지스트리 번호 → 첫 등장 순 색인(호환 모듈 내부, 스레드마다 하나). 번호로 바로 찾는 표에 세대 번호를 붙여 매번 비우지 않는다.
 * 섹션 실체화({@code PalettedContainer.furnaceDataFromIds})가 주인이 아닌 스레드의 첫 {@code data} 읽기에서 부른다 — 예전 HashMap&lt;Integer,Integer&gt;
 * 판은 칸마다 박싱·해시를 타서 섹션 하나에 수십 µs 가 들었다. 재진입하지 않는다(안에서 다른 코드를 부르지 않는다).
 */
final class FirstSeenScratch {

    private static final ThreadLocal<FirstSeenScratch> LOCAL = ThreadLocal.withInitial(FirstSeenScratch::new);

    /** 표 칸 = (세대 &lt;&lt; 12) | 첫 등장 번호. 첫 등장 번호는 4096 미만(12 비트). */
    private static final int IDX_BITS = 12;
    private static final int IDX_MASK = (1 << IDX_BITS) - 1;
    private static final int MAX_GEN = (1 << (31 - IDX_BITS)) - 1;
    /** 번호 상한(블록 상태 약 2.6만 — 넉넉히). 넘거나 음수면 레지스트리에 없는 번호다. */
    private static final int MAX_ID = 1 << 20;

    /** 번호 → (세대, 첫 등장 번호). 번호로 바로 찾는다(해시·탐사·박싱 없음) — 가장 큰 번호에 맞춰 늘린다. */
    private int[] table = new int[1024];
    private int generation;
    private final int[] values = new int[4096];

    static FirstSeenScratch get() { return LOCAL.get(); }

    /** 첫 등장 순 i 번째 번호(i &lt; 마지막 {@link #index} 의 반환값). 다음 index 때까지 유효. */
    int[] values() { return values; }

    /** ids[0..size) 를 훑어 cells[i] = 그 칸 번호의 첫 등장 순 색인으로 채우고 서로 다른 번호 수를 돌려준다. size ≤ 4096. */
    int index(int[] ids, int size, int[] cells) {
        if (size > values.length) throw new IllegalArgumentException("size " + size);
        int g = ++generation;
        if (g > MAX_GEN) {   // 세대 번호가 다 찼다 — 표를 비우고 1 부터
            java.util.Arrays.fill(table, 0);
            g = generation = 1;
        }
        int tag = g << IDX_BITS;
        int[] t = table, out = values;
        int n = 0;
        for (int i = 0; i < size; i++) {
            int id = ids[i];
            if (id < 0 || id >= t.length) {
                if (id < 0 || id >= MAX_ID) throw new IllegalStateException("id missing from the registry: " + id);
                t = table = java.util.Arrays.copyOf(t, Integer.highestOneBit(id) << 1);   // 새 칸은 0 = 어느 세대에도 안 쓰임
            }
            int e = t[id];
            int idx;
            if ((e & ~IDX_MASK) == tag) {
                idx = e & IDX_MASK;
            } else {
                idx = n;
                t[id] = tag | idx;
                out[n++] = id;
            }
            cells[i] = idx;
        }
        return n;
    }
}
