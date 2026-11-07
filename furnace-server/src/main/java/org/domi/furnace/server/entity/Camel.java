// 바닐라 Camel: 브레인으로 움직이는 말과 동물. 앉았다 일어나는 자세 전환(마지막 자세 바뀐 틱)과 선인장 먹이로 번식한다.
// 두 명까지 탄다(앞·뒤 자리, 동물 승객은 0.2 앞으로). 앉은 자세는 몸 높이 -1.43(눈 0.845). 웅크려 우클릭하면 안장 칸 창.
// 머리 회전 고정(clampHeadRotationToBody)은 클라이언트 표시용이라 생략.
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
    /** SITTING_DIMENSIONS: scalable(1.7, 2.375 - 1.43).withEyeHeight(0.845). */
    private static final float SITTING_HEIGHT = 2.375F - 1.43F, SITTING_EYE = 0.845F;
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

    /** Camel.getDefaultDimensions(pose): 앉은 자세면 SITTING_DIMENSIONS × 나이 배율. */
    @Override
    protected void refreshBabyDimensions() {
        float s = getAgeScale();
        if (getPose() == POSE_SITTING) setDimensions(EntityType.CAMEL.width() * s, SITTING_HEIGHT * s, SITTING_EYE * s);
        else super.refreshBabyDimensions();
    }

    /** 자세가 바뀌면 몸 크기를 다시 잰다(Entity.onSyncedDataUpdated(DATA_POSE) → refreshDimensions). */
    @Override
    public void setPose(int pose) {
        super.setPose(pose);
        refreshBabyDimensions();
    }

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
        if (isDashing() && dashCooldown < 50 && (onGround() || isInWaterOrBubble() || isInLava() || isPassenger())) setDashing(false);
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
        if (p.sneaking() && !isBaby()) {
            openCustomInventoryScreen(p);
            return true;
        }
        if (isFood(held)) return fedFood(p, hand, held);
        if (getPassengerCount() < 2 && !isBaby()) doPlayerRide(p);
        return true;
    }

    private int getPassengerCount() { return passengers().size(); }

    /** Camel.openCustomInventoryScreen: 길들임·승객 조건 없이 바로 안장 칸 창. */
    @Override
    public void openCustomInventoryScreen(Player p) {
        Player.InventoryHost host = p.inventoryHost();
        if (host != null) host.openHorseInventory(this, inventoryContainer());
    }

    /** Camel.canAddPassenger: 승객이 둘 이하면(바닐라 그대로 size() <= 2). */
    @Override
    protected boolean canAddPassenger(Rider r) { return passengers().size() <= 2; }

    /**
     * Camel.getPassengerAttachmentPoint: 앞자리 z 0.5, 둘이 타면 뒷자리 -0.7, 동물 승객은 +0.2. 높이는 자세 전환 애니메이션을 따른다
     * (사라진 낙타는 0.01). 내 몸 방향으로 돌린다.
     */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Rider r, float scale) {
        var ps = passengers();
        int i = Math.max(ps.indexOf(r), 0);
        boolean first = i == 0;
        float z = 0.5F;
        float y = (float) (isRemoved() ? 0.01F : bodyAnchorAnimationYOffset(first, 0.0F, poseHeight() * scale, scale));
        if (ps.size() > 1) {
            if (!first) z = -0.7F;
            if (r instanceof Animal) z += 0.2F;
        }
        return new Vec3(0.0, y, z * scale).yRot(-yRot() * (float) (Math.PI / 180.0));
    }

    /** 지금 자세의 기본 몸 높이(배율 전). */
    private float poseHeight() { return getPose() == POSE_SITTING ? SITTING_HEIGHT : EntityType.CAMEL.height(); }

    /** Camel.getBodyAnchorAnimationYOffset(first, partialTick, dimensions, scale). */
    private double bodyAnchorAnimationYOffset(boolean first, float partialTick, float height, float scale) {
        double y = height - 0.375F * scale;
        float sittingOffset = scale * 1.43F;
        float lowered = sittingOffset - scale * 0.2F;
        float remain = sittingOffset - lowered;
        boolean transition = isInPoseTransition();
        boolean sitting = isCamelSitting();
        if (transition) {
            int total = sitting ? SITTING_TO_STANDING_TICKS : STANDING_TO_SITTING_TICKS;
            int mid;
            float f;
            if (sitting) {
                mid = 28;
                f = first ? 0.5F : 0.1F;
            } else {
                mid = first ? 24 : 32;
                f = first ? 0.6F : 0.35F;
            }
            float t = Math.clamp((float) getPoseTime() + partialTick, 0.0F, (float) total);
            boolean firstHalf = t < mid;
            float a = firstHalf ? t / mid : (t - mid) / (total - mid);
            float b = sittingOffset - f * lowered;
            y += sitting ? lerp(a, firstHalf ? sittingOffset : b, firstHalf ? b : remain)
                    : lerp(a, firstHalf ? remain - sittingOffset : remain - b, firstHalf ? remain - b : 0.0F);
        }
        if (sitting && !transition) y += remain;
        return y;
    }

    private static float lerp(float t, float a, float b) { return a + t * (b - a); }

    /** Camel.tickRidden: 앉았거나 자세가 바뀌는 중이면 몸을 돌리지 않고, 앞으로 가려 하면 앉아 있다가 일어난다. */
    @Override
    protected void tickRidden(Player p) {
        if (refuseToMove()) yRotO = yBodyRot = yHeadRot = yRot();   // getRiddenRotation = 내 회전 그대로
        else super.tickRidden(p);
        if (p.inputZza() > 0.0F && isCamelSitting() && !isInPoseTransition()) standUp();
    }

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
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.EAT);
        return true;
    }

    // ------------------------------------------------------------------ 질주(PlayerRideableJumping)

    /** Camel.canJump: 움직임을 거부하지 않고(앉음·자세 변경 중 아님) 안장이 있다. 질주 쿨다운은 조종하는 클라이언트가 가진다. */
    @Override public boolean canJump() { return !refuseToMove() && super.canJump(); }

    /**
     * Camel.handleStartJump(int): 질주 시작 — 소리, entity_action 진동(소리 다음, 질주 표시 앞), 질주 표시.
     * 질주 힘(executeRidersJump)은 조종하는 클라이언트가 계산하고 서버는 탈것 이동 패킷으로 받는다.
     */
    @Override
    public void handleStartJump(int power) {
        makeSound("entity.camel.dash");
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_ACTION);
        setDashing(true);
    }

    @Override public void handleStopJump() {}

    /** Camel.getJumpCooldown: 남은 질주 쿨다운. */
    public int getJumpCooldown() { return dashCooldown; }

    // ------------------------------------------------------------------ 자세 전환(앉기/서기)

    public boolean isDashing() { return (Boolean) entityData.get(DATA_DASH); }

    /** setDashing + onSyncedDataUpdated(DASH): 값이 바뀌면(첫 틱 제외) 질주 쿨다운이 0 일 때 55 로. 서버에서도 불려 dash_ready 소리가 난다. */
    public void setDashing(boolean v) {
        boolean changed = (Boolean) entityData.get(DATA_DASH) != v;
        entityData.set(DATA_DASH, v);
        if (changed && !firstTick) dashCooldown = dashCooldown == 0 ? DASH_COOLDOWN_TICKS : dashCooldown;
    }

    /** 마지막 자세가 바뀐 틱이 음수면 앉은 상태(양수면 선 상태). */
    public boolean isCamelSitting() { return (Long) entityData.get(DATA_LAST_POSE_CHANGE_TICK) < 0L; }

    public boolean isInPoseTransition() { return getPoseTime() < (isCamelSitting() ? SITTING_TO_STANDING_TICKS : STANDING_TO_SITTING_TICKS); }

    public boolean isVisuallySittingDown() { return isCamelSitting() && getPoseTime() < SITTING_TO_STANDING_TICKS && getPoseTime() >= 0L; }

    /** Camel.canCamelChangePose = wouldNotSuffocateAtTargetPose(앉았으면 선 자세, 아니면 앉은 자세): 그 자세 몸 상자(1e-7 줄임)에 블록 충돌이 없다. */
    public boolean canChangePose() {
        float s = getAgeScale();
        float w = EntityType.CAMEL.width() * s, h = (isCamelSitting() ? EntityType.CAMEL.height() : SITTING_HEIGHT) * s;
        double hw = w / 2.0;
        return level.noCollision(new AABB(x() - hw, y(), z() - hw, x() + hw, y() + h, z() + hw).deflate(1.0E-7));
    }

    public void sitDown() {
        if (!isCamelSitting()) {
            makeSound("entity.camel.sit");
            setPose(POSE_SITTING);
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_ACTION);
            resetLastPoseChangeTick(-level.gameTime());
        }
    }

    public void standUp() {
        if (isCamelSitting()) {
            makeSound("entity.camel.stand");
            setPose(POSE_STANDING);
            gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_ACTION);
            resetLastPoseChangeTick(level.gameTime());
        }
    }

    public void standUpInstantly() {
        setPose(POSE_STANDING);
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.ENTITY_ACTION);
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

    /** Camel.playStepSound: 모래 걸음 블록(camel_sand_step_sound_blocks) 위면 step_sand, 아니면 step. 크기 1. */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) {
        playSound(org.domi.furnace.server.block.B.isTag(state, "minecraft:camel_sand_step_sound_blocks") ? "minecraft:entity.camel.step_sand" : "minecraft:entity.camel.step", 1.0F, 1.0F);
    }
}
