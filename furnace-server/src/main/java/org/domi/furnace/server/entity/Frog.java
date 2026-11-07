// 바닐라 Frog: 브레인으로 움직이는 양서류. 슬라임/작은 마그마 큐브를 혀로 잡아먹고, 길게 뛰며, 물가에 개구리알을 낳는다. 변종은 바이옴에 따라(온대/온난/한랭).
// TODO(마그마 큐브·개구리 조명): 큰 마그마 큐브를 먹고 개구리 조명을 떨구는 것과 애니메이션 상태는 클라이언트 연출이라 생략.
package org.domi.furnace.server.entity;

import java.util.Optional;
import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.attribute.AttributeMap;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.entity.ai.brain.SensorType;
import org.domi.furnace.server.entity.ai.brain.Unit;
import org.domi.furnace.server.entity.ai.control.LookControl;
import org.domi.furnace.server.entity.ai.control.SmoothSwimmingMoveControl;
import org.domi.furnace.server.entity.ai.navigation.*;
import org.domi.furnace.server.item.ItemTags;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.play.SetEntityData;
import org.domi.furnace.server.world.gen.BiomeTags;

public class Frog extends Animal {
    public static final int DATA_VARIANT_ID = 17, DATA_TONGUE_TARGET_ID = 18;
    public static final String[] VARIANTS = {"temperate", "warm", "cold"};
    private static final String TAG_VARIANT = "variant";

