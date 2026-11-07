// 바닐라 Endermite: 엔더 진주로 생기는 작은 몹. 2400틱(2분) 뒤 사라진다(이름표 없을 때). 체력 8, 공격력 2.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class Endermite extends Monster {
    private int life;

    public Endermite(ChunkLevel level, int id) {
        super(EntityType.ENDERMITE, level, id);
        xpReward = 3;
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 8.0).add(Attribute.MOVEMENT_SPEED, 0.25).add(Attribute.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 1.0));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
    }

    @Override public String getAmbientSound() { return "entity.endermite.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.endermite.hurt"; }

    @Override protected String getDeathSound() { return "entity.endermite.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Lifetime", life);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        life = tag.getIntOr0("Lifetime");
    }

    @Override
    public void tick() {
        yBodyRot = yRot();
        super.tick();
    }

    @Override
    protected void aiStep() {
        super.aiStep();
        if (!hasCustomName()) life++;
        if (life >= 2400) discard();
    }

    /** Endermite.playStepSound: endermite.step 크기 0.15(블록 소리를 쓰지 않는다). */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) { playSound("minecraft:entity.endermite.step", 0.15F, 1.0F); }
}
