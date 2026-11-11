// 바닐라 PhantomSpawner(CustomSpawner): 잠을 오래 안 잔 플레이어 위 하늘에 팬텀을 낸다.
// 서버에서는 차원에 하나(DimensionSpawners)가 전역 틱에서 타이머·하늘 검사를 하고, 플레이어마다의 일은 그 플레이어의 region 스레드로 넘긴다
// (A10-05·12 — 예전엔 region 마다 타이머가 따로 돌고 새 region 이 생기면 곧바로 한 번 굴렸다).
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

    public int nextTick() { return nextTick; }

    /** 한 레벨에서 그 자리에서: 낸 팬텀 수. spawnEnemies 가 거짓이면 아무것도 하지 않는다. players: 바닐라 level.players(). */
    public int tick(ChunkLevel level, Iterable<Player> players, boolean spawnEnemies) {
        java.util.List<Player> list = new java.util.ArrayList<>();
        players.forEach(list::add);
        int[] n = {0};
        tick(CustomSpawnerHost.of(level, list), spawnEnemies, c -> n[0] += c);
        return n[0];
    }

    /** 바닐라 PhantomSpawner.tick: 타이머·하늘은 host 에서, 플레이어마다(목록 순서) 그 플레이어의 레벨에서. done: 플레이어마다 낸 수(null 가능). */
    public void tick(CustomSpawnerHost host, boolean spawnEnemies, java.util.function.IntConsumer done) {
        if (!spawnEnemies) return;
        if (!host.gameRule("doInsomnia")) return;
        var random = host.random();
        nextTick--;
        if (nextTick > 0) return;
        nextTick += (60 + random.nextInt(60)) * 20;
        if (host.skyDarken() < 5 && host.hasSkyLight()) return;
        for (Player player : host.players()) {
            host.atPlayer(player, (level, r) -> {
                int c = spawnFor(level, player, r);
                if (done != null) done.accept(c);
            });
        }
    }

    private static int spawnFor(ChunkLevel level, Player player, org.domi.furnace.server.block.RandomSource random) {
        if (player.gameMode() == GameMode.SPECTATOR) return 0;
        Pos pos = player.blockPosition();
        if (level.hasSkyLight() && !(pos.y() >= level.seaLevel() && level.canSeeSky(pos))) return 0;
        var difficulty = level.getCurrentDifficultyAt(pos);
        if (!difficulty.isGreaterThan(random.nextFloat() * 3.0F)) return 0;
        int sinceRest = Math.max(1, player.stats() == null ? 1 : player.stats().get("minecraft:time_since_rest"));
        if (random.nextInt(sinceRest) < 72000) return 0;
        // 바닐라: above(20 + r15) → east(-10 + r21) → south(-10 + r21) 순으로 난수를 부른다.
        int dy = 20 + random.nextInt(15), dx = -10 + random.nextInt(21), dz = -10 + random.nextInt(21);
        Pos spawnPos = new Pos(pos.x() + dx, pos.y() + dy, pos.z() + dz);
        if (!SpawnPlacements.isValidEmptySpawnBlock(level, spawnPos, "minecraft:phantom")) return 0;
        int count = 0;
        int n = 1 + random.nextInt(difficulty.difficulty() + 1);
        for (int i = 0; i < n; i++) {
            if (!(Entities.create(level, "minecraft:phantom") instanceof Mob phantom)) continue;
            phantom.moveTo(spawnPos.x() + 0.5, spawnPos.y(), spawnPos.z() + 0.5, 0.0F, 0.0F);
            phantom.finalizeSpawn(random, difficulty);
            level.addFreshEntity(phantom.spawnReason("NATURAL"));
            count++;
        }
        return count;
    }
}
