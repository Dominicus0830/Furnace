// 바닐라 AttributeInstance: 기본값 + 수정자(id 로 유일). 값 = ((기본+더하기) + 기본×배율합) × Π(1+전체배율), 범위로 자른다.
package org.domi.furnace.server.attribute;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AttributeInstance {
    private final Attribute attribute;
    private double base;
    private final Map<String, AttributeModifier> modifiers = new LinkedHashMap<>();
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
        modifiers.put(m.id(), m);
        setDirty();
    }

    public boolean removeModifier(String id) {
        boolean r = modifiers.remove(id) != null;
        if (r) setDirty();
        return r;
    }

    public void removeModifiers() {
        if (!modifiers.isEmpty()) { modifiers.clear(); setDirty(); }
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
