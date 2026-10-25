// 바닐라 Zoglin: 좀비가 된 호글린. 아무나(조글린·크리퍼 제외) 공격하는 적대 몹. 호글린처럼 대상을 날려 보낸다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Zoglin extends Monster implements HoglinBase {
    public static final int DATA_BABY = 16;
    private int attackAnimationRemainingTicks;

    public Zoglin(ChunkLevel level, int id) {
        super(EntityType.ZOGLIN, level, id);
        xpReward = 5;
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Zoglin> makeBrain() {
        Brain<Zoglin> b = new Brain<>(java.util.List.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_PLAYER,
                MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, MemoryModuleType.LOOK_TARGET, MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                MemoryModuleType.PATH, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_PLAYERS));
        initCore(b);
        initIdle(b);
        initFight(b);
        b.setCoreActivities(Set.of(Activity.CORE));
        b.setDefaultActivity(Activity.IDLE);
        b.useDefaultActivity();
        return b;
    }

    private static Map.Entry<BehaviorControl<? super Zoglin>, Integer> w(BehaviorControl<? super Zoglin> b, int weight) { return Map.entry(b, weight); }

    private static void initCore(Brain<Zoglin> brain) { brain.addActivity(Activity.CORE, 0, List.of(new LookAtTargetSink(45, 90), new MoveToTargetSink())); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdle(Brain<Zoglin> brain) {
        brain.addActivity(Activity.IDLE, 10, List.of(StartAttacking.<Zoglin>create(Zoglin::findNearestValidAttackTarget), SetEntityLookTargetSometimes.create(8.0F, UniformInt.of(30, 60)),
                new RunOne<Zoglin>(List.<Map.Entry<? extends BehaviorControl<? super Zoglin>, Integer>>of(w(RandomStroll.stroll(0.4F), 2), w(SetWalkTargetFromLookTarget.create(0.4F, 3), 2),
                        w(new DoNothing(30, 60), 1)))));
    }

    private static void initFight(Brain<Zoglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.FIGHT, 10, List.of(SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.0F),
                OneShot.sequence(OneShot.triggerIf((Zoglin z) -> z.isAdult()), MeleeAttack.create(40)), OneShot.sequence(OneShot.triggerIf((Zoglin z) -> z.isBaby()), MeleeAttack.create(15)),
                StopAttackingIfTargetInvalid.<Zoglin>create()), MemoryModuleType.ATTACK_TARGET);
    }

    @SuppressWarnings("unchecked")
    public Brain<Zoglin> zoglinBrain() { return (Brain<Zoglin>) brain; }

    private Optional<Targetable> findNearestValidAttackTarget() {
        return zoglinBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities.empty()).findClosest(this::isTarget);
    }

    private boolean isTarget(Targetable t) {
        if (t instanceof Entity e && (e.type() == EntityType.ZOGLIN || e.type() == EntityType.CREEPER)) return false;
        return Sensor.isEntityAttackable(this, t);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 40.0).add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.KNOCKBACK_RESISTANCE, 0.6F).add(Attribute.ATTACK_KNOCKBACK, 1.0).add(Attribute.ATTACK_DAMAGE, 6.0);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BABY, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    public boolean isAdult() { return !isBaby(); }

    @Override
    public boolean doHurtTarget(Targetable target) {
        this.attackAnimationRemainingTicks = 10;
        broadcastEntityEvent((byte) 4);
        playSound("entity.zoglin.attack", 1.0F, getVoicePitch());
        return HoglinBase.hurtAndThrowTarget(this, target);
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        boolean hurt = super.hurtBy(source, amount, attacker);
        if (hurt && attacker != null && isTarget(attacker) && !BehaviorUtils.isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(this, attacker, 4.0)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            zoglinBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, attacker, 200L);
        }
        return hurt;
    }

    @Override
    protected void customServerAiStep() {
        Activity before = zoglinBrain().getActiveNonCoreActivity().orElse(null);
        zoglinBrain().tick(level, this);
        zoglinBrain().setActiveActivityToFirstValid(List.of(Activity.FIGHT, Activity.IDLE));
        Activity after = zoglinBrain().getActiveNonCoreActivity().orElse(null);
        if (after == Activity.FIGHT && before != Activity.FIGHT) makeSound("entity.zoglin.angry");
        setAggressive(zoglinBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
    }

    @Override
    public void tick() {
        if (attackAnimationRemainingTicks > 0) attackAnimationRemainingTicks--;
        super.tick();
    }

    @Override public Targetable getTarget() { return getTargetFromBrain(); }

    @Override public int getAttackAnimationRemainingTicks() { return attackAnimationRemainingTicks; }

    @Override public boolean isBaby() { return (Boolean) entityData.get(DATA_BABY); }

    public void setBaby(boolean baby) {
        entityData.set(DATA_BABY, baby);
        if (baby) attributes().get(Attribute.ATTACK_DAMAGE).setBaseValue(0.5);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (isBaby()) tag.putBoolean("IsBaby", true);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("IsBaby")) setBaby(true);
    }

    @Override
    public String getAmbientSound() { return zoglinBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) ? "entity.zoglin.angry" : "entity.zoglin.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.zoglin.hurt"; }

    @Override protected String getDeathSound() { return "entity.zoglin.death"; }
}
