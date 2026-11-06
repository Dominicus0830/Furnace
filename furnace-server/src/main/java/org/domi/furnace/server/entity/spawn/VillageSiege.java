// 바닐라 VillageSiege(CustomSpawner, 오버월드): 밤이 한가운데(getTimeOfDay == 0.5)가 되는 순간 1/10 확률로 그날 밤 포위를 정한다.
// 포위 밤에는 마을(POI 섹션 거리 1 이내)에 있는 첫 비관전 플레이어 둘레 32칸 원 위의 한 점을 잡고, 3틱마다 좀비를 한 마리씩 최대 20마리 낸다.
// 좀비는 그 점 둘레 16x16 안에서 10번 뽑은 마을 안의 어둡고 설 수 있는 칸에 EVENT 로 난다.
// 차이: 바닐라 level.players() 는 차원 전체 플레이어, 여기서는 이 region 의 플레이어. 상태(밤마다의 포위 여부)도 region 마다 따로 간다.
// 가정: finalizeSpawn 을 moveTo 보다 먼저 부르므로 난이도는 원점 칸(0,0,0) 기준이다(바닐라 코드 순서 그대로, 원점 청크가 이 region 에 없으면 머문 시간 0).
package org.domi.furnace.server.entity.spawn;

import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Zombie;
import org.domi.furnace.server.entity.ai.control.Mth;
import org.domi.furnace.server.world.GameMode;

public final class VillageSiege {
    private static final String ZOMBIE = "minecraft:zombie";

    /** 바닐라 State: SIEGE_CAN_ACTIVATE(자정 전까지 대기), SIEGE_TONIGHT(오늘 밤), SIEGE_DONE(없음/끝). */
    public enum State { CAN_ACTIVATE, TONIGHT, DONE }

    private boolean hasSetupSiege;
    private State siegeState = State.DONE;
    private int zombiesToSpawn;
    private int nextSpawnTime;
    private int spawnX, spawnY, spawnZ;

    public State state() { return siegeState; }
    public int zombiesToSpawn() { return zombiesToSpawn; }
    public boolean hasSetupSiege() { return hasSetupSiege; }
    /** 포위 중심점(마지막으로 정한 점). */
    public Pos spawnPos() { return new Pos(spawnX, spawnY, spawnZ); }

    /** 낸 틱이면 1(바닐라 반환값). spawnEnemies: 적대 몹 스폰 허용. players: 이 region 의 플레이어. */
    public int tick(ChunkLevel level, List<Player> players, boolean spawnEnemies) {
        if (!level.isDay() && spawnEnemies) {
            float time = timeOfDay(level.dayTime());
            if ((double) time == 0.5) {
                siegeState = level.random().nextInt(10) == 0 ? State.TONIGHT : State.DONE;
            }
            if (siegeState == State.DONE) return 0;
            if (!hasSetupSiege) {
                if (!tryToSetupSiege(level, players)) return 0;
                hasSetupSiege = true;
            }
            if (nextSpawnTime > 0) {
                nextSpawnTime--;
                return 0;
            }
            nextSpawnTime = 2;
            if (zombiesToSpawn > 0) {
                trySpawn(level);
                zombiesToSpawn--;
            } else {
                siegeState = State.DONE;
            }
            return 1;
        }
        siegeState = State.DONE;
        hasSetupSiege = false;
        return 0;
    }

    /** DimensionType.timeOfDay(dayTime) — 고정 시간 차원은 오버월드가 아니라 다루지 않는다. */
    public static float timeOfDay(long dayTime) {
        double x = (double) dayTime / 24000.0 - 0.25;
        long fl = (long) x;
        double d = x - (x < fl ? fl - 1 : fl);   // Mth.frac
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (float) (d * 2.0 + e) / 3.0F;
    }

    private boolean tryToSetupSiege(ChunkLevel level, List<Player> players) {
        RandomSource random = level.random();
        for (Player player : players) {
            if (player.gameMode() == GameMode.SPECTATOR) continue;
            Pos pos = player.blockPosition();
            if (level.isCloseToVillage(pos, 1) && !level.biomeIs(pos, "minecraft:without_zombie_sieges")) {
                for (int i = 0; i < 10; i++) {
                    float f = random.nextFloat() * (float) (Math.PI * 2);
                    spawnX = pos.x() + Mth.floor(Mth.cos(f) * 32.0F);
                    spawnY = pos.y();
                    spawnZ = pos.z() + Mth.floor(Mth.sin(f) * 32.0F);
                    if (findRandomSpawnPos(level, new Pos(spawnX, spawnY, spawnZ)) != null) {
                        nextSpawnTime = 0;
                        zombiesToSpawn = 20;
                        break;
                    }
                }
                return true;
            }
        }
        return false;
    }

    private void trySpawn(ChunkLevel level) {
        double[] v = findRandomSpawnPos(level, new Pos(spawnX, spawnY, spawnZ));
        if (v == null) return;
        if (!(Entities.create(level, ZOMBIE) instanceof Zombie zombie)) return;
        zombie.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(zombie.blockPosition()));   // moveTo 전이라 원점 기준(바닐라 순서)
        zombie.moveTo(v[0], v[1], v[2], level.random().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntityWithPassengers(zombie.spawnReason("VILLAGE_INVASION"));
    }

    /** 둘레 16x16 에서 10번: x 다음 z 를 뽑고, 마을 안이며 좀비 EVENT 스폰 규칙을 만족하는 첫 칸의 바닥 가운데. 난수는 칸마다 x, z(+ 규칙 안의 어둠 굴림). */
    static double[] findRandomSpawnPos(ChunkLevel level, Pos around) {
        RandomSource random = level.random();
        for (int i = 0; i < 10; i++) {
            int x = around.x() + random.nextInt(16) - 8;
            int z = around.z() + random.nextInt(16) - 8;
            int y = level.getWorldSurfaceHeight(x, z);
            Pos pos = new Pos(x, y, z);
            if (level.isCloseToVillage(pos, 1) && SpawnPlacements.checkSpawnRules(ZOMBIE, level, SpawnReason.EVENT, pos, random)) {
                return new double[] {x + 0.5, y, z + 0.5};
            }
        }
        return null;
    }
}
