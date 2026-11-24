// 바닐라 SynchedEntityData: 번호마다 (직렬화기, 기본값, 값). 바뀐 값만 dirty 로 모아 보내고, 처음 보일 때는 기본값과 다른 것만 보낸다.
// 값 비교는 바닐라처럼 equals — 아이템 스택은 바닐라 ItemStack 이 equals 를 두지 않으므로 같은 객체인지로 본다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public final class SynchedData {

    /** 값이 바뀔 때 부르는 엔티티 훅(바닐라 SynchedEntityData.set → entity.onSyncedDataUpdated). define·setQuiet 는 부르지 않는다. */
    private final java.util.function.IntConsumer onUpdate;

    public SynchedData(java.util.function.IntConsumer onUpdate) { this.onUpdate = onUpdate; }

    private Object[] values = new Object[0], defaults = new Object[0];
    private int[] serializers = new int[0];
    private boolean[] dirty = new boolean[0];
    private boolean isDirty;
    /** 칸이 바뀔 때 부르는 사본 갱신(엔티티 데이터 설계 D5, 주인 스레드). 플러그인이 이 엔티티의 칸을 보기 전에는 null — 값싼 null 검사뿐이다. */
    private java.util.function.IntConsumer published;
    private Object watcher;

    /** define(id, 기본값) — 번호는 0 부터 빈틈없이. */
    public void define(int index, int serializer, Object initial) {
        if (index != values.length) throw new IllegalStateException("entity data indices are not contiguous: " + index);
        values = java.util.Arrays.copyOf(values, index + 1);
        defaults = java.util.Arrays.copyOf(defaults, index + 1);
        serializers = java.util.Arrays.copyOf(serializers, index + 1);
        dirty = java.util.Arrays.copyOf(dirty, index + 1);
        values[index] = defaults[index] = initial;
        serializers[index] = serializer;
    }

    public Object get(int index) { return values[index]; }

    /** define 때 값(바닐라 DataItem.initialValue — getNonDefaultValues 의 기준). */
    public Object defaultValue(int index) { return defaults[index]; }

    /** 정의된 칸 수. */
    public int size() { return values.length; }

    /** 그 칸의 직렬화 번호(SetEntityData.SERIALIZER_*). */
    public int serializer(int index) { return serializers[index]; }

    public void set(int index, Object value) { set(index, value, false); }

    /** 같지 않거나(force) 면 바꾸고 훅을 부른 뒤 dirty(바닐라 순서: 값 → onSyncedDataUpdated → dirty). */
    public void set(int index, Object value, boolean force) {
        if (force || !same(serializers[index], values[index], value)) {
            values[index] = value;
            onUpdate.accept(index);
            dirty[index] = true;
            isDirty = true;
            if (published != null) published.accept(index);
        }
    }

    private static boolean same(int serializer, Object a, Object b) {
        return serializer == SetEntityData.SERIALIZER_ITEM ? a == b : Objects.equals(a, b);
    }

    /** 바닐라에서 값 객체를 제자리에서 바꾸는 경우(스택 줄이기): 값만 바꾸고 dirty 로 두지 않는다. */
    public void setQuiet(int index, Object value) {
        values[index] = value;
        if (published != null) published.accept(index);
    }

    /** 칸 보기(D5): 처음이면 make 로 만들어 두고(그 갱신기가 칸이 바뀔 때마다 불린다), 이미 있으면 그것. 주인 스레드에서만. */
    @SuppressWarnings("unchecked")
    public <T extends java.util.function.IntConsumer> T watch(java.util.function.Supplier<T> make) {
        if (watcher == null) {
            T w = make.get();
            watcher = w;
            published = w;
        }
        return (T) watcher;
    }

    public boolean isDirty() { return isDirty; }

    /** 그 칸이 packDirty 로 나갈 것인가. */
    public boolean dirty(int index) { return dirty[index]; }

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
