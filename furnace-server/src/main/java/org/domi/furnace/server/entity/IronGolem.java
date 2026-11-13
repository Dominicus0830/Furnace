// 바닐라 IronGolem: 마을을 지키는 철 골렘(체력 100, 공격력 15, 넉백 저항 1). 적대 몹과 화난 대상을 공격하고, 때리면 상대를 공중으로 띄운다. 철 주괴로 25 회복.
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class IronGolem extends AbstractGolem implements NeutralMob {
    public static final int DATA_FLAGS_ID = 16;
    private static final TargetingConditions OFFER_FLOWER_TARGETING = TargetingConditions.forNonCombat().range(6.0);
    private int attackAnimationTick;
    private int offerFlowerTick;
    private int remainingPersistentAngerTime;
    private UUID persistentAngerTarget;

    public IronGolem(ChunkLevel level, int id) { super(EntityType.IRON_GOLEM, level, id); }

    /** IronGolem.checkSpawnObstruction: 아래 칸에 설 수 있고, 위 두 칸과 제자리(유체 무시)가 빈 스폰 칸이며 다른 엔티티와 겹치지 않는다. */
    @Override
    public boolean checkSpawnObstruction(ChunkLevel level) {
        org.domi.furnace.server.block.Pos p = blockPosition(), below = p.below();
        if (!org.domi.furnace.server.block.B.isFaceSturdy(level.getBlockState(below), level, below, org.domi.furnace.server.block.Dir.UP)) return false;
        for (int i = 1; i < 3; i++) {
            org.domi.furnace.server.block.Pos q = p.above(i);
            if (!org.domi.furnace.server.entity.spawn.SpawnPlacements.isValidEmptySpawnBlock(level, q, type().name())) return false;
        }
        int s = level.getBlockState(p);
        boolean empty = !org.domi.furnace.server.block.B.isCollisionShapeFullBlock(s) && !org.domi.furnace.server.block.B.behavior(s).isSignalSource(s)
                && !org.domi.furnace.server.block.B.isTag(s, "minecraft:prevent_mob_spawning_inside")
                && !org.domi.furnace.server.entity.spawn.SpawnPlacements.isBlockDangerous(type().name(), s);
        return empty && level.isUnobstructed(org.domi.furnace.server.entity.spawn.NaturalSpawner.shapeOf(boundingBox()));
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MAX_HEALTH, 100.0).add(Attribute.MOVEMENT_SPEED, 0.25).add(Attribute.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attribute.ATTACK_DAMAGE, 15.0).add(Attribute.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(2, new MoveTowardsTargetGoal(this, 0.9, 32.0F));
        goalSelector.addGoal(2, new MoveBackToVillageGoal(this, 0.6, false));
        goalSelector.addGoal(4, new GolemRandomStrollInVillageGoal(this, 0.6));
        goalSelector.addGoal(5, new OfferFlowerGoal());
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, 6.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new DefendVillageTargetGoal(this));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, this::isAngryAt));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Mob.class, 5, false, false, t -> t instanceof Enemy && !(t instanceof Creeper)));
        targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    /** IronGolem.doPush(cfq): 민 대상이 크리퍼가 아닌 적대 몹이면 1/20 확률로 공격 대상으로 삼는다. */
    @Override
    protected void doPush(Entity e) {
        if (e instanceof Enemy && !(e instanceof Creeper) && random.nextInt(20) == 0) setTarget((LivingEntity) e);
        super.doPush(e);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_FLAGS_ID, SetEntityData.SERIALIZER_BYTE, (byte) 0);
    }

    @Override
    protected int decreaseAirSupply(int air) { return air; }

    @Override
    protected void aiStep() {
        super.aiStep();
        if (attackAnimationTick > 0) attackAnimationTick--;
        if (offerFlowerTick > 0) offerFlowerTick--;
        updatePersistentAnger(true);
    }

    @Override
    public boolean canAttackType(Targetable t) {
        if (isPlayerCreated() && t.isPlayer()) return false;
        return !(t instanceof Creeper) && super.canAttackType(t);
    }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PlayerCreated", isPlayerCreated());
        addPersistentAngerSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setPlayerCreated(tag.getBoolean("PlayerCreated"));
        readPersistentAngerSaveData(tag);
    }

    @Override public void startPersistentAngerTimer() { setRemainingPersistentAngerTime(random.nextIntBetweenInclusive(20 * 20, 39 * 20)); }

    @Override public void setRemainingPersistentAngerTime(int t) { remainingPersistentAngerTime = t; }

    @Override public int getRemainingPersistentAngerTime() { return remainingPersistentAngerTime; }

    @Override public void setPersistentAngerTarget(UUID u) { persistentAngerTarget = u; }

    @Override public UUID getPersistentAngerTarget() { return persistentAngerTarget; }

    @Override public boolean canAttackTarget(Targetable t) { return canAttackType(t); }

    private float attackDamage() { return (float) attributeValue(Attribute.ATTACK_DAMAGE); }

    @Override
    public boolean doHurtTarget(Targetable t) {
        attackAnimationTick = 10;
        broadcastEntityEvent((byte) 4);
        float f = attackDamage();
        float dmg = (int) f > 0 ? f / 2.0F + random.nextInt((int) f) : f;
        boolean hurt = t.hurtByMob(new Damage("mob_attack", false, false, false), dmg, this);
        if (hurt) {
            double kbRes = t instanceof LivingEntity le ? le.attributeValue(Attribute.KNOCKBACK_RESISTANCE) : t instanceof Player ? 0.0 : 0.0;
            double d1 = Math.max(0.0, 1.0 - kbRes);
            t.addDeltaMovement(0.0, 0.4F * d1, 0.0);
        }
        playSound("entity.iron_golem.attack", 1.0F, 1.0F);
        return hurt;
    }

    /** Crackiness: 체력 비율로 금 단계(없음 → 낮음 → 중간 → 높음). */
    private int crackiness() {
        float r = health() / maxHealth();
        return r < 0.25F ? 3 : r < 0.5F ? 2 : r < 0.75F ? 1 : 0;
    }

    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        int before = crackiness();
        boolean hurt = super.hurtBy(source, amount, attacker);
        if (hurt && crackiness() != before) playSound("entity.iron_golem.damage", 1.0F, 1.0F);
        return hurt;
    }

    @Override protected String getHurtSound(Damage source) { return "entity.iron_golem.hurt"; }

    @Override protected String getDeathSound() { return "entity.iron_golem.death"; }

    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        if (held.isEmpty() || !ItemTools.itemName(held).equals("minecraft:iron_ingot")) return false;
        float h = health();
        heal(25.0F);
        if (health() == h) return false;
        float pitch = 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F;
        playSound("entity.iron_golem.repair", 1.0F, pitch);
        usePlayerItem(p, hand, held);
        return true;
    }

    public boolean isPlayerCreated() { return ((Byte) entityData.get(DATA_FLAGS_ID) & 1) != 0; }

    public void setPlayerCreated(boolean v) {
        byte b = (Byte) entityData.get(DATA_FLAGS_ID);
        entityData.set(DATA_FLAGS_ID, (byte) (v ? b | 1 : b & -2));
    }

    void offerFlower(boolean offer) {
        if (offer) {
            offerFlowerTick = 400;
            broadcastEntityEvent((byte) 11);
        } else {
            offerFlowerTick = 0;
            broadcastEntityEvent((byte) 34);
        }
    }

    /** OfferFlowerGoal: 낮에 가끔(1/8000) 근처 주민에게 꽃을 내민다(400틱). */
    private final class OfferFlowerGoal extends org.domi.furnace.server.entity.ai.Goal {
        private Villager villager;
        private int tick;

        OfferFlowerGoal() { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            if (!level.isDay()) return false;
            if (random.nextInt(8000) != 0) return false;
            villager = level.getNearestEntity(Villager.class, OFFER_FLOWER_TARGETING, IronGolem.this, x(), y(), z(), boundingBox().inflate(6.0, 2.0, 6.0));
            return villager != null;
        }

        @Override public boolean canContinueToUse() { return tick > 0; }

        @Override
        public void start() {
            tick = adjustedTickDelay(400);
            offerFlower(true);
        }

        @Override
        public void stop() {
            offerFlower(false);
            villager = null;
        }

        @Override
        public void tick() {
            getLookControl().setLookAt(villager, 30.0F, 30.0F);
            tick--;
        }
    }

    /** 바닐라 GolemRandomStrollInVillageGoal: 30% 는 아무 데나, 나머지는 골렘을 원하는 주민 쪽과 마을 섹션의 점유 POI 쪽 중 하나(70%/30% 로 먼저 볼 쪽)를 고른다. */
    static final class GolemRandomStrollInVillageGoal extends RandomStrollGoal {
        GolemRandomStrollInVillageGoal(IronGolem golem, double speed) { super(golem, speed, 240, false); }

        @Override
        protected double[] getPosition() {
            float f = mob.level().random().nextFloat();
            if (mob.level().random().nextFloat() < 0.3F) return getPositionTowardsAnywhere();
            double[] v;
            if (f < 0.7F) {
                v = getPositionTowardsVillagerWhoWantsGolem();
                if (v == null) v = getPositionTowardsPoi();
            } else {
                v = getPositionTowardsPoi();
                if (v == null) v = getPositionTowardsVillagerWhoWantsGolem();
            }
            return v == null ? getPositionTowardsAnywhere() : v;
        }

        private double[] getPositionTowardsAnywhere() { return LandRandomPos.getPos(mob, 10, 7); }

        private double[] getPositionTowardsVillagerWhoWantsGolem() {
            ChunkLevel level = mob.level();
            java.util.List<Villager> list = level.getEntitiesOfClass(Villager.class, mob.boundingBox().inflate(32.0),
                    v -> v.type() == EntityType.VILLAGER && v.wantsToSpawnGolem(level.gameTime()));
            if (list.isEmpty()) return null;
            Villager v = list.get(level.random().nextInt(list.size()));
            return LandRandomPos.getPosTowards(mob, 10, 7, new double[] {v.x(), v.y(), v.z()}, (float) (Math.PI / 2));
        }

        private double[] getPositionTowardsPoi() {
            int[] section = getRandomVillageSection();
            if (section == null) return null;
            Pos poi = getRandomPoiWithinSection(section);
            return poi == null ? null : LandRandomPos.getPosTowards(mob, 10, 7, new double[] {poi.x() + 0.5, poi.y(), poi.z() + 0.5}, (float) (Math.PI / 2));
        }

        /** SectionPos.cube(골렘 섹션, 2) 중 마을 거리 0 인 섹션(x → y → z 순) 하나를 무작위로. */
        private int[] getRandomVillageSection() {
            ChunkLevel level = mob.level();
            Pos p = mob.blockPosition();
            int sx = p.x() >> 4, sy = p.y() >> 4, sz = p.z() >> 4;
            java.util.List<int[]> list = new java.util.ArrayList<>();
            for (int z = sz - 2; z <= sz + 2; z++)
                for (int y = sy - 2; y <= sy + 2; y++)
                    for (int x = sx - 2; x <= sx + 2; x++)
                        if (level.poiManager().isVillageCenterSection(x, y, z)) list.add(new int[] {x, y, z});
            return list.isEmpty() ? null : list.get(level.random().nextInt(list.size()));
        }

        private Pos getRandomPoiWithinSection(int[] s) {
            ChunkLevel level = mob.level();
            Pos center = new Pos((s[0] << 4) + 8, (s[1] << 4) + 8, (s[2] << 4) + 8);
            java.util.List<org.domi.furnace.server.world.poi.PoiRecord> list = level.poiManager().getInRange(t -> true, center, 8,
                    org.domi.furnace.server.world.poi.PoiManager.Occupancy.IS_OCCUPIED);
            return list.isEmpty() ? null : list.get(level.random().nextInt(list.size())).pos();
        }
    }

    /** 바닐라 MoveBackToVillageGoal: 마을 칸이 아니면 둘레 2섹션 중 마을에 더 가까운 섹션 가운데 쪽으로 걷는다. */
    static final class MoveBackToVillageGoal extends RandomStrollGoal {
        MoveBackToVillageGoal(IronGolem golem, double speed, boolean checkNoActionTime) { super(golem, speed, 10, checkNoActionTime); }

        @Override
        public boolean canUse() { return mob.level().isVillage(mob.blockPosition()) ? false : super.canUse(); }

        @Override
        protected double[] getPosition() {
            Pos p = mob.blockPosition();
            int[] sp = {p.x() >> 4, p.y() >> 4, p.z() >> 4};
            int[] best = org.domi.furnace.server.entity.ai.behavior.VillageBoundRandomStroll.findSectionClosestToVillage(mob.level().poiManager(), sp, 2);
            if (best == sp) return null;
            return DefaultRandomPos.getPosTowards(mob, 10, 7, new double[] {(best[0] << 4) + 8 + 0.5, (best[1] << 4) + 8, (best[2] << 4) + 8 + 0.5}, (float) (Math.PI / 2));
        }
    }

    /** 바닐라 DefendVillageTargetGoal: 둘레(10,8,10) 주민 중 누구에게든 평판 -100 이하인 플레이어(마지막 것)를 노린다. 찾은 대상은 지우지 않는다(바닐라 그대로). */
    static final class DefendVillageTargetGoal extends TargetGoal {
        private final IronGolem golem;
        private Targetable potentialTarget;
        private final TargetingConditions attackTargeting = TargetingConditions.forCombat().range(64.0);

        DefendVillageTargetGoal(IronGolem golem) {
            super(golem, false, true);
            this.golem = golem;
            setFlags(java.util.EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            AABB box = golem.boundingBox().inflate(10.0, 8.0, 10.0);
            ChunkLevel level = golem.level();
            java.util.List<Villager> villagers = level.getEntitiesOfClass(Villager.class, box, v -> true);
            villagers.removeIf(v -> !attackTargeting.test(golem, v));
            java.util.List<Player> players = new java.util.ArrayList<>();
            level.forEachPlayerIn(box, players::add);   // 바닐라 getNearbyPlayers: 위치가 상자 안인 플레이어
            players.removeIf(pl -> !box.contains(pl.x(), pl.y(), pl.z()) || !attackTargeting.test(golem, pl));
            for (Villager v : villagers) {
                for (Player pl : players) {
                    if (v.getPlayerReputation(pl) <= -100) potentialTarget = pl;
                }
            }
            if (potentialTarget == null) return false;
            return !(potentialTarget instanceof Player pl) || pl.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR
                    && pl.gameMode() != org.domi.furnace.server.world.GameMode.CREATIVE;
        }

        @Override
        public void start() {
            golem.setTarget(potentialTarget);
            super.start();
        }
    }

    /** IronGolem.playStepSound: iron_golem.step 크기 1(0.15 를 곱하지 않는다). */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) { playSound("minecraft:entity.iron_golem.step", 1.0F, 1.0F); }
}
