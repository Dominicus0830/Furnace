// 바닐라 Mob: 몹 플래그(NoAI·왼손잡이·공격 중)와 영속(PersistenceRequired). AI·길찾기는 아직 없다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.entity.ai.GoalSelector;
import org.domi.furnace.server.entity.ai.control.JumpControl;
import org.domi.furnace.server.entity.ai.control.LookControl;
import org.domi.furnace.server.entity.ai.control.MoveControl;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class Mob extends LivingEntity {

    /** Mob.DATA_MOB_FLAGS_ID = 15: 1 NoAI, 2 왼손잡이, 4 공격 중. */
    public static final int DATA_MOB_FLAGS = 15;

    protected boolean persistenceRequired;
    /** xpReward: 종류별 기본 경험치(적대 몹 5, 동물은 별도 규칙, 주민 0). */
    protected int xpReward;
    protected final GoalSelector goalSelector = new GoalSelector();
    protected final GoalSelector targetSelector = new GoalSelector();
    protected LookControl lookControl;
    protected MoveControl moveControl;
    protected JumpControl jumpControl;
    protected PathNavigation navigation;
    private final java.util.EnumMap<PathType, Float> pathfindingMalus = new java.util.EnumMap<>(PathType.class);

    protected Mob(EntityType type, ChunkLevel level, int id) {
        super(type, level, id);
        lookControl = new LookControl(this);
        moveControl = new MoveControl(this);
        jumpControl = new JumpControl(this);
        navigation = createNavigation(level);
        registerGoals();
    }

    /** 종류별 목표 등록(vanilla registerGoals). 하위 클래스 필드는 아직 초기화 전이므로 쓰지 말 것. */
    protected void registerGoals() {}

    @Override
    protected void createAttributes(org.domi.furnace.server.attribute.AttributeMap map) {   // Mob.createMobAttributes
        super.createAttributes(map);
        map.add(Attribute.FOLLOW_RANGE, 16.0);
    }

    protected PathNavigation createNavigation(ChunkLevel level) { return new GroundPathNavigation(this, level); }

    public PathNavigation getNavigation() { return navigation; }

    public float getPathfindingMalus(PathType type) {
        Float f = pathfindingMalus.get(type);
        return f == null ? type.malus() : f;
    }

    public void setPathfindingMalus(PathType type, float malus) { pathfindingMalus.put(type, malus); }

    /** 서 있는 칸으로 칠 수 있는 유체인가(canStandOnFluid) — 기본 아니오. */
    public boolean canStandOnFluid(int fluid) { return false; }

    // ------------------------------------------------------------------ 제한 구역(restrictTo)

    private Pos restrictCenter = Pos.ZERO;
    private float restrictRadius = -1.0F;

    public boolean isWithinRestriction(Pos pos) {
        if (restrictRadius == -1.0F) return true;
        long dx = pos.x() - restrictCenter.x(), dy = pos.y() - restrictCenter.y(), dz = pos.z() - restrictCenter.z();
        return (double) (dx * dx + dy * dy + dz * dz) < (double) (restrictRadius * restrictRadius);
    }

    public void restrictTo(Pos center, int radius) {
        restrictCenter = center;
        restrictRadius = radius;
    }

    public Pos getRestrictCenter() { return restrictCenter; }

    public float getRestrictRadius() { return restrictRadius; }

    public void clearRestriction() { restrictRadius = -1.0F; }

    public boolean hasRestriction() { return restrictRadius != -1.0F; }

    // ------------------------------------------------------------------ 대상·전투

    protected Targetable target;
    private final java.util.Set<Integer> seenTargets = new java.util.HashSet<>(), unseenTargets = new java.util.HashSet<>();

    public Targetable getTarget() { return target; }

    public void setTarget(Targetable t) { target = t; }

    protected boolean hasTarget() { return target != null; }

    /** canAttackType: 이 몹이 그 종류를 때릴 수 있는가(기본 모두, 가스트 같은 것만 제외). */
    public boolean canAttackType(Targetable t) { return true; }

    public boolean isAggressive() { return ((Byte) entityData.get(DATA_MOB_FLAGS) & 4) != 0; }

    public void setAggressive(boolean v) {
        byte b = (Byte) entityData.get(DATA_MOB_FLAGS);
        entityData.set(DATA_MOB_FLAGS, (byte) (v ? b | 4 : b & ~4));
    }

    /** Sensing.hasLineOfSight: 틱마다 비우는 캐시. */
    public boolean hasLineOfSight(Targetable t) {
        if (seenTargets.contains(t.id())) return true;
        if (unseenTargets.contains(t.id())) return false;
        boolean seen = rayHasLineOfSight(t);
        (seen ? seenTargets : unseenTargets).add(t.id());
        return seen;
    }

    /** LivingEntity.hasLineOfSight: 눈에서 눈까지(128칸 안) 충돌 모양에 안 막힌다. */
    private boolean rayHasLineOfSight(Targetable t) {
        double dx = t.x() - x(), dy = t.eyeY() - eyeY(), dz = t.z() - z();
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > 128.0) return false;
        return org.domi.furnace.server.block.Clip.clipCollider(level, x(), eyeY(), z(), t.x(), t.eyeY(), t.z()).miss();
    }

    private static final double MELEE_REACH = Math.sqrt(2.04F) - 0.6F;

    /** isWithinMeleeAttackRange: 내 몸통을 가로로 (루트 2.04 − 0.6) 넓힌 상자가 상대 몸통과 겹친다. */
    public boolean isWithinMeleeAttackRange(Targetable t) {
        return boundingBox().inflate(MELEE_REACH, 0.0, MELEE_REACH).intersects(t.boundingBox());
    }

    /** Mob.doHurtTarget: 공격력 속성만큼 때리고, 맞으면 밀치기(attack_knockback)·되받는 감속·마지막으로 때린 대상 기록. TODO(인챈트 효과·무기 후처리). */
    public boolean doHurtTarget(Targetable t) {
        float damage = (float) attributeValue(Attribute.ATTACK_DAMAGE);
        boolean hurt = t.hurtByMob(new Damage("mob_attack", false, false, false), damage, this);
        if (hurt) {
            float kb = (float) attributeValue(Attribute.ATTACK_KNOCKBACK);
            if (kb > 0.0F) {
                t.knockback(kb * 0.5F, org.domi.furnace.server.entity.ai.control.Mth.sin(yRot() * (float) (Math.PI / 180.0)), -org.domi.furnace.server.entity.ai.control.Mth.cos(yRot() * (float) (Math.PI / 180.0)));
                setDeltaMovement(vx() * 0.6, vy(), vz() * 0.6);
            }
            setLastHurtMob(t);
            doPostHurtEffects(t);
        }
        return hurt;
    }

    protected void doPostHurtEffects(Targetable t) {}

    /** Mob.getMaxFallDistance: 대상이 없으면 3, 있으면 체력과 난이도에 따라. */
    public int getMaxFallDistance() {
        if (!hasTarget()) return (int) Math.floor(3.0F);
        int i = (int) (health() - maxHealth() * 0.33F);
        i -= (3 - level.difficulty()) * 4;
        if (i < 0) i = 0;
        return (int) Math.floor(i + 3.0F);
    }

    public boolean isInLiquid() { return isInWaterOrBubble() || isInLava(); }

    public boolean isPassenger() { return false; }

    public LookControl getLookControl() { return lookControl; }

    public MoveControl getMoveControl() { return moveControl; }

    public JumpControl getJumpControl() { return jumpControl; }

    public GoalSelector goalSelector() { return goalSelector; }

    public GoalSelector targetSelector() { return targetSelector; }

    public int getHeadRotSpeed() { return 10; }

    public int getMaxHeadXRot() { return 40; }

    public int getMaxHeadYRot() { return 75; }

    /** 길찾기가 진행 중인가 — 아직 내비게이션이 없다. */
    public boolean navigationIsInProgress() { return navigation.isInProgress(); }

    @Override
    public void setSpeed(float v) {
        super.setSpeed(v);
        setZza(v);
    }

    /** 가끔(1000 중 ambientSoundTime 미만) 주변 소리를 낸다. */
    protected int ambientSoundTime;

    /** 테스트용: 기본 경험치. */
    public int xpRewardForTest() { return xpReward; }

    public int getAmbientSoundInterval() { return 80; }

    protected void resetAmbientSoundTime() { ambientSoundTime = -getAmbientSoundInterval(); }

    @Override
    public void baseTick() {
        super.baseTick();
        if (isAlive() && random.nextInt(1000) < ambientSoundTime++) {
            resetAmbientSoundTime();
            makeSound(getAmbientSound());
        }
    }

    @Override
    protected void playHurtSound(Damage source) {
        resetAmbientSoundTime();
        super.playHurtSound(source);
    }

    @Override
    protected boolean isEffectiveAi() { return !isNoAi(); }

    /** Mob.customServerAiStep(vanilla fm — serverAiStep): 감지 → 목표 선택(2틱에 한 번만 전체) → 내비게이션 → 몹 틱 → 제어 순. */
    @Override
    protected final void serverAiStep() {
        noActionTime++;
        seenTargets.clear();
        unseenTargets.clear();
        int i = tickCount + id();
        if (i % 2 != 0 && tickCount > 1) {
            targetSelector.tickRunningGoals(false);
            goalSelector.tickRunningGoals(false);
        } else {
            targetSelector.tick();
            goalSelector.tick();
        }
        navigation.tick();
        customServerAiStep();
        moveControl.tick();
        lookControl.tick();
        jumpControl.tick();
        // TODO(디버그 정보): sendDebugPackets
    }

    protected void customServerAiStep() {}

    /** Mob.getExperienceReward: xpReward 가 있으면 장비(드롭 확률 1 이하)마다 1+nextInt(3) 추가. TODO(장비): 몹 장비 칸이 생기면. */
    @Override
    protected int getExperienceReward() { return xpReward; }


    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_MOB_FLAGS, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    public boolean isNoAi() { return ((Byte) entityData.get(DATA_MOB_FLAGS) & 1) != 0; }

    public void setNoAi(boolean v) {
        byte b = (Byte) entityData.get(DATA_MOB_FLAGS);
        entityData.set(DATA_MOB_FLAGS, (byte) (v ? b | 1 : b & ~1));
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PersistenceRequired", persistenceRequired);
        tag.putBoolean("LeftHanded", ((Byte) entityData.get(DATA_MOB_FLAGS) & 2) != 0);
        if (isNoAi()) tag.putBoolean("NoAI", true);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        persistenceRequired = tag.getBoolean("PersistenceRequired");
        byte b = (byte) ((tag.getBoolean("LeftHanded") ? 2 : 0));
        entityData.set(DATA_MOB_FLAGS, b);
        setNoAi(tag.getBoolean("NoAI"));
    }
}
