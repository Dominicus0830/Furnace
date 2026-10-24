// 바닐라 AttributeMap: 엔티티가 가진 속성들(종류별 공급자가 정한 것만). 바뀐 것은 모아 두었다가 update_attributes 로 보낸다.
package org.domi.furnace.server.attribute;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class AttributeMap {
    private final Map<Attribute, AttributeInstance> instances = new LinkedHashMap<>();
    private final Set<AttributeInstance> dirty = new LinkedHashSet<>();

    public AttributeInstance register(Attribute a) {
        return instances.computeIfAbsent(a, k -> {
            AttributeInstance[] self = new AttributeInstance[1];
            self[0] = new AttributeInstance(k, () -> dirty.add(self[0]));
            return self[0];
        });
    }

    public AttributeMap add(Attribute a, double base) {
        register(a).setBaseValue(base);
        dirty.clear();   // 처음 설정은 바뀜으로 치지 않는다(처음 보일 때 전부 보낸다)
        return this;
    }

    public boolean has(Attribute a) { return instances.containsKey(a); }

    public AttributeInstance get(Attribute a) { return instances.get(a); }

    public double value(Attribute a) {
        AttributeInstance i = instances.get(a);
        return i == null ? a.defaultValue() : i.value();
    }

    public double baseValue(Attribute a) {
        AttributeInstance i = instances.get(a);
        return i == null ? a.defaultValue() : i.baseValue();
    }

    /** 처음 보일 때 보낼 속성들(syncable 전부). */
    public List<AttributeInstance> syncable() {
        List<AttributeInstance> out = new ArrayList<>();
        for (AttributeInstance i : instances.values()) if (i.attribute().syncable()) out.add(i);
        return out;
    }

    public List<AttributeInstance> all() { return new ArrayList<>(instances.values()); }

    /** 바뀐 속성들을 꺼내고 비운다(getAttributesToSync — 동기화 대상 전부 sendable 로 본다). */
    public List<AttributeInstance> takeDirty() {
        List<AttributeInstance> out = new ArrayList<>();
        for (AttributeInstance i : dirty) if (i.attribute().syncable()) out.add(i);
        dirty.clear();
        return out;
    }

    public NbtList save() {
        NbtList list = new NbtList((byte) 10);
        for (AttributeInstance i : instances.values()) {
            NbtCompound t = new NbtCompound();
            t.putString("id", i.attribute().name());
            t.put("base", i.baseValue());
            if (!i.modifiers().isEmpty()) {
                NbtList mods = new NbtList((byte) 10);
                for (AttributeModifier m : i.modifiers().values()) {
                    NbtCompound mt = new NbtCompound();
                    mt.putString("id", m.id());
                    mt.put("amount", m.amount());
                    mt.putString("operation", m.operationName());
                    mods.add(mt);
                }
                t.putList("modifiers", mods);
            }
            list.add(t);
        }
        return list;
    }

    public void load(NbtList list) {
        if (list == null) return;
        for (int k = 0; k < list.size(); k++) {
            if (!(list.get(k) instanceof NbtCompound t) || !(t.tags().get("id") instanceof String id)) continue;
            Attribute a = Attribute.byName(id);
            AttributeInstance i = a == null ? null : instances.get(a);
            if (i == null) continue;
            if (t.tags().get("base") instanceof Number n) i.setBaseValue(n.doubleValue());
            i.removeModifiers();
            if (t.tags().get("modifiers") instanceof NbtList mods) {
                for (int j = 0; j < mods.size(); j++) {
                    if (mods.get(j) instanceof NbtCompound mt && mt.tags().get("id") instanceof String mid && mt.tags().get("amount") instanceof Number amt) {
                        String op = mt.tags().get("operation") instanceof String o ? o : "add_value";
                        i.addOrReplaceModifier(new AttributeModifier(mid, amt.doubleValue(), AttributeModifier.operationOf(op)));
                    }
                }
            }
        }
        dirty.clear();
    }
}
