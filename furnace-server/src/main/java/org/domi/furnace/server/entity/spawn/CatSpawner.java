// 바닐라 CatSpawner(CustomSpawner): 1200틱마다 무작위 플레이어 8~31칸 밖 한 칸에서, 마을(POI 섹션 거리 2)이면 집 POI 가 4개 넘고
// 48칸 안 고양이가 5마리 미만일 때, 마녀 오두막 조각 안이면 16칸 안 고양이가 없을 때 고양이 한 마리를 낸다.
// 차이: 바닐라 getRandomPlayer 는 차원 전체 플레이어, 여기서는 이 region 의 플레이어. nextTick 도 region 마다 따로 간다.
package org.domi.furnace.server.entity.spawn;

import java.util.List;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Cat;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiTypes;

public final class CatSpawner {
    private static final int TICK_DELAY = 1200;
    private static final String CAT = "minecraft:cat";
    /** #cats_spawn_in(1.21.1) = swamp_hut. */
    private static final String CATS_SPAWN_IN = "minecraft:swamp_hut";
    private int nextTick;

    public int nextTick() { return nextTick; }

    /** 낸 고양이 수(0/1). spawnFriendlies: 동물 스폰 허용. */
    public int tick(ChunkLevel level, List<Player> players, boolean spawnFriendlies) {
        if (!spawnFriendlies || !level.gameRule("doMobSpawning")) return 0;
        nextTick--;
        if (nextTick > 0) return 0;
        nextTick = TICK_DELAY;
        List<Player> alive = players.stream().filter(p -> !p.vitals().isDeadOrDying()).toList();
        if (alive.isEmpty()) return 0;
        RandomSource random = level.random();
        Player player = alive.get(random.nextInt(alive.size()));
        int dx = (8 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
        int dz = (8 + random.nextInt(24)) * (random.nextBoolean() ? -1 : 1);
        Pos pos = player.blockPosition().offset(dx, 0, dz);
        if (!level.hasChunksAt(pos.x() - 10, pos.z() - 10, pos.x() + 10, pos.z() + 10)) return 0;
        if (SpawnPlacements.isSpawnPositionOk(CAT, level, pos)) {
            if (level.isCloseToVillage(pos, 2)) return spawnInVillage(level, pos);
            if (level.structureWithPieceAt(pos, CATS_SPAWN_IN) != null) return spawnInHut(level, pos);
        }
        return 0;
    }

    private int spawnInVillage(ChunkLevel level, Pos pos) {
        if (level.poiManager().getCountInRange(t -> t == PoiTypes.HOME, pos, 48, PoiManager.Occupancy.IS_OCCUPIED) > 4L) {
            List<Cat> cats = level.getEntitiesOfClass(Cat.class, box(pos).inflate(48.0, 8.0, 48.0), c -> true);
            if (cats.size() < 5) return spawnCat(pos, level);
        }
        return 0;
    }

    private int spawnInHut(ChunkLevel level, Pos pos) {
        List<Cat> cats = level.getEntitiesOfClass(Cat.class, box(pos).inflate(16.0, 8.0, 16.0), c -> true);
        return cats.size() < 1 ? spawnCat(pos, level) : 0;
    }

    private static AABB box(Pos p) { return new AABB(p.x(), p.y(), p.z(), p.x() + 1, p.y() + 1, p.z() + 1); }

    /** spawnCat: 바닐라 순서 그대로 finalizeSpawn(NATURAL) → moveTo(pos, 0, 0) → 추가. */
    private int spawnCat(Pos pos, ChunkLevel level) {
        if (!(Entities.create(level, CAT) instanceof Cat cat)) return 0;
        cat.finalizeSpawn(level.random(), level.getCurrentDifficultyAt(pos), null);
        cat.moveTo(pos.x() + 0.5, pos.y(), pos.z() + 0.5, 0.0F, 0.0F);   // moveTo(BlockPos): 칸 아래 가운데
        level.addFreshEntityWithPassengers(cat);
        return 1;
    }
}
