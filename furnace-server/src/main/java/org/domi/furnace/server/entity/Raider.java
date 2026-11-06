// 바닐라 Raider(+PatrollingMonster): 습격 파도(wave)·참여 여부, 순찰 지도자/목표/순찰 중 상태와 장거리 순찰 목표(LongDistancePatrolGoal).
// 습격(Raid) 소속: currentRaid·ticksOutsideRaid, 죽으면 습격에서 빠지고 처치한 플레이어를 마을의 영웅 후보로.
// 목표: ObtainRaidLeaderBannerGoal(1)·PathfindToRaidGoal(3)·RaiderMoveThroughVillageGoal(4)·RaiderCelebration(5)·HoldGroundAttackGoal.
// 저장: RaidId 로 습격에 다시 들어간다. 차이: 습격이 아직 이 region 레벨에 올라오지 않았으면(중심 청크가 다른 region·미로드)
//   번호를 기억해 두고(pendingRaidId) 틱마다 다시 찾는다. 바닐라는 차원 전체 습격이 처음부터 메모리에 있다.
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
        // Raider.registerGoals
        goalSelector.addGoal(1, new ObtainRaidLeaderBannerGoal(this));
        goalSelector.addGoal(3, new PathfindToRaidGoal(this));
        goalSelector.addGoal(4, new RaiderMoveThroughVillageGoal(this, 1.05F, 1));
        goalSelector.addGoal(5, new RaiderCelebration(this));
    }

    /** Raider.getCelebrateSound. */
    public abstract String getCelebrateSound();

    /** RaidId 로 불러왔지만 아직 찾지 못한 습격 번호(-1 이면 없음). */
    private int pendingRaidId = -1;

    /** 습격이 저장소로 보류될 때(Raid.detachRaiders). */
    public void parkRaid(int id) {
        currentRaid = null;
        pendingRaidId = id;
    }

    /** 기억한 번호의 습격이 이 레벨에 있으면 들어간다(Raider.readAdditionalSaveData 의 뒷부분). 저장소가 모르는 번호면 잊는다. */
    private void resolvePendingRaid() {
        var raid = level().raids().get(pendingRaidId);
        if (raid != null) {
            pendingRaidId = -1;
            currentRaid = raid;
            raid.addWaveMob(wave, this, false);
            if (isPatrolLeader()) raid.setLeader(wave, this);
        } else if (!level().raidStore().knows(pendingRaidId)) {
            pendingRaidId = -1;
        }
    }

    /** Raider.aiStep: 참여 가능하고 습격이 없으면 20틱마다 그 자리 습격에 들어가고, 습격 중 플레이어·철 골렘을 노리면 무행동 시간을 0 으로. */
    @Override
    protected void aiStep() {
        if (isAlive()) {
            if (pendingRaidId >= 0) resolvePendingRaid();
            var raid = currentRaid;
            if (canJoinRaid()) {
                if (raid == null) {
                    if (level().gameTime() % 20L == 0L) {
                        var r1 = level().raids().getRaidAt(blockPosition());
                        if (r1 != null && org.domi.furnace.server.world.raid.Raids.canJoinRaid(this, r1)) {
                            r1.joinRaid(r1.groupsSpawned(), this, null, true);
                        }
                    }
                } else {
                    Targetable t = getTarget();
                    if (t != null && (t instanceof Player || t instanceof IronGolem)) noActionTime = 0;
                }
            }
        }
        super.aiStep();
    }

    /** Raider.hurt: 습격 중이면 맞기 전에 보스바를 갱신한다. */
    @Override
    public boolean hurt(Damage source, float amount) {
        if (hasActiveRaid()) currentRaid.updateBossbar();
        return super.hurt(source, amount);
    }

    /** Raider.isCaptain: 머리에 불길한 깃발을 쓴 순찰 지도자. */
    public boolean isCaptain() {
        ItemStack s = getItemBySlot(EquipmentSlot.HEAD);
        return !s.isEmpty() && matches(s, leaderBanner()) && isPatrolLeader();
    }

    /** ItemStack.matches: 수량·아이템·구성 요소가 같다. */
    static boolean matches(ItemStack a, ItemStack b) {
        return a.count() == b.count() && a.sameItemSameComponents(b);
    }

    /**
     * Raider.pickUpItem: 습격 중이고 그 파도에 대장이 없을 때 불길한 깃발이면 머리에 쓰고(쓰던 것은 확률로 떨군다) 파도 대장이 된다.
     * 아니면 Mob.pickUpItem.
     */
    @Override
    protected void pickUpItem(ItemEntity ie) {
        ItemStack item = ie.getItem();
        boolean hasLeader = hasActiveRaid() && currentRaid.getLeader(wave) != null;
        if (hasActiveRaid() && !hasLeader && matches(item, leaderBanner())) {
            ItemStack cur = getItemBySlot(EquipmentSlot.HEAD);
            double d = getEquipmentDropChance(EquipmentSlot.HEAD);
            if (!cur.isEmpty() && Math.max(random.nextFloat() - 0.1F, 0.0F) < d) spawnAtLocation(cur);
            onItemPickup(ie);
            setItemSlot(EquipmentSlot.HEAD, item);
            ie.broadcastTake(this, item.count());
            ie.discard();
            currentRaid.setLeader(wave, this);
            setPatrolLeader(true);
        } else {
            super.pickUpItem(ie);
        }
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

    /** Raider.canJoinPatrol: 습격 중이 아닐 때만. */
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
        if (currentRaid != null) tag.putInt("RaidId", currentRaid.id());
        else if (pendingRaidId >= 0) tag.putInt("RaidId", pendingRaidId);
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
        // Raider.readAdditionalSaveData: RaidId(int) 가 있으면 그 습격에 다시 들어간다(지도자면 파도 대장으로)
        if (tag.tags().get("RaidId") instanceof Integer id) {
            pendingRaidId = id;
            resolvePendingRaid();
        }
    }

    public boolean isPatrolling() { return patrolling; }

    /** Raider.updateNoActionTime: 밝기와 무관하게 틱마다 2 쌓는다(습격자는 빨리 사라진다). */
    @Override
    protected void updateNoActionTime() { noActionTime += 2; }

    /** Raider.HoldGroundAttackGoal: 순찰 중 적을 보면 근처 습격자에게 알리고, 멀면 제자리에서 노려보다 가까워지면 공격 태세. (습격 소속이 아니고 플레이어에게 맞지 않았을 때만) */
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
            Targetable last = mob.getLastHurtByMob();
            return mob.getCurrentRaid() == null && mob.isPatrolling() && mob.getTarget() != null && !mob.isAggressive()
                    && (last == null || !last.isPlayer());
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
    public boolean removeWhenFarAway(double distSqr) { return hasRaidRef() ? false : !patrolling || distSqr > 16384.0; }

    /** Raider.requiresCustomPersistence: 습격 소속이면 사라지지 않는다. */
    @Override
    protected boolean requiresCustomPersistence() { return super.requiresCustomPersistence() || hasRaidRef(); }

    /** getCurrentRaid() != null(보류 중인 번호 포함). */
    private boolean hasRaidRef() { return currentRaid != null || pendingRaidId >= 0; }

    /** Raider.ObtainRaidLeaderBannerGoal: 습격 중 파도 대장이 없으면 근처(16, 8, 16)의 불길한 깃발로 가서 줍는다. */
    public static class ObtainRaidLeaderBannerGoal extends org.domi.furnace.server.entity.ai.Goal {
        private final Raider mob;

        public ObtainRaidLeaderBannerGoal(Raider mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        /** Raider.ALLOWED_ITEMS: 줍기 지연이 없고 살아 있는 불길한 깃발. */
        private static boolean allowed(ItemEntity ie) {
            return !ie.hasPickUpDelay() && ie.isAlive() && matches(ie.getItem(), leaderBanner());
        }

        @Override
        public boolean canUse() {
            var raid = mob.getCurrentRaid();
            if (mob.hasActiveRaid() && !raid.isOver() && mob.canBeLeader() && !matches(mob.getItemBySlot(EquipmentSlot.HEAD), leaderBanner())) {
                Raider leader = raid.getLeader(mob.getWave());
                if (leader == null || !leader.isAlive()) {
                    List<ItemEntity> list = mob.level().getEntitiesOfClass(ItemEntity.class, mob.boundingBox().inflate(16.0, 8.0, 16.0), ObtainRaidLeaderBannerGoal::allowed);
                    if (!list.isEmpty()) return mob.getNavigation().moveToEntityAt(list.get(0).blockPosition(), 1.15F);
                }
            }
            return false;
        }

        @Override
        public void tick() {
            Pos t = mob.getNavigation().getTargetPos();
            if (t != null) {
                double dx = t.x() + 0.5 - mob.x(), dy = t.y() + 0.5 - mob.y(), dz = t.z() + 0.5 - mob.z();
                if (dx * dx + dy * dy + dz * dz < 1.414 * 1.414) {
                    List<ItemEntity> list = mob.level().getEntitiesOfClass(ItemEntity.class, mob.boundingBox().inflate(4.0, 4.0, 4.0), ObtainRaidLeaderBannerGoal::allowed);
                    if (!list.isEmpty()) mob.pickUpItem(list.get(0));
                }
            }
        }
    }

    /** PathfindToRaidGoal: 마을 밖이면 습격 중심 쪽(15, 4, 90°)으로 걷고, 20틱마다 근처(16) 습격자를 끌어들인다. */
    public static class PathfindToRaidGoal extends org.domi.furnace.server.entity.ai.Goal {
        private static final int RECRUITMENT_SEARCH_TICK_DELAY = 20;
        private final Raider mob;
        private int recruitmentTick;

        public PathfindToRaidGoal(Raider mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return mob.getTarget() == null && !mob.hasControllingPassenger() && mob.hasActiveRaid() && !mob.getCurrentRaid().isOver()
                    && !mob.level().isVillage(mob.blockPosition());
        }

        @Override
        public boolean canContinueToUse() {
            return mob.hasActiveRaid() && !mob.getCurrentRaid().isOver() && !mob.level().isVillage(mob.blockPosition());
        }

        @Override
        public void tick() {
            if (!mob.hasActiveRaid()) return;
            var raid = mob.getCurrentRaid();
            if (mob.tickCount > recruitmentTick) {
                recruitmentTick = mob.tickCount + RECRUITMENT_SEARCH_TICK_DELAY;
                recruitNearby(raid);
            }
            if (mob.getNavigation().isDone()) {   // !isPathFinding
                Pos c = raid.center();
                double[] v = org.domi.furnace.server.entity.ai.util.DefaultRandomPos.getPosTowards(mob, 15, 4,
                        new double[] {c.x() + 0.5, c.y(), c.z() + 0.5}, (float) (Math.PI / 2));
                if (v != null) mob.getNavigation().moveTo(v[0], v[1], v[2], 1.0);
            }
        }

        private void recruitNearby(org.domi.furnace.server.world.raid.Raid raid) {
            if (!raid.isActive()) return;
            // 바닐라는 HashSet 으로 모은다(여기서는 목록 순서 — joinRaid 순서만 다를 수 있다)
            var set = new java.util.LinkedHashSet<Raider>(mob.level().getEntitiesOfClass(Raider.class, mob.boundingBox().inflate(16.0, 16.0, 16.0),
                    r -> !r.hasActiveRaid() && org.domi.furnace.server.world.raid.Raids.canJoinRaid(r, raid)));
            for (Raider r : set) raid.joinRaid(raid.groupsSpawned(), r, null, true);
        }
    }

    /** Raider.RaiderCelebration: 습격이 패배(습격자 승리)로 끝나면 축하 — 가끔 축하 소리, 가끔 뛴다. */
    public static class RaiderCelebration extends org.domi.furnace.server.entity.ai.Goal {
        private final Raider mob;

        RaiderCelebration(Raider mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            var raid = mob.getCurrentRaid();
            return mob.isAlive() && mob.getTarget() == null && raid != null && raid.isLoss();
        }

        @Override
        public void start() {
            mob.setCelebrating(true);
            super.start();
        }

        @Override
        public void stop() {
            mob.setCelebrating(false);
            super.stop();
        }

        @Override
        public void tick() {
            if (!mob.isSilent() && mob.random().nextInt(adjustedTickDelay(100)) == 0) mob.makeSound(mob.getCelebrateSound());
            if (!mob.isPassenger() && mob.random().nextInt(adjustedTickDelay(50)) == 0) mob.getJumpControl().jump();
            super.tick();
        }
    }

    /** Raider.RaiderMoveThroughVillageGoal: 습격 중 48칸 안의 아직 안 가 본 집(home POI)으로 가고, 최근 세 곳은 기억한다. */
    public static class RaiderMoveThroughVillageGoal extends org.domi.furnace.server.entity.ai.Goal {
        private final Raider raider;
        private final double speedModifier;
        private Pos poiPos;
        private final List<Pos> visited = new java.util.ArrayList<>();
        private final int distanceToPoi;
        private boolean stuck;

        public RaiderMoveThroughVillageGoal(Raider raider, double speed, int distanceToPoi) {
            this.raider = raider;
            this.speedModifier = speed;
            this.distanceToPoi = distanceToPoi;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            updateVisited();
            return isValidRaid() && hasSuitablePoi() && raider.getTarget() == null;
        }

        private boolean isValidRaid() { return raider.hasActiveRaid() && !raider.getCurrentRaid().isOver(); }

        private boolean hasSuitablePoi() {
            Pos p = raider.level().poiManager().getRandom(t -> t == org.domi.furnace.server.world.poi.PoiTypes.HOME, this::hasNotVisited,
                    org.domi.furnace.server.world.poi.PoiManager.Occupancy.ANY, raider.blockPosition(), 48, raider.random());
            if (p == null) return false;
            poiPos = p;
            return true;
        }

        /** Vec3i.closerToCenterThan(pos, d). */
        private boolean closerToCenterThan(Pos p, double d) {
            double dx = p.x() + 0.5 - raider.x(), dy = p.y() + 0.5 - raider.y(), dz = p.z() + 0.5 - raider.z();
            return dx * dx + dy * dy + dz * dz < d * d;
        }

        @Override
        public boolean canContinueToUse() {
            if (raider.getNavigation().isDone()) return false;
            return raider.getTarget() == null && !closerToCenterThan(poiPos, raider.bbWidth() + distanceToPoi) && !stuck;
        }

        @Override
        public void stop() {
            if (closerToCenterThan(poiPos, distanceToPoi)) visited.add(poiPos);
        }

        @Override
        public void start() {
            super.start();
            raider.setNoActionTime(0);
            raider.getNavigation().moveTo(poiPos.x(), poiPos.y(), poiPos.z(), speedModifier);
            stuck = false;
        }

        @Override
        public void tick() {
            if (raider.getNavigation().isDone()) {
                double[] v = {poiPos.x() + 0.5, poiPos.y(), poiPos.z() + 0.5};
                double[] w = org.domi.furnace.server.entity.ai.util.DefaultRandomPos.getPosTowards(raider, 16, 7, v, (float) (Math.PI / 10));
                if (w == null) w = org.domi.furnace.server.entity.ai.util.DefaultRandomPos.getPosTowards(raider, 8, 7, v, (float) (Math.PI / 2));
                if (w == null) {
                    stuck = true;
                    return;
                }
                raider.getNavigation().moveTo(w[0], w[1], w[2], speedModifier);
            }
        }

        private boolean hasNotVisited(Pos p) {
            for (Pos q : visited) if (java.util.Objects.equals(p, q)) return false;
            return true;
        }

        private void updateVisited() {
            if (visited.size() > 2) visited.remove(0);
        }
    }
}
