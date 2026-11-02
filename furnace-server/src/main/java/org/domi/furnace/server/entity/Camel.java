// 바닐라 Camel: 브레인으로 움직이는 말과 동물. 앉았다 일어나는 자세 전환(마지막 자세 바뀐 틱)과 선인장 먹이로 번식한다.
// TODO(발소리·끈): 발걸음 소리(모래 위는 step_sand)와 목줄 판정은 엔진에 아직 없다. TODO(탑승): 안장·2인 탑승·질주(dash)·탑승자 위치는 탑승 규칙이 생기면. TODO(자세 크기): 앉은 자세 몸 크기(높이 -1.43)와 몸 회전 제어는 아직 없다.
package org.domi.furnace.server.entity;

import java.util.Optional;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Camel extends AbstractHorse {
    public static final int DATA_DASH = 18, DATA_LAST_POSE_CHANGE_TICK = 19;
    public static final float BABY_SCALE = 0.45F;
    public static final int DASH_COOLDOWN_TICKS = 55;
    public static final int MAX_HEAD_Y_ROT = 30;
    public static final int STANDING_TO_SITTING_TICKS = 52;
    public static final int SITTING_TO_STANDING_TICKS = 40;
    private int dashCooldown;

    public Camel(ChunkLevel level, int id) {
        super(EntityType.CAMEL, level, id);
        moveControl = new CamelMoveControl(this);
        if (getNavigation() instanceof GroundPathNavigation g) {
            g.setCanFloat(true);
            g.setCanWalkOverFences(true);
        }
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Camel> makeBrain() {
        Brain<Camel> b = new Brain<>(java.util.List.of(MemoryModuleType.IS_PANICKING, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.WALK_TARGET,
                MemoryModuleType.LOOK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryModuleType.GAZE_COOLDOWN_TICKS, MemoryModuleType.IS_TEMPTED,
                MemoryModuleType.BREED_TARGET, MemoryModuleType.NEAREST_VISIBLE_ADULT),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY, SensorType.CAMEL_TEMPTATIONS, SensorType.NEAREST_ADULT));
        return CamelAi.makeBrain(b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Camel> camelBrain() { return (Brain<Camel>) brain; }

    @Override
    protected void registerGoals() { }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.get(Attribute.MAX_HEALTH).setBaseValue(32.0);
        map.get(Attribute.MOVEMENT_SPEED).setBaseValue(0.09F);
        map.get(Attribute.JUMP_STRENGTH).setBaseValue(0.42F);
        map.get(Attribute.STEP_HEIGHT).setBaseValue(1.5);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_DASH, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_LAST_POSE_CHANGE_TICK, SetEntityData.SERIALIZER_LONG, 0L);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("LastPoseTick", (Long) entityData.get(DATA_LAST_POSE_CHANGE_TICK));
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        long t = tag.getLong("LastPoseTick");
        if (t < 0L) setPose(POSE_SITTING);
        resetLastPoseChangeTick(t);
    }

    @Override
    public GroupData finalizeSpawn(RandomSource r, DifficultyInstance difficulty, GroupData data) {
        CamelAi.initMemories(this, r);
        resetLastPoseChangeTickToFullStand(level.gameTime());
        return super.finalizeSpawn(r, difficulty, data);
    }

    @Override protected float babyScale() { return BABY_SCALE; }

    @Override protected boolean canEatGrass() { return false; }

    @Override public boolean isTamed() { return true; }

    @Override public int getMaxHeadYRot() { return MAX_HEAD_Y_ROT; }

    @Override
    protected void customServerAiStep() {
        camelBrain().tick(level, this);
        CamelAi.updateActivity(this);
        super.customServerAiStep();
    }

    @Override
    protected void aiStep() {
        super.aiStep();
        if (isDashing() && dashCooldown < 50 && onGround()) setDashing(false);
        if (dashCooldown > 0) {
            dashCooldown--;
            if (dashCooldown == 0) level.playSound(null, blockPosition().x() + 0.5, blockPosition().y() + 0.5, blockPosition().z() + 0.5, "entity.camel.dash_ready", "neutral", 1.0F, 1.0F);
        }
        if (isCamelSitting() && isInWater()) standUpInstantly();
    }

    @Override
    protected void travel(float inX, float inY, float inZ) {
        if (refuseToMove() && onGround()) {
            setDeltaMovement(0.0, vy(), 0.0);
            inX = 0.0F;
            inZ = 0.0F;
        }
        super.travel(inX, inY, inZ);
    }

    public boolean refuseToMove() { return isCamelSitting() || isInPoseTransition(); }

    @Override public boolean canMate(Animal other) { return other != this && other instanceof Camel c && canParent() && c.canParent(); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Camel(level, level.nextEntityId()); }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:camel_food"); }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (p.sneaking() && !isBaby()) return true;   // TODO(인벤토리): 안장 칸 열기
        if (isFood(held)) return fedFood(p, hand, held);
        if (getPassengerCount() < 2 && !isBaby()) doPlayerRide(p);
        return true;
    }

    private int getPassengerCount() { return passengers().size(); }

    @Override
    protected boolean handleEating(Player p, ItemStack s) {
        if (!isFood(s)) return false;
        boolean canHeal = health() < maxHealth();
        if (canHeal) heal(2.0F);
        boolean canAge = isTamed() && getAge() == 0 && canFallInLove();
        if (canAge) setInLove(p);
        boolean baby = isBaby();
        if (baby) ageUp(10);
        if (!canHeal && !canAge && !baby) return false;
        if (!isSilent()) {
            String eat = getEatingSound();
            if (eat != null) level.playSound(null, x(), y(), z(), eat, soundSource(), 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
        }
        return true;
    }

    // ------------------------------------------------------------------ 자세 전환(앉기/서기)

    public boolean isDashing() { return (Boolean) entityData.get(DATA_DASH); }

    public void setDashing(boolean v) { entityData.set(DATA_DASH, v); }

    /** 마지막 자세가 바뀐 틱이 음수면 앉은 상태(양수면 선 상태). */
    public boolean isCamelSitting() { return (Long) entityData.get(DATA_LAST_POSE_CHANGE_TICK) < 0L; }

    public boolean isInPoseTransition() { return getPoseTime() < (isCamelSitting() ? SITTING_TO_STANDING_TICKS : STANDING_TO_SITTING_TICKS); }

    public boolean isVisuallySittingDown() { return isCamelSitting() && getPoseTime() < SITTING_TO_STANDING_TICKS && getPoseTime() >= 0L; }

    /** TODO(자세 크기): 바뀔 자세로 들어갈 공간이 있는지 — 자세별 몸 크기가 없어 항상 참. */
    public boolean canChangePose() { return true; }

    public void sitDown() {
        if (!isCamelSitting()) {
            playSound("entity.camel.sit", 1.0F, 1.0F);
            setPose(POSE_SITTING);
            resetLastPoseChangeTick(-level.gameTime());
        }
    }

    public void standUp() {
        if (isCamelSitting()) {
            playSound("entity.camel.stand", 1.0F, 1.0F);
            setPose(POSE_STANDING);
            resetLastPoseChangeTick(level.gameTime());
        }
    }

    public void standUpInstantly() {
        setPose(POSE_STANDING);
        resetLastPoseChangeTickToFullStand(level.gameTime());
    }

    private void resetLastPoseChangeTick(long t) { entityData.set(DATA_LAST_POSE_CHANGE_TICK, t); }

    private void resetLastPoseChangeTickToFullStand(long gameTime) { resetLastPoseChangeTick(Math.max(0L, gameTime - STANDING_TO_SITTING_TICKS - 1L)); }

    public long getPoseTime() { return level.gameTime() - Math.abs((Long) entityData.get(DATA_LAST_POSE_CHANGE_TICK)); }

    @Override
    protected void actuallyHurt(Damage source, float amount) {
        standUpInstantly();
        super.actuallyHurt(source, amount);
    }

    // ------------------------------------------------------------------ 소리

    @Override public String getAmbientSound() { return "entity.camel.ambient"; }

    @Override protected String getDeathSound() { return "entity.camel.death"; }

    @Override protected String getHurtSound(Damage source) { return "entity.camel.hurt"; }

    @Override protected String getEatingSound() { return "entity.camel.eat"; }

    /** Camel.checkLeashDistance: 6칸 넘게 당기면 앉아 있다가 일어난다(AbstractHorse 의 먹기 해제는 부르지 않는다). */
    @Override
    protected boolean checkLeashDistance(Rider holder, float dist) {
        if (dist > 6.0F && isCamelSitting() && !isInPoseTransition() && canChangePose()) standUp();
        return true;
    }

    private static class CamelMoveControl extends MoveControl {
        private final Camel camel;

        CamelMoveControl(Camel camel) {
            super(camel);
            this.camel = camel;
        }

        @Override
        public void tick() {
            if (operation == Operation.MOVE_TO && !camel.isLeashed() && camel.isCamelSitting() && !camel.isInPoseTransition() && camel.canChangePose()) camel.standUp();
            super.tick();
        }
    }
}
