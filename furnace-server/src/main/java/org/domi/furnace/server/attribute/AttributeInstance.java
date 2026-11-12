// 바닐라 AttributeInstance: 기본값 + 수정자(id 로 유일). 값은 바닐라 calculateValue 와 같은 순서·같은 부동소수 연산으로 계산하고 범위로 자른다.
package org.domi.furnace.server.attribute;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AttributeInstance {
    private final Attribute attribute;
    private double base;
    // 바꿀 때마다 새 불변 사본으로 갈아 끼운다 — 다른 region 스레드가 읽는 중(전리품 luck 등)이어도 ConcurrentModificationException 이 없다.
    private volatile State state = State.EMPTY;
    private double cached;
    private boolean dirty = true;
    private final Runnable onDirty;

    public AttributeInstance(Attribute attribute, Runnable onDirty) {
        this.attribute = attribute;
        this.base = attribute.defaultValue();
        this.onDirty = onDirty;
    }

    public Attribute attribute() { return attribute; }

    public double baseValue() { return base; }

    public void setBaseValue(double v) {
        if (v == base) return;
        base = v;
        setDirty();
    }

    /** 바닐라 modifierById(Object2ObjectArrayMap): 넣은 순서. */
    public Map<String, AttributeModifier> modifiers() { return state.byId; }

    public boolean hasModifier(String id) { return state.byId.containsKey(id); }

    public AttributeModifier getModifier(String id) { return state.byId.get(id); }

    /**
     * 같은 id 가 이미 있으면 바닐라는 예외를 던진다 — 여기서는 덮어쓴다. 바닐라 addOrReplacePermanentModifier 와 같이 옛것을 지우고 다시 넣는다
     * (modifiers() 순서에서 끝으로 간다). 연산별 값 계산 순서는 바닐라 해시 맵 순회 순서다(ModifierOrder).
     */
    public void addOrReplaceModifier(AttributeModifier m) {
        State s = state;
        if (s.byId.containsKey(m.id())) s = s.without(m.id());
        state = s.with(m);
        setDirty();
    }

    /** 바닐라 addOrUpdateTransientModifier: 같은 id 가 있으면 제자리에서 바꾼다(순서 유지). 같은 값이면 아무것도 안 한다. */
    public void addOrUpdateTransientModifier(AttributeModifier m) {
        State s = state;
        AttributeModifier old = s.byId.get(m.id());
        if (m.equals(old)) return;
        state = old == null ? s.with(m) : s.replace(old, m);
        setDirty();
    }

    public boolean removeModifier(String id) {
        State s = state;
        if (!s.byId.containsKey(id)) return false;
        state = s.without(id);
        setDirty();
        return true;
    }

    public void removeModifiers() {
        if (!state.byId.isEmpty()) { state = State.EMPTY; setDirty(); }
    }

    private void setDirty() {
        dirty = true;
        onDirty.run();
    }

    public double value() {
        if (dirty) {
            cached = compute();
            dirty = false;
        }
        return cached;
    }

    /**
     * 캐시를 건드리지 않고 지금 값을 계산한다(소유 스레드 밖에서 읽는 사본용 — base 는 최근 값일 수 있다).
     * 바닐라 calculateValue: d = base + Σadd(차례로); e = d; e += d × baseMul(차례로); e ×= (1 + totalMul)(차례로).
     */
    public double compute() {
        State s = state;
        double[] acc = {base};
        s.order[AttributeModifier.ADD_VALUE].forEachId(id -> acc[0] += s.byId.get(id).amount());
        double d = acc[0];
        s.order[AttributeModifier.ADD_MULTIPLIED_BASE].forEachId(id -> acc[0] += d * s.byId.get(id).amount());
        s.order[AttributeModifier.ADD_MULTIPLIED_TOTAL].forEachId(id -> acc[0] *= 1.0 + s.byId.get(id).amount());
        return attribute.sanitize(acc[0]);
    }

    /** 수정자 전체(넣은 순서) + 연산별 바닐라 순회 순서. 불변. */
    private record State(Map<String, AttributeModifier> byId, ModifierOrder[] order) {
        static final State EMPTY = new State(Map.of(),
                new ModifierOrder[] {ModifierOrder.EMPTY, ModifierOrder.EMPTY, ModifierOrder.EMPTY});

        private static int op(AttributeModifier m) {
            int o = m.operation();
            return o == AttributeModifier.ADD_MULTIPLIED_BASE || o == AttributeModifier.ADD_MULTIPLIED_TOTAL ? o : AttributeModifier.ADD_VALUE;
        }

        State with(AttributeModifier m) {
            Map<String, AttributeModifier> next = new LinkedHashMap<>(byId);
            next.put(m.id(), m);
            ModifierOrder[] o = order.clone();
            int k = op(m);
            o[k] = o[k].put(m.id());
            return new State(Collections.unmodifiableMap(next), o);
        }

        State without(String id) {
            AttributeModifier old = byId.get(id);
            Map<String, AttributeModifier> next = new LinkedHashMap<>(byId);
            next.remove(id);
            ModifierOrder[] o = order.clone();
            int k = op(old);
            o[k] = o[k].remove(id);
            return new State(Collections.unmodifiableMap(next), o);
        }

        /** 같은 id 의 값만 바꾼다(제자리). 연산이 달라지면 옛 연산 표에서 빼고 새 연산 표에 넣는다(바닐라 맵 동작과 같다). */
        State replace(AttributeModifier old, AttributeModifier m) {
            Map<String, AttributeModifier> next = new LinkedHashMap<>(byId);
            next.put(m.id(), m);
            ModifierOrder[] o = order.clone();
            int ko = op(old), kn = op(m);
            if (ko != kn) {
                o[ko] = o[ko].remove(m.id());
                o[kn] = o[kn].put(m.id());
            }
            return new State(Collections.unmodifiableMap(next), o);
        }
    }
}
