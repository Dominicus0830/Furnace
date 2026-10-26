// 바닐라 Vindicator: 도끼를 든 일러저. 플레이어·주민·철 골렘을 노린다. 'Johnny' 이름이면 모든 생명체를 공격한다.
// TODO(문·습격·이름표): VindicatorBreakDoorGoal, 습격 문 열기(RaiderOpenDoorGoal), 습격 무기 마법 부여, 이름표 Johnny 감지, 마을에서 문 열기 허용.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class Vindicator extends AbstractIllager {
    private boolean isJohnny;

    public Vindicator(ChunkLevel level, int id) { super(EntityType.VINDICATOR, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.35F).add(Attribute.MAX_HEALTH, 12.0).add(Attribute.FOLLOW_RANGE, 24.0).add(Attribute.ATTACK_DAMAGE, 5.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(3, new HoldGroundAttackGoal(this, 10.0F));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, Raider.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, true));
        goalSelector.addGoal(8, new RandomStrollGoal(this, 0.6));
        goalSelector.addGoal(9, new InteractGoal(this, 3.0F, 1.0F));
        goalSelector.addGoal(10, new LookAtMobGoal(this, Mob.class, 8.0F, 0.02F));
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (isJohnny) tag.putBoolean("Johnny", true);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get("Johnny") != null) isJohnny = tag.getBoolean("Johnny");
    }

    @Override
    public IllagerArmPose getArmPose() {
        if (isAggressive()) return IllagerArmPose.ATTACKING;
        return isCelebrating() ? IllagerArmPose.CELEBRATING : IllagerArmPose.CROSSED;
    }

    @Override
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        if (!hasActiveRaid()) setItemSlot(EquipmentSlot.MAINHAND, stackOf("minecraft:iron_axe"));
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        ((org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation) getNavigation()).setCanOpenDoors(true);
        populateDefaultEquipmentSlots(r, difficulty);
        populateDefaultEquipmentEnchantments(r, difficulty);
    }

    @Override
    public void applyRaidBuffs(int wave, boolean captain) {
        // TODO(습격): 파도에 따라 도끼 마법(날카로움/힘) 부여
    }

    @Override public String getAmbientSound() { return "entity.vindicator.ambient"; }

    @Override protected String getDeathSound() { return "entity.vindicator.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.vindicator.hurt"; }
}
