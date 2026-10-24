// 바닐라 Raider(+PatrollingMonster) 의 뼈대: 습격 파도(wave)·참여 여부·순찰 지도자/목표를 저장한다. TODO: 습격(Raid) 시스템과 순찰 목표들(배너·마을 찾기·문 열기·축하).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class Raider extends Monster {
    public static final int DATA_IS_CELEBRATING = 16;
    private int wave;
    private boolean canJoinRaid;
    private boolean patrolLeader;
    private int[] patrolTarget;
    private boolean patrolling;

    protected Raider(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IS_CELEBRATING, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    /** Raider.applyRaidBuffs(level, wave, isCaptain): 각 종류가 파도에 따라 장비를 준다(습격 구현 때 사용). */
    public abstract void applyRaidBuffs(int wave, boolean captain);

    public boolean hasActiveRaid() { return false; }

    public boolean isCelebrating() { return (Boolean) entityData.get(DATA_IS_CELEBRATING); }

    public void setCelebrating(boolean v) { entityData.set(DATA_IS_CELEBRATING, v); }

    public int getWave() { return wave; }

    public void setWave(int w) { wave = w; }

    public boolean canJoinRaid() { return canJoinRaid; }

    public void setCanJoinRaid(boolean v) { canJoinRaid = v; }

    public boolean isPatrolLeader() { return patrolLeader; }

    public void setPatrolLeader(boolean v) { patrolLeader = v; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Wave", wave);
        tag.putBoolean("CanJoinRaid", canJoinRaid);
        if (patrolTarget != null) {
            NbtCompound t = new NbtCompound();
            t.putInt("X", patrolTarget[0]);
            t.putInt("Y", patrolTarget[1]);
            t.putInt("Z", patrolTarget[2]);
            tag.putCompound("PatrolTarget", t);
        }
        tag.putBoolean("PatrolLeader", patrolLeader);
        tag.putBoolean("Patrolling", patrolling);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        wave = tag.getIntOr0("Wave");
        canJoinRaid = tag.getBoolean("CanJoinRaid");
        if (tag.tags().get("PatrolTarget") instanceof NbtCompound t) patrolTarget = new int[] {t.getIntOr0("X"), t.getIntOr0("Y"), t.getIntOr0("Z")};
        patrolLeader = tag.getBoolean("PatrolLeader");
        patrolling = tag.getBoolean("Patrolling");
    }

    public boolean isPatrolling() { return patrolling; }

    /** Raider.updateNoActionTime: 밝기와 무관하게 틱마다 2 쌓는다(습격자는 빨리 사라진다). */
    @Override
    protected void updateNoActionTime() { noActionTime += 2; }

    /** Raider.HoldGroundAttackGoal: 순찰 중 적을 보면 근처 습격자에게 알리고, 멀면 제자리에서 노려보다 가까워지면 공격 태세. (습격 없을 때만) */
    public static class HoldGroundAttackGoal extends org.domi.furnace.server.entity.ai.Goal {
        private final Raider mob;
        private final float hostileRadiusSqr;
        private final org.domi.furnace.server.entity.ai.targeting.TargetingConditions shoutTargeting =
                org.domi.furnace.server.entity.ai.targeting.TargetingConditions.forCombat().range(8.0).ignoreLineOfSight().ignoreInvisibilityTesting();

        public HoldGroundAttackGoal(Raider mob, float radius) {
            this.mob = mob;
            this.hostileRadiusSqr = radius * radius;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return !mob.hasActiveRaid() && mob.isPatrolling() && mob.getTarget() != null && !mob.isAggressive();
        }

        private java.util.List<Raider> nearbyRaiders() {
            var box = mob.boundingBox().inflate(8.0, 8.0, 8.0);
            var out = new java.util.ArrayList<Raider>();
            for (Raider r : mob.level().getEntitiesOfClass(Raider.class, box, x -> true)) {
                if (r != mob && shoutTargeting.test(mob, r)) out.add(r);
            }
            return out;
        }

        @Override
        public void start() {
            mob.getNavigation().stop();
            for (Raider r : nearbyRaiders()) r.setTarget(mob.getTarget());
        }

        @Override
        public void stop() {
            Targetable t = mob.getTarget();
            if (t != null) {
                for (Raider r : nearbyRaiders()) {
                    r.setTarget(t);
                    r.setAggressive(true);
                }
                mob.setAggressive(true);
            }
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            Targetable t = mob.getTarget();
            if (t != null) {
                double dx = t.x() - mob.x(), dy = t.y() - mob.y(), dz = t.z() - mob.z();
                if (dx * dx + dy * dy + dz * dz > hostileRadiusSqr) {
                    mob.getLookControl().setLookAt(t.x(), t.eyeY(), t.z(), 30.0F, 30.0F);
                    if (mob.random().nextInt(50) == 0) mob.playAmbientSound();
                } else {
                    mob.setAggressive(true);
                }
            }
        }
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) { return !hasActiveRaid() && (!patrolling || distSqr > 16384.0); }
}
