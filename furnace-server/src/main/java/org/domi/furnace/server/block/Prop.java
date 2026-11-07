// 블록 상태 속성 키(이름 하나에 블록별 배치). 값은 그 블록의 값 목록에서의 번호.
// 읽기 (state - first) / stride % size, 바꾸기 state + (새 번호 - 현재 번호) * stride.
package org.domi.furnace.server.block;


public final class Prop {

    private final String name;
    private final int[] stride;      // 0 = 그 블록에 없음
    private final int[] size;
    private final String[][] values;
    // 파생 표(X1 성능): 값 문자열을 매번 비교·해석하지 않도록 정의 때 한 번 계산한다. 결과는 문자열 경로와 같다.
    private final int[] trueIdx;      // 값 "true" 의 번호(없으면 -1)
    private final int[] falseIdx;     // 값 "false" 의 번호(없으면 -1)
    private final int[][] ints;       // 모든 값이 Integer.toString 과 같은 정수 문자열일 때 번호 → 정수(아니면 null)
    private final int[] intBase;      // ints 의 최솟값
    private final int[][] intIndex;   // (정수 - intBase) → 번호(없으면 -1)

    Prop(String name, int blockCount) {
        this.name = name;
        this.stride = new int[blockCount];
        this.size = new int[blockCount];
        this.values = new String[blockCount][];
        this.trueIdx = new int[blockCount];
        this.falseIdx = new int[blockCount];
        this.ints = new int[blockCount][];
        this.intBase = new int[blockCount];
        this.intIndex = new int[blockCount][];
        java.util.Arrays.fill(trueIdx, -1);
        java.util.Arrays.fill(falseIdx, -1);
    }

    void define(int block, int stride, String[] values) {
        this.stride[block] = stride;
        this.size[block] = values.length;
        this.values[block] = values;
        trueIdx[block] = -1;
        falseIdx[block] = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals("true")) trueIdx[block] = i;
            else if (values[i].equals("false")) falseIdx[block] = i;
        }
        int[] parsed = new int[values.length];
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        boolean allInts = values.length > 0;
        for (int i = 0; i < values.length && allInts; i++) {
            try {
                parsed[i] = Integer.parseInt(values[i]);
            } catch (NumberFormatException e) {
                allInts = false;
                break;
            }
            if (!Integer.toString(parsed[i]).equals(values[i])) allInts = false;
            min = Math.min(min, parsed[i]);
            max = Math.max(max, parsed[i]);
        }
        if (allInts && (long) max - min < 4096) {
            int[] idx = new int[max - min + 1];
            java.util.Arrays.fill(idx, -1);
            for (int i = 0; i < parsed.length; i++) if (idx[parsed[i] - min] < 0) idx[parsed[i] - min] = i;   // 같은 문자열이 둘이면 앞쪽(indexOf 와 같다)
            ints[block] = parsed;
            intBase[block] = min;
            intIndex[block] = idx;
        } else {
            ints[block] = null;
            intIndex[block] = null;
        }
    }

    public String name() { return name; }

    private static Blocks b() { return Blocks.get(); }

    /** 이 상태의 블록에 이 속성이 있는가. */
    public boolean has(int state) { return stride[b().blockOfState[state]] != 0; }

    /** 값 번호(속성이 없으면 -1). */
    public int get(int state) {
        Blocks bs = b();
        int blk = bs.blockOfState[state];
        int st = stride[blk];
        if (st == 0) return -1;
        return (state - bs.first[blk]) / st % size[blk];
    }

    /** 값 번호를 바꾼 상태(속성이 없으면 그대로). */
    public int with(int state, int value) {
        Blocks bs = b();
        int blk = bs.blockOfState[state];
        int st = stride[blk];
        if (st == 0) return state;
        int cur = (state - bs.first[blk]) / st % size[blk];
        return state + (value - cur) * st;
    }

    /** 값 이름. */
    public String valueName(int state) {
        int v = get(state);
        return v < 0 ? null : values[b().blockOfState[state]][v];
    }

    /** 값 이름의 번호(그 블록에 없는 값이면 -1). */
    public int indexOf(int state, String value) {
        String[] vs = values[b().blockOfState[state]];
        if (vs == null) return -1;
        for (int i = 0; i < vs.length; i++) if (vs[i] == value) return i;   // 상수 문자열은 같은 객체인 경우가 많다
        for (int i = 0; i < vs.length; i++) if (vs[i].equals(value)) return i;
        return -1;
    }

    /** 값 이름으로 바꾼 상태(없는 값이면 그대로). */
    public int with(int state, String value) {
        int i = indexOf(state, value);
        return i < 0 ? state : with(state, i);
    }

    // 불리언(값 목록 true|false — 번호 0 = true)
    public boolean is(int state) {
        Blocks bs = b();
        int blk = bs.blockOfState[state];
        int st = stride[blk];
        if (st == 0) return false;
        return (state - bs.first[blk]) / st % size[blk] == trueIdx[blk];
    }

    public int with(int state, boolean v) {
        int blk = b().blockOfState[state];
        int i = v ? trueIdx[blk] : falseIdx[blk];
        return i < 0 ? state : with(state, i);
    }

    // 정수 속성(값 목록이 숫자 문자열 — 번호 + 최솟값)
    public int intValue(int state) {
        Blocks bs = b();
        int blk = bs.blockOfState[state];
        int st = stride[blk];
        if (st == 0) return 0;
        int[] iv = ints[blk];
        int idx = (state - bs.first[blk]) / st % size[blk];
        if (iv != null) return iv[idx];
        return Integer.parseInt(values[blk][idx]);   // 정수가 아닌 값 목록: 예전과 같이 NumberFormatException
    }

    public int withInt(int state, int v) {
        int blk = b().blockOfState[state];
        int[] idx = intIndex[blk];
        if (idx == null) return with(state, Integer.toString(v));
        long k = (long) v - intBase[blk];
        if (k < 0 || k >= idx.length || idx[(int) k] < 0) return state;
        return with(state, idx[(int) k]);
    }

    /** 그 블록의 값 개수. */
    public int size(int state) { return size[b().blockOfState[state]]; }

    /** 그 블록의 값 목록(읽기 전용으로 쓸 것). */
    public String[] values(int state) { return values[b().blockOfState[state]]; }

    /** 다음 값(바닐라 BlockState.cycle). */
    public int cycle(int state) {
        int v = get(state);
        if (v < 0) return state;
        return with(state, (v + 1) % size(state));
    }

    @Override
    public String toString() { return name; }
}
