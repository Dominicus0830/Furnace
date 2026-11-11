// 바닐라 WitherSkeleton: 위더 효과(200틱)를 거는 근접 해골. 키 2.4, 불 면역, 돌 검. 화살은 불(100초). 피글린(야수 포함)을 노린다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;

public class WitherSkeleton extends AbstractSkeleton {

    public WitherSkeleton(ChunkLevel level, int id) {
        super(EntityType.WITHER_SKELETON, level, id);
        setPathfindingMalus(org.domi.furnace.server.entity.ai.navigation.PathType.LAVA, 8.0F);
    }

    /** 바닐라 cku.B: 대상 3 NearestAttackableTargetGoal(AbstractPiglin, 보여야 함)을 AbstractSkeleton 목표보다 먼저 넣는다. */
    @Override
    protected void registerGoals() {
        targetSelector.addGoal(3, new org.domi.furnace.server.entity.ai.goal.NearestAttackableTargetGoal(this, AbstractPiglin.class, true));
        super.registerGoals();
    }

    @Override
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, stackOf("minecraft:stone_sword"));
    }

    @Override
    protected void populateDefaultEquipmentEnchantments(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {}

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        attributes().get(Attribute.ATTACK_DAMAGE).setBaseValue(4.0);
        reassessWeaponGoal();
    }

    @Override
    public boolean doHurtTarget(Targetable t) {
        if (!super.doHurtTarget(t)) return false;
        if (t instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffect.WITHER, 200));
        else if (t instanceof Player p) p.vitals().addEffect(new MobEffectInstance(MobEffect.WITHER, 200));
        return true;
    }

    @Override protected void prepareArrow(Arrow arrow) { arrow.igniteForSeconds(100.0F); }

    @Override
    public boolean canBeAffected(MobEffectInstance i) { return i.effect() != MobEffect.WITHER && super.canBeAffected(i); }

    @Override public String getAmbientSound() { return "entity.wither_skeleton.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.wither_skeleton.hurt"; }

    @Override protected String getDeathSound() { return "entity.wither_skeleton.death"; }

    @Override String getStepSound() { return "entity.wither_skeleton.step"; }

    /** 충전 크리퍼 폭발에 죽으면 머리를 한 번 떨군다(바닐라 dropCustomDeathLoot). */
    @Override
    protected void dropCustomDeathLoot(Damage source, boolean byPlayer) {
        super.dropCustomDeathLoot(source, byPlayer);
        Creeper.dropSkullIfCharged(this, damageSourceEntity(), "wither_skeleton_skull");
    }
}
