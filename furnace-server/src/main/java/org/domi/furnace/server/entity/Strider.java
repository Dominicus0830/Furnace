// 바닐라 Strider(ckq): 용암 위를 걷는 네더 동물. 따뜻한 곳(용암·특정 블록)에서 벗어나면 떨며(suffocating) 느려진다. 뒤틀린 균류로 번식·유인.
// 안장을 얹고 타며, 뒤틀린 균류 막대를 들면 조종·가속한다(ItemBasedSteering). 이동은 클라이언트가 정하고 서버는 move_vehicle 로 받는다.
// 스폰 때 1/30 확률로 좀비 피글린 기수(뒤틀린 균류 막대·안장), 그 밖에 1/10 확률로 새끼 스트라이더 기수.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pathfinding;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.goal.*;
import org.domi.furnace.server.entity.ai.navigation.GroundPathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.SetEntityData;

public class Strider extends Animal {
    public static final int DATA_BOOST_TIME = 17, DATA_SUFFOCATING = 18, DATA_SADDLE_ID = 19;
    private static final String SUFFOCATING_MODIFIER_ID = "minecraft:suffocating";
    private static final AttributeModifier SUFFOCATING_MODIFIER = new AttributeModifier(SUFFOCATING_MODIFIER_ID, -0.34F, AttributeModifier.ADD_MULTIPLIED_BASE);

