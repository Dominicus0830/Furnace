// 한 플레이어(연결)가 다른 엔티티의 장비 칸을 "다른 아이템으로" 보는 덮어쓰기. 서버 장비는 그대로이고 그 사람에게 나가는 set_equipment 와
// 스폰 직후 장비만 바꾼다(ViewRewriter). 엔티티는 번호로 묶는다 — 번호는 서버 실행 동안 안 바뀐다.
package org.domi.furnace.server.entity.view;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemStack;

public final class EquipmentLayer {

    public record Entry(Object owner, ItemStack stack) {}

    private final ConcurrentHashMap<Integer, Map<EquipmentSlot, Entry>> byEntity = new ConcurrentHashMap<>();
    /** 엔티티 번호 → 걷을 때 진짜 장비를 읽을 단서(만든 쪽이 정한 불투명 값). */
    private final ConcurrentHashMap<Integer, Object> targets = new ConcurrentHashMap<>();
    private volatile boolean active;

    public boolean active() { return active; }

    /** 그 엔티티의 덮어쓰기(불변), 없으면 null. */
    public Map<EquipmentSlot, Entry> of(int entityId) { return byEntity.get(entityId); }

    public Object target(int entityId) { return targets.get(entityId); }

    public java.util.List<Integer> ids() { return java.util.List.copyOf(byEntity.keySet()); }

    public synchronized void put(Object owner, int entityId, Object target, EquipmentSlot slot, ItemStack stack) {
        targets.put(entityId, target);
        Map<EquipmentSlot, Entry> m = new EnumMap<>(EquipmentSlot.class);
        Map<EquipmentSlot, Entry> old = byEntity.get(entityId);
        if (old != null) m.putAll(old);
        m.put(slot, new Entry(owner, stack));
        byEntity.put(entityId, java.util.Collections.unmodifiableMap(m));
        active = true;
    }

    /** owner 가 건 그 칸을 걷는다. 걷었으면 true. */
    public synchronized boolean remove(Object owner, int entityId, EquipmentSlot slot) {
        Map<EquipmentSlot, Entry> old = byEntity.get(entityId);
        if (old == null) return false;
        Entry e = old.get(slot);
        if (e == null || !e.owner.equals(owner)) return false;
        Map<EquipmentSlot, Entry> m = new EnumMap<>(EquipmentSlot.class);
        m.putAll(old);
        m.remove(slot);
        store(entityId, m);
        return true;
    }

    /** owner 가 건 그 엔티티의 칸을 모두 걷는다(걷은 칸들). */
    public synchronized Set<EquipmentSlot> removeEntity(Object owner, int entityId) {
        Map<EquipmentSlot, Entry> old = byEntity.get(entityId);
        Set<EquipmentSlot> removed = new HashSet<>();
        if (old == null) return removed;
        Map<EquipmentSlot, Entry> m = new EnumMap<>(EquipmentSlot.class);
        for (var e : old.entrySet()) {
            if (e.getValue().owner.equals(owner)) removed.add(e.getKey()); else m.put(e.getKey(), e.getValue());
        }
        store(entityId, m);
        return removed;
    }

    /** owner 가 건 모든 덮어쓰기를 걷는다(엔티티 번호 → 걷은 칸들). */
    public synchronized Map<Integer, Set<EquipmentSlot>> removeAll(Object owner) {
        Map<Integer, Set<EquipmentSlot>> out = new HashMap<>();
        for (Integer id : java.util.List.copyOf(byEntity.keySet())) {
            Set<EquipmentSlot> r = removeEntity(owner, id);
            if (!r.isEmpty()) out.put(id, r);
        }
        return out;
    }

    private void store(int entityId, Map<EquipmentSlot, Entry> m) {
        if (m.isEmpty()) { byEntity.remove(entityId); targets.remove(entityId); } else byEntity.put(entityId, java.util.Collections.unmodifiableMap(m));
        active = !byEntity.isEmpty();
    }
}
