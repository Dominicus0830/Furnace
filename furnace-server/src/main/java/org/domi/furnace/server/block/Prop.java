// 블록 상태 속성 키(이름 하나에 블록별 배치). 값은 그 블록의 값 목록에서의 번호.
// 읽기 (state - first) / stride % size, 바꾸기 state + (새 번호 - 현재 번호) * stride.
package org.domi.furnace.server.block;


public final class Prop {

    private final String name;
    private final int[] stride;      // 0 = 그 블록에 없음
    private final int[] size;
    private final String[][] values;

    Prop(String name, int blockCount) {
        this.name = name;
        this.stride = new int[blockCount];
        this.size = new int[blockCount];
        this.values = new String[blockCount][];
    }

    void define(int block, int stride, String[] values) {
        this.stride[block] = stride;
        this.size[block] = values.length;
        this.values[block] = values;
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
        for (int i = 0; i < vs.length; i++) if (vs[i].equals(value)) return i;
        return -1;
    }

    /** 값 이름으로 바꾼 상태(없는 값이면 그대로). */
    public int with(int state, String value) {
        int i = indexOf(state, value);
        return i < 0 ? state : with(state, i);
    }

    // 불리언(값 목록 true|false — 번호 0 = true)
    public boolean is(int state) { return "true".equals(valueName(state)); }

    public int with(int state, boolean v) { return with(state, v ? "true" : "false"); }

    // 정수 속성(값 목록이 숫자 문자열 — 번호 + 최솟값)
    public int intValue(int state) {
        String v = valueName(state);
        return v == null ? 0 : Integer.parseInt(v);
    }

    public int withInt(int state, int v) { return with(state, Integer.toString(v)); }

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
