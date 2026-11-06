// 바닐라 Hoglin: 브레인으로 움직이는 네더 동물. 어른은 플레이어를 들이받아 날려 보내고 피글린·기피 블록을 피한다. 오버월드에서 15초 넘게 있으면 조글린이 된다.
// TODO(공격 후처리): 인챈트 후처리 효과. TODO(끈): 목줄 규칙이 생기면.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Hoglin extends Animal implements Enemy, HoglinBase {
    /** canBeLeashed(디컴파일 y): Hoglin(clh)은 Enemy 여도 맬 수 있다. */
    @Override
    public boolean canBeLeashed() { return true; }


    public static final int DATA_IMMUNE_TO_ZOMBIFICATION = 17;
    public static final int CONVERSION_TIME = 300;
    private int attackAnimationRemainingTicks;
    private int timeInOverworld;
    private boolean cannotBeHunted;

    public Hoglin(ChunkLevel level, int id) {
        super(EntityType.HOGLIN, level, id);
        xpReward = 5;
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Hoglin> makeBrain() {
        Brain<Hoglin> b = new Brain<>(java.util.List.of(MemoryModuleType.BREED_TARGET, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN,
                MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN, MemoryModuleType.AVOID_TARGET, MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT, MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT,
                MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.NEAREST_REPELLENT, MemoryModuleType.PACIFIED,
                MemoryModuleType.IS_PANICKING),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS, SensorType.NEAREST_ADULT, SensorType.HOGLIN_SPECIFIC));
        return HoglinAi.makeBrain(b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Hoglin> hoglinBrain() { return (Brain<Hoglin>) brain; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 40.0).add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.KNOCKBACK_RESISTANCE, 0.6F).add(Attribute.ATTACK_KNOCKBACK, 1.0).add(Attribute.ATTACK_DAMAGE, 6.0);
    }

    @Override
    public boolean doHurtTarget(Targetable target) {
        this.attackAnimationRemainingTicks = 10;
        broadcastEntityEvent((byte) 4);
        playSound("entity.hoglin.attack", 1.0F, getVoicePitch());
        HoglinAi.onHitTarget(this, target);
        return HoglinBase.hurtAndThrowTarget(this, target);
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        boolean hurt = super.hurtBy(source, amount, attacker);
        if (hurt && attacker != null) HoglinAi.onHurtBy(this, attacker);
        return hurt;
    }

    @Override
    protected void customServerAiStep() {
        hoglinBrain().tick(level, this);
        HoglinAi.updateActivity(this);
        if (isConverting()) {
            timeInOverworld++;
            if (timeInOverworld > CONVERSION_TIME) {
                playSound("entity.hoglin.converted_to_zombified", 1.0F, getVoicePitch());
                finishConversion();
            }
        } else {
            timeInOverworld = 0;
        }
    }

    @Override
    public void tick() {
        if (attackAnimationRemainingTicks > 0) attackAnimationRemainingTicks--;
        super.tick();
    }

    @Override
    protected void ageBoundaryReached() {
        if (isBaby()) {
            xpReward = 3;
            attributes().get(Attribute.ATTACK_DAMAGE).setBaseValue(0.5);
        } else {
            xpReward = 5;
            attributes().get(Attribute.ATTACK_DAMAGE).setBaseValue(6.0);
        }
    }

    @Override
    public GroupData finalizeSpawn(RandomSource r, DifficultyInstance difficulty, GroupData data) {
        if (r.nextFloat() < 0.2F) setBaby(true);
        return super.finalizeSpawn(r, difficulty, data);
    }

    @Override public boolean removeWhenFarAway(double distSqr) { return !persistenceRequired; }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) {
        if (HoglinAi.isPosNearNearestRepellent(this, pos)) return -1.0F;
        return B.is(level.getBlockState(pos.below()), "minecraft:crimson_nylium") ? 10.0F : 0.0F;
    }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        boolean ok = super.interact(p, hand, held, region);
        if (ok) persistenceRequired = true;
        return ok;
    }

    @Override public int getAttackAnimationRemainingTicks() { return attackAnimationRemainingTicks; }

    @Override public Targetable getTarget() { return getTargetFromBrain(); }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:hoglin_food"); }

    public boolean isAdult() { return !isBaby(); }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IMMUNE_TO_ZOMBIFICATION, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (isImmuneToZombification()) tag.putBoolean("IsImmuneToZombification", true);
        tag.putInt("TimeInOverworld", timeInOverworld);
        if (cannotBeHunted) tag.putBoolean("CannotBeHunted", true);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setImmuneToZombification(tag.getBoolean("IsImmuneToZombification"));
        timeInOverworld = tag.getInt("TimeInOverworld");
        setCannotBeHunted(tag.getBoolean("CannotBeHunted"));
    }

    public void setImmuneToZombification(boolean v) { entityData.set(DATA_IMMUNE_TO_ZOMBIFICATION, v); }

    private boolean isImmuneToZombification() { return (Boolean) entityData.get(DATA_IMMUNE_TO_ZOMBIFICATION); }

    public boolean isConverting() { return !level.piglinSafe() && !isImmuneToZombification() && !isNoAi(); }

    private void setCannotBeHunted(boolean v) { cannotBeHunted = v; }

    public boolean canBeHunted() { return isAdult() && !cannotBeHunted; }

    private void finishConversion() {
        Zoglin zoglin = new Zoglin(level, level.nextEntityId());
        zoglin = convertTo(zoglin, true, "PIGLIN_ZOMBIFIED");
        if (zoglin != null) zoglin.addEffect(new MobEffectInstance(MobEffect.NAUSEA, 200, 0));
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Hoglin child = new Hoglin(level, level.nextEntityId());
        child.persistenceRequired = true;
        return child;
    }

    @Override public boolean canFallInLove() { return !HoglinAi.isPacified(this) && super.canFallInLove(); }

    @Override public String soundSource() { return "hostile"; }

    @Override
    public String getAmbientSound() { return HoglinAi.getSoundForCurrentActivity(this).orElse(null); }

    @Override protected String getHurtSound(Damage source) { return "entity.hoglin.hurt"; }

    @Override protected String getDeathSound() { return "entity.hoglin.death"; }



    /** 바닐라 Hoglin: 아기도 경험치를 준다. */
    @Override
    protected boolean shouldDropExperience() { return true; }
}
