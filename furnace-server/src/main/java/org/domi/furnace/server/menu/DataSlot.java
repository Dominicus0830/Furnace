// 바닐라 DataSlot — 메뉴가 클라이언트에 알리는 정수 하나(화로 진행도·마법 부여 비용 등).
package org.domi.furnace.server.menu;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public abstract class DataSlot {

    private int prevValue;

    public abstract int get();

    public abstract void set(int value);

    /** 바닐라 checkAndClearUpdateFlag. */
    public boolean checkAndClearUpdateFlag() {
        int v = get();
        boolean changed = v != prevValue;
        prevValue = v;
        return changed;
    }

    public static DataSlot of(IntSupplier getter, IntConsumer setter) {
        return new DataSlot() {
            @Override public int get() { return getter.getAsInt(); }
            @Override public void set(int value) { setter.accept(value); }
        };
    }

    /** 바닐라 DataSlot.standalone. */
    public static DataSlot standalone() {
        return new DataSlot() {
            private int value;
            @Override public int get() { return value; }
            @Override public void set(int v) { value = v; }
        };
    }
}
