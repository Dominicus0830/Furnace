// 바닐라 Illusioner: 활을 쏘고, 자신을 투명하게 하며(분신), 어려움에서 대상을 실명시키는 일러저.
// 분신(미러 이미지) 위치는 클라이언트 연출이라 서버에선 계산하지 않는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.goal.*;

public class Illusioner extends SpellcasterIllager implements RangedAttackMob {
    public Illusioner(ChunkLevel level, int id) {
        super(EntityType.ILLUSIONER, level, id);
        xpReward = 5;
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.5).add(Attribute.FOLLOW_RANGE, 18.0).add(Attribute.MAX_HEALTH, 32.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new SpellcasterCastingSpellGoal());
        goalSelector.addGoal(4, new IllusionerMirrorSpellGoal());
        goalSelector.addGoal(5, new IllusionerBlindnessSpellGoal());
        goalSelector.addGoal(6, new RangedBowAttackGoal<>(this, 0.5, 20, 15.0F));
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new InteractGoal(this, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtMobGoal(this, Mob.class, 8.0F, 0.02F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true).setUnseenMemoryTicks(300));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, false).setUnseenMemoryTicks(300));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, false).setUnseenMemoryTicks(300));
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, stackOf("minecraft:bow"));
        super.finalizeSpawn(r, difficulty);
    }

    @Override
    public void performRangedAttack(Targetable target, float power) {
        Arrow arrow = new Arrow(level, level.nextEntityId(), x(), eyeY() - 0.1F, z());
        arrow.setOwner(this);
        arrow.setEnchantmentEffectsFromEntity(power);
        double dx = target.x() - x();
        double dy = target.y() + target.bbHeight() * (1.0 / 3.0) - arrow.y();
        double dz = target.z() - z();
        double h = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + h * 0.2F, dz, 1.6F, 14 - level.difficulty() * 4);
        playSound("entity.skeleton.shoot", 1.0F, 1.0F / (random.nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
    }

    @Override
    public IllagerArmPose getArmPose() {
        if (isCastingSpell()) return IllagerArmPose.SPELLCASTING;
        return isAggressive() ? IllagerArmPose.BOW_AND_ARROW : IllagerArmPose.CROSSED;
    }

    @Override public void applyRaidBuffs(int wave, boolean captain) { }

    @Override public String getAmbientSound() { return "entity.illusioner.ambient"; }

    @Override protected String getDeathSound() { return "entity.illusioner.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.illusioner.hurt"; }

    @Override protected String getCastingSoundEvent() { return "entity.illusioner.cast_spell"; }

    class IllusionerBlindnessSpellGoal extends SpellcasterUseSpellGoal {
        private int lastTargetId;

        @Override
        public boolean canUse() {
            if (!super.canUse()) return false;
            Targetable t = getTarget();
            if (t == null) return false;
            return t.id() == lastTargetId ? false : level.getCurrentDifficultyAt(blockPosition()).isGreaterThan(3.0F);
        }

        @Override
        public void start() {
            super.start();
            Targetable t = getTarget();
            if (t != null) lastTargetId = t.id();
        }

        @Override protected int getCastingTime() { return 20; }

        @Override protected int getCastingInterval() { return 180; }

        @Override
        protected void performSpellCasting() {
            Targetable t = getTarget();
            MobEffectInstance blind = new MobEffectInstance(MobEffect.BLINDNESS, 400);
            if (t instanceof LivingEntity le) le.addEffect(blind);
            else if (t instanceof Player p) p.vitals().addEffect(blind);
        }

        @Override protected String getSpellPrepareSound() { return "entity.illusioner.prepare_blindness"; }

        @Override protected IllagerSpell getSpell() { return IllagerSpell.BLINDNESS; }
    }

    class IllusionerMirrorSpellGoal extends SpellcasterUseSpellGoal {
        @Override
        public boolean canUse() { return super.canUse() && !hasEffect(MobEffect.INVISIBILITY); }

        @Override protected int getCastingTime() { return 20; }

        @Override protected int getCastingInterval() { return 340; }

        @Override
        protected void performSpellCasting() { addEffect(new MobEffectInstance(MobEffect.INVISIBILITY, 1200)); }

        @Override protected String getSpellPrepareSound() { return "entity.illusioner.prepare_mirror"; }

        @Override protected IllagerSpell getSpell() { return IllagerSpell.DISAPPEAR; }
    }
}
