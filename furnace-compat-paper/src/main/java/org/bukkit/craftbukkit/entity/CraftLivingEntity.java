package org.bukkit.craftbukkit.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.domi.furnace.api.entity.LivingEntityState;
import org.domi.furnace.api.entity.MobEffect;
import org.domi.furnace.compat.paper.EntityStates;
import org.domi.furnace.compat.paper.UnsupportedFeature;

public class CraftLivingEntity extends CraftEntity implements LivingEntity {

    /** 호환 모듈 전용(Compat 캐시가 만든다). */
    public CraftLivingEntity(org.domi.furnace.api.entity.LivingEntity handle) { super(handle); }

    private org.domi.furnace.api.entity.LivingEntity living() { return (org.domi.furnace.api.entity.LivingEntity) furnaceHandle(); }

    @Override
    public net.minecraft.world.entity.LivingEntity getHandle() { return (net.minecraft.world.entity.LivingEntity) super.getHandle(); }

    @Override
    protected net.minecraft.world.entity.Entity createHandle() { return new net.minecraft.world.entity.LivingEntity(this); }

    @Override
    public double getHealth() { return living().health(); }

    @Override
    public double getMaxHealth() { return living().maxHealth(); }

    @Override
    public void setHealth(double health) {
        if (health < 0 || health > getMaxHealth()) throw new IllegalArgumentException("Health must be between 0 and " + getMaxHealth() + ", but was " + health);
        living().updateHealth((float) health);
    }

    // ---- 상태 효과

    static MobEffect toFurnace(PotionEffect e) {
        return new MobEffect(e.getType().getKey().toString(), e.getDuration(), e.getAmplifier(), e.isAmbient(), e.hasParticles(), e.hasIcon());
    }

    static PotionEffect toBukkit(MobEffect e) {
        PotionEffectType t = PotionEffectType.fromKey(e.type());
        if (t == null) throw new UnsupportedFeature("PotionEffectType " + e.type(), "Bukkit 효과 목록에 없는 종류다(데이터 팩·플러그인 효과)");
        return new PotionEffect(t, e.duration(), e.amplifier(), e.ambient(), e.particles(), e.icon());
    }

    /** Paper 처럼 결과와 상관없이 true. 소유 스레드가 아니면 소유 스레드로 예약한다. */
    @Override
    public boolean addPotionEffect(PotionEffect effect) {
        if (effect == null) throw new IllegalArgumentException("effect cannot be null");
        MobEffect m = toFurnace(effect);
        EntityStates.write(furnaceHandle(), LivingEntityState.class, s -> s.addEffect(m), "LivingEntity#addPotionEffect");
        return true;
    }

    @Override
    @Deprecated
    public boolean addPotionEffect(PotionEffect effect, boolean force) { return addPotionEffect(effect); }

    @Override
    public boolean addPotionEffects(Collection<PotionEffect> effects) {
        boolean ok = true;
        for (PotionEffect e : effects) ok &= addPotionEffect(e);
        return ok;
    }

    @Override
    public boolean hasPotionEffect(PotionEffectType type) {
        String k = type.getKey().toString();
        return EntityStates.read(furnaceHandle(), LivingEntityState.class, s -> s.effect(k).isPresent(), false, "LivingEntity#hasPotionEffect");
    }

    @Override
    public PotionEffect getPotionEffect(PotionEffectType type) {
        String k = type.getKey().toString();
        return EntityStates.read(furnaceHandle(), LivingEntityState.class, s -> s.effect(k).map(CraftLivingEntity::toBukkit).orElse(null), null,
                "LivingEntity#getPotionEffect");
    }

    @Override
    public void removePotionEffect(PotionEffectType type) {
        String k = type.getKey().toString();
        EntityStates.write(furnaceHandle(), LivingEntityState.class, s -> s.removeEffect(k), "LivingEntity#removePotionEffect");
    }

    @Override
    public Collection<PotionEffect> getActivePotionEffects() {
        return EntityStates.read(furnaceHandle(), LivingEntityState.class, s -> {
            List<PotionEffect> out = new ArrayList<>();
            for (MobEffect m : s.effects()) out.add(toBukkit(m));
            return out;
        }, new ArrayList<>(), "LivingEntity#getActivePotionEffects");
    }

    @Override
    public boolean clearActivePotionEffects() {
        return EntityStates.read(furnaceHandle(), LivingEntityState.class, s -> s.clearEffects() > 0, false, "LivingEntity#clearActivePotionEffects");
    }

    // ---- AI·밀림

    /** 몹이 아니면 아무 일도 없다(Paper 와 같다). */
    @Override
    public void setAI(boolean ai) {
        if (!(furnaceHandle() instanceof org.domi.furnace.api.entity.Mob mob)) return;
        try {
            mob.aiNow().ifPresent(a -> a.setNoAi(!ai));
        } catch (org.domi.furnace.api.concurrent.ThreadOwnershipException notOwner) {
            mob.editAi(a -> {
                a.setNoAi(!ai);
                return null;
            });
        }
    }

    @Override
    public boolean hasAI() {
        if (!(furnaceHandle() instanceof org.domi.furnace.api.entity.Mob mob)) return false;
        return mob.aiNow().map(a -> !a.isNoAi()).orElse(false);
    }

    @Override
    public void setCollidable(boolean collidable) {
        EntityStates.write(furnaceHandle(), LivingEntityState.class, s -> s.setCollidable(collidable), "LivingEntity#setCollidable");
    }

    @Override
    public boolean isCollidable() { return EntityStates.read(furnaceHandle(), LivingEntityState.class, LivingEntityState::isCollidable, true, "LivingEntity#isCollidable"); }
}
