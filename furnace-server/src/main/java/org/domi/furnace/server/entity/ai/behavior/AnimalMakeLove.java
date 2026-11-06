// 바닐라 AnimalMakeLove: 사랑 모드 동물이 보이는 같은 종류 짝에게 서로 바라보며 걸어가, 닿으면 새끼를 낳는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Animal;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public class AnimalMakeLove extends Behavior<Animal> {
    private static final int BREED_RANGE = 3;
    private static final int MIN_DURATION = 60;
    private static final int MAX_DURATION = 110;
    private final EntityType partnerType;
    private final float speedModifier;
    private final int closeEnoughDistance;
    private long spawnChildAtTime;

    public AnimalMakeLove(EntityType partnerType) { this(partnerType, 1.0F, 2); }

    public AnimalMakeLove(EntityType partnerType, float speed, int closeEnough) {
        super(Map.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT, MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT), 110);
        this.partnerType = partnerType;
        this.speedModifier = speed;
        this.closeEnoughDistance = closeEnough;
    }

    @Override protected boolean checkExtraStartConditions(ChunkLevel level, Animal animal) { return animal.isInLove() && findValidBreedPartner(animal).isPresent(); }

    @Override
    protected void start(ChunkLevel level, Animal animal, long gameTime) {
        Animal partner = findValidBreedPartner(animal).get();
        animal.getBrain().setMemory(MemoryModuleType.BREED_TARGET, partner);
        partner.getBrain().setMemory(MemoryModuleType.BREED_TARGET, animal);
        BehaviorUtils.lockGazeAndWalkToEachOther(animal, partner, speedModifier, closeEnoughDistance);
        int i = MIN_DURATION + animal.random().nextInt(50);
        spawnChildAtTime = gameTime + i;
    }

    @Override
    protected boolean canStillUse(ChunkLevel level, Animal animal, long gameTime) {
        if (!hasBreedTargetOfRightType(animal)) return false;
        Animal partner = getBreedTarget(animal);
        return partner.isAlive() && animal.canMate(partner) && BehaviorUtils.entityIsVisible(animal.getBrain(), partner) && gameTime <= spawnChildAtTime && !animal.isPanicking() && !partner.isPanicking();
    }

    @Override
    protected void tick(ChunkLevel level, Animal animal, long gameTime) {
        Animal partner = getBreedTarget(animal);
        BehaviorUtils.lockGazeAndWalkToEachOther(animal, partner, speedModifier, closeEnoughDistance);
        if (BehaviorUtils.distSqr(animal, partner) < BREED_RANGE * BREED_RANGE) {
            if (gameTime >= spawnChildAtTime) {
                animal.spawnChildFromBreeding(level, partner);
                animal.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
                partner.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
            }
        }
    }

    @Override
    protected void stop(ChunkLevel level, Animal animal, long gameTime) {
        animal.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
        animal.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        animal.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        spawnChildAtTime = 0L;
    }

    private Animal getBreedTarget(Animal animal) { return (Animal) animal.getBrain().getMemory(MemoryModuleType.BREED_TARGET).get(); }

    private boolean hasBreedTargetOfRightType(Animal animal) {
        var brain = animal.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.BREED_TARGET) && brain.getMemory(MemoryModuleType.BREED_TARGET).get().type() == partnerType;
    }

    private Optional<Animal> findValidBreedPartner(Animal animal) {
        return animal.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get()
                .findClosest(t -> t instanceof Entity e && e.type() == partnerType && t instanceof Animal a && animal.canMate(a) && !a.isPanicking()).map(Animal.class::cast);
    }
}