    public Frog(ChunkLevel level, int id) {
        super(EntityType.FROG, level, id);
        lookControl = new FrogLookControl(this);
        setPathfindingMalus(PathType.WATER, 4.0F);
        setPathfindingMalus(PathType.TRAPDOOR, -1.0F);
        moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        brain = makeBrain();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Brain<Frog> makeBrain() {
        Brain<Frog> b = new Brain<>(java.util.List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.WALK_TARGET, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryModuleType.PATH, MemoryModuleType.BREED_TARGET, MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS,
                MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryModuleType.ATTACK_TARGET, MemoryModuleType.TEMPTING_PLAYER, MemoryModuleType.TEMPTATION_COOLDOWN_TICKS,
                MemoryModuleType.IS_TEMPTED, MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY, MemoryModuleType.NEAREST_ATTACKABLE, MemoryModuleType.IS_IN_WATER,
                MemoryModuleType.IS_PREGNANT, MemoryModuleType.IS_PANICKING, MemoryModuleType.UNREACHABLE_TONGUE_TARGETS),
                (java.util.List) java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY, SensorType.FROG_ATTACKABLES, SensorType.FROG_TEMPTATIONS, SensorType.IS_IN_WATER));
        return FrogAi.makeBrain(b);
    }

    @SuppressWarnings("unchecked")
    public Brain<Frog> frogBrain() { return (Brain<Frog>) brain; }

    @Override
    protected PathNavigation createNavigation(ChunkLevel level) { return new FrogPathNavigation(this, level); }

    @Override
    protected void createAttributes(AttributeMap map) {
        super.createAttributes(map);
        map.add(Attribute.MOVEMENT_SPEED, 1.0).add(Attribute.MAX_HEALTH, 10.0).add(Attribute.ATTACK_DAMAGE, 10.0).add(Attribute.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_VARIANT_ID, SetEntityData.SERIALIZER_FROG_VARIANT, 0);
        entityData.define(DATA_TONGUE_TARGET_ID, SetEntityData.SERIALIZER_OPTIONAL_INT, null);
    }

    public void eraseTongueTarget() { entityData.set(DATA_TONGUE_TARGET_ID, null); }

    public Optional<Entity> getTongueTarget() {
        Integer id = (Integer) entityData.get(DATA_TONGUE_TARGET_ID);
        if (id == null) return Optional.empty();
        for (Entity e : level.getEntitiesOfClass(Entity.class, boundingBox().inflate(16.0), x -> x.id() == id)) return Optional.of(e);
        return Optional.empty();
    }

    public void setTongueTarget(Targetable target) { entityData.set(DATA_TONGUE_TARGET_ID, target.id()); }

    @Override public int getMaxHeadXRot() { return 35; }

    @Override public int getMaxHeadYRot() { return 5; }

    public int variant() { return (Integer) entityData.get(DATA_VARIANT_ID); }

    public void setVariant(int v) { entityData.set(DATA_VARIANT_ID, v); }

    @Override
    protected void addAdditionalSaveData(NbtCompound tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_VARIANT, "minecraft:" + VARIANTS[Math.floorMod(variant(), VARIANTS.length)]);
    }

    @Override
    protected void readAdditionalSaveData(NbtCompound tag) {
        super.readAdditionalSaveData(tag);
        if (tag.tags().get(TAG_VARIANT) instanceof String s) {
            String n = s.replace("minecraft:", "");
            for (int i = 0; i < VARIANTS.length; i++) if (VARIANTS[i].equals(n)) setVariant(i);
        }
    }

    @Override
    protected void customServerAiStep() {
        frogBrain().tick(level, this);
        FrogAi.updateActivity(this);
        super.customServerAiStep();
    }

    @Override
    public AgeableMob getBreedOffspring(ChunkLevel level, AgeableMob partner) {
        Frog child = new Frog(level, level.nextEntityId());
        FrogAi.initMemories(child, level.random());
        return child;
    }

    @Override public boolean isBaby() { return false; }

    @Override
    public void spawnChildFromBreeding(ChunkLevel level, Animal partner) {
        finalizeBreeding(level, partner, null);
        getBrain().setMemory(MemoryModuleType.IS_PREGNANT, Unit.INSTANCE);
    }

    @Override
    public void finalizeSpawn(org.domi.furnace.server.block.RandomSource r, DifficultyInstance difficulty) {
        super.finalizeSpawn(r, difficulty);
        Pos p = blockPosition();
        String biome = level.biomeAt(p.x(), p.y(), p.z());
        if (BiomeTags.is(biome, "minecraft:spawns_cold_variant_frogs")) setVariant(2);
        else if (BiomeTags.is(biome, "minecraft:spawns_warm_variant_frogs")) setVariant(1);
        else setVariant(0);
        FrogAi.initMemories(this, r);
    }

    @Override public String getAmbientSound() { return "entity.frog.ambient"; }

    @Override protected String getHurtSound(Damage source) { return "entity.frog.hurt"; }

    @Override protected String getDeathSound() { return "entity.frog.death"; }

    @Override public boolean isPushedByFluid() { return false; }

    @Override
    protected int calculateFallDamage(float distance, float multiplier) { return super.calculateFallDamage(distance, multiplier) - 5; }

    @Override
    protected void travel(float inX, float inY, float inZ) {
        if (isEffectiveAi() && isInWater()) {
            moveRelative(getSpeed(), inX, inY, inZ);
            move(MOVER_SELF, vx(), vy(), vz());
            setDeltaMovement(vx() * 0.9, vy() * 0.9, vz() * 0.9);
        } else {
            super.travel(inX, inY, inZ);
        }
    }

    public static boolean canEat(Targetable t) {
        if (t instanceof MagmaCube m && m.getSize() != 1) return false;
        return t instanceof Entity e && e.type().is("minecraft:frog_food");
    }

    @Override public boolean isFood(ItemStack s) { return ItemTags.is(s, "minecraft:frog_food"); }

    /** 혀를 내미는 동안엔 X 회전을 되돌리지 않는다. */
    private static class FrogLookControl extends LookControl {
        private final Frog frog;

        FrogLookControl(Frog frog) {
            super(frog);
            this.frog = frog;
        }

        @Override protected boolean resetXRotOnTick() { return frog.getTongueTarget().isEmpty(); }
    }

    private static class FrogNodeEvaluator extends AmphibiousNodeEvaluator {
        FrogNodeEvaluator(boolean prefersShallowSwimming) { super(prefersShallowSwimming); }

        @Override
        public Node getStart() {
            return !mob.isInWater() ? super.getStart() : getStartNode(new Pos((int) Math.floor(mob.boundingBox().minX()), (int) Math.floor(mob.boundingBox().minY()), (int) Math.floor(mob.boundingBox().minZ())));
        }

        @Override
        public PathType getPathType(PathfindingContext ctx, int x, int y, int z) {
            int below = ctx.getBlockState(new Pos(x, y - 1, z));
            return org.domi.furnace.server.block.B.isTag(below, "minecraft:frog_prefer_jump_to") ? PathType.OPEN : super.getPathType(ctx, x, y, z);
        }
    }

    private static class FrogPathNavigation extends AmphibiousPathNavigation {
        FrogPathNavigation(Frog frog, ChunkLevel level) { super(frog, level); }

        @Override protected boolean canCutCorner(PathType t) { return t != PathType.WATER_BORDER && super.canCutCorner(t); }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            nodeEvaluator = new FrogNodeEvaluator(true);
            nodeEvaluator.setCanPassDoors(true);
            return new PathFinder(nodeEvaluator, maxVisitedNodes);
        }
    }

    /** Frog.playStepSound: frog.step 크기 0.15(블록 소리를 쓰지 않는다). */
    @Override
    protected void playStepSound(org.domi.furnace.server.block.Pos pos, int state) { playSound("minecraft:entity.frog.step", 0.15F, 1.0F); }
}
