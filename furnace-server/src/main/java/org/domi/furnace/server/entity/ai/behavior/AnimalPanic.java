// 바닐라 AnimalPanic: 공황 원인 피해를 받으면(또는 이미 공황이면) 도망갈 점으로 걸어간다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.DefaultRandomPos;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.world.gen.feature.BlockPosIter;

public class AnimalPanic extends Behavior<PathfinderMob> {
    private static final int PANIC_MIN_DURATION = 100;
    private static final int PANIC_MAX_DURATION = 120;
    private static final int PANIC_DISTANCE_HORIZONTAL = 5;
    private static final int PANIC_DISTANCE_VERTICAL = 4;
    private final float speedMultiplier;
    private final Function<PathfinderMob, String> panicCausesTag;

    public AnimalPanic(float speed) { this(speed, m -> "minecraft:panic_causes"); }

    public AnimalPanic(float speed, Function<PathfinderMob, String> tag) {
        super(Map.of(MemoryModuleType.IS_PANICKING, MemoryStatus.REGISTERED, MemoryModuleType.HURT_BY, MemoryStatus.REGISTERED), PANIC_MIN_DURATION, PANIC_MAX_DURATION);
        this.speedMultiplier = speed;
        this.panicCausesTag = tag;
    }

    @Override
    protected boolean checkExtraStartConditions(ChunkLevel level, PathfinderMob mob) {
        var brain = mob.getBrain();
        return brain.getMemory(MemoryModuleType.HURT_BY).map(d -> DamageType.byKey(d.type()).isInTag(panicCausesTag.apply(mob))).orElse(false)
                || brain.hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }

    @Override protected boolean canStillUse(ChunkLevel level, PathfinderMob mob, long gameTime) { return true; }

    @Override
    protected void start(ChunkLevel level, PathfinderMob mob, long gameTime) {
        mob.getBrain().setMemory(MemoryModuleType.IS_PANICKING, true);
        mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Override
    protected void stop(ChunkLevel level, PathfinderMob mob, long gameTime) { mob.getBrain().eraseMemory(MemoryModuleType.IS_PANICKING); }

    @Override
    protected void tick(ChunkLevel level, PathfinderMob mob, long gameTime) {
        if (mob.getNavigation().isDone()) {
            double[] v = getPanicPos(mob, level);
            if (v != null) mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(v, speedMultiplier, 0));
        }
    }

    private double[] getPanicPos(PathfinderMob mob, ChunkLevel level) {
        if (mob.isOnFire()) {
            Optional<Pos> water = lookForWater(level, mob);
            if (water.isPresent()) return new double[] {water.get().x() + 0.5, water.get().y(), water.get().z() + 0.5};
        }
        return DefaultRandomPos.getPos(mob, PANIC_DISTANCE_HORIZONTAL, PANIC_DISTANCE_VERTICAL);
    }

    private Optional<Pos> lookForWater(ChunkLevel level, PathfinderMob mob) {
        Pos here = mob.blockPosition();
        if (!BlockInfo.get().collisionShape(level.getBlockState(here)).isEmpty()) return Optional.empty();
        boolean wide = (int) Math.ceil(mob.bbWidth()) == 2;
        Pos[] found = new Pos[1];
        BlockPosIter.withinManhattan(here.x(), here.y(), here.z(), 5, 1, 5, (x, y, z) -> {
            boolean ok;
            if (wide) ok = isWater(level, x, y, z) && isWater(level, x, y, z + 1) && isWater(level, x + 1, y, z) && isWater(level, x + 1, y, z + 1);
            else ok = isWater(level, x, y, z);
            if (ok) {
                found[0] = new Pos(x, y, z);
                return false;
            }
            return true;
        });
        return Optional.ofNullable(found[0]);
    }

    private static boolean isWater(ChunkLevel level, int x, int y, int z) { return level.isWaterAt(new Pos(x, y, z)); }
}
