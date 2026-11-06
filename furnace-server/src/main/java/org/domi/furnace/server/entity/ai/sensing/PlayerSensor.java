// 바닐라 PlayerSensor: 가까운(16칸) 플레이어 목록과, 그중 가장 가까운 보이는 플레이어·공격 가능한 플레이어를 기억한다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.world.GameMode;

public class PlayerSensor extends Sensor<Mob> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(MemoryModuleType.NEAREST_PLAYERS, MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER);
    }

    @Override
    protected void doTick(ChunkLevel level, Mob mob) {
        List<Player> all = new ArrayList<>();
        level.forEachPlayer(p -> {
            if (p.gameMode() != GameMode.SPECTATOR && !p.isDisconnected() && closerThan(mob, p, 16.0)) all.add(p);
        });
        all.sort(Comparator.comparingDouble(p -> NearestLivingEntitySensor.distSqr(mob, p)));
        var brain = mob.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_PLAYERS, all);
        List<Player> visible = new ArrayList<>();
        for (Player p : all) if (isEntityTargetable(mob, p)) visible.add(p);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER, visible.isEmpty() ? null : visible.get(0));
        Optional<Player> attackable = visible.stream().filter(p -> isEntityAttackable(mob, p)).findFirst();
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER, attackable);
    }

    static boolean closerThan(Mob a, org.domi.furnace.server.entity.Targetable b, double d) {
        return NearestLivingEntitySensor.distSqr(a, b) < d * d;
    }
}
