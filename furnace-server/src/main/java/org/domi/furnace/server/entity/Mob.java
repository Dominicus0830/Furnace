// 바닐라 Mob: 몹 플래그(NoAI·왼손잡이·공격 중)와 영속(PersistenceRequired). AI·길찾기는 아직 없다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
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

    protected PathNavigation createNavigation(ChunkLevel level) { return new GroundPathNavigation(this, level); }

    public PathNavigation getNavigation() { return navigation; }

    public float getPathfindingMalus(PathType type) {
        Float f = pathfindingMalus.get(type);
        return f == null ? type.malus() : f;
    }

    public void setPathfindingMalus(PathType type, float malus) { pathfindingMalus.put(type, malus); }

    /** 서 있는 칸으로 칠 수 있는 유체인가(canStandOnFluid) — 기본 아니오. */
    public boolean canStandOnFluid(int fluid) { return false; }

    /** 공격 대상이 있는가 — TODO(대상): 대상 시스템(플레이어도 대상)이 생기면. */
    protected boolean hasTarget() { return false; }

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

    @Override
    protected boolean isEffectiveAi() { return !isNoAi(); }

    /** Mob.customServerAiStep(vanilla fm — serverAiStep): 감지 → 목표 선택(2틱에 한 번만 전체) → 내비게이션 → 몹 틱 → 제어 순. */
    @Override
    protected final void serverAiStep() {
        noActionTime++;
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
