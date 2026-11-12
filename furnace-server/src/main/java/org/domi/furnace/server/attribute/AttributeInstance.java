// 바닐라 AttributeInstance: 기본값 + 수정자(id 로 유일). 값 = ((기본+더하기) + 기본×배율합) × Π(1+전체배율), 범위로 자른다.
package org.domi.furnace.server.attribute;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AttributeInstance {
    private final Attribute attribute;
    private double base;
    // 바꿀 때마다 새 불변 사본으로 갈아 끼운다 — 다른 region 스레드가 읽는 중(전리품 luck 등)이어도 ConcurrentModificationException 이 없다.
    private volatile Map<String, AttributeModifier> modifiers = Map.of();
    // 바닐라 AttributeInstance.permanentModifiers 의 보수: 저장하지 않는 수정자 id(플러그인 API 의 addTransientModifier). modifiers 와 같은 방식으로 통째로 갈아 끼운다.
    private volatile java.util.Set<String> transientIds = java.util.Set.of();
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

    public Map<String, AttributeModifier> modifiers() { return modifiers; }

    public boolean hasModifier(String id) { return modifiers.containsKey(id); }

    public AttributeModifier getModifier(String id) { return modifiers.get(id); }

    /** 같은 id 가 이미 있으면 바닐라는 예외를 던진다 — 여기서는 덮어쓴다(addOrReplacePermanentModifier). 저장되는 수정자가 된다. */
    public void addOrReplaceModifier(AttributeModifier m) {
        put(m, false);
    }

    /** 저장하지 않는 수정자를 단다(바닐라 addTransientModifier). 같은 id 가 있으면 덮어쓴다 — 중복 검사는 부르는 쪽이 한다. */
    public void addOrReplaceTransientModifier(AttributeModifier m) {
        put(m, true);
    }

    private void put(AttributeModifier m, boolean isTransient) {
        Map<String, AttributeModifier> next = new LinkedHashMap<>(modifiers);
        next.put(m.id(), m);
        modifiers = java.util.Collections.unmodifiableMap(next);
        if (isTransient != transientIds.contains(m.id())) {
            java.util.Set<String> t = new java.util.HashSet<>(transientIds);
            if (isTransient) t.add(m.id()); else t.remove(m.id());
            transientIds = java.util.Set.copyOf(t);
        }
        setDirty();
    }

    /** 저장하지 않는 수정자인가(AttributeMap.save 가 뺀다). */
    public boolean isTransient(String id) { return transientIds.contains(id); }

    public boolean removeModifier(String id) {
        if (!modifiers.containsKey(id)) return false;
        Map<String, AttributeModifier> next = new LinkedHashMap<>(modifiers);
        next.remove(id);
        modifiers = java.util.Collections.unmodifiableMap(next);
        if (transientIds.contains(id)) {
            java.util.Set<String> t = new java.util.HashSet<>(transientIds);
            t.remove(id);
            transientIds = java.util.Set.copyOf(t);
        }
        setDirty();
        return true;
    }

    public void removeModifiers() {
        if (!modifiers.isEmpty()) { modifiers = Map.of(); transientIds = java.util.Set.of(); setDirty(); }
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

    /** 캐시를 건드리지 않고 지금 값을 계산한다(소유 스레드 밖에서 읽는 사본용 — base 는 최근 값일 수 있다). */
    public double compute() {
        double d = base, add = 0, mulBase = 0, mulTotal = 1;
        for (AttributeModifier m : modifiers.values()) {
            switch (m.operation()) {
                case AttributeModifier.ADD_VALUE -> add += m.amount();
                case AttributeModifier.ADD_MULTIPLIED_BASE -> mulBase += m.amount();
                default -> mulTotal *= 1.0 + m.amount();
            }
        }
        d = base + add;
        double e = d + d * mulBase;
        e *= mulTotal;
        return attribute.sanitize(e);
    }
}
