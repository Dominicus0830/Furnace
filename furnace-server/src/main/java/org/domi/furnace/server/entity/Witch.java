// 바닐라 Witch: 물약을 던져 공격하고(대상 상황에 맞는 물약), 불·물·체력에 따라 물약을 마신다. 피해 감소(자기 피해 0, 마법 ×0.15). TODO: 습격 연동 목표(치료 대상).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Witch extends Raider implements RangedAttackMob {
    public static final int DATA_USING_ITEM = 17;
    private static final String SPEED_MODIFIER_DRINKING_ID = "minecraft:drinking";
    private static final AttributeModifier SPEED_MODIFIER_DRINKING = new AttributeModifier(SPEED_MODIFIER_DRINKING_ID, -0.25, AttributeModifier.ADD_VALUE);
    private int usingTime;
    private NearestAttackableTargetGoal attackPlayersGoal;

    public Witch(ChunkLevel level, int id) { super(EntityType.WITCH, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 26.0).add(Attribute.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_USING_ITEM, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        attackPlayersGoal = new NearestAttackableTargetGoal(this, Player.class, 10, true, false, null);
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 60, 10.0F));
        goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(3, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        // TODO(습격): targetSelector 2 = 치료 대상 습격자(NearestHealableRaiderTargetGoal)
        targetSelector.addGoal(3, attackPlayersGoal);
    }

    public boolean isDrinkingPotion() { return (Boolean) entityData.get(DATA_USING_ITEM); }

    public void setUsingItemFlag(boolean v) { entityData.set(DATA_USING_ITEM, v); }

    @Override public void applyRaidBuffs(int wave, boolean captain) {}

    /** Witch.canBeLeader: 마녀는 순찰 지도자가 되지 않는다(가정 — 바닐라 jar 대조 필요). */
    @Override public boolean canBeLeader() { return false; }

    @Override public String getAmbientSound() { return "entity.witch.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.witch.hurt"; }

    @Override protected String getDeathSound() { return "entity.witch.death"; }

    @Override
    protected void aiStep() {
        if (isAlive()) {
            if (isDrinkingPotion()) {
                if (usingTime-- <= 0) {
                    setUsingItemFlag(false);
                    ItemStack held = getItemBySlot(EquipmentSlot.MAINHAND);
                    setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    if (ItemTools.itemName(held).equals("minecraft:potion")) {
                        for (MobEffectInstance e : Consuming.potionEffects(held)) addEffect(e);
                    }
                    attributes().get(Attribute.MOVEMENT_SPEED).removeModifier(SPEED_MODIFIER_DRINKING_ID);
                }
            } else {
                String potion = null;
                if (random.nextFloat() < 0.15F && isUnderwaterEyes() && !hasEffect(MobEffect.WATER_BREATHING)) potion = "minecraft:water_breathing";
                else if (random.nextFloat() < 0.15F && (isOnFire() || getLastDamageType() != null && org.domi.furnace.server.entity.damage.DamageType.keyHasTag(getLastDamageType(), "is_fire"))
                        && !hasEffect(MobEffect.FIRE_RESISTANCE)) potion = "minecraft:fire_resistance";
                else if (random.nextFloat() < 0.05F && health() < maxHealth()) potion = "minecraft:healing";
                else if (random.nextFloat() < 0.5F && getTarget() != null && !hasEffect(MobEffect.SPEED) && distSqr(getTarget()) > 121.0) potion = "minecraft:swiftness";
                if (potion != null) {
                    ItemStack stack = ItemTools.with(new ItemStack(ItemContext.id("minecraft:item", "potion"), 1), "minecraft:potion_contents",
                            java.util.Map.of("potion", potion));
                    setItemSlot(EquipmentSlot.MAINHAND, stack);
                    usingTime = 32;   // 물약 사용 시간
                    setUsingItemFlag(true);
                    if (!isSilent()) level.playSound(null, x(), y(), z(), "entity.witch.drink", soundSource(), 1.0F, 0.8F + random.nextFloat() * 0.4F);
                    var speed = attributes().get(Attribute.MOVEMENT_SPEED);
                    speed.removeModifier(SPEED_MODIFIER_DRINKING_ID);
                    speed.addOrReplaceModifier(SPEED_MODIFIER_DRINKING);
                }
            }
            if (random.nextFloat() < 7.5E-4F) broadcastEntityEvent((byte) 15);
        }
        super.aiStep();
    }

    private boolean isUnderwaterEyes() { return isEyeInWater(); }

    private double distSqr(Targetable t) {
        double dx = x() - t.x(), dy = y() - t.y(), dz = z() - t.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    protected float getDamageAfterMagicAbsorb(Damage source, float amount) {
        amount = super.getDamageAfterMagicAbsorb(source, amount);
        if (DamageTypeHelper.isMagic(source)) amount *= 0.15F;
        return amount;
    }

    private static final class DamageTypeHelper {
        static boolean isMagic(Damage d) { return org.domi.furnace.server.entity.damage.DamageType.keyHasTag(d.type(), "witch_resistant_to"); }
    }

    @Override
    public void performRangedAttack(Targetable target, float power) {
        if (isDrinkingPotion()) return;
        double mvx = target instanceof Entity e ? e.vx() : 0, mvz = target instanceof Entity e2 ? e2.vz() : 0;
        double dx = target.x() + mvx - x();
        double dy = target.eyeY() - 1.1F - y();
        double dz = target.z() + mvz - z();
        double h = Math.sqrt(dx * dx + dz * dz);
        String potion = "minecraft:harming";
        if (target instanceof Raider) {
            potion = target instanceof LivingEntity le && le.health() <= 4.0F ? "minecraft:healing" : "minecraft:regeneration";
            setTarget(null);
        } else if (h >= 8.0 && !hasEffectOn(target, MobEffect.SLOWNESS)) {
            potion = "minecraft:slowness";
        } else if (targetHealth(target) >= 8.0F && !hasEffectOn(target, MobEffect.POISON)) {
            potion = "minecraft:poison";
        } else if (h <= 3.0 && !hasEffectOn(target, MobEffect.WEAKNESS) && random.nextFloat() < 0.25F) {
            potion = "minecraft:weakness";
        }
        ThrownPotion tp = new ThrownPotion(level, level.nextEntityId());
        tp.setItem(ItemTools.with(new ItemStack(ItemContext.id("minecraft:item", "splash_potion"), 1), "minecraft:potion_contents", java.util.Map.of("potion", potion)));
        tp.startAt(this);
        tp.setXRot(tp.xRot() - -20.0F);
        tp.shoot(dx, dy + h * 0.2, dz, 0.75F, 8.0F);
        if (!isSilent()) level.playSound(null, x(), y(), z(), "entity.witch.throw", soundSource(), 1.0F, 0.8F + random.nextFloat() * 0.4F);
        level.addFreshEntity(tp);
    }

    private static boolean hasEffectOn(Targetable t, MobEffect e) {
        if (t instanceof LivingEntity le) return le.hasEffect(e);
        if (t instanceof Player p) return p.vitals().hasEffect(e);
        return false;
    }

    private static float targetHealth(Targetable t) {
        if (t instanceof LivingEntity le) return le.health();
        if (t instanceof Player p) return p.vitals().health();
        return 0.0F;
    }

    @Override public String getCelebrateSound() { return "entity.witch.celebrate"; }
}
