// 바닐라 AttributeInstance: 기본값 + 수정자(id 로 유일). 값은 바닐라 calculateValue 와 같은 순서·같은 부동소수 연산으로 계산하고 범위로 자른다.
package org.domi.furnace.server.attribute;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
     * (modifiers() 순서에서 끝으로 간다). 저장되는 수정자가 된다. 연산별 값 계산 순서는 바닐라 해시 맵 순회 순서다(ModifierOrder).
     * 해시 충돌(같은 칸)이 있으면 지우고 다시 넣기가 계산 순서도 바꾼다 — 바닐라와 같다.
     */
    public void addOrReplaceModifier(AttributeModifier m) {
        State s = state;
        if (s.byId.containsKey(m.id())) s = s.without(m.id());
        state = s.with(m, false);
        setDirty();
    }

    /**
     * 수정자 여러 개를 차례로 addOrReplaceModifier 한 것과 같은 결과를 상태 복사 한 번으로 만든다(플레이어 값 계산이 매번 새 인스턴스에 몇 개씩 단다).
     * 비어 있는 인스턴스에 서로 다른 id 만 들어올 때의 빠른 길이고, 그 밖에는 하나씩 단다.
     */
    public void addAllModifiers(java.util.Collection<AttributeModifier> mods) {
        if (mods.isEmpty()) return;
        if (!state.byId.isEmpty()) {
            for (AttributeModifier m : mods) addOrReplaceModifier(m);
            return;
        }
        Map<String, AttributeModifier> byId = new LinkedHashMap<>();
        for (AttributeModifier m : mods) {
            if (byId.put(m.id(), m) != null) {   // 같은 id 가 겹치면 하나씩(지우고 다시 넣기) 단다
                for (AttributeModifier x : mods) addOrReplaceModifier(x);
                return;
            }
        }
        java.util.List<String>[] ids = newIdLists();
        for (AttributeModifier m : mods) ids[State.op(m)].add(m.id());
        ModifierOrder[] o = new ModifierOrder[3];
        for (int k = 0; k < 3; k++) o[k] = ids[k].isEmpty() ? ModifierOrder.EMPTY : ModifierOrder.EMPTY.putAll(ids[k]);
        state = new State(Collections.unmodifiableMap(byId), o, Set.of());
        setDirty();
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<String>[] newIdLists() {
        return new java.util.List[] {new java.util.ArrayList<String>(), new java.util.ArrayList<String>(), new java.util.ArrayList<String>()};
    }

    /**
     * 저장하지 않는 수정자를 단다. 바닐라 addOrUpdateTransientModifier 와 같이 같은 id 가 있으면 제자리에서 바꾼다(순서 유지).
     * 이미 같은 값의 저장 안 하는 수정자면 아무것도 안 한다. (바닐라 addTransientModifier 의 중복 예외는 부르는 쪽이 검사한다.)
     */
    public void addOrReplaceTransientModifier(AttributeModifier m) {
        State s = state;
        AttributeModifier old = s.byId.get(m.id());
        if (old == null) {
            state = s.with(m, true);
        } else {
            if (m.equals(old) && s.transientIds.contains(m.id())) return;
            state = s.replace(old, m, true);
        }
        setDirty();
    }

    /** 저장하지 않는 수정자인가(AttributeMap.save 가 뺀다). */
    public boolean isTransient(String id) { return state.transientIds.contains(id); }

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

    /** 수정자 전체(넣은 순서) + 연산별 바닐라 순회 순서 + 저장 안 하는 id. 불변. */
    private record State(Map<String, AttributeModifier> byId, ModifierOrder[] order, Set<String> transientIds) {
        static final State EMPTY = new State(Map.of(),
                new ModifierOrder[] {ModifierOrder.EMPTY, ModifierOrder.EMPTY, ModifierOrder.EMPTY}, Set.of());

        /** 범위 밖 연산 값은 ADD_VALUE 로 본다(operationName·operationOf 의 기본값과 같다 — 옛 compute 는 전체 배율로 처리했다). */
        static int op(AttributeModifier m) {
            int o = m.operation();
            return o == AttributeModifier.ADD_MULTIPLIED_BASE || o == AttributeModifier.ADD_MULTIPLIED_TOTAL ? o : AttributeModifier.ADD_VALUE;
        }

        private static Set<String> withTransient(Set<String> t, String id, boolean isTransient) {
            if (isTransient == t.contains(id)) return t;
            Set<String> n = new HashSet<>(t);
            if (isTransient) n.add(id); else n.remove(id);
            return Set.copyOf(n);
        }

        State with(AttributeModifier m, boolean isTransient) {
            Map<String, AttributeModifier> next = new LinkedHashMap<>(byId);
            next.put(m.id(), m);
            ModifierOrder[] o = order.clone();
            int k = op(m);
            o[k] = o[k].put(m.id());
            return new State(Collections.unmodifiableMap(next), o, withTransient(transientIds, m.id(), isTransient));
        }

        State without(String id) {
            AttributeModifier old = byId.get(id);
            Map<String, AttributeModifier> next = new LinkedHashMap<>(byId);
            next.remove(id);
            ModifierOrder[] o = order.clone();
            int k = op(old);
            o[k] = o[k].remove(id);
            return new State(Collections.unmodifiableMap(next), o, withTransient(transientIds, id, false));
        }

        /** 같은 id 의 값만 바꾼다(제자리). 연산이 달라지면 옛 연산 표에서 빼고 새 연산 표에 넣는다. 바닐라와 다른 점(의도): 바닐라 addOrUpdateTransientModifier 는 옛 연산 맵에서 빼지 않아 수정자가 두 번 계산되는 버그가 있고, 저장 목록(permanentModifiers)도 건드리지 않는다. */
        State replace(AttributeModifier old, AttributeModifier m, boolean isTransient) {
            Map<String, AttributeModifier> next = new LinkedHashMap<>(byId);
            next.put(m.id(), m);
            ModifierOrder[] o = order.clone();
            int ko = op(old), kn = op(m);
            if (ko != kn) {
                o[ko] = o[ko].remove(m.id());
                o[kn] = o[kn].put(m.id());
            }
            return new State(Collections.unmodifiableMap(next), o, withTransient(transientIds, m.id(), isTransient));
        }
    }
}
