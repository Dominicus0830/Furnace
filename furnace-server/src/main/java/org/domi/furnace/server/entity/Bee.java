// 바닐라 Bee: 꽃가루를 모아 벌집에 꿀을 채우는 나는 동물(NeutralMob). 맞으면 화가 나 주변 벌과 함께 쏘고, 한 번 쏘면 곧 죽는다.
// 목표: 공격·벌집 들어가기·번식·유혹·수분·부모 따르기·벌집 찾기·벌집 가기·아는 꽃 가기·작물 키우기·배회·뜨기.
// 동기화: 17 = 플래그(2 구르기·4 쏨·8 꿀), 18 = 남은 분노 시간.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Clip;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.entity.BeehiveBlockEntity;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.behavior.UniformInt;
import org.domi.furnace.server.entity.ai.control.FlyingMoveControl;
import org.domi.furnace.server.entity.ai.control.LookControl;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.FlyingPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.entity.ai.util.FlyingRandomPos;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiTypes;

public class Bee extends Animal implements NeutralMob {
    public static final int DATA_FLAGS_ID = 17, DATA_REMAINING_ANGER_TIME = 18;
    public static final int TICKS_PER_FLAP = 2;   // Mth.ceil(1.4959966F)
    private static final int FLAG_ROLL = 2, FLAG_HAS_STUNG = 4, FLAG_HAS_NECTAR = 8;
    private static final int STING_DEATH_COUNTDOWN = 1200;
    private static final int TICKS_BEFORE_GOING_TO_KNOWN_FLOWER = 2400;
    private static final int TICKS_WITHOUT_NECTAR_BEFORE_GOING_HOME = 3600;
    private static final int MAX_CROPS_GROWABLE = 10;
    private static final int POISON_SECONDS_NORMAL = 10, POISON_SECONDS_HARD = 18;
    private static final int TOO_FAR_DISTANCE = 32, HIVE_CLOSE_ENOUGH_DISTANCE = 2, PATHFIND_TO_HIVE_WHEN_CLOSER_THAN = 16, HIVE_SEARCH_DISTANCE = 20;
    private static final UniformInt PERSISTENT_ANGER_TIME = UniformInt.of(400, 780);   // TimeUtil.rangeOfSeconds(20, 39)
    private static final int COOLDOWN_BEFORE_LOCATING_NEW_HIVE = 200, COOLDOWN_BEFORE_LOCATING_NEW_FLOWER = 200;

    private UUID persistentAngerTarget;
    private float rollAmount, rollAmountO;
    private int timeSinceSting;
    int ticksWithoutNectarSinceExitingHive;
    private int stayOutOfHiveCountdown;
    private int numCropsGrownSincePollination;
    int remainingCooldownBeforeLocatingNewHive;
    int remainingCooldownBeforeLocatingNewFlower;
    Pos savedFlowerPos;
    Pos hivePos;
    BeePollinateGoal beePollinateGoal;
    BeeGoToHiveGoal goToHiveGoal;
    private BeeGoToKnownFlowerGoal goToKnownFlowerGoal;
    private int underWaterTicks;

