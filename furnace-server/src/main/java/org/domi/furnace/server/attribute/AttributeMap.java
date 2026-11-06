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
    /** 같은 인스턴스를 속성 번호로(틱마다 많이 부르는 value/get 을 해시 없이). 인스턴스는 빼지 않는다. */
    private AttributeInstance[] byId = new AttributeInstance[0];
    private final Set<AttributeInstance> dirty = new LinkedHashSet<>();
    // 바닐라 attributesToUpdate: 동기화와 별개로 LivingEntity.refreshDirtyAttributes 가 비운다(최대 체력 클램프 등)
    private final Set<AttributeInstance> toUpdate = new LinkedHashSet<>();
    /** 기본값·수정자가 바뀔 때마다 오른다(저장용 스냅샷을 다시 뜰지 판단). */
    private int version;

    public AttributeInstance register(Attribute a) {
        return instances.computeIfAbsent(a, k -> {
            AttributeInstance[] self = new AttributeInstance[1];
            self[0] = new AttributeInstance(k, () -> { dirty.add(self[0]); toUpdate.add(self[0]); version++; });
            if (k.id() >= byId.length) byId = java.util.Arrays.copyOf(byId, Math.max(k.id() + 1, byId.length * 2));
            byId[k.id()] = self[0];
            return self[0];
        });
    }

    public AttributeMap add(Attribute a, double base) {
        register(a).setBaseValue(base);
        dirty.clear();
        toUpdate.clear();   // 처음 설정은 바뀜으로 치지 않는다(처음 보일 때 전부 보낸다)
        return this;
    }

    public int version() { return version; }

    public boolean has(Attribute a) { return get(a) != null; }

    public AttributeInstance get(Attribute a) {
        int id = a.id();
        return id < byId.length ? byId[id] : null;
    }

    public double value(Attribute a) {
        AttributeInstance i = get(a);
        return i == null ? a.defaultValue() : i.value();
    }

    public double baseValue(Attribute a) {
        AttributeInstance i = get(a);
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
        if (dirty.isEmpty()) return List.of();   // 틱마다 엔티티마다 불린다 — 바뀐 것이 없으면 목록·반복자를 만들지 않는다
        List<AttributeInstance> out = new ArrayList<>();
        for (AttributeInstance i : dirty) if (i.attribute().syncable()) out.add(i);
        dirty.clear();
        return out;
    }

    /** 갱신 대기 속성을 꺼내고 비운다(바닐라 getAttributesToUpdate + clear). */
    public List<Attribute> takeToUpdate() {
        if (toUpdate.isEmpty()) return List.of();
        List<Attribute> out = new ArrayList<>();
        for (AttributeInstance i : toUpdate) out.add(i.attribute());
        toUpdate.clear();
        return out;
    }

    public NbtList save() { return save((a, id) -> false); }

    /** skip 이 참인 수정자는 빼고 저장한다(바닐라 AttributeInstance.save 는 permanent 수정자만 — 장비·효과 같은 transient 는 뺀다). */
    public NbtList save(java.util.function.BiPredicate<Attribute, String> skip) {
        NbtList list = new NbtList((byte) 10);
        for (AttributeInstance i : instances.values()) {
            NbtCompound t = new NbtCompound();
            t.putString("id", i.attribute().name());
            t.put("base", i.baseValue());
            NbtList mods = new NbtList((byte) 10);
            for (AttributeModifier m : i.modifiers().values()) {
                if (skip.test(i.attribute(), m.id())) continue;
                NbtCompound mt = new NbtCompound();
                mt.putString("id", m.id());
                mt.put("amount", m.amount());
                mt.putString("operation", m.operationName());
                mods.add(mt);
            }
            if (mods.size() > 0) t.putList("modifiers", mods);
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
