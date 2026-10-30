// 바닐라 Zombie: 적대 몹. 플레이어·주민을 쫓아 때리고, 아기는 빠르고 작으며, 소리·저장을 갖는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.goal.HurtByTargetGoal;
import org.domi.furnace.server.entity.ai.goal.LookAtPlayerGoal;
import org.domi.furnace.server.entity.ai.goal.NearestAttackableTargetGoal;
import org.domi.furnace.server.entity.ai.goal.RandomLookAroundGoal;
import org.domi.furnace.server.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import org.domi.furnace.server.entity.ai.goal.ZombieAttackGoal;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Zombie extends Monster {
    /** Zombie 엔티티 데이터: 16 아기, 17 특수 종류(int), 18 드라운드 변환 중. */
    public static final int DATA_BABY = 16, DATA_SPECIAL_TYPE = 17, DATA_DROWNED_CONVERSION = 18;
    private static final String BABY_MODIFIER_ID = "minecraft:baby";

    private boolean canBreakDoors;

    public Zombie(ChunkLevel level, int id) { this(EntityType.ZOMBIE, level, id); }

    protected Zombie(EntityType type, ChunkLevel level, int id) { super(type, level, id); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.FOLLOW_RANGE, 35.0).add(Attribute.MOVEMENT_SPEED, 0.23F).add(Attribute.ATTACK_DAMAGE, 3.0).add(Attribute.ARMOR, 2.0)
                .add(Attribute.SPAWN_REINFORCEMENTS, 0.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BABY, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_SPECIAL_TYPE, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_DROWNED_CONVERSION, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(4, new ZombieAttackTurtleEggGoal(this, 1.0, 3));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        addBehaviourGoals();
    }

    /** 바닐라 Zombie.ZombieAttackTurtleEggGoal: 거북 알을 밟아 깬다(도착 거리 1.14). */
    final class ZombieAttackTurtleEggGoal extends org.domi.furnace.server.entity.ai.goal.RemoveBlockGoal {
        ZombieAttackTurtleEggGoal(PathfinderMob mob, double speed, int verticalRange) { super("minecraft:turtle_egg", mob, speed, verticalRange); }

        @Override
        public void playDestroyProgressSound(ChunkLevel level, org.domi.furnace.server.block.Pos pos) {
            level.playSound(null, pos, "minecraft:entity.zombie.destroy_egg", "hostile", 0.5F, 0.9F + Zombie.this.random.nextFloat() * 0.2F);
        }

        @Override
        public void playBreakSound(ChunkLevel level, org.domi.furnace.server.block.Pos pos) {
            level.playSound(null, pos, "minecraft:entity.turtle.egg_break", "blocks", 0.7F, 0.9F + level.random().nextFloat() * 0.2F);
        }

        @Override
        public double acceptedDistance() { return 1.14; }
    }

    protected void addBehaviourGoals() {
        goalSelector.addGoal(2, new ZombieAttackGoal(this, 1.0, false));
        goalSelector.addGoal(6, new org.domi.furnace.server.entity.ai.goal.MoveThroughVillageGoal(this, 1.0, true, 4, this::canBreakDoors));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal(this, Player.class, true));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, PathfinderMob.class, false, Villager::isAbstractVillager));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal(this, IronGolem.class, true));
        targetSelector.addGoal(5, new NearestAttackableTargetGoal(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    /** 햇빛에 타는 몹인가(Zombie.isSunSensitive). 허스크는 거짓. */
    protected boolean isSunSensitive() { return true; }

    /** Zombie.aiStep: 낮에 하늘이 뚫린 곳이면 머리 방어구를 깎거나(0~1) 없으면 8초간 불이 붙는다. */
    @Override
    protected void aiStep() {
        if (isAlive()) {
            boolean burn = isSunSensitive() && isSunBurnTick();
            if (burn) {
                ItemStack helmet = getItemBySlot(EquipmentSlot.HEAD);
                if (!helmet.isEmpty()) {
                    if (org.domi.furnace.server.item.ItemTools.isDamageable(helmet)) {
                        helmet = org.domi.furnace.server.item.ItemTools.withDamage(helmet, org.domi.furnace.server.item.ItemTools.damage(helmet) + random.nextInt(2));
                        if (org.domi.furnace.server.item.ItemTools.damage(helmet) >= org.domi.furnace.server.item.ItemTools.maxDamage(helmet)) {
                            broadcastEntityEvent((byte) 52);   // 머리 장비 부서짐
                            setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                        } else {
                            setItemSlot(EquipmentSlot.HEAD, helmet);
                        }
                    }
                    burn = false;
                }
                if (burn) igniteForSeconds(8.0F);
            }
        }
        super.aiStep();
    }

    /** Zombie.doHurtTarget: 맞췄고 빈손이며 불붙은 상태면 난이도 비례 확률로 상대에게 불을 옮긴다. */
    /**
     * Zombie.hurt: 맞았고 대상(지금 노리는 것, 없으면 때린 쪽)이 있으며 어려움이고 doMobSpawning 이면
     * 확률(지원군 속성)로 7~40칸 밖에 좀비를 불러온다. 부른 쪽은 -0.05, 불려온 쪽은 -0.05 의 영구 보정.
     */
    @Override
    public boolean hurtBy(Damage source, float amount, Targetable attacker) {
        if (!super.hurtBy(source, amount, attacker)) return false;
        if (level == null) return false;
        Targetable target = getTarget();
        if (target == null) target = attacker;
        if (target != null && level.difficulty() == 3 && random.nextFloat() < attributeValue(Attribute.SPAWN_REINFORCEMENTS) && level.gameRule("doMobSpawning")) {
            int bx = floor(x()), by = floor(y()), bz = floor(z());
            Zombie zombie = new Zombie(level, level.nextEntityId());
            for (int i = 0; i < 50; i++) {
                int x = bx + nextIntBetween(7, 40) * nextIntBetween(-1, 1);
                int y = by + nextIntBetween(7, 40) * nextIntBetween(-1, 1);
                int z = bz + nextIntBetween(7, 40) * nextIntBetween(-1, 1);
                org.domi.furnace.server.block.Pos pos = new org.domi.furnace.server.block.Pos(x, y, z);
                if (org.domi.furnace.server.entity.spawn.SpawnPlacements.isSpawnPositionOk(type().name(), level, pos)
                        && org.domi.furnace.server.entity.spawn.SpawnPlacements.checkSpawnRules(type().name(), level,
                                org.domi.furnace.server.entity.spawn.SpawnReason.REINFORCEMENT, pos, level.random())) {
                    zombie.setPos(x, y, z);
                    if (!hasNearbyAlivePlayer(x, y, z, 7.0) && zombie.checkSpawnObstruction(level) && level.noCollision(zombie.boundingBox())) {
                        zombie.setTarget(target);
                        zombie.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(zombie.blockPosition()));
                        level.addFreshEntityWithPassengers(zombie);
                        var chance = attributes().get(Attribute.SPAWN_REINFORCEMENTS);
                        var caller = chance.getModifier("minecraft:reinforcement_caller_charge");
                        double charge = caller != null ? caller.amount() : 0.0;
                        chance.removeModifier("minecraft:reinforcement_caller_charge");
                        chance.addOrReplaceModifier(new AttributeModifier("minecraft:reinforcement_caller_charge", charge - 0.05, AttributeModifier.ADD_VALUE));
                        zombie.attributes().get(Attribute.SPAWN_REINFORCEMENTS)
                                .addOrReplaceModifier(new AttributeModifier("minecraft:reinforcement_callee_charge", -0.05F, AttributeModifier.ADD_VALUE));
                        break;
                    }
                }
            }
        }
        return true;
    }

    /** Mth.nextInt(random, min, max). */
    private int nextIntBetween(int min, int max) { return min >= max ? min : random.nextInt(max - min + 1) + min; }

    /** Level.hasNearbyAlivePlayer: 관전자가 아닌 산 플레이어가 거리 안에 있나. */
    private boolean hasNearbyAlivePlayer(double x, double y, double z, double dist) {
        boolean[] found = {false};
        level.forEachPlayerIn(new AABB(x - dist, y - dist, z - dist, x + dist, y + dist, z + dist), p -> {
            double dx = p.x() - x, dy = p.y() - y, dz = p.z() - z;
            if (p.vitals().health() > 0.0F && p.gameMode() != org.domi.furnace.server.world.GameMode.SPECTATOR && dx * dx + dy * dy + dz * dz < dist * dist) found[0] = true;
        });
        return found[0];
    }

    @Override
    public boolean doHurtTarget(Targetable t) {
        boolean hit = super.doHurtTarget(t);
        if (hit) {
            float f = level.getCurrentDifficultyAt(blockPosition()).effectiveDifficulty();
            if (getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() && isOnFire() && random.nextFloat() < f * 0.3F && t instanceof Entity e) {
                e.igniteForSeconds(2 * (int) f);
            }
        }
        return hit;
    }

    /** Zombie.wasKilled: 주민을 죽이면 난이도(보통 50%, 어려움 100%)에 따라 좀비 주민으로 바꾼다. 바꿨으면 false(드롭 없음). */
    @Override
    public boolean killedEntity(LivingEntity victim) {
        boolean ok = super.killedEntity(victim);
        int diff = level.difficulty();
        if ((diff == 2 || diff == 3) && victim instanceof Villager v) {
            if (diff != 3 && random.nextBoolean()) return ok;
            ZombieVillager zv = new ZombieVillager(level, level.nextEntityId());
            zv.moveTo(v.x(), v.y(), v.z(), v.yRot(), v.xRot());
            int[] d = v.villagerData();
            zv.setVillagerData(Villager.TYPES.get(d[0]), Villager.PROFESSIONS.get(d[1]), d[2]);
            zv.setGossips(v.getGossips().store());
            zv.setOffers(v.offers());
            zv.setVillagerXp(v.villagerXp());
            zv.finalizeSpawnForConversion(level.random(), level.getCurrentDifficultyAt(zv.blockPosition()));
            zv.setVillagerData(Villager.TYPES.get(d[0]), Villager.PROFESSIONS.get(d[1]), d[2]);
            zv.setBabyForConversion(v.isBaby());
            zv.setNoAi(v.isNoAi());
            zv.persistenceRequired = true;
            v.discard();
            level.addFreshEntity(zv);
            if (!isSilent()) level.levelEvent(null, 1026, blockPosition(), 0);
            ok = false;
        }
        return ok;
    }

    public boolean canBreakDoors() { return canBreakDoors; }

    /** Zombie.DOOR_BREAKING_PREDICATE: 어려움(3)에서만. */
    private final org.domi.furnace.server.entity.ai.goal.BreakDoorGoal breakDoorGoal =
            new org.domi.furnace.server.entity.ai.goal.BreakDoorGoal(this, difficulty -> difficulty == 3);

    /** Zombie.setCanBreakDoors: 땅 길찾기일 때만, 문을 열 수 있게 하고 문 부수기 목표(우선순위 1)를 붙이거나 뗀다. */
    public void setCanBreakDoors(boolean v) {
        if (supportsBreakDoorGoal() && org.domi.furnace.server.entity.ai.util.GoalUtils.hasGroundPathNavigation(this)) {
            if (canBreakDoors != v) {
                canBreakDoors = v;
                ((org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation) getNavigation()).setCanOpenDoors(v);
                if (v) goalSelector.addGoal(1, breakDoorGoal);
                else goalSelector.removeGoal(breakDoorGoal);
            }
        } else if (canBreakDoors) {
            goalSelector.removeGoal(breakDoorGoal);
            canBreakDoors = false;
        }
    }

    protected boolean supportsBreakDoorGoal() { return true; }

    // ---- 물속 변환 (Zombie.tick / startUnderWaterConversion / doUnderWaterConversion)
    private int inWaterTime;
    private int conversionTime;

    /** 물속에 600틱 있으면 변환을 시작하는 몹인가(Zombie.convertsInWater). 좀비·허스크 참, 드라운드·좀비 피글린 거짓. */
    protected boolean convertsInWater() { return true; }

    public boolean isUnderWaterConverting() { return (Boolean) entityData.get(DATA_DROWNED_CONVERSION); }

    private void startUnderWaterConversion(int ticks) {
        conversionTime = ticks;
        entityData.set(DATA_DROWNED_CONVERSION, true);
    }

    /** 변환 결과 몹을 만들어 돌려준다(없으면 null). 좀비 → 드라운드(TODO), 허스크 → 좀비. */
    protected Zombie createConversionTarget() { return new Drowned(level, level.nextEntityId()); }

    protected void doUnderWaterConversion() {
        convertToZombieType(createConversionTarget());
    }

    protected void convertToZombieType(Zombie target) {
        if (target == null) return;
        Zombie z = convertTo(target, true);
        if (z != null) {
            z.handleAttributes(z.level.getCurrentDifficultyAt(z.blockPosition()).specialMultiplier());
            z.setCanBreakDoors(z.supportsBreakDoorGoal() && canBreakDoors());
            if (!isSilent()) level.levelEvent(null, 1040, blockPosition(), 0);
        }
    }

    @Override
    protected void setBabyForConversion(boolean baby) { setBaby(baby); }

    @Override
    public void tick() {
        if (isAlive() && !isNoAi()) {
            if (isUnderWaterConverting()) {
                conversionTime--;
                if (conversionTime < 0) doUnderWaterConversion();
            } else if (convertsInWater()) {
                if (isEyeInWater()) {
                    inWaterTime++;
                    if (inWaterTime >= 600) startUnderWaterConversion(300);
                } else {
                    inWaterTime = -1;
                }
            }
        }
        super.tick();
    }

    /** 아기 확률 5%(Zombie.getSpawnAsBabyOdds). */
    public static boolean getSpawnAsBabyOdds(org.domi.furnace.server.block.RandomSource r) { return r.nextFloat() < 0.05F; }

    /**
     * Zombie.finalizeSpawn: Mob 기본 → 줍기 가능(0.55×배율) → 아기(5%) → 문 부수기 → 기본 장비·마법 → (10월 31일 호박) → 영구 속성 무작위화.
     * 레벨 난수와 엔티티 난수를 바닐라와 같은 순서로 쓴다.
     */
    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        float special = difficulty.specialMultiplier();
        setCanPickUpLoot(r.nextFloat() < 0.55F * special);
        boolean baby = getSpawnAsBabyOdds(r);   // spawnData == null 이면 ZombieGroupData(아기 확률, nextBoolean) 를 새로 만든다
        boolean jockey = r.nextBoolean();
        finishFinalizeSpawn(r, difficulty, baby, jockey);
    }

    /** 감염·변환으로 만든 좀비용: 그룹 데이터 (false, true) 라서 아기·기수 난수를 뽑지 않는다. */
    public void finalizeSpawnForConversion(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        float special = difficulty.specialMultiplier();
        setCanPickUpLoot(r.nextFloat() < 0.55F * special);
        finishFinalizeSpawn(r, difficulty, false, true);
    }

    private void finishFinalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, boolean baby, boolean jockey) {
        float special = difficulty.specialMultiplier();
        if (baby) {
            setBaby(true);
            if (jockey) {
                if (r.nextFloat() < 0.05) {   // 5 × 3 × 5 안의 아무도 안 탄 닭에 탄다
                    var list = level.getEntitiesOfClass(Chicken.class, boundingBox().inflate(5.0, 3.0, 5.0), c -> c.isAlive() && !c.isVehicle() && !c.isPassenger());
                    if (!list.isEmpty()) {
                        Chicken c = list.get(0);
                        c.setChickenJockey(true);
                        startRiding(c);
                    }
                } else if (r.nextFloat() < 0.05) {   // 새 닭(JOCKEY 스폰)을 만들어 탄다
                    Chicken c = new Chicken(level, level.nextEntityId());
                    c.moveTo(x(), y(), z(), yRot(), 0.0F);
                    c.finalizeSpawnGroup(r, difficulty, null);
                    c.setChickenJockey(true);
                    startRiding(c);
                    level.addFreshEntity(c);
                }
            }
        }
        setCanBreakDoors(supportsBreakDoorGoal() && r.nextFloat() < special * 0.1F);
        populateDefaultEquipmentSlots(r, difficulty);
        populateDefaultEquipmentEnchantments(r, difficulty);
        if (getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (today.getMonthValue() == 10 && today.getDayOfMonth() == 31 && r.nextFloat() < 0.25F) {
                setItemSlot(EquipmentSlot.HEAD, stackOf(r.nextFloat() < 0.1F ? "minecraft:jack_o_lantern" : "minecraft:carved_pumpkin"));
                setDropChance(EquipmentSlot.HEAD, 0.0F);
            }
        }
        handleAttributes(special);
    }

    @Override
    protected void populateDefaultEquipmentSlots(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.populateDefaultEquipmentSlots(r, difficulty);
        if (r.nextFloat() < (level.difficulty() == 3 ? 0.05F : 0.01F)) {
            int i = r.nextInt(3);
            setItemSlot(EquipmentSlot.MAINHAND, stackOf(i == 0 ? "minecraft:iron_sword" : "minecraft:iron_shovel"));
        }
    }

    /** Zombie.handleAttributes: 지원군 확률·밀려남 저항·따라가기 범위·리더 좀비 보너스(엔티티 난수). */
    protected void handleAttributes(float special) {
        randomizeReinforcementsChance();
        attributes().get(Attribute.KNOCKBACK_RESISTANCE).addOrReplaceModifier(new AttributeModifier("minecraft:random_spawn_bonus", random.nextDouble() * 0.05F,
                AttributeModifier.ADD_VALUE));
        double d = random.nextDouble() * 1.5 * special;
        if (d > 1.0) {
            attributes().get(Attribute.FOLLOW_RANGE).addOrReplaceModifier(new AttributeModifier("minecraft:zombie_random_spawn_bonus", d,
                    AttributeModifier.ADD_MULTIPLIED_TOTAL));
        }
        if (random.nextFloat() < special * 0.05F) {
            attributes().get(Attribute.SPAWN_REINFORCEMENTS).addOrReplaceModifier(new AttributeModifier("minecraft:leader_zombie_bonus", random.nextDouble() * 0.25 + 0.5,
                    AttributeModifier.ADD_VALUE));
            attributes().get(Attribute.MAX_HEALTH).addOrReplaceModifier(new AttributeModifier("minecraft:leader_zombie_bonus", random.nextDouble() * 3.0 + 1.0,
                    AttributeModifier.ADD_MULTIPLIED_TOTAL));
            setCanBreakDoors(supportsBreakDoorGoal());
        }
    }

    protected void randomizeReinforcementsChance() { attributes().get(Attribute.SPAWN_REINFORCEMENTS).setBaseValue(random.nextDouble() * 0.1F); }

    @Override
    public boolean isBaby() { return (Boolean) entityData.get(DATA_BABY); }

    public void setBaby(boolean baby) {
        entityData.set(DATA_BABY, baby);
        var speed = attributes().get(Attribute.MOVEMENT_SPEED);
        speed.removeModifier(BABY_MODIFIER_ID);
        if (baby) speed.addOrReplaceModifier(new AttributeModifier(BABY_MODIFIER_ID, 0.5, AttributeModifier.ADD_MULTIPLIED_BASE));
        if (baby) setDimensions(0.3F, 0.975F, 0.93F);   // 크기 0.5 배, 눈높이 0.93
        else setDimensions(0.6F, 1.95F, 1.74F);
    }

    /** Zombie.getExperienceReward: 아기는 xpReward 를 2.5 배로(바닐라는 필드 자체를 키운다). */
    @Override
    protected int getExperienceReward() {
        if (isBaby()) xpReward = (int) (xpReward * 2.5);
        return super.getExperienceReward();
    }

    @Override public int getAmbientSoundInterval() { return 80; }

    @Override protected String getAmbientSound() { return "entity.zombie.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.zombie.hurt"; }

    @Override protected String getDeathSound() { return "entity.zombie.death"; }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("IsBaby", isBaby());
        tag.putBoolean("CanBreakDoors", canBreakDoors);
        tag.putInt("InWaterTime", isInWater() ? inWaterTime : -1);
        tag.putInt("DrownedConversionTime", isUnderWaterConverting() ? conversionTime : -1);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        setBaby(tag.getBoolean("IsBaby"));
        canBreakDoors = tag.getBoolean("CanBreakDoors");
        inWaterTime = tag.getIntOr0("InWaterTime");   // 바닐라 getInt: 없으면 0(구조물이 남긴 좀비 주민 NBT 등)
        if (tag.contains("DrownedConversionTime") && tag.getInt("DrownedConversionTime") > -1) startUnderWaterConversion(tag.getInt("DrownedConversionTime"));
    }
}