    public Bee(ChunkLevel level, int id) {
        super(EntityType.BEE, level, id);
        // 바닐라 필드 초기화 순서: Mob 생성자(registerGoals) 뒤에 엔티티 난수로 20~60
        remainingCooldownBeforeLocatingNewFlower = 20 + random.nextInt(41);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.lookControl = new BeeLookControl(this);
        setPathfindingMalus(PathType.DANGER_FIRE, -1.0F);
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 16.0F);
        setPathfindingMalus(PathType.COCOA, -1.0F);
        setPathfindingMalus(PathType.FENCE, -1.0F);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FLAGS_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
        entityData.define(DATA_REMAINING_ANGER_TIME, SetEntityData.SERIALIZER_INT, 0);
    }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) { return level.isEmptyBlock(pos) ? 10.0F : 0.0F; }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 10.0).add(Attribute.FLYING_SPEED, 0.6F).add(Attribute.MOVEMENT_SPEED, 0.3F).add(Attribute.ATTACK_DAMAGE, 2.0)
                .add(Attribute.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new BeeAttackGoal(this, 1.4F, true));
        goalSelector.addGoal(1, new BeeEnterHiveGoal());
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        goalSelector.addGoal(3, new TemptGoal(this, 1.25, s -> ItemTags.is(s, "minecraft:bee_food"), false));
        beePollinateGoal = new BeePollinateGoal();
        goalSelector.addGoal(4, beePollinateGoal);
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.25));
        goalSelector.addGoal(5, new BeeLocateHiveGoal());
        goToHiveGoal = new BeeGoToHiveGoal();
        goalSelector.addGoal(5, goToHiveGoal);
        goToKnownFlowerGoal = new BeeGoToKnownFlowerGoal();
        goalSelector.addGoal(6, goToKnownFlowerGoal);
        goalSelector.addGoal(7, new BeeGrowCropGoal());
        goalSelector.addGoal(8, new BeeWanderGoal());
        goalSelector.addGoal(9, new FloatGoal(this));
        targetSelector.addGoal(1, new BeeHurtByOtherGoal(this).setAlertOthers());
        targetSelector.addGoal(2, new BeeBecomeAngryTargetGoal(this));
        targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    // ------------------------------------------------------------------ 저장

    private static void putPos(NbtCompound tag, String k, Pos p) { tag.putIntArray(k, new int[] {p.x(), p.y(), p.z()}); }

    private static Pos readPos(NbtCompound tag, String k) { return tag.tags().get(k) instanceof int[] a && a.length == 3 ? new Pos(a[0], a[1], a[2]) : null; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        if (hasHive()) putPos(tag, "hive_pos", hivePos);
        if (hasSavedFlowerPos()) putPos(tag, "flower_pos", savedFlowerPos);
        tag.putBoolean("HasNectar", hasNectar());
        tag.putBoolean("HasStung", hasStung());
        tag.putInt("TicksSincePollination", ticksWithoutNectarSinceExitingHive);
        tag.putInt("CannotEnterHiveTicks", stayOutOfHiveCountdown);
        tag.putInt("CropsGrownSincePollination", numCropsGrownSincePollination);
        addPersistentAngerSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        hivePos = readPos(tag, "hive_pos");
        savedFlowerPos = readPos(tag, "flower_pos");
        super.readAdditionalSaveData(tag);
        setHasNectar(tag.getBoolean("HasNectar"));
        setHasStung(tag.getBoolean("HasStung"));
        ticksWithoutNectarSinceExitingHive = tag.getIntOr0("TicksSincePollination");
        stayOutOfHiveCountdown = tag.getIntOr0("CannotEnterHiveTicks");
        numCropsGrownSincePollination = tag.getIntOr0("CropsGrownSincePollination");
        readPersistentAngerSaveData(tag);
    }

    // ------------------------------------------------------------------ 쏘기·피해

    /** doHurtTarget: 공격력(정수로 자른 값)으로 sting. 맞으면 침 수 +1, 보통 10초·어려움 18초 독, 쏨 표시·분노 끝·소리. */
    @Override
    public boolean doHurtTarget(Targetable t) {
        boolean hurt = t.hurtByMob(new Damage("sting", false, false, false), (float) (int) attributeValue(Attribute.ATTACK_DAMAGE), this);
        if (hurt) {
            EnchantEffects.thorns(level, t, this);   // doPostAttackEffects: 맞은 쪽 장비의 가시
            int seconds = level.difficulty() == 2 ? POISON_SECONDS_NORMAL : level.difficulty() == 3 ? POISON_SECONDS_HARD : 0;
            if (t instanceof LivingEntity le) {
                le.entityData.set(DATA_STINGER_COUNT, (Integer) le.entityData.get(DATA_STINGER_COUNT) + 1);
                if (seconds > 0) le.addEffect(new MobEffectInstance(MobEffect.POISON, seconds * 20, 0));
            } else if (t instanceof Player p) {
                // TODO(플레이어): 침 수(DATA_STINGER_COUNT) 동기화
                if (seconds > 0) p.vitals().addEffect(new MobEffectInstance(MobEffect.POISON, seconds * 20, 0));
            }
            setHasStung(true);
            stopBeingAngry();
            playSound("entity.bee.sting", 1.0F, 1.0F);
        }
        return hurt;
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (isInvulnerableTo(source)) return false;
        beePollinateGoal.stopPollinating();
        return super.hurtBy(source, amount, attacker);
    }

    // ------------------------------------------------------------------ 틱

    @Override
    protected void aiStep() {
        super.aiStep();
        // 서버에서도 입자 인자(레벨 난수 nextDouble 2번)를 평가한다
        if (hasNectar() && getCropsGrownSincePollination() < MAX_CROPS_GROWABLE && random.nextFloat() < 0.05F) {
            for (int i = 0; i < random.nextInt(2) + 1; i++) {
                level.random().nextDouble();
                level.random().nextDouble();
            }
        }
        updateRollAmount();
    }

    private void updateRollAmount() {
        rollAmountO = rollAmount;
        if (isRolling()) rollAmount = Math.min(1.0F, rollAmount + 0.2F);
        else rollAmount = Math.max(0.0F, rollAmount - 0.24F);
    }

    @Override
    protected void customServerAiStep() {
        boolean stung = hasStung();
        if (isInWaterOrBubble()) underWaterTicks++;
        else underWaterTicks = 0;
        if (underWaterTicks > 20) hurt(new Damage("drown", false, false, false), 1.0F);
        if (stung) {
            timeSinceSting++;
            if (timeSinceSting % 5 == 0 && random.nextInt(Math.clamp(STING_DEATH_COUNTDOWN - timeSinceSting, 1, STING_DEATH_COUNTDOWN)) == 0) {
                hurt(new Damage("generic", false, false, false), health());
            }
        }
        if (!hasNectar()) ticksWithoutNectarSinceExitingHive++;
        updatePersistentAnger(false);
    }

    @Override
    public void tick() {
        super.tick();
        if (stayOutOfHiveCountdown > 0) stayOutOfHiveCountdown--;
        if (remainingCooldownBeforeLocatingNewHive > 0) remainingCooldownBeforeLocatingNewHive--;
        if (remainingCooldownBeforeLocatingNewFlower > 0) remainingCooldownBeforeLocatingNewFlower--;
        Targetable t = getTarget();
        boolean roll = isAngry() && !hasStung() && t != null && distSqr(t) < 4.0;
        setRolling(roll);
        if (tickCount % 20 == 0 && !isHiveValid()) hivePos = null;
    }

    private double distSqr(Targetable t) {
        double dx = t.x() - x(), dy = t.y() - y(), dz = t.z() - z();
        return dx * dx + dy * dy + dz * dz;
    }

    // ------------------------------------------------------------------ 상태

    public Pos getSavedFlowerPos() { return savedFlowerPos; }

    public boolean hasSavedFlowerPos() { return savedFlowerPos != null; }

    public void setSavedFlowerPos(Pos p) { savedFlowerPos = p; }

    public Pos getHivePos() { return hivePos; }

    public boolean hasHive() { return hivePos != null; }

    public void setHivePos(Pos p) { hivePos = p; }

    public int getStayOutOfHiveCountdown() { return stayOutOfHiveCountdown; }

    public void setStayOutOfHiveCountdown(int t) { stayOutOfHiveCountdown = t; }

    public int ticksWithoutNectarSinceExitingHive() { return ticksWithoutNectarSinceExitingHive; }

    private boolean isTiredOfLookingForNectar() { return ticksWithoutNectarSinceExitingHive > TICKS_WITHOUT_NECTAR_BEFORE_GOING_HOME; }

    /** wantsToEnterHive: 들어갈 수 있고(대기 0·수분 중 아님·쏜 적 없음·대상 없음) 지쳤거나 비·밤이거나 꿀이 있고, 벌집에 연기가 없다. */
    boolean wantsToEnterHive() {
        if (stayOutOfHiveCountdown <= 0 && !beePollinateGoal.isPollinating() && !hasStung() && getTarget() == null) {
            boolean b = isTiredOfLookingForNectar() || level.isRaining() || level.isNight() || hasNectar();
            return b && !isHiveNearFire();
        }
        return false;
    }

    private boolean isHiveNearFire() {
        if (hivePos == null) return false;
        return level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive && hive.isFireNearby(level);
    }

    public void resetTicksWithoutNectarSinceExitingHive() { ticksWithoutNectarSinceExitingHive = 0; }

    private boolean doesHiveHaveSpace(Pos p) { return level.getBlockEntity(p) instanceof BeehiveBlockEntity hive && !hive.isFull(); }

    int getCropsGrownSincePollination() { return numCropsGrownSincePollination; }

    private void resetNumCropsGrownSincePollination() { numCropsGrownSincePollination = 0; }

    void incrementNumCropsGrownSincePollination() { numCropsGrownSincePollination++; }

    boolean isHiveValid() {
        if (!hasHive()) return false;
        if (isTooFarAway(hivePos)) return false;
        return level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity;
    }

    public boolean hasNectar() { return getFlag(FLAG_HAS_NECTAR); }

    void setHasNectar(boolean v) {
        if (v) resetTicksWithoutNectarSinceExitingHive();
        setFlag(FLAG_HAS_NECTAR, v);
    }

    public boolean hasStung() { return getFlag(FLAG_HAS_STUNG); }

    private void setHasStung(boolean v) { setFlag(FLAG_HAS_STUNG, v); }

    private boolean isRolling() { return getFlag(FLAG_ROLL); }

    private void setRolling(boolean v) { setFlag(FLAG_ROLL, v); }

    boolean isTooFarAway(Pos p) { return !closerThan(p, TOO_FAR_DISTANCE); }

    private void setFlag(int f, boolean v) {
        byte b = (Byte) entityData.get(DATA_FLAGS_ID);
        entityData.set(DATA_FLAGS_ID, (byte) (v ? b | f : b & ~f));
    }

    private boolean getFlag(int f) { return ((Byte) entityData.get(DATA_FLAGS_ID) & f) != 0; }

    /** dropOffNectar: 꿀을 내려놓고 작물 수를 0 으로. */
    public void dropOffNectar() {
        setHasNectar(false);
        resetNumCropsGrownSincePollination();
    }

    /** BlockPos.closerThan(blockPosition(), d): 칸 사이 거리 제곱 < d². */
    boolean closerThan(Pos p, int d) {
        Pos b = blockPosition();
        double dx = p.x() - b.x(), dy = p.y() - b.y(), dz = p.z() - b.z();
        return dx * dx + dy * dy + dz * dz < (double) d * d;
    }

    boolean isFlowerValid(Pos p) { return level.hasChunkAt(p) && B.isTag(level.getBlockState(p), "minecraft:flowers"); }

    /** pathfindRandomlyTowards: 높이·거리에 맞춰 AirRandomPos 로 목표 쪽 공중 점을 골라 간다. */
    void pathfindRandomlyTowards(Pos pos) {
        double tx = pos.x() + 0.5, ty = pos.y(), tz = pos.z() + 0.5;   // Vec3.atBottomCenterOf
        int yOff = 0;
        Pos here = blockPosition();
        int dy = (int) ty - here.y();
        if (dy > 2) yOff = 4;
        else if (dy < -2) yOff = -4;
        int h = 6, v = 8;
        int man = here.distManhattan(pos);
        if (man < 15) {
            h = man / 2;
            v = man / 2;
        }
        double[] p = FlyingRandomPos.getAirPosTowardsTarget(this, h, v, yOff, tx, ty, tz, (float) (Math.PI / 10));
        if (p != null) {
            navigation.setMaxVisitedNodesMultiplier(0.5F);
            navigation.moveTo(p[0], p[1], p[2], 1.0);
        }
    }

    // ------------------------------------------------------------------ 분노(NeutralMob)

    @Override public int getRemainingPersistentAngerTime() { return (Integer) entityData.get(DATA_REMAINING_ANGER_TIME); }

    @Override public void setRemainingPersistentAngerTime(int t) { entityData.set(DATA_REMAINING_ANGER_TIME, t); }

    @Override public UUID getPersistentAngerTarget() { return persistentAngerTarget; }

    @Override public void setPersistentAngerTarget(UUID u) { persistentAngerTarget = u; }

    @Override public void startPersistentAngerTimer() { setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(random)); }

    @Override public boolean canAttackTarget(Targetable t) { return canAttackType(t); }

    // ------------------------------------------------------------------ 기타

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(Pos pos) { return !level.isEmptyBlock(pos.below()); }

            @Override
            public void tick() {
                if (!beePollinateGoal.isPollinating()) super.tick();
            }
        };
        nav.setCanOpenDoors(false);
        nav.setCanFloat(false);
        nav.setCanPassDoors(true);
        return nav;
    }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:bee_food"); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Bee(level, level.nextEntityId()); }

    @Override
    protected void checkFallDamage(double dy, boolean ground, int state, Pos pos) {}

    @Override
    protected boolean isFlapping() { return isFlying() && tickCount % TICKS_PER_FLAP == 0; }

    public boolean isFlying() { return !onGround(); }

    @Override
    protected void jumpInLiquid(boolean lava) { setDeltaMovement(vx(), vy() + 0.01, vz()); }

    @Override public String getAmbientSound() { return null; }

    @Override protected String getHurtSound(Damage source) { return "entity.bee.hurt"; }

    @Override protected String getDeathSound() { return "entity.bee.death"; }

    @Override protected float getSoundVolume() { return 0.4F; }

    public float getRollAmount(float pt) { return rollAmountO + pt * (rollAmount - rollAmountO); }

    // ------------------------------------------------------------------ 목표

    /** BaseBeeGoal: 화나 있지 않을 때만. */
    abstract class BaseBeeGoal extends Goal {
        abstract boolean canBeeUse();

        abstract boolean canBeeContinueToUse();

        @Override public boolean canUse() { return canBeeUse() && !isAngry(); }

        @Override public boolean canContinueToUse() { return canBeeContinueToUse() && !isAngry(); }
    }

    final class BeeAttackGoal extends MeleeAttackGoal {
        BeeAttackGoal(Bee bee, double speed, boolean followEvenIfNotSeen) { super(bee, speed, followEvenIfNotSeen); }

        @Override public boolean canUse() { return super.canUse() && isAngry() && !hasStung(); }

        @Override public boolean canContinueToUse() { return super.canContinueToUse() && isAngry() && !hasStung(); }
    }

    static final class BeeBecomeAngryTargetGoal extends NearestAttackableTargetGoal {
        BeeBecomeAngryTargetGoal(Bee bee) { super(bee, Player.class, 10, true, false, bee::isAngryAt); }

        @Override public boolean canUse() { return beeCanTarget() && super.canUse(); }

        @Override
        public boolean canContinueToUse() {
            if (beeCanTarget() && mob.getTarget() != null) return super.canContinueToUse();
            targetMob = null;
            return false;
        }

        private boolean beeCanTarget() {
            Bee b = (Bee) mob;
            return b.isAngry() && !b.hasStung();
        }
    }

    final class BeeEnterHiveGoal extends BaseBeeGoal {
        @Override
        boolean canBeeUse() {
            if (hasHive() && wantsToEnterHive() && closerToCenterThan(hivePos, 2.0) && level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive) {
                if (!hive.isFull()) return true;
                hivePos = null;
            }
            return false;
        }

        @Override boolean canBeeContinueToUse() { return false; }

        @Override
        public void start() {
            if (level.getBlockEntity(hivePos) instanceof BeehiveBlockEntity hive) hive.addOccupant(level, Bee.this);
        }
    }

    /** BlockPos.closerToCenterThan(position, d): 칸 가운데까지 거리 제곱 < d². */
    boolean closerToCenterThan(Pos p, double d) {
        double dx = p.x() + 0.5 - x(), dy = p.y() + 0.5 - y(), dz = p.z() + 0.5 - z();
        return dx * dx + dy * dy + dz * dz < d * d;
    }

    final class BeeGoToHiveGoal extends BaseBeeGoal {
        int travellingTicks = level.random().nextInt(10);
        final List<Pos> blacklistedTargets = new ArrayList<>();
        private Path lastPath;
        private int ticksStuck;

        BeeGoToHiveGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        boolean canBeeUse() {
            return hivePos != null && !isLeashed() && wantsToEnterHive() && !hasReachedTarget(hivePos) && B.isTag(level.getBlockState(hivePos), "minecraft:beehives");
        }

        @Override boolean canBeeContinueToUse() { return canBeeUse(); }

        @Override
        public void start() {
            travellingTicks = 0;
            ticksStuck = 0;
            super.start();
        }

        @Override
        public void stop() {
            travellingTicks = 0;
            ticksStuck = 0;
            navigation.stop();
            navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (hivePos == null) return;
            travellingTicks++;
            if (travellingTicks > adjustedTickDelay(600)) {
                dropAndBlacklistHive();
            } else if (!navigation.isInProgress()) {
                if (!closerThan(hivePos, PATHFIND_TO_HIVE_WHEN_CLOSER_THAN)) {
                    if (isTooFarAway(hivePos)) dropHive();
                    else pathfindRandomlyTowards(hivePos);
                } else {
                    boolean ok = pathfindDirectlyTowards(hivePos);
                    if (!ok) {
                        dropAndBlacklistHive();
                    } else if (lastPath != null && navigation.getPath().sameAs(lastPath)) {
                        ticksStuck++;
                        if (ticksStuck > 60) {
                            dropHive();
                            ticksStuck = 0;
                        }
                    } else {
                        lastPath = navigation.getPath();
                    }
                }
            }
        }

        private boolean pathfindDirectlyTowards(Pos p) {
            navigation.setMaxVisitedNodesMultiplier(10.0F);
            navigation.moveTo(p.x(), p.y(), p.z(), 2, 1.0);
            return navigation.getPath() != null && navigation.getPath().canReach();
        }

        boolean isTargetBlacklisted(Pos p) { return blacklistedTargets.contains(p); }

        private void blacklistTarget(Pos p) {
            blacklistedTargets.add(p);
            while (blacklistedTargets.size() > 3) blacklistedTargets.remove(0);
        }

        void clearBlacklist() { blacklistedTargets.clear(); }

        private void dropAndBlacklistHive() {
            if (hivePos != null) blacklistTarget(hivePos);
            dropHive();
        }

        private void dropHive() {
            hivePos = null;
            remainingCooldownBeforeLocatingNewHive = COOLDOWN_BEFORE_LOCATING_NEW_HIVE;
        }

        private boolean hasReachedTarget(Pos p) {
            if (closerThan(p, HIVE_CLOSE_ENOUGH_DISTANCE)) return true;
            Path path = navigation.getPath();
            return path != null && path.getTarget().equals(p) && path.canReach() && path.isDone();
        }
    }

    final class BeeGoToKnownFlowerGoal extends BaseBeeGoal {
        int travellingTicks = level.random().nextInt(10);

        BeeGoToKnownFlowerGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        boolean canBeeUse() {
            return savedFlowerPos != null && !isLeashed() && wantsToGoToKnownFlower() && isFlowerValid(savedFlowerPos) && !closerThan(savedFlowerPos, 2);
        }

        @Override boolean canBeeContinueToUse() { return canBeeUse(); }

        @Override
        public void start() {
            travellingTicks = 0;
            super.start();
        }

        @Override
        public void stop() {
            travellingTicks = 0;
            navigation.stop();
            navigation.resetMaxVisitedNodesMultiplier();
        }

        @Override
        public void tick() {
            if (savedFlowerPos == null) return;
            travellingTicks++;
            if (travellingTicks > adjustedTickDelay(600)) {
                savedFlowerPos = null;
            } else if (!navigation.isInProgress()) {
                if (isTooFarAway(savedFlowerPos)) savedFlowerPos = null;
                else pathfindRandomlyTowards(savedFlowerPos);
            }
        }

        private boolean wantsToGoToKnownFlower() { return ticksWithoutNectarSinceExitingHive > TICKS_BEFORE_GOING_TO_KNOWN_FLOWER; }
    }

    private static final Prop AGE = B.prop("age");

    final class BeeGrowCropGoal extends BaseBeeGoal {
        @Override
        boolean canBeeUse() {
            if (getCropsGrownSincePollination() >= MAX_CROPS_GROWABLE) return false;
            return random.nextFloat() < 0.3F ? false : hasNectar() && isHiveValid();
        }

        @Override boolean canBeeContinueToUse() { return canBeeUse(); }

        @Override
        public void tick() {
            if (random.nextInt(adjustedTickDelay(30)) != 0) return;
            for (int i = 1; i <= 2; i++) {
                Pos p = blockPosition().below(i);
                int s = level.getBlockState(p);
                var b = BlockBehaviors.of(s);
                int grown = -1;
                if (B.isTag(s, "minecraft:bee_growables")) {
                    if (b instanceof org.domi.furnace.server.block.types.CropBlock crop) {
                        if (!crop.isMaxAge(s)) grown = crop.stateForAge(crop.age(s) + 1);
                    } else if (b instanceof org.domi.furnace.server.block.types.StemBlock) {
                        int age = AGE.intValue(s);
                        if (age < 7) grown = AGE.withInt(s, age + 1);
                    } else if (B.is(s, "minecraft:sweet_berry_bush")) {
                        int age = AGE.intValue(s);
                        if (age < 3) grown = AGE.withInt(s, age + 1);
                    } else if (B.is(s, "minecraft:cave_vines") || B.is(s, "minecraft:cave_vines_plant")) {
                        ((org.domi.furnace.server.block.types.BonemealableBlock) b).performBonemeal(level, random, p, s);
                    }
                    if (grown != -1) {
                        level.levelEvent(null, 2011, p, 15);
                        level.setBlockAndUpdate(p, grown);
                        incrementNumCropsGrownSincePollination();
                    }
                }
            }
        }
    }

    final class BeeHurtByOtherGoal extends HurtByTargetGoal {
        BeeHurtByOtherGoal(Bee bee) { super(bee); }

        @Override public boolean canContinueToUse() { return isAngry() && super.canContinueToUse(); }

        @Override
        protected void alertOther(Mob other, Targetable target) {
            if (other instanceof Bee && mob.hasLineOfSight(target)) other.setTarget(target);
        }
    }

    final class BeeLocateHiveGoal extends BaseBeeGoal {
        @Override boolean canBeeUse() { return remainingCooldownBeforeLocatingNewHive == 0 && !hasHive() && wantsToEnterHive(); }

        @Override boolean canBeeContinueToUse() { return false; }

        @Override
        public void start() {
            remainingCooldownBeforeLocatingNewHive = COOLDOWN_BEFORE_LOCATING_NEW_HIVE;
            List<Pos> list = findNearbyHivesWithSpace();
            if (list.isEmpty()) return;
            for (Pos p : list) {
                if (!goToHiveGoal.isTargetBlacklisted(p)) {
                    hivePos = p;
                    return;
                }
            }
            goToHiveGoal.clearBlacklist();
            hivePos = list.get(0);
        }

        private List<Pos> findNearbyHivesWithSpace() {
            Pos here = blockPosition();
            List<Pos> out = new ArrayList<>();
            for (var r : level.poiManager().getInRange(PoiTypes.BEE_HOME, here, HIVE_SEARCH_DISTANCE, PoiManager.Occupancy.ANY)) {
                if (doesHiveHaveSpace(r.pos())) out.add(r.pos());
            }
            out.sort(Comparator.comparingDouble(p -> {
                double dx = p.x() - here.x(), dy = p.y() - here.y(), dz = p.z() - here.z();
                return dx * dx + dy * dy + dz * dz;
            }));
            return out;
        }
    }

    final class BeeLookControl extends LookControl {
        BeeLookControl(Mob m) { super(m); }

        @Override
        public void tick() {
            if (!isAngry()) super.tick();
        }

        @Override
        protected boolean resetXRotOnTick() { return !beePollinateGoal.isPollinating(); }
    }

    final class BeePollinateGoal extends BaseBeeGoal {
        private static final int MIN_POLLINATION_TICKS = 400;
        private static final double ARRIVAL_THRESHOLD = 0.1;
        private static final int POSITION_CHANGE_CHANCE = 25;
        private static final float SPEED_MODIFIER = 0.35F;
        private static final float HOVER_POS_OFFSET = 0.33333334F;
        private int successfulPollinatingTicks;
        private int lastSoundPlayedTick;
        private boolean pollinating;
        private double[] hoverPos;
        private int pollinatingTicks;
        private static final int MAX_POLLINATING_TICKS = 600;

        private final IntPredicate validFlower = s -> {
            Prop wl = B.prop("waterlogged");
            if (wl.has(s) && wl.is(s)) return false;
            if (B.isTag(s, "minecraft:flowers")) {
                if (B.is(s, "minecraft:sunflower")) return "upper".equals(B.prop("half").valueName(s));
                return true;
            }
            return false;
        };

        BeePollinateGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        boolean canBeeUse() {
            if (remainingCooldownBeforeLocatingNewFlower > 0) return false;
            if (hasNectar()) return false;
            if (level.isRaining()) return false;
            Pos f = findNearbyFlower();
            if (f != null) {
                savedFlowerPos = f;
                navigation.moveTo(f.x() + 0.5, f.y() + 0.5, f.z() + 0.5, 1.2F);
                return true;
            }
            remainingCooldownBeforeLocatingNewFlower = 20 + random.nextInt(41);
            return false;
        }

        @Override
        boolean canBeeContinueToUse() {
            if (!pollinating) return false;
            if (!hasSavedFlowerPos()) return false;
            if (level.isRaining()) return false;
            if (hasPollinatedLongEnough()) return random.nextFloat() < 0.2F;
            if (tickCount % 20 == 0 && !isFlowerValid(savedFlowerPos)) {
                savedFlowerPos = null;
                return false;
            }
            return true;
        }

        private boolean hasPollinatedLongEnough() { return successfulPollinatingTicks > MIN_POLLINATION_TICKS; }

        boolean isPollinating() { return pollinating; }

        void stopPollinating() { pollinating = false; }

        @Override
        public void start() {
            successfulPollinatingTicks = 0;
            pollinatingTicks = 0;
            lastSoundPlayedTick = 0;
            pollinating = true;
            resetTicksWithoutNectarSinceExitingHive();
        }

        @Override
        public void stop() {
            if (hasPollinatedLongEnough()) setHasNectar(true);
            pollinating = false;
            navigation.stop();
            remainingCooldownBeforeLocatingNewFlower = COOLDOWN_BEFORE_LOCATING_NEW_FLOWER;
        }

        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            pollinatingTicks++;
            if (pollinatingTicks > MAX_POLLINATING_TICKS) {
                savedFlowerPos = null;
                return;
            }
            double[] v = {savedFlowerPos.x() + 0.5, savedFlowerPos.y() + 0.5 + 0.6F, savedFlowerPos.z() + 0.5};   // atCenterOf + (0, 0.6F, 0)
            if (dist2(v, x(), y(), z()) > 1.0) {
                hoverPos = v;
                setWantedPos();
                return;
            }
            if (hoverPos == null) hoverPos = v;
            boolean arrived = dist2(hoverPos, x(), y(), z()) <= ARRIVAL_THRESHOLD;
            boolean move = true;
            if (!arrived && pollinatingTicks > MAX_POLLINATING_TICKS) {
                savedFlowerPos = null;
                return;
            }
            if (arrived) {
                boolean change = random.nextInt(POSITION_CHANGE_CHANCE) == 0;
                if (change) {
                    double ox = getOffset();
                    double oz = getOffset();
                    hoverPos = new double[] {v[0] + ox, v[1], v[2] + oz};
                    navigation.stop();
                } else {
                    move = false;
                }
                getLookControl().setLookAt(v[0], v[1], v[2]);
            }
            if (move) setWantedPos();
            successfulPollinatingTicks++;
            if (random.nextFloat() < 0.05F && successfulPollinatingTicks > lastSoundPlayedTick + 60) {
                lastSoundPlayedTick = successfulPollinatingTicks;
                playSound("entity.bee.pollinate", 1.0F, 1.0F);
            }
        }

        private static double dist2(double[] a, double x, double y, double z) {
            double dx = a[0] - x, dy = a[1] - y, dz = a[2] - z;
            return dx * dx + dy * dy + dz * dz;
        }

        private void setWantedPos() { getMoveControl().setWantedPosition(hoverPos[0], hoverPos[1], hoverPos[2], SPEED_MODIFIER); }

        private float getOffset() { return (random.nextFloat() * 2.0F - 1.0F) * HOVER_POS_OFFSET; }

        /** findNearbyBlock(pred, 5): y 는 0,1,-1,2,-2…(아래로 1 칸 치우침), 수평은 껍질 순서로 — 처음 맞는 칸. */
        private Pos findNearbyFlower() {
            Pos o = blockPosition();
            double r = 5.0;
            for (int dy = 0; dy <= r; dy = dy > 0 ? -dy : 1 - dy) {
                for (int s = 0; s < r; s++) {
                    for (int dx = 0; dx <= s; dx = dx > 0 ? -dx : 1 - dx) {
                        for (int dz = dx < s && dx > -s ? s : 0; dz <= s; dz = dz > 0 ? -dz : 1 - dz) {
                            Pos p = o.offset(dx, dy - 1, dz);
                            double ddx = p.x() - o.x(), ddy = p.y() - o.y(), ddz = p.z() - o.z();
                            if (ddx * ddx + ddy * ddy + ddz * ddz < r * r && validFlower.test(level.getBlockState(p))) return p;
                        }
                    }
                }
            }
            return null;
        }
    }

    final class BeeWanderGoal extends Goal {
        BeeWanderGoal() { setFlags(EnumSet.of(Flag.MOVE)); }

        @Override public boolean canUse() { return navigation.isDone() && random.nextInt(10) == 0; }

        @Override public boolean canContinueToUse() { return navigation.isInProgress(); }

        @Override
        public void start() {
            double[] v = findPos();
            if (v != null) navigation.moveTo(navigation.createPath(new Pos((int) Math.floor(v[0]), (int) Math.floor(v[1]), (int) Math.floor(v[2])), 1), 1.0);
        }

        private double[] findPos() {
            double vx, vz;
            if (isHiveValid() && !closerThan(hivePos, 22)) {
                double dx = hivePos.x() + 0.5 - x(), dy = hivePos.y() + 0.5 - y(), dz = hivePos.z() + 0.5 - z();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                vx = len < 1.0E-5F ? 0.0 : dx / len;
                vz = len < 1.0E-5F ? 0.0 : dz / len;
            } else {
                double[] view = Clip.viewVector(xRot(), yRot());
                vx = view[0];
                vz = view[2];
            }
            double[] p = FlyingRandomPos.getAirPosTowards(Bee.this, 8, 7, vx, vz, (float) (Math.PI / 2), 3, 1);
            return p != null ? p : FlyingRandomPos.getHoverPos(Bee.this, 8, 4, -2, vx, vz, (float) (Math.PI / 2));
        }
    }

}
