// 바닐라 Raider(+PatrollingMonster): 습격 파도(wave)·참여 여부, 순찰 지도자/목표/순찰 중 상태와 장거리 순찰 목표(LongDistancePatrolGoal).
// 습격(Raid) 소속: currentRaid·ticksOutsideRaid, 죽으면 습격에서 빠지고 처치한 플레이어를 마을의 영웅 후보로.
// TODO(습격): Raider 의 목표들(ObtainRaidLeaderBannerGoal·PathfindToRaidGoal·RaiderMoveThroughVillageGoal·RaiderCelebration).
//   TODO(저장): 바닐라는 RaidId 를 저장해 다시 불러올 때 습격에 다시 넣는다(raids.dat 저장이 없어 생략).
package org.domi.furnace.server.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.spawn.SpawnReason;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public abstract class Raider extends Monster {
    public static final int DATA_IS_CELEBRATING = 16;
    private int wave;
    private boolean canJoinRaid;
    private boolean patrolLeader;
    private Pos patrolTarget;
    private boolean patrolling;
    /** finalizeSpawn 에 넘어오지 않는 바닐라 MobSpawnType 대신(스포너가 finalizeSpawn 직전에 정한다). 기본 NATURAL. */
    private SpawnReason finalizeReason = SpawnReason.NATURAL;

    protected Raider(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IS_CELEBRATING, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    /** PatrollingMonster.registerGoals: 장거리 순찰(4). 하위 클래스는 super.registerGoals() 를 먼저 부른다. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(4, new LongDistancePatrolGoal(this, 0.7, 0.595));
        // TODO(습격): Raider.registerGoals 의 ObtainRaidLeaderBannerGoal(1)·PathfindToRaidGoal(3)·RaiderMoveThroughVillageGoal(4)·RaiderCelebration(5)
    }

    public void setFinalizeReason(SpawnReason r) { finalizeReason = r; }

    /**
     * PatrollingMonster.finalizeSpawn → Raider.finalizeSpawn → Mob.finalizeSpawn.
     * 순찰·이벤트·구조물 스폰이 아니면 레벨 난수 nextFloat() &lt; 0.06 이고 지도자가 될 수 있으면 지도자(난수는 canBeLeader 보다 먼저 소비).
     */
    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        SpawnReason reason = finalizeReason;
        finalizeReason = SpawnReason.NATURAL;
        if (reason != SpawnReason.PATROL && reason != SpawnReason.EVENT && reason != SpawnReason.STRUCTURE
                && r.nextFloat() < 0.06F && canBeLeader()) patrolLeader = true;
        if (patrolLeader) {
            setItemSlot(EquipmentSlot.HEAD, leaderBanner());
            setDropChance(EquipmentSlot.HEAD, 2.0F);
        }
        if (reason == SpawnReason.PATROL) patrolling = true;
        // Raider.finalizeSpawn
        setCanJoinRaid(type() != EntityType.WITCH || reason != SpawnReason.NATURAL);
        super.finalizeSpawn(r, difficulty);
    }

    /**
     * Raid.getLeaderBannerInstance: 불길한 깃발(흰 깃발 + 무늬 8겹, 추가 툴팁 숨김, 이름 block.minecraft.ominous_banner, 희귀도 uncommon).
     * 가정: 1.21.1 은 이름에 금색 스타일 대신 rarity uncommon 을 쓴다고 보았다(바닐라 jar 대조 필요).
     */
    public static ItemStack leaderBanner() {
        ItemStack s = stackOf("minecraft:white_banner");
        if (s.isEmpty()) return s;
        String[][] layers = {{"rhombus", "cyan"}, {"stripe_bottom", "light_gray"}, {"stripe_center", "gray"}, {"border", "light_gray"},
            {"stripe_middle", "black"}, {"half_horizontal", "light_gray"}, {"circle", "light_gray"}, {"border", "black"}};
        List<Object> list = new java.util.ArrayList<>();
        for (String[] l : layers) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("pattern", "minecraft:" + l[0]);
            m.put("color", l[1]);
            list.add(m);
        }
        s = ItemTools.with(s, "minecraft:banner_patterns", list);
        s = ItemTools.with(s, "minecraft:hide_additional_tooltip", Map.of());
        s = ItemTools.with(s, "minecraft:item_name", "{\"translate\":\"block.minecraft.ominous_banner\"}");
        return ItemTools.with(s, "minecraft:rarity", "uncommon");
    }

    /** PatrollingMonster.canBeLeader(약탈수·마녀는 거짓으로 덮어쓴다). */
    public boolean canBeLeader() { return true; }

    /** PatrollingMonster.canJoinPatrol. TODO(습격): 바닐라 Raider 는 습격 중이 아닐 때만. */
    public boolean canJoinPatrol() { return !hasActiveRaid(); }

    public Pos getPatrolTarget() { return patrolTarget; }

    public void setPatrolTarget(Pos p) { patrolTarget = p; patrolling = true; }

    public boolean hasPatrolTarget() { return patrolTarget != null; }

    public void setPatrolling(boolean v) { patrolling = v; }

    /** PatrollingMonster.findPatrolTarget: 제 위치에서 ±500 안(엔티티 난수 x 다음 z). */
    public void findPatrolTarget() {
        Pos p = blockPosition();
        int dx = -500 + random.nextInt(1000), dz = -500 + random.nextInt(1000);
        patrolTarget = new Pos(p.x() + dx, p.y(), p.z() + dz);
        patrolling = true;
    }

    /** Raider.applyRaidBuffs(level, wave, isCaptain): 각 종류가 파도에 따라 장비를 준다(습격 구현 때 사용). */
    public abstract void applyRaidBuffs(int wave, boolean captain);

    private org.domi.furnace.server.world.raid.Raid currentRaid;
    private int ticksOutsideRaid;

    public org.domi.furnace.server.world.raid.Raid getCurrentRaid() { return currentRaid; }

    public void setCurrentRaid(org.domi.furnace.server.world.raid.Raid r) { currentRaid = r; }

    public int getTicksOutsideRaid() { return ticksOutsideRaid; }

    public void setTicksOutsideRaid(int t) { ticksOutsideRaid = t; }

    /** hasActiveRaid: 습격 소속이고 그 습격이 활성. */
    public boolean hasActiveRaid() { return currentRaid != null && currentRaid.isActive(); }

    /** Raider.die: 습격에서 대장 자리·소속을 지우고, 처치자가 플레이어면 영웅 후보. */
    @Override
    public void die(Damage source) {
        var raid = currentRaid;
        if (raid != null) {
            if (isPatrolLeader()) raid.removeLeader(getWave());
            if (lastHurtByPlayer != null && lastHurtByPlayerTime > 0) raid.addHeroOfTheVillage(lastHurtByPlayer);
            raid.removeFromRaid(this, false);
        }
        super.die(source);
    }

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
        // 1.20.5+ NbtUtils.writeBlockPos: 정수 배열 [x, y, z], 키 patrol_target
        if (patrolTarget != null) tag.putIntArray("patrol_target", new int[] {patrolTarget.x(), patrolTarget.y(), patrolTarget.z()});
        tag.putBoolean("PatrolLeader", patrolLeader);
        tag.putBoolean("Patrolling", patrolling);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        wave = tag.getIntOr0("Wave");
        canJoinRaid = tag.getBoolean("CanJoinRaid");
        patrolTarget = tag.tags().get("patrol_target") instanceof int[] a && a.length == 3 ? new Pos(a[0], a[1], a[2]) : null;
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

    /** PatrollingMonster.LongDistancePatrolGoal: 순찰 목표 쪽으로 10칸씩(옆으로 비스듬히) 걷고, 지도자는 동료 목표를 맞춘다. */
    public static class LongDistancePatrolGoal extends org.domi.furnace.server.entity.ai.Goal {
        private static final int NAVIGATION_FAILED_COOLDOWN = 200;
        private final Raider mob;
        private final double speedModifier, leaderSpeedModifier;
        private long cooldownUntil;

        public LongDistancePatrolGoal(Raider mob, double speed, double leaderSpeed) {
            this.mob = mob;
            this.speedModifier = speed;
            this.leaderSpeedModifier = leaderSpeed;
            this.cooldownUntil = -1L;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            boolean cooling = mob.level().gameTime() < cooldownUntil;
            return mob.isPatrolling() && mob.getTarget() == null && !mob.hasControllingPassenger() && mob.hasPatrolTarget() && !cooling;
        }

        @Override
        public void tick() {
            boolean leader = mob.isPatrolLeader();
            PathNavigation nav = mob.getNavigation();
            if (!nav.isDone()) return;
            List<Raider> companions = findPatrolCompanions();
            if (mob.isPatrolling() && companions.isEmpty()) {
                mob.setPatrolling(false);
                return;
            }
            Pos t = mob.getPatrolTarget();
            // Vec3i.closerToCenterThan(position, 10)
            double cx = t.x() + 0.5 - mob.x(), cy = t.y() + 0.5 - mob.y(), cz = t.z() + 0.5 - mob.z();
            if (leader && cx * cx + cy * cy + cz * cz < 100.0) {
                mob.findPatrolTarget();
                return;
            }
            // v = atBottomCenterOf(target); d = pos - v; v = d.yRot(90.0F, 라디안 그대로).scale(0.4) + v; w = (v - pos).normalize() * 10 + pos
            double vx = t.x() + 0.5, vy = t.y(), vz = t.z() + 0.5;
            double dx = mob.x() - vx, dy = mob.y() - vy, dz = mob.z() - vz;
            float c = org.domi.furnace.server.entity.ai.control.Mth.cos(90.0F), s = org.domi.furnace.server.entity.ai.control.Mth.sin(90.0F);
            double rx = dx * c + dz * s, rz = dz * c - dx * s;
            vx = rx * 0.4 + vx;
            vy = dy * 0.4 + vy;
            vz = rz * 0.4 + vz;
            double ux = vx - mob.x(), uy = vy - mob.y(), uz = vz - mob.z();
            double len = Math.sqrt(ux * ux + uy * uy + uz * uz);
            if (len < 1.0E-5F) { ux = 0; uz = 0; } else { ux /= len; uz /= len; }
            int bx = (int) Math.floor(ux * 10.0 + mob.x()), bz = (int) Math.floor(uz * 10.0 + mob.z());
            int by = mob.level().getMotionBlockingNoLeavesHeight(bx, bz);
            if (!nav.moveTo(bx, by, bz, leader ? leaderSpeedModifier : speedModifier)) {
                moveRandomly();
                cooldownUntil = mob.level().gameTime() + NAVIGATION_FAILED_COOLDOWN;
            } else if (leader) {
                Pos np = new Pos(bx, by, bz);
                for (Raider r : companions) r.setPatrolTarget(np);
            }
        }

        private List<Raider> findPatrolCompanions() {
            return mob.level().getEntitiesOfClass(Raider.class, mob.boundingBox().inflate(16.0, 16.0, 16.0), r -> r.canJoinPatrol() && r != mob);
        }

        private boolean moveRandomly() {
            var r = mob.random();
            Pos p = mob.blockPosition();
            int x = p.x() - 8 + r.nextInt(16), z = p.z() - 8 + r.nextInt(16);
            return mob.getNavigation().moveTo(x, mob.level().getMotionBlockingNoLeavesHeight(x, z), z, speedModifier);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) { return !hasActiveRaid() && (!patrolling || distSqr > 16384.0); }
}
