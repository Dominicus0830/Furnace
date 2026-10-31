// 살아 있는 엔티티 → 전리품/발전 과제 조건이 보는 LootEntity (바닐라 EntityPredicate 가 읽는 값 전부).
// 호출은 그 엔티티를 가진 스레드에서 한다. 다른 스레드에서 오래 보려면 KillCredit.snapshot 으로 값을 떠 둔다.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.ItemSlots;
import org.domi.furnace.server.command.arguments.SlotRanges;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.loot.LootEntity;
import org.domi.furnace.server.loot.LootStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public final class LootEntities {

    private LootEntities() {}

    /** 모든 값을 바닐라 EntityPredicate 가 읽는 대로 돌려주는 엔티티 보기. null 이면 null. */
    public static LootEntity of(Rider r) {
        if (r == null) return null;
        if (r instanceof Player p) return new PlayerView(p);
        return new EntityView((Entity) r);
    }

    static Map<String, LootEntity.Effect> effectsOf(Iterable<MobEffectInstance> list) {
        Map<String, LootEntity.Effect> m = new LinkedHashMap<>();
        for (MobEffectInstance e : list) m.put(e.effect().name(), new LootEntity.Effect(e.amplifier(), e.duration(), e.ambient(), e.visible()));
        return m;
    }

    static List<LootStack> slotItems(Rider r, String range) {
        SlotRanges.Range sr = SlotRanges.byName(range);
        List<LootStack> out = new ArrayList<>();
        if (sr == null) return out;
        for (int slot : sr.slots()) {
            ItemSlots.SlotAccess a = ItemSlots.of(r, slot);
            if (a == null) { out.add(LootStack.empty()); continue; }   // SlotAccess.NULL → 빈 스택
            ItemStack s = a.get();
            out.add(s == null ? LootStack.empty() : ServerCore.lootStackOf(s));
        }
        return out;
    }

    static double[] center(org.domi.furnace.server.block.Pos p) { return new double[] {p.x() + 0.5, p.y() + 0.5, p.z() + 0.5}; }

    static double[] bottomCenter(org.domi.furnace.server.block.Pos p) { return new double[] {p.x() + 0.5, p.y(), p.z() + 0.5}; }

    private static EquipmentSlot equipmentSlot(String name) {
        for (EquipmentSlot s : EquipmentSlot.values()) if (s.slotName().equals(name)) return s;
        return null;
    }

    private static final class EntityView implements LootEntity {
        final Entity e;

        EntityView(Entity e) { this.e = e; }

        public String type() { return e.typeName(); }
        public boolean isOnFire() { return e.isOnFire(); }
        public boolean onGround() { return e.onGround(); }
        public double[] position() { return new double[] {e.x(), e.y(), e.z()}; }
        public boolean isBaby() { return e instanceof LivingEntity le && le.isBaby(); }
        public LootStack equipment(String slot) {
            if (!(e instanceof LivingEntity le)) return LootStack.empty();
            EquipmentSlot es = equipmentSlot(slot);
            return es == null ? LootStack.empty() : ServerCore.lootStackOf(le.getItemBySlot(es));
        }
        public Map<String, Effect> effects() { return e instanceof LivingEntity le ? effectsOf(le.getActiveEffects()) : Map.of(); }
        public NbtCompound nbt() { return e.saveWithoutId(new NbtCompound()); }
        public LootEntity vehicle() { return e.vehicle() == null ? null : of(e.vehicle()); }
        public List<LootEntity> passengers() {
            List<LootEntity> l = new ArrayList<>();
            for (Rider r : e.passengers()) l.add(of(r));
            return l;
        }
        public LootEntity targetedEntity() { return e instanceof Mob m && m.getTarget() instanceof Rider t ? of(t) : null; }
        public double[] steppingOnPos() { return center(e.getOnPos()); }
        public double[] movementAffectedByPos() { return bottomCenter(e.getBlockPosBelowThatAffectsMyMovement()); }
        public double[] velocity() { return new double[] {e.vx(), e.vy(), e.vz()}; }
        public float fallDistance() { return e.fallDistance; }
        public String team() { var t = e.getTeam(); return t == null ? null : t.name(); }
        public List<LootStack> slotItems(String range) { return LootEntities.slotItems(e, range); }
        public int tickCount() { return e.tickCount; }
    }

    private static final class PlayerView implements LootEntity {
        final Player p;

        PlayerView(Player p) { this.p = p; }

        public String type() { return "minecraft:player"; }
        public double[] position() { return new double[] {p.x(), p.y(), p.z()}; }
        public boolean isCrouching() { return p.sneaking(); }
        public boolean isSprinting() { return p.sprinting(); }
        public boolean isSwimming() { return p.swimming; }
        public boolean onGround() { return p.onGround(); }
        public LootStack equipment(String slot) {
            EquipmentSlot es = equipmentSlot(slot);
            return es == null ? LootStack.empty() : ServerCore.lootStackOf(p.equipment(es));
        }
        public Map<String, Effect> effects() { return effectsOf(p.vitals().activeEffects()); }
        public NbtCompound nbt() {
            synchronized (p.menuLock()) { return PlayerData.snapshotNbt(p); }
        }
        public LootEntity vehicle() { return p.vehicle() == null ? null : of(p.vehicle()); }
        public double[] steppingOnPos() { return center(p.blockPosition().below()); }
        public double[] movementAffectedByPos() { return bottomCenter(p.blockPosition().below()); }
        public double[] velocity() { double[] m = p.serverMovement(); return new double[] {m[0], m[1], m[2]}; }
        public float fallDistance() { return p.vitals().fallDistance(); }
        public String team() { var t = p.team(); return t == null ? null : t.name(); }
        public List<LootStack> slotItems(String range) { return LootEntities.slotItems(p, range); }
        public int tickCount() { return p.vitals().tickCount(); }
    }
}