    public Strider(ChunkLevel level, int id) {
        super(EntityType.STRIDER, level, id);
        setPathfindingMalus(PathType.WATER, -1.0F);
        setPathfindingMalus(PathType.LAVA, 0.0F);
        setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
        setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
    }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 0.175F).add(Attribute.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_BOOST_TIME, SetEntityData.SERIALIZER_INT, 0);
        entityData.define(DATA_SUFFOCATING, SetEntityData.SERIALIZER_BOOLEAN, false);
        entityData.define(DATA_SADDLE_ID, SetEntityData.SERIALIZER_BOOLEAN, false);
    }

    private final ItemBasedSteering steering = new ItemBasedSteering(entityData, DATA_BOOST_TIME, DATA_SADDLE_ID);
    private TemptGoal temptGoal;

    @Override
    protected void addAdditionalSaveData(org.domi.furnace.server.net.io.nbt.NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        steering.addAdditionalSaveData(tag);
    }

    @Override
    protected void readAdditionalSaveData(org.domi.furnace.server.net.io.nbt.NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        steering.readAdditionalSaveData(tag);
    }

    /** Saddleable.isSaddled. */
    public boolean isSaddled() { return steering.hasSaddle(); }

    /** Saddleable.isSaddleable: 살아 있고 다 자랐다. */
    public boolean isSaddleable() { return isAlive() && !isBaby(); }

    /** Saddleable.equipSaddle(stack, source): 안장을 얹고, 소리 출처가 있으면 strider.saddle(0.5, 1.0). 기수 스폰은 출처 없이 부른다. */
    public void equipSaddle(String soundSource) {
        steering.setSaddle(true);
        if (soundSource != null) level.playSound(null, x(), y(), z(), "minecraft:entity.strider.saddle", soundSource, 0.5F, 1.0F);
    }

    /** Strider.boost: ItemBasedSteering.boost(엔티티 난수). 소유권 전제는 돼지와 같다(탄 플레이어와 같은 region). */
    public boolean boost() { return steering.boost(random); }

    /** 가속 중인가(시험·관측용). */
    public boolean isBoosting() { return steering.isBoosting(); }

    public boolean isSuffocating() { return (Boolean) entityData.get(DATA_SUFFOCATING); }

    public void setSuffocating(boolean v) {
        entityData.set(DATA_SUFFOCATING, v);
        var inst = attributes().get(Attribute.MOVEMENT_SPEED);
        if (inst != null) {
            if (v) inst.addOrReplaceModifier(SUFFOCATING_MODIFIER);
            else inst.removeModifier(SUFFOCATING_MODIFIER_ID);
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new PanicGoal(this, 1.65));
        goalSelector.addGoal(2, new BreedGoal(this, 1.0));
        temptGoal = new TemptGoal(this, 1.4, s -> ItemTags.is(s, "minecraft:strider_tempt_items"), false);
        goalSelector.addGoal(3, temptGoal);
        goalSelector.addGoal(4, new StriderGoToLavaGoal(1.0));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.0));
        goalSelector.addGoal(7, new RandomStrollGoal(this, 1.0, 60));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        goalSelector.addGoal(9, new LookAtMobGoal(this, Strider.class, 8.0F, 0.02F));
    }

    @Override
    public boolean canStandOnFluid(int fluid) { return Fluids.isLava(fluid); }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) {
        return new GroundPathNavigation(this, level) {
            @Override
            public boolean isStableDestination(Pos pos) { return Fluids.isLava(level.getFluidState(pos)) || super.isStableDestination(pos); }
        };
    }

    @Override
    public boolean isSensitiveToWater() { return true; }

    @Override
    public boolean isOnFire() { return false; }

    @Override
    public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:strider_food"); }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) { return new Strider(level, level.nextEntityId()); }

    @Override
    public float getWalkTargetValue(Pos pos, ChunkLevel level) {
        if (Fluids.isLava(level.getFluidState(pos))) return 10.0F;
        return isInLava() ? Float.NEGATIVE_INFINITY : 0.0F;
    }

    /** Strider.checkFallDamage: 칸 안 블록 동작을 먼저 보고, 용암 안이면 낙하 거리를 지운다. */
    @Override
    protected void checkFallDamage(double dy, boolean ground, int state, Pos pos) {
        checkInsideBlocks();
        if (isInLava()) resetFallDistance();
        else super.checkFallDamage(dy, ground, state, pos);
    }

    /** temptGoal.isRunning — 유혹당하는 중. */
    private boolean isBeingTempted() { return temptGoal != null && temptGoal.isRunning(); }

    /**
     * Strider.tick: 유혹 중이면 1/140 로 happy, 아니면 패닉 중 1/60 로 retreat(엔티티 난수). AI 가 있으면 따뜻한 곳이 아니거나
     * 탄 탈것이 떠는 스트라이더면 떤다. 그 뒤 기본 틱, 용암 위 뜨기, 칸 안 블록 동작.
     */
    @Override
    public void tick() {
        if (isBeingTempted() && random.nextInt(140) == 0) makeSound("minecraft:entity.strider.happy");
        else if (isPanicking() && random.nextInt(60) == 0) makeSound("minecraft:entity.strider.retreat");
        if (!isNoAi()) {
            int here = level.getBlockState(blockPosition());
            int below = level.getBlockState(getOnPosLegacy());
            boolean warm = B.isTag(here, "minecraft:strider_warm_blocks") || B.isTag(below, "minecraft:strider_warm_blocks") || fluidHeight(false) > 0.0;
            boolean vehicleSuffocating = vehicle() instanceof Strider s && s.isSuffocating();
            setSuffocating(!warm || vehicleSuffocating);
        }
        super.tick();
        floatStrider();
        checkInsideBlocks();
    }

    /** nextStep: moveDist + 0.6. */
    @Override
    protected float nextStep() { return moveDist + 0.6F; }

    /** shouldPassengersInheritMalus: 참(바닐라) — 탄 몹의 길찾기는 스트라이더의 비용표(용암 걷기)를 쓴다. */
    @Override
    public boolean shouldPassengersInheritMalus() { return true; }

    private void floatStrider() {
        if (isInLava()) {
            Pos p = blockPosition();
            if (y() > p.y() + 0.5 - 1.0E-5F && !Fluids.isLava(level.getFluidState(p.above()))) {
                setOnGround(true);
            } else {
                setDeltaMovement(vx() * 0.5, vy() * 0.5 + 0.05, vz() * 0.5);
            }
        }
    }

    /** getAmbientSound: 패닉도 유혹도 아니면 ambient. */
    @Override public String getAmbientSound() { return !isPanicking() && !isBeingTempted() ? "entity.strider.ambient" : null; }

    @Override protected String getHurtSound(Damage source) { return "entity.strider.hurt"; }

    @Override protected String getDeathSound() { return "entity.strider.death"; }

    // ------------------------------------------------------------------ 탑승·조종

    /** Strider.getControllingPassenger: 안장이 있고 첫 승객이 뒤틀린 균류 막대를 든 플레이어면 그 플레이어, 아니면 Mob 기본(좀비 피글린 기수 등). */
    @Override
    public Rider getControllingPassenger() {
        if (isSaddled() && firstPassenger() instanceof Player p && ItemBasedSteering.isHolding(p, "minecraft:warped_fungus_on_a_stick")) return p;
        return super.getControllingPassenger();
    }

    /** Strider.tickRidden: 탄 플레이어 방향(pitch 는 절반)을 따르고 가속 시간을 진행한다. 이동 입력·속도(getRiddenSpeed)는 클라이언트가 쓴다. */
    @Override
    protected void tickRidden(Player p) {
        setYRot(p.yaw());
        setXRot(p.pitch() * 0.5F);
        yRotO = yBodyRot = yHeadRot = yRot();
        steering.tickBoost();
        super.tickRidden(p);
    }

    /** getRiddenSpeed: 이동 속도 × (떨면 0.35, 아니면 0.55) × 가속 배율. 서버 이동에는 쓰지 않는다(클라이언트가 조종). */
    public float getRiddenSpeed() {
        return (float) (attributeValue(Attribute.MOVEMENT_SPEED) * (isSuffocating() ? 0.35F : 0.55F) * steering.boostFactor());
    }

    /** canAddPassenger: 승객이 없고 눈이 용암에 잠기지 않았다. */
    @Override
    protected boolean canAddPassenger(Rider r) { return !isVehicle() && !isEyeInLava(); }

    /**
     * Strider.getDismountLocationForPassenger: 승객 시선 방향과 ±22.5°·±45° 다섯 방향의 탈출 벡터마다, 몸통 꼭대기 높이부터
     * 바닥 아래 0.5 까지 한 칸씩 내려가며 후보 칸을 모은다(중복 제거, 순서 유지). 용암이 아닌 칸에서 바닥이 맞고
     * 승객 자세(플레이어는 서기·웅크리기·헤엄)의 몸이 끼지 않는 첫 자리. 없으면 (x, 몸통 꼭대기, z).
     * TODO(자세): 바닐라는 고른 자세로 승객 자세를 바꾼다.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(Rider r) {
        float pw = r instanceof Entity e ? e.bbWidth() : DismountHelper.PLAYER_WIDTH;
        float yaw = r.riderYRot();
        Vec3[] dirs = {
                collisionHorizontalEscapeVector(bbWidth(), pw, yaw),
                collisionHorizontalEscapeVector(bbWidth(), pw, yaw - 22.5F),
                collisionHorizontalEscapeVector(bbWidth(), pw, yaw + 22.5F),
                collisionHorizontalEscapeVector(bbWidth(), pw, yaw - 45.0F),
                collisionHorizontalEscapeVector(bbWidth(), pw, yaw + 45.0F)};
        java.util.LinkedHashSet<Pos> spots = new java.util.LinkedHashSet<>();
        double top = boundingBox().maxY(), bottom = boundingBox().minY() - 0.5;
        for (Vec3 d : dirs) {
            int bx = floor(x() + d.x()), bz = floor(z() + d.z());
            int by = floor(top);
            for (double yy = top; yy > bottom; yy--) spots.add(new Pos(bx, by--, bz));
        }
        for (Pos p : spots) {
            if (Fluids.isLava(level.getFluidState(p))) continue;
            double h = DismountHelper.blockFloorHeight(level, p);
            if (!DismountHelper.isBlockFloorValid(h)) continue;
            Vec3 at = new Vec3(p.x() + 0.5, p.y() + h, p.z() + 0.5);
            for (float[] dim : DismountHelper.dismountBounds(r)) {
                double hw = dim[0] / 2.0F;
                AABB box = new AABB(at.x() - hw, at.y(), at.z() - hw, at.x() + hw, at.y() + dim[1], at.z() + hw);
                if (DismountHelper.canDismountTo(level, box)) return at;
            }
        }
        return new Vec3(x(), boundingBox().maxY(), z());
    }

    /**
     * Strider.mobInteract: 먹이가 아니고 안장이 있고 승객이 없고 웅크리지 않았으면 탄다. 아니면 Animal(먹이·번식) — 그것이 처리 안 했으면
     * 안장 아이템을 얹는다(SaddleItem). 먹이로 처리했으면 조용하지 않을 때 strider.eat(1.0, 1 + (r-r)·0.2, 엔티티 난수).
     */
    @Override
    public boolean interact(Player p, int hand, ItemStack held, org.domi.furnace.server.world.Region region) {
        boolean food = isFood(held);
        if (!food && isSaddled() && !isVehicle() && !p.sneaking()) {
            p.startRiding(this, false);
            return true;
        }
        if (!super.interact(p, hand, held, region)) {
            if (!held.isEmpty() && ItemTools.itemName(held).equals("minecraft:saddle")) return saddleItemInteract(p, hand, held);
            return false;
        }
        if (food && !isSilent()) {
            level.playSound(null, x(), y(), z(), "minecraft:entity.strider.eat", soundSource(), 1.0F, 1.0F + (random.nextFloat() - random.nextFloat()) * 0.2F);
        }
        return true;
    }

    /** SaddleItem.interactLivingEntity: 살아 있고 안장이 없고 얹을 수 있으면 하나 써서 얹는다(소리 출처 neutral, 장착 게임 이벤트). 크리에이티브는 줄지 않는다. */
    private boolean saddleItemInteract(Player p, int hand, ItemStack held) {
        if (!isAlive() || isSaddled() || !isSaddleable()) return false;
        equipSaddle("neutral");
        gameEvent(org.domi.furnace.server.world.gameevent.GameEvent.EQUIP, this);
        if (!p.vitals().isCreative()) {
            var inv = p.inventory();
            inv.set(hand == 0 ? inv.heldWindowSlot() : 45, held.withCount(held.count() - 1));
        }
        return true;
    }

    /** dropEquipment: 안장이 있으면 안장을 떨군다. */
    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (isSaddled()) spawnAtLocation(new ItemStack(ItemContext.id("minecraft:item", "minecraft:saddle"), 1));
    }

    // ------------------------------------------------------------------ 스폰(finalizeSpawn)

    /**
     * Strider.finalizeSpawn: 새끼면 그대로. 아니면 레벨 난수로 nextInt(30)==0 → 좀비 피글린 기수(아기 여부 Zombie.getSpawnAsBabyOdds,
     * 기수 금지 그룹으로 JOCKEY 스폰 → 탑승 → 뒤틀린 균류 막대 → 내 안장, 소리 없음), 아니면 nextInt(10)==0 → 새끼 스트라이더 기수,
     * 아니면 아기 확률 0.5 그룹. 기수가 있으면 그룹은 아기 확률 0. 들어온 그룹 데이터는 (새끼가 아니면) 버린다.
     * 기수는 레벨에 넣지 않고 태우기만 한다 — 부른 쪽이 addFreshEntityWithPassengers 로 함께 넣는다.
     */
    @Override
    public GroupData finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty, GroupData data) {
        if (isBaby()) return super.finalizeSpawn(r, difficulty, data);
        if (r.nextInt(30) == 0) {
            ZombifiedPiglin rider = new ZombifiedPiglin(level, level.nextEntityId());
            boolean babyRider = Zombie.getSpawnAsBabyOdds(r);
            data = spawnJockey(rider, () -> rider.finalizeSpawnWithGroup(r, difficulty, babyRider, false));
            rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ItemContext.id("minecraft:item", "minecraft:warped_fungus_on_a_stick"), 1));
            equipSaddle(null);
        } else if (r.nextInt(10) == 0) {
            Strider baby = new Strider(level, level.nextEntityId());
            baby.setAge(BABY_START_AGE);
            data = spawnJockey(baby, () -> baby.finalizeSpawnGroup(r, difficulty, null));
        } else {
            data = new GroupData(0.5F);
        }
        return super.finalizeSpawn(r, difficulty, data);
    }

    /** spawnJockey: 내 자리·방향으로 옮기고 JOCKEY 스폰 마무리 뒤 강제로 태운다. 반환은 아기 확률 0 그룹. */
    private GroupData spawnJockey(Mob rider, Runnable finalize) {
        rider.moveTo(x(), y(), z(), yRot(), 0.0F);
        finalize.run();
        rider.startRiding(this, true);
        return new GroupData(0.0F);
    }

    /** StriderGoToLavaGoal: 용암 위(위가 뚫린 용암)로 걸어간다. */
    private final class StriderGoToLavaGoal extends MoveToBlockGoal {
        StriderGoToLavaGoal(double speed) { super(Strider.this, speed, 8, 2); }

        @Override protected Pos getMoveToTarget() { return blockPos; }

        @Override public boolean canContinueToUse() { return !isInLava() && isValidTarget(level, blockPos); }

        @Override public boolean canUse() { return !isInLava() && super.canUse(); }

        @Override public boolean shouldRecalculatePath() { return tryTicks % 20 == 0; }

        @Override
        protected boolean isValidTarget(BlockLevel lv, Pos pos) {
            return B.is(lv.getBlockState(pos), "minecraft:lava") && Pathfinding.isPathfindable(lv.getBlockState(pos.above()), Pathfinding.LAND);
        }
    }

    @Override
    public boolean checkSpawnObstruction(ChunkLevel level) { return level.isUnobstructed(org.domi.furnace.server.entity.spawn.NaturalSpawner.shapeOf(boundingBox())); }

    /** Strider.playStepSound: 용암 속이면 step_lava, 아니면 step. 크기 1(0.15 를 곱하지 않는다). */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) { playSound(isInLava() ? "minecraft:entity.strider.step_lava" : "minecraft:entity.strider.step", 1.0F, 1.0F); }
}
