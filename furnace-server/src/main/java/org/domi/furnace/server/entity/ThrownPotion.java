// 바닐라 ThrownPotion(투척/지속 물약 공통): 맞으면 반경 4×2×4 안 생물에게 효과(거리 비례), 물이면 불을 끈다. TODO: 물병의 화로·모닥불 끄기/물 감응 몹 피해(일부).
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
            if (stack.itemId() == org.domi.furnace.server.item.ItemContext.id("minecraft:item", "lingering_potion")) makeCloud(effects, Consuming.potionColor(stack));
            else applySplash(effects, direct);
        }
        int color = Consuming.potionColor(stack);
        boolean instant = !effects.isEmpty() && effects.get(0).effect().isInstantaneous();
        level.levelEvent(null, instant ? 2007 : 2002, blockPosition(), color);
        discard();
    }

    /** makeAreaOfEffectCloud: 반경 3, 사용마다 -0.5, 대기 10틱, 틱마다 -반경/지속. 물약 효과 지속은 1/4. */
    private void makeCloud(java.util.List<MobEffectInstance> effects, int color) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, level.nextEntityId());
        cloud.moveTo(x(), y(), z(), 0.0F, 0.0F);
        Targetable owner = getOwner();
        if (owner instanceof LivingEntity) cloud.setOwner(owner);
        cloud.setRadius(3.0F);
        cloud.setRadiusOnUse(-0.5F);
        cloud.setWaitTime(10);
        cloud.setRadiusPerTick(-cloud.getRadius() / cloud.durationTicks());
        java.util.List<MobEffectInstance> list = new java.util.ArrayList<>();
        for (MobEffectInstance mi : effects) {
            list.add(new MobEffectInstance(mi.effect(), mi.effect().isInstantaneous() ? mi.duration() : mi.duration() / 4, mi.amplifier(), mi.ambient(), mi.visible(), mi.showIcon()));
        }
        cloud.setEffects(list, color);
        level.addFreshEntity(cloud);
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

    /** 물약은 속도가 아니라 대상 위치 − 물약 위치. */
    @Override
    public double[] hurtKnockbackDirection(Targetable target) { return new double[] {target.x() - x(), target.z() - z()}; }
}
