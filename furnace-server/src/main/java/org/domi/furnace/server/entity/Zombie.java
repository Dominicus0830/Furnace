// 바닐라 Zombie: 적대 몹. 플레이어·주민을 쫓아 때리고, 아기는 빠르고 작으며, 소리·저장을 갖는다.
// 아직 없는 것(주석 TODO): 물에서 드라운드 변환, 햇빛에 타기(빛 엔진·높이 지도 필요), 지원군 소환, 장비·줍기, 마을 통과/문 부수기/거북 알 목표, 주민 감염.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.HurtByTargetGoal;
import org.domi.furnace.server.entity.ai.goal.LookAtPlayerGoal;
import org.domi.furnace.server.entity.ai.goal.NearestAttackableTargetGoal;
import org.domi.furnace.server.entity.ai.goal.RandomLookAroundGoal;
import org.domi.furnace.server.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import org.domi.furnace.server.entity.ai.goal.ZombieAttackGoal;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Zombie extends Monster {
    /** Zombie 엔티티 데이터: 16 아기, 17 특수 종류(int), 18 드라운드 변환 중. */
    public static final int DATA_BABY = 16, DATA_SPECIAL_TYPE = 17, DATA_DROWNED_CONVERSION = 18;
    private static final String BABY_MODIFIER_ID = "minecraft:baby";

    private boolean canBreakDoors;

    public Zombie(ChunkLevel level, int id) { super(EntityType.ZOMBIE, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.FOLLOW_RANGE, 35.0).add(Attribute.MOVEMENT_SPEED, 0.23F).add(Attribute.ATTACK_DAMAGE, 3.0).add(Attribute.ARMOR, 2.0)
                .add(Attribute.SPAWN_REINFORCEMENTS, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BABY, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_SPECIAL_TYPE, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_DROWNED_CONVERSION, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        addBehaviourGoals();
    }

    protected void addBehaviourGoals() {
        goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0, false));
        // TODO(마을 통과): 6 = MoveThroughVillageGoal(this, 1.0, true, 4, this::canBreakDoors) — 마을 POI 필요
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(/* ZombifiedPiglin: 아직 없음 */));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Villager.class, false));
        // TODO(대상): 3 = 철 골렘(mustSee), 5 = 땅 위의 아기 거북
    }

    public boolean canBreakDoors() { return canBreakDoors; }

    @Override
    public boolean isBaby() { return (Boolean) entityData.get(DATA_BABY); }

    public void setBaby(boolean baby) {
        entityData.set(DATA_BABY, baby);
        var speed = attributes().get(Attribute.MOVEMENT_SPEED);
        speed.removeModifier(BABY_MODIFIER_ID);
        if (baby) speed.addOrReplaceModifier(new AttributeModifier(BABY_MODIFIER_ID, 0.5, AttributeModifier.ADD_MULTIPLIED_BASE));
        if (baby) setDimensions(0.3F, 0.975F, 0.93F);   // 크기 0.5 배, 눈높이 0.93
        else setDimensions(0.6F, 1.95F, 1.74F);
    }

    /** Zombie.getExperienceReward: 아기는 xpReward 를 2.5 배로(바닐라는 필드 자체를 키운다). */
    @Override
    protected int getExperienceReward() {
        if (isBaby()) xpReward = (int) (xpReward * 2.5);
        return super.getExperienceReward();
    }

    @Override public int getAmbientSoundInterval() { return 80; }

    @Override protected String getAmbientSound() { return "entity.zombie.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.zombie.hurt"; }

    @Override protected String getDeathSound() { return "entity.zombie.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsBaby", isBaby());
        tag.putBoolean("CanBreakDoors", canBreakDoors);
        tag.putInt("InWaterTime", isInWater() ? 0 : -1);
        tag.putInt("DrownedConversionTime", -1);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setBaby(tag.getBoolean("IsBaby"));
        canBreakDoors = tag.getBoolean("CanBreakDoors");
    }
}
