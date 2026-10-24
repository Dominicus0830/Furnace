// 바닐라 ThrownPotion(투척/지속 물약 공통): 맞으면 반경 4×2×4 안 생물에게 효과(거리 비례), 물이면 불을 끈다. TODO: 지속 물약 구름, 물병의 화로·모닥불 끄기/물 감응 몹 피해(일부).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffectInstance;

public class ThrownPotion extends ThrowableProjectile {

    public ThrownPotion(ChunkLevel level, int id) { super(EntityType.POTION, level, id); }

    @Override protected String defaultItem() { return "minecraft:splash_potion"; }

    @Override protected double defaultGravity() { return 0.05; }

    @Override
    protected void onHitEntity(Hit hit) { splash(hit.target()); }

    @Override
    protected void onHitBlock(Hit hit) { splash(null); }

    @Override
    protected void onHit(Hit hit) {
        super.onHit(hit);
    }

    private void splash(Targetable direct) {
        ItemStack stack = getItem();
        java.util.List<MobEffectInstance> effects = Consuming.potionEffects(stack);
        if (Consuming.isWaterPotion(stack)) {
            applyWater();
        } else if (!effects.isEmpty()) {
            // TODO(지속 물약): 지속 물약이면 효과 구름을 만든다
            applySplash(effects, direct);
        }
        int color = Consuming.potionColor(stack);
        boolean instant = !effects.isEmpty() && effects.get(0).effect().isInstantaneous();
        level.levelEvent(null, instant ? 2007 : 2002, blockPosition(), color);
        discard();
    }

    private void applyWater() {
        AABB box = boundingBox().inflate(4.0, 2.0, 4.0);
        for (Entity e : level.getEntities(this, box, x -> x instanceof LivingEntity)) {
            LivingEntity le = (LivingEntity) e;
            double d = (le.x() - x()) * (le.x() - x()) + (le.y() - y()) * (le.y() - y()) + (le.z() - z()) * (le.z() - z());
            if (d < 16.0) {
                if (le.isSensitiveToWater()) le.hurtBy(new Entity.Damage("magic", false, false, false), 1.0F, getOwner());
                if (le.isOnFire() && le.isAlive()) le.clearFire();
            }
        }
    }

    private void applySplash(java.util.List<MobEffectInstance> effects, Targetable direct) {
        AABB box = boundingBox().inflate(4.0, 2.0, 4.0);
        for (Entity e : level.getEntities(this, box, x -> x instanceof LivingEntity)) {
            LivingEntity le = (LivingEntity) e;
            apply(le, effects, direct, distSqr(le.x(), le.y(), le.z()));
        }
        java.util.List<Player> players = new java.util.ArrayList<>();
        level.forEachPlayerIn(box, players::add);
        for (Player p : players) {
            double d = distSqr(p.x(), p.y(), p.z());
            if (d >= 16.0) continue;
            double proximity = p == direct ? 1.0 : 1.0 - Math.sqrt(d) / 4.0;
            for (MobEffectInstance mi : effects) {
                if (mi.effect().isInstantaneous()) {
                    p.vitals().applyInstantaneousEffect(mi.effect(), mi.amplifier(), proximity);
                } else {
                    int dur = (int) (proximity * mi.duration() + 0.5);
                    MobEffectInstance n = new MobEffectInstance(mi.effect(), dur, mi.amplifier(), mi.ambient(), mi.visible(), mi.showIcon());
                    if (!n.endsWithin(20)) p.vitals().addEffect(n);
                }
            }
        }
    }

    private double distSqr(double px, double py, double pz) { return (px - x()) * (px - x()) + (py - y()) * (py - y()) + (pz - z()) * (pz - z()); }

    private void apply(LivingEntity le, java.util.List<MobEffectInstance> effects, Targetable direct, double d) {
        if (d >= 16.0) return;
        double proximity = le == direct ? 1.0 : 1.0 - Math.sqrt(d) / 4.0;
        for (MobEffectInstance mi : effects) {
            if (mi.effect().isInstantaneous()) {
                mi.effect().applyInstantenousEffect(le.effectHost(), mi.amplifier(), proximity);
            } else {
                int dur = (int) (proximity * mi.duration() + 0.5);
                MobEffectInstance n = new MobEffectInstance(mi.effect(), dur, mi.amplifier(), mi.ambient(), mi.visible(), mi.showIcon());
                if (!n.endsWithin(20)) le.addEffect(n);
            }
        }
    }
}
