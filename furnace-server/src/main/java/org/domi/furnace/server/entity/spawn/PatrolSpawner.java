// 바닐라 PatrolSpawner(CustomSpawner): 5일째부터 낮에 10~11분마다 1/5 확률로 플레이어 24~47칸 밖에 약탈자 순찰대(지도자 + 대원)를 낸다.
// 바닐라 ServerLevel.isCloseToVillage(플레이어 위치, 2): 마을 POI 섹션 거리 2 이내면 내지 않는다(기본 생성자가 level 의 POI 로 판정).
// 차이: 바닐라 level.players() 는 차원 전체 플레이어, 여기서는 이 region 의 플레이어. nextTick 도 region 마다 따로 간다.
package org.domi.furnace.server.entity.spawn;

import java.util.List;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Raider;
import org.domi.furnace.server.world.GameMode;

public final class PatrolSpawner {
    private static final String PILLAGER = "minecraft:pillager";
    private int nextTick;
    private final Predicate<Pos> closeToVillage;

    /** 판정을 주입하지 않으면 tick 의 level 로 isCloseToVillage(pos, 2). */
    public PatrolSpawner() { this(null); }

    /** closeToVillage: isCloseToVillage(pos, 2) 대용(시험·POI 연결용). */
    public PatrolSpawner(Predicate<Pos> closeToVillage) { this.closeToVillage = closeToVillage; }

    public int nextTick() { return nextTick; }

    /** 낸 순찰대 크기(바닐라 반환값 n: 시도한 칸 수). players: 이 레벨(region)의 플레이어. */
    public int tick(ChunkLevel level, List<Player> players, boolean spawnEnemies) {
        if (!spawnEnemies) return 0;
        if (!level.gameRule("doPatrolSpawning")) return 0;
        RandomSource random = level.random();
        nextTick--;
        if (nextTick > 0) return 0;
        nextTick += 12000 + random.nextInt(1200);
        long day = level.dayTime() / 24000L;
        if (day < 5L || !level.isDay()) return 0;
        if (random.nextInt(5) != 0) return 0;
        int n = players.size();
        if (n < 1) return 0;
        Player player = players.get(random.nextInt(n));
        if (player.gameMode() == GameMode.SPECTATOR) return 0;
        if (closeToVillage != null ? closeToVillage.test(player.blockPosition()) : level.isCloseToVillage(player.blockPosition(), 2)) return 0;
        int dx = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
        int dz = (24 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
        Pos pp = player.blockPosition();
        int x = pp.x() + dx, y = pp.y(), z = pp.z() + dz;
        if (!level.hasChunksAt(x - 10, z - 10, x + 10, z + 10)) return 0;
        if (level.biomeIs(new Pos(x, y, z), "minecraft:without_patrol_spawns")) return 0;
        int count = 0;
        int size = (int) Math.ceil(level.getCurrentDifficultyAt(new Pos(x, y, z)).effectiveDifficulty()) + 1;
        for (int i = 0; i < size; i++) {
            count++;
            y = level.getMotionBlockingNoLeavesHeight(x, z);
            Pos pos = new Pos(x, y, z);
            if (i == 0) {
                if (!spawnPatrolMember(level, pos, random, true)) break;
            } else {
                spawnPatrolMember(level, pos, random, false);
            }
            x = x + random.nextInt(5) - random.nextInt(5);
            z = z + random.nextInt(5) - random.nextInt(5);
        }
        return count;
    }

    private static boolean spawnPatrolMember(ChunkLevel level, Pos pos, RandomSource random, boolean leader) {
        if (!SpawnPlacements.isValidEmptySpawnBlock(level, pos, PILLAGER)) return false;
        if (!SpawnPlacements.checkSpawnRules(PILLAGER, level, SpawnReason.PATROL, pos, random)) return false;
        if (!(Entities.create(level, PILLAGER) instanceof Raider pm)) return false;
        if (leader) {
            pm.setPatrolLeader(true);
            pm.findPatrolTarget();   // 바닐라도 setPos 전(원점 기준)에 부른다
        }
        pm.setPos(pos.x(), pos.y(), pos.z());
        pm.setFinalizeReason(SpawnReason.PATROL);
        pm.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(pos));
        level.addFreshEntityWithPassengers(pm.spawnReason("PATROL"));
        return true;
    }
}
