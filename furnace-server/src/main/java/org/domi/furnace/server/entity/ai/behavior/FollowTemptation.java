// 바닐라 FollowTemptation: 유혹하는 플레이어를 바라보며 따라간다(가까우면 멈춤). 끝나면 100틱 쿨다운.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public class FollowTemptation extends Behavior<PathfinderMob> {
    public static final int TEMPTATION_COOLDOWN = 100;
    public static final double DEFAULT_CLOSE_ENOUGH_DIST = 2.5;
    public static final double BACKED_UP_CLOSE_ENOUGH_DIST = 3.5;
    private final Function<org.domi.furnace.server.entity.Mob, Float> speedModifier;
    private final Function<org.domi.furnace.server.entity.Mob, Double> closeEnoughDistance;

    public FollowTemptation(Function<org.domi.furnace.server.entity.Mob, Float> speed) { this(speed, m -> DEFAULT_CLOSE_ENOUGH_DIST); }

    public FollowTemptation(Function<org.domi.furnace.server.entity.Mob, Float> speed, Function<org.domi.furnace.server.entity.Mob, Double> closeEnough) {
        super(conditions());
        this.speedModifier = speed;
        this.closeEnoughDistance = closeEnough;
    }

    private static Map<MemoryModuleType<?>, MemoryStatus> conditions() {
        Map<MemoryModuleType<?>, MemoryStatus> m = new HashMap<>();
        m.put(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED);
        m.put(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED);
        m.put(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT);
        m.put(MemoryModuleType.IS_TEMPTED, MemoryStatus.REGISTERED);
        m.put(MemoryModuleType.TEMPTING_PLAYER, MemoryStatus.VALUE_PRESENT);
        m.put(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT);
        m.put(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT);
        return m;
    }

    @Override protected boolean timedOut(long gameTime) { return false; }

    @Override
    protected boolean canStillUse(ChunkLevel level, PathfinderMob mob, long gameTime) {
        var brain = mob.getBrain();
        return brain.getMemory(MemoryModuleType.TEMPTING_PLAYER).isPresent() && !brain.hasMemoryValue(MemoryModuleType.BREED_TARGET) && !brain.hasMemoryValue(MemoryModuleType.IS_PANICKING);
    }

    @Override protected void start(ChunkLevel level, PathfinderMob mob, long gameTime) { mob.getBrain().setMemory(MemoryModuleType.IS_TEMPTED, true); }

    @Override
    protected void stop(ChunkLevel level, PathfinderMob mob, long gameTime) {
        var brain = mob.getBrain();
        brain.setMemory(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS, 100);
        brain.setMemory(MemoryModuleType.IS_TEMPTED, false);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void tick(ChunkLevel level, PathfinderMob mob, long gameTime) {
        Player player = mob.getBrain().getMemory(MemoryModuleType.TEMPTING_PLAYER).get();
        var brain = mob.getBrain();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        double d = closeEnoughDistance.apply(mob);
        double dx = player.x() - mob.x(), dy = player.y() - mob.y(), dz = player.z() - mob.z();
        if (dx * dx + dy * dy + dz * dz < d * d) brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        else brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(player, false), speedModifier.apply(mob), 2));
    }
}
