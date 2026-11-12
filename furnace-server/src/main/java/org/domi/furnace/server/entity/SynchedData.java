// 바닐라 SynchedEntityData: 번호마다 (직렬화기, 기본값, 값). 바뀐 값만 dirty 로 모아 보내고, 처음 보일 때는 기본값과 다른 것만 보낸다.
// 값 비교는 바닐라처럼 equals — 아이템 스택은 바닐라 ItemStack 이 equals 를 두지 않으므로 같은 객체인지로 본다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public final class SynchedData {

    private Object[] values = new Object[0], defaults = new Object[0];
    private int[] serializers = new int[0];
    private boolean[] dirty = new boolean[0];
    private boolean isDirty;

    /** define(id, 기본값) — 번호는 0 부터 빈틈없이. */
    public void define(int index, int serializer, Object initial) {
        if (index != values.length) throw new IllegalStateException("엔티티 데이터 번호가 이어지지 않음: " + index);
        values = java.util.Arrays.copyOf(values, index + 1);
        defaults = java.util.Arrays.copyOf(defaults, index + 1);
        serializers = java.util.Arrays.copyOf(serializers, index + 1);
        dirty = java.util.Arrays.copyOf(dirty, index + 1);
        values[index] = defaults[index] = initial;
        serializers[index] = serializer;
    }

    public Object get(int index) { return values[index]; }

    /** 정의된 칸 수. */
    public int size() { return values.length; }

    /** 그 칸의 직렬화 번호(SetEntityData.SERIALIZER_*). */
    public int serializer(int index) { return serializers[index]; }

    public void set(int index, Object value) { set(index, value, false); }

    /** 같지 않거나(force) 면 바꾸고 dirty. */
    public void set(int index, Object value, boolean force) {
        if (force || !same(serializers[index], values[index], value)) {
            values[index] = value;
            dirty[index] = true;
            isDirty = true;
        }
    }

    private static boolean same(int serializer, Object a, Object b) {
        return serializer == SetEntityData.SERIALIZER_ITEM ? a == b : Objects.equals(a, b);
    }

    /** 바닐라에서 값 객체를 제자리에서 바꾸는 경우(스택 줄이기): 값만 바꾸고 dirty 로 두지 않는다. */
    public void setQuiet(int index, Object value) { values[index] = value; }

    public boolean isDirty() { return isDirty; }

    /** packDirty: dirty 값들(번호 순)을 꺼내고 지운다. 없으면 null. */
    public List<SetEntityData.Item> packDirty() {
        if (!isDirty) return null;
        isDirty = false;
        List<SetEntityData.Item> out = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            if (dirty[i]) {
                dirty[i] = false;
                out.add(new SetEntityData.Item(i, serializers[i], values[i]));
            }
        }
        return out;
    }

    /** getNonDefaultValues: 기본값과 다른 값들(번호 순). 없으면 null. */
    public List<SetEntityData.Item> nonDefaultValues() {
        List<SetEntityData.Item> out = null;
        for (int i = 0; i < values.length; i++) {
            if (!same(serializers[i], defaults[i], values[i])) {
                if (out == null) out = new ArrayList<>();
                out.add(new SetEntityData.Item(i, serializers[i], values[i]));
            }
        }
        return out;
    }
}
