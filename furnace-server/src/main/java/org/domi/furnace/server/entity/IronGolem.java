// 바닐라 IronGolem: 마을을 지키는 철 골렘(체력 100, 공격력 15, 넉백 저항 1). 적대 몹과 화난 대상을 공격하고, 때리면 상대를 공중으로 띄운다. 철 주괴로 25 회복.
// TODO(마을): 마을 기억(MoveBackToVillage·마을 안 서성이기의 마을 부분)·DefendVillage(평판)·밀치기(doPush: 적대 몹 1/20 확률로 공격 대상).
package org.domi.furnace.server.entity;

import java.util.UUID;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;
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
        // targetSelector 1: DefendVillageTargetGoal (평판 필요)
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Player.class, 10, true, false, this::isAngryAt));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, Mob.class, 5, false, false, t -> t instanceof Enemy && !(t instanceof Creeper)));
        targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
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

    /** GolemRandomStrollInVillageGoal: 난수 두 번을 쓴 뒤(30% 이면 아무 데나) 마을 쪽 목표를 찾는다. TODO(마을): 주민/POI 기반 목표는 없는 것으로 처리. */
    private static final class GolemRandomStrollInVillageGoal extends RandomStrollGoal {
        GolemRandomStrollInVillageGoal(IronGolem golem, double speed) { super(golem, speed, 240, false); }

        @Override
        protected double[] getPosition() {
            mob.level().random().nextFloat();
            mob.level().random().nextFloat();   // 0.3 미만이면 아무 데나, 아니면 주민·마을 쪽을 먼저 보지만 없으면 결국 아무 데나
            return DefaultRandomPos.getPos(mob, 10, 7);
        }
    }

    /** MoveBackToVillageGoal: 마을 구조물 밖이면 가까운 마을 쪽으로. TODO(마을): 마을 위치를 몰라 목표는 항상 없다(난수 1번은 쓴다). */
    private static final class MoveBackToVillageGoal extends RandomStrollGoal {
        MoveBackToVillageGoal(IronGolem golem, double speed, boolean checkNoActionTime) { super(golem, speed, 10, checkNoActionTime); }

        @Override
        protected double[] getPosition() { return null; }
    }
}
