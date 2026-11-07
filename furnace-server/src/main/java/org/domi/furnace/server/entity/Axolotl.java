// 바닐라 Axolotl: 브레인으로 움직이는 양서류. 물고기를 사냥하고, 맞으면 죽은 척하며, 플레이어를 도와 싸운다. 물 밖에서는 숨이 줄어든다.
// 죽은 척하는 동안은 적으로 보이지 않는다(canBeSeenAsEnemy — 바닐라 1.21.1 은 isPushable 을 바꾸지 않는다).
package org.domi.furnace.server.entity;

import java.util.Optional;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.effect.MobEffectInstance;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.entity.ai.control.SmoothSwimmingLookControl;
import org.domi.furnace.server.entity.ai.control.SmoothSwimmingMoveControl;
import org.domi.furnace.server.entity.ai.navigation.AmphibiousPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Axolotl extends Animal implements Bucketable {
    public static final int DATA_VARIANT = 17, DATA_PLAYING_DEAD = 18, FROM_BUCKET = 19;
    public static final int PLAYDEAD_TICKS = 200;
    public static final int TOTAL_AIR_SUPPLY = 6000;
    public static final int REHYDRATE_AIR_SUPPLY = 1800;
    /** 변종: 0 lucy, 1 wild, 2 gold, 3 cyan(흔함), 4 blue(희귀). */
    public static final String[] VARIANTS = {"lucy", "wild", "gold", "cyan", "blue"};

    /** 무리로 생성될 때 앞의 두 마리 변종을 공유하고, 셋째부터는 아기로 나온다. */
    public static class AxolotlGroupData extends AgeableMob.GroupData {
        public final int[] types;

        public AxolotlGroupData(int... types) {
            super(false);
            this.types = types;
        }

        public int getVariant(RandomSource r) { return types[r.nextInt(types.length)]; }
    }

    public Axolotl(ChunkLevel level, int id) {
        super(EntityType.AXOLOTL, level, id);
        setPathfindingMalus(PathType.WATER, 0.0F);
        moveControl = new AxolotlMoveControl(this);
        lookControl = new AxolotlLookControl(this, 20);
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Axolotl> makeBrain() {
        Brain<Axolotl> b = new Brain<>(java.util.List.of(MemoryModuleType.BREED_TARGET, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.LOOK_TARGET, MemoryModuleType.HURT_BY,
                MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.PLAY_DEAD_TICKS, MemoryModuleType.NEAREST_ATTACKABLE,
                MemoryModuleType.ATTACK_TARGET, MemoryModuleType.ATTACK_COOLING_DOWN, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
                MemoryModuleType.IS_TEMPTED, MemoryModuleType.HAS_HUNTING_COOLDOWN, MemoryModuleType.IS_PANICKING),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.NEAREST_ADULT, SensorType.HURT_BY, SensorType.AXOLOTL_ATTACKABLES, SensorType.AXOLOTL_TEMPTATIONS));
        return AxolotlAi.makeBrain(b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Axolotl> axolotlBrain() { return (Brain<Axolotl>) brain; }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) { return new AmphibiousPathNavigation(this, level); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 14.0).add(Attribute.MOVEMENT_SPEED, 1.0).add(Attribute.ATTACK_DAMAGE, 2.0).add(Attribute.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_VARIANT, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_PLAYING_DEAD, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(FROM_BUCKET, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", variant());
        tag.putBoolean("FromBucket", fromBucket());
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Variant"));
        setFromBucket(tag.getBoolean("FromBucket"));
    }

    @Override
    protected void updateSwimming() {
        if (!isPlayingDead()) super.updateSwimming();
    }

    @Override
    public GroupData finalizeSpawn(RandomSource r, DifficultyInstance difficulty, GroupData data) {
        boolean baby = false;
        if (data instanceof AxolotlGroupData g) {
            if (g.groupSize >= 2) baby = true;
        } else {
            data = new AxolotlGroupData(commonVariant(r), commonVariant(r));
        }
        setVariant(((AxolotlGroupData) data).getVariant(r));
        if (baby) setAge(-24000);
        return super.finalizeSpawn(r, difficulty, data);
    }

    /** Axolotl.finalizeSpawn: 양동이에서 나올 때는 아무것도 굴리지 않고 데이터를 그대로 돌려준다(Mob.finalizeSpawn 난수도 없다). */
    @Override
    public Object finalizeSpawnGroup(RandomSource r, DifficultyInstance difficulty, Object group, org.domi.furnace.server.entity.spawn.SpawnReason reason) {
        if (reason == org.domi.furnace.server.entity.spawn.SpawnReason.BUCKET) return group;
        return super.finalizeSpawnGroup(r, difficulty, group, reason);
    }

    @Override
    public void baseTick() {
        int air = getAirSupply();
        super.baseTick();
        if (!isNoAi()) handleAirSupply(air);
    }

    protected void handleAirSupply(int previousAir) {
        if (isAlive() && !isInWaterRainOrBubble()) {
            setAirSupply(previousAir - 1);
            if (getAirSupply() == -20) {
                setAirSupply(0);
                hurt(new Damage("dry_out", false, false, false), 2.0F);
            }
        } else {
            setAirSupply(getMaxAirSupply());
        }
    }

    @Override
    protected int increaseAirSupply(int air) { return Math.min(air + REHYDRATE_AIR_SUPPLY, getMaxAirSupply()); }

    @Override public int getMaxAirSupply() { return TOTAL_AIR_SUPPLY; }

    public int variant() { return (Integer) entityData.get(DATA_VARIANT); }

    public void setVariant(int v) { entityData.set(DATA_VARIANT, Math.floorMod(v, VARIANTS.length)); }

    private static int commonVariant(RandomSource r) { return r.nextInt(4); }

    private static int rareVariant(RandomSource r) { return 4 + r.nextInt(1); }

    public boolean isPlayingDead() { return (Boolean) entityData.get(DATA_PLAYING_DEAD); }

    /** Axolotl.canBeSeenAsEnemy(cgq): 죽은 척하면 몹이 대상으로 삼지 않는다. */
    @Override
    public boolean canBeSeenAsEnemy() { return !isPlayingDead() && super.canBeSeenAsEnemy(); }

    public void setPlayingDead(boolean v) { entityData.set(DATA_PLAYING_DEAD, v); }

    @Override public boolean fromBucket() { return (Boolean) entityData.get(FROM_BUCKET); }

    @Override public void setFromBucket(boolean v) { entityData.set(FROM_BUCKET, v); }

    /** Axolotl.requiresCustomPersistence: 양동이에서 나온 아홀로틀은 사라지지 않는다. */
    @Override protected boolean requiresCustomPersistence() { return super.requiresCustomPersistence() || fromBucket(); }

    @Override public ItemStack getBucketItemStack() { return stackOf("axolotl_bucket"); }

    @Override public String getPickupSound() { return "minecraft:item.bucket.fill_axolotl"; }

    /** Axolotl.mobInteract: 물 양동이면 잡고, 아니면 기본(먹이). */
    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        return Bucketable.bucketMobPickup(p, hand, held, this, region) || super.interact(p, hand, held, region);
    }

    /** Axolotl.usePlayerItem: 열대어 양동이를 먹이로 주면 물 양동이가 남는다(ItemUtils.createFilledResult, 중복 방지). */
    @Override
    protected void usePlayerItem(Player p, int hand, ItemStack held) {
        if (!org.domi.furnace.server.item.ItemTools.itemName(held).equals("minecraft:tropical_fish_bucket")) {
            super.usePlayerItem(p, hand, held);
            return;
        }
        var inv = p.inventory();
        int ws = hand == 0 ? inv.heldWindowSlot() : 45;
        ItemStack water = stackOf("water_bucket");
        if (p.vitals().instabuild()) {
            if (!inv.contains(water)) inv.add(water, false, 64, false);
            return;
        }
        ItemStack rest = held.withCount(held.count() - 1);
        if (rest.isEmpty()) {
            inv.set(ws, water);
        } else {
            inv.set(ws, rest);
            inv.add(water, false, 64, false);
        }
    }

    /** Axolotl.saveToBucketTag: 공통 데이터 + Variant·Age·HuntingCooldown(남은 틱). */
    @Override
    public ItemStack saveToBucketTag(ItemStack bucket) {
        ItemStack s = Bucketable.saveDefaultDataToBucketTag(this, bucket);
        NbtCompound tag = Bucketable.bucketTag(s);
        tag.putInt("Variant", variant());
        tag.putInt("Age", getAge());
        if (axolotlBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN)) {
            tag.putLong("HuntingCooldown", axolotlBrain().getTimeUntilExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN));
        }
        return Bucketable.withBucketTag(s, tag);
    }

    /** Axolotl.loadFromBucketTag: 공통 데이터 + Variant, Age·HuntingCooldown 은 있을 때만. */
    @Override
    public void loadFromBucketTag(NbtCompound tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        setVariant(tag.getInt("Variant"));
        if (tag.contains("Age")) setAge(tag.getInt("Age"));
        if (tag.contains("HuntingCooldown")) axolotlBrain().setMemoryWithExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN, true, tag.getLong("HuntingCooldown"));
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Axolotl child = new Axolotl(level, level.nextEntityId());
        int v;
        if (random.nextInt(1200) == 0) v = rareVariant(random);
        else v = random.nextBoolean() ? variant() : ((Axolotl) partner).variant();
        child.setVariant(v);
        child.persistenceRequired = true;
        return child;
    }

    @Override public Targetable getTarget() { return getTargetFromBrain(); }

    @Override public boolean isFood(ItemStack s) { return AxolotlAi.isFood(s); }

    @Override public boolean isPushedByFluid() { return false; }

    @Override public boolean removeWhenFarAway(double distSqr) { return !fromBucket() && !hasCustomName(); }

    @Override public int getMaxHeadXRot() { return 1; }

    @Override public int getMaxHeadYRot() { return 1; }

    @Override
    protected void customServerAiStep() {
        axolotlBrain().tick(level, this);
        AxolotlAi.updateActivity(this);
        if (!isNoAi()) {
            Optional<Integer> ticks = axolotlBrain().getMemory(MemoryModuleType.PLAY_DEAD_TICKS);
            setPlayingDead(ticks.isPresent() && ticks.get() > 0);
        }
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        float health = health();
        if (!isNoAi() && level.random().nextInt(3) == 0 && (level.random().nextInt(3) < amount || health / maxHealth() < 0.5F) && amount < health && isInWater()
                && (attacker != null || directCause() != null) && !isPlayingDead()) {
            axolotlBrain().setMemory(MemoryModuleType.PLAY_DEAD_TICKS, PLAYDEAD_TICKS);
        }
        return super.hurtBy(source, amount, attacker);
    }

    /** 공격 대상이 죽었을 때: 마지막으로 때린 플레이어가 20칸 안에 있으면 재생 효과를 준다. */
    static void onStopAttacking(Mob axolotl, Targetable target) {
        if (!target.isAlive() && target instanceof LivingEntity le && le.getLastDamageAttacker() instanceof Player killer) {
            boolean near = false;
            var box = axolotl.boundingBox().inflate(20.0);
            if (box.intersects(killer.boundingBox())) near = true;
            if (near) rewardPlayer(killer);
        }
    }

    private static void rewardPlayer(Player p) {
        var vitals = p.vitals();
        MobEffectInstance existing = vitals.getEffect(MobEffect.REGENERATION);
        if (existing == null || existing.duration() <= 2399) {
            int old = existing != null ? existing.duration() : 0;
            int d = Math.min(2400, 100 + old);
            vitals.addEffect(new MobEffectInstance(MobEffect.REGENERATION, d, 0));
        }
        vitals.removeEffect(MobEffect.MINING_FATIGUE);
    }

    @Override public String getAmbientSound() { return isInWater() ? "entity.axolotl.idle_water" : "entity.axolotl.idle_air"; }

    @Override protected String getHurtSound(Damage source) { return "entity.axolotl.hurt"; }

    @Override protected String getDeathSound() { return "entity.axolotl.death"; }

    private static class AxolotlMoveControl extends SmoothSwimmingMoveControl {
        private final Axolotl axolotl;

        AxolotlMoveControl(Axolotl axolotl) {
            super(axolotl, 85, 10, 0.1F, 0.5F, false);
            this.axolotl = axolotl;
        }

        @Override
        public void tick() {
            if (!axolotl.isPlayingDead()) super.tick();
        }
    }

    private static class AxolotlLookControl extends SmoothSwimmingLookControl {
        private final Axolotl axolotl;

        AxolotlLookControl(Axolotl axolotl, int maxYRotFromCenter) {
            super(axolotl, maxYRotFromCenter);
            this.axolotl = axolotl;
        }

        @Override
        public void tick() {
            if (!axolotl.isPlayingDead()) super.tick();
        }
    }

    @Override
    public boolean checkSpawnObstruction(ChunkLevel level) { return level.isUnobstructed(org.domi.furnace.server.entity.spawn.NaturalSpawner.shapeOf(boundingBox())); }
}
