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

    /** 엔더 드래곤의 파트: 바닐라에서는 종류가 ender_dragon 인 별개 엔티티라 부모 값에 파트의 위치를 얹는다. */
    static LootEntity ofPart(EnderDragonPart part) {
        return new EntityView(part.parent) {
            @Override public double[] position() { return new double[] {part.x(), part.y(), part.z()}; }
        };
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

    private static class EntityView implements LootEntity {
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
        public Object typeSpecific(String key) { return LootTypeSpecific.of(e, key); }
    }

    private static final class PlayerView implements LootEntity {
        final Player p;

        PlayerView(Player p) { this.p = p; }

        public String type() { return "minecraft:player"; }
        public double[] position() { return new double[] {p.x(), p.y(), p.z()}; }
        public boolean isCrouching() { return p.sneaking(); }
        public boolean isFlying() { return p.isFallFlying() || p.vitals().isFlying(); }
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

        public Map<String, Object> gameProfile() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", p.name());
            long msb = p.uuid().getMostSignificantBits(), lsb = p.uuid().getLeastSignificantBits();
            m.put("id", List.of((int) (msb >> 32), (int) msb, (int) (lsb >> 32), (int) lsb));   // UUIDUtil.CODEC: int 4개
            List<Object> props = new ArrayList<>();
            for (var pr : p.profileProperties()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("name", pr.name());
                e.put("value", pr.value());
                if (pr.signature() != null) e.put("signature", pr.signature());
                props.add(e);
            }
            m.put("properties", props);
            return m;
        }

        /**
         * 바닐라 PlayerPredicate 가 읽는 값: gamemode(이름), level, recipe:&lt;id&gt;, advancement:&lt;id&gt;[|criterion],
         * stat:&lt;종류&gt;|&lt;값&gt;(통계 값), looking_at(시선 끝 엔티티). 없는 것은 null.
         */
        public Object typeSpecific(String key) {
            if (key.equals("looking_at")) return lookingAt();
            if (key.startsWith("stat:")) {
                int bar = key.indexOf('|');
                var s = p.stats();   // 통계가 안 붙은 플레이어(시험)는 바닐라 getValue 처럼 0
                return s == null ? 0 : s.get(new org.domi.furnace.server.stats.PlayerStats.Stat(key.substring(5, bar), key.substring(bar + 1)));
            }
            if (key.equals("gamemode")) return p.gameMode().key();
            if (key.equals("level")) return p.vitals().xpLevel();
            if (key.startsWith("recipe:")) return p.recipeBook().contains(org.domi.furnace.server.data.ResourceId.parse(key.substring(7)));
            if (key.startsWith("advancement:")) {
                String rest = key.substring(12);
                int slash = rest.indexOf('|');
                String id = slash < 0 ? rest : rest.substring(0, slash);
                return p.advancements().advancementState(org.domi.furnace.server.data.ResourceId.parse(id), slash < 0 ? null : rest.substring(slash + 1));
            }
            return null;
        }

        /**
         * 바닐라 PlayerPredicate.LookingAtPredicate: 눈에서 시선 방향 100칸 선분(상자를 1.0 키움)에서 관전자 아닌 엔티티 중 선분과 가장
         * 가까이 만나는 것(ProjectileUtil.getEntityHitResult, 여유 0)을 고르고, 그 엔티티까지 hasLineOfSight 도 참이어야 한다.
         * 이 플레이어를 가진 region 스레드에서 부른다(그 레벨이 가진 엔티티만 본다).
         */
        private LootEntity lookingAt() {
            var level = p.currentLevel;
            if (level == null) return null;
            double ex = p.x(), ey = p.eyeY(), ez = p.z();
            double[] v = org.domi.furnace.server.block.Clip.viewVector(p.pitch(), p.yaw());
            double tx = ex + v[0] * 100.0, ty = ey + v[1] * 100.0, tz = ez + v[2] * 100.0;
            AABB box = new AABB(Math.min(ex, tx), Math.min(ey, ty), Math.min(ez, tz), Math.max(ex, tx), Math.max(ey, ty), Math.max(ez, tz)).inflate(1.0);
            // Level.getEntities(player, box, !spectator): 엔더 드래곤을 잡으면 그 바로 뒤에 파트가 조건만으로 붙는다. 플레이어는 따로 훑는다.
            List<Object> cands = new ArrayList<>();
            for (Entity e : level.getEntities(null, box, x -> !x.isSpectator())) {
                cands.add(e);
                if (e instanceof EnderDragon d) for (EnderDragonPart part : d.subEntities) cands.add(part);
            }
            level.forEachPlayerIn(box, o -> {
                if (o != p && !o.isSpectator()) cands.add(o);
            });
            Object best = null;
            double bestD = Double.MAX_VALUE;
            for (Object c : cands) {
                AABB bb = c instanceof Entity e ? e.boundingBox() : c instanceof Player o ? o.boundingBox() : ((EnderDragonPart) c).boundingBox();
                double[] hit = bb.clip(ex, ey, ez, tx, ty, tz);
                if (hit == null) continue;
                double dx = hit[0] - ex, dy = hit[1] - ey, dz = hit[2] - ez;
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = c;
                }
            }
            if (best == null) return null;
            double bx, by, bz;
            LootEntity target;
            if (best instanceof Entity e) { bx = e.x(); by = e.eyeY(); bz = e.z(); target = of(e); }
            else if (best instanceof Player o) { bx = o.x(); by = o.eyeY(); bz = o.z(); target = of(o); }
            else { var part = (EnderDragonPart) best; bx = part.x(); by = part.eyeY(); bz = part.z(); target = ofPart(part); }
            // LivingEntity.hasLineOfSight: 눈에서 눈까지 128칸 안이고 충돌 모양에 안 막힌다
            double ddx = bx - ex, ddy = by - ey, ddz = bz - ez;
            if (Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) > 128.0) return null;
            return org.domi.furnace.server.block.Clip.clipCollider(level, ex, ey, ez, bx, by, bz).miss() ? target : null;
        }
    }
}
