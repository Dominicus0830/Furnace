// 바닐라 PhantomSpawner(CustomSpawner): 잠을 오래 안 잔 플레이어 위 하늘에 팬텀을 낸다.
package org.domi.furnace.server.entity.spawn;

import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.GameMode;

public final class PhantomSpawner {
    private int nextTick;

    /** 낸 팬텀 수. spawnEnemies 가 거짓이면 아무것도 하지 않는다. players: 이 레벨(region)의 플레이어. */
    public int tick(ChunkLevel level, Iterable<Player> players, boolean spawnEnemies) {
        if (!spawnEnemies) return 0;
        if (!level.gameRule("doInsomnia")) return 0;
        var random = level.random();
        nextTick--;
        if (nextTick > 0) return 0;
        nextTick += (60 + random.nextInt(60)) * 20;
        if (level.skyDarken() < 5 && level.hasSkyLight()) return 0;
        int count = 0;
        for (Player player : players) {
            if (player.gameMode() == GameMode.SPECTATOR) continue;
            Pos pos = player.blockPosition();
            if (level.hasSkyLight() && !(pos.y() >= level.seaLevel() && level.canSeeSky(pos))) continue;
            var difficulty = level.getCurrentDifficultyAt(pos);
            if (!difficulty.isGreaterThan(random.nextFloat() * 3.0F)) continue;
            int sinceRest = Math.max(1, player.stats() == null ? 1 : player.stats().get("minecraft:time_since_rest"));
            if (random.nextInt(sinceRest) < 72000) continue;
            // 바닐라: above(20 + r15) → east(-10 + r21) → south(-10 + r21) 순으로 난수를 부른다.
            int dy = 20 + random.nextInt(15), dx = -10 + random.nextInt(21), dz = -10 + random.nextInt(21);
            Pos spawnPos = new Pos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
            if (!SpawnPlacements.isValidEmptySpawnBlock(level, spawnPos, "minecraft:phantom")) continue;
            int n = 1 + random.nextInt(difficulty.difficulty() + 1);
            for (int i = 0; i < n; i++) {
                if (!(Entities.create(level, "minecraft:phantom") instanceof Mob phantom)) continue;
                phantom.moveTo(spawnPos.x() + 0.5, spawnPos.y(), spawnPos.z() + 0.5, 0.0F, 0.0F);
                phantom.finalizeSpawn(random, difficulty);
                level.addFreshEntity(phantom);
                count++;
            }
        }
        return count;
    }
}
