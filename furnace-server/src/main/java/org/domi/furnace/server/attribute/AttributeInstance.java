// 바닐라 AttributeInstance: 기본값 + 수정자(id 로 유일). 값 = ((기본+더하기) + 기본×배율합) × Π(1+전체배율), 범위로 자른다.
package org.domi.furnace.server.attribute;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AttributeInstance {
    private final Attribute attribute;
    private double base;
    // 바꿀 때마다 새 불변 사본으로 갈아 끼운다 — 다른 region 스레드가 읽는 중(전리품 luck 등)이어도 ConcurrentModificationException 이 없다.
    private volatile Map<String, AttributeModifier> modifiers = Map.of();
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

    /** 같은 id 가 이미 있으면 바닐라는 예외를 던진다 — 여기서는 덮어쓴다(addOrReplacePermanentModifier). */
    public void addOrReplaceModifier(AttributeModifier m) {
        Map<String, AttributeModifier> next = new LinkedHashMap<>(modifiers);
        next.put(m.id(), m);
        modifiers = java.util.Collections.unmodifiableMap(next);
        setDirty();
    }

    public boolean removeModifier(String id) {
        if (!modifiers.containsKey(id)) return false;
        Map<String, AttributeModifier> next = new LinkedHashMap<>(modifiers);
        next.remove(id);
        modifiers = java.util.Collections.unmodifiableMap(next);
        setDirty();
        return true;
    }

    public void removeModifiers() {
        if (!modifiers.isEmpty()) { modifiers = Map.of(); setDirty(); }
    }

    private void setDirty() {
        dirty = true;
        onDirty.run();
    }

    public double value() {
        if (dirty) {
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
            cached = attribute.sanitize(e);
            dirty = false;
        }
        return cached;
    }
}
