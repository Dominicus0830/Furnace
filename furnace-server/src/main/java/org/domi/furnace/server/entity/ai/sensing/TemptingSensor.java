// 바닐라 TemptingSensor: 유혹 아이템을 든 가장 가까운(10칸, 시야 무시) 플레이어를 기억한다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.world.GameMode;

public class TemptingSensor extends Sensor<PathfinderMob> {
    public static final int TEMPTATION_RANGE = 10;
    private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().range(10.0).ignoreLineOfSight();
    private final Predicate<ItemStack> temptations;

    public TemptingSensor(Predicate<ItemStack> temptations) { this.temptations = temptations; }

    @Override
    protected void doTick(ChunkLevel level, PathfinderMob mob) {
        var brain = mob.getBrain();
        List<Player> list = new ArrayList<>();
        level.forEachPlayer(p -> {
            if (p.gameMode() != GameMode.SPECTATOR && !p.isDisconnected() && TEMPT_TARGETING.test(mob, p) && NearestLivingEntitySensor.distSqr(mob, p) < 100.0
                    && playerHoldingTemptation(p)) list.add(p);
        });
        list.sort(Comparator.comparingDouble(p -> NearestLivingEntitySensor.distSqr(mob, p)));
        if (!list.isEmpty()) brain.setMemory(MemoryModuleType.TEMPTING_PLAYER, list.get(0));
        else brain.eraseMemory(MemoryModuleType.TEMPTING_PLAYER);
    }

    private boolean playerHoldingTemptation(Player p) {
        var inv = p.inventory();
        return isTemptation(inv.get(inv.heldWindowSlot())) || isTemptation(inv.get(45));
    }

    private boolean isTemptation(ItemStack s) { return temptations.test(s); }

    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.TEMPTING_PLAYER); }
}
