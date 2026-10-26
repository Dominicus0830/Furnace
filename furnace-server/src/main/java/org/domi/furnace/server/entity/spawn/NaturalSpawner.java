// 바닐라 NaturalSpawner + LocalMobCapCalculator + PotentialCalculator: 플레이어 근처 청크마다 종류(MobCategory)별로 몹을 자연 스폰한다.
// 한 틱: 지역의 몹을 세어 종류별 상한(max × 청크 수 / 289)과 플레이어별 지역 상한을 보고, 청크마다 무작위 칸에서 최대 3묶음을 시도한다.
// 이 서버는 region 별로 돌아서 몹 수·상한 계산은 region 안의 엔티티와 청크만 본다(바닐라는 차원 전체) — region 경계 근처에서만 다르다.
// TODO: 구조물 스폰 덮어쓰기(네더 요새·늪 오두막·해저 신전·약탈자 전초기지의 mob 목록), 순찰·방랑 상인·고양이 특수 스포너(순찰 AI·POI·상인 필요). 팬텀은 PhantomSpawner, 청크 생성 동물은 spawnMobsForChunkGeneration.
package org.domi.furnace.server.entity.spawn;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.block.Shape;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.Entities;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Player;

public final class NaturalSpawner {
    private static final int MAGIC_NUMBER = 289;   // 17²
    private static final MobCategory[] SPAWNING_CATEGORIES = java.util.Arrays.stream(MobCategory.values()).filter(c -> c != MobCategory.MISC).toArray(MobCategory[]::new);

    private NaturalSpawner() {}

    public static Shape shapeOf(AABB b) { return new Shape(new double[] {b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()}); }

    // ------------------------------------------------------------------ 상태(몹 수·지역 상한·전하)

    public static final class SpawnState {
        private final int spawnableChunkCount;
        private final EnumMap<MobCategory, Integer> counts = new EnumMap<>(MobCategory.class);
        private final List<double[]> charges = new ArrayList<>();   // {x, y, z, charge}
        private final Map<Player, EnumMap<MobCategory, Integer>> playerCounts = new HashMap<>();
        private final List<Player> players;
        private Pos lastPos;
        private String lastType;
        private double lastCharge;

        SpawnState(int spawnableChunkCount, List<Player> players) {
            this.spawnableChunkCount = spawnableChunkCount;
            this.players = players;
        }

        private List<Player> playersNear(int cx, int cz) {
            List<Player> out = new ArrayList<>();
            double mx = cx * 16 + 8, mz = cz * 16 + 8;
            for (Player p : players) {
                if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) continue;
                double dx = p.x() - mx, dz = p.z() - mz;
                if (dx * dx + dz * dz <= 128.0 * 128.0) out.add(p);
            }
            return out;
        }

        void addMobToLocalCaps(int cx, int cz, MobCategory cat) {
            for (Player p : playersNear(cx, cz)) playerCounts.computeIfAbsent(p, k -> new EnumMap<>(MobCategory.class)).merge(cat, 1, Integer::sum);
        }

        private boolean localCanSpawn(MobCategory cat, int cx, int cz) {
            for (Player p : playersNear(cx, cz)) {
                var m = playerCounts.get(p);
                if (m == null || m.getOrDefault(cat, 0) < cat.max()) return true;
            }
            return false;
        }

        boolean canSpawnForCategory(MobCategory cat, int cx, int cz) {
            int limit = cat.max() * spawnableChunkCount / MAGIC_NUMBER;
            return counts.getOrDefault(cat, 0) < limit && localCanSpawn(cat, cx, cz);
        }

        /** SpawnState.canSpawn(type, pos, chunk): 스폰 비용(에너지 예산) 검사. */
        boolean canSpawn(String type, Pos pos, ChunkLevel level) {
            lastPos = pos;
            lastType = type;
            BiomeSpawns.SpawnCost cost = BiomeSpawns.cost(biomeName(level, pos), type);
            if (cost == null) {
                lastCharge = 0.0;
                return true;
            }
            lastCharge = cost.charge();
            return energyChange(pos, cost.charge()) <= cost.energyBudget();
        }

        private double energyChange(Pos pos, double newCharge) {
            if (newCharge == 0.0) return 0.0;
            double sum = 0.0;
            for (double[] c : charges) {
                double dx = c[0] - pos.x(), dy = c[1] - pos.y(), dz = c[2] - pos.z();
                double d = dx * dx + dy * dy + dz * dz;
                sum += d == 0.0 ? Double.POSITIVE_INFINITY : c[3] / Math.sqrt(d);
            }
            return sum * newCharge;
        }

        void addCharge(Pos pos, double charge) {
            if (charge != 0.0) charges.add(new double[] {pos.x(), pos.y(), pos.z(), charge});
        }

        void afterSpawn(Mob mob, ChunkLevel level) {
            String type = mob.type().name();
            Pos pos = mob.blockPosition();
            double charge;
            if (pos.equals(lastPos) && type.equals(lastType)) {
                charge = lastCharge;
            } else {
                BiomeSpawns.SpawnCost cost = BiomeSpawns.cost(biomeName(level, pos), type);
                charge = cost == null ? 0.0 : cost.charge();
            }
            addCharge(pos, charge);
            MobCategory cat = EntityMeta.category(type);
            counts.merge(cat, 1, Integer::sum);
            addMobToLocalCaps(pos.x() >> 4, pos.z() >> 4, cat);
        }
    }

    static String biomeName(ChunkLevel level, Pos pos) {
        String b = level.biomeAt(pos.x(), pos.y(), pos.z());
        return b == null ? null : b.indexOf(':') < 0 ? "minecraft:" + b : b;
    }

    /** NaturalSpawner.createState: region 의 엔티티를 세어 종류별 수·지역 상한·전하를 모은다. */
    public static SpawnState createState(int spawnableChunkCount, Iterable<Entity> entities, ChunkLevel level) {
        List<Player> players = new ArrayList<>();
        level.forEachPlayer(players::add);
        SpawnState state = new SpawnState(spawnableChunkCount, players);
        for (Entity e : entities) {
            if (!(e instanceof Mob mob) || mob.isRemoved()) continue;
            if (mob.isPersistentForCaps()) continue;
            MobCategory cat = EntityMeta.category(e.type().name());
            if (cat == MobCategory.MISC) continue;
            Pos pos = e.blockPosition();
            BiomeSpawns.SpawnCost cost = BiomeSpawns.cost(biomeName(level, pos), e.type().name());
            if (cost != null) state.addCharge(pos, cost.charge());
            state.addMobToLocalCaps(pos.x() >> 4, pos.z() >> 4, cat);
            state.counts.merge(cat, 1, Integer::sum);
        }
        return state;
    }

    // ------------------------------------------------------------------ 한 청크

    /** NaturalSpawner.spawnForChunk. spawnFriendlies=동물, spawnEnemies=몬스터, spawnPassives=400틱마다(유지 종류). */
    public static void spawnForChunk(ChunkLevel level, int cx, int cz, SpawnState state, boolean spawnFriendlies, boolean spawnEnemies, boolean spawnPassives) {
        for (MobCategory cat : SPAWNING_CATEGORIES) {
            if ((spawnFriendlies || !cat.isFriendly()) && (spawnEnemies || cat.isFriendly()) && (spawnPassives || !cat.isPersistent()) && state.canSpawnForCategory(cat, cx, cz)) {
                spawnCategoryForChunk(cat, level, cx, cz, state);
            }
        }
    }

    private static void spawnCategoryForChunk(MobCategory cat, ChunkLevel level, int cx, int cz, SpawnState state) {
        RandomSource rnd = level.random();
        int x = cx * 16 + rnd.nextInt(16), z = cz * 16 + rnd.nextInt(16);
        int top = level.getWorldSurfaceHeight(x, z) + 1;
        int min = level.minBuildHeight();
        int y = rnd.nextInt(top - min + 1) + min;
        if (y >= min + 1) spawnCategoryForPosition(cat, level, cx, cz, new Pos(x, y, z), state);
    }

    private static boolean isRightDistanceToPlayerAndSpawnPoint(ChunkLevel level, int cx, int cz, Pos pos, double distSqr) {
        if (distSqr <= 576.0) return false;
        int[] sp = level.sharedSpawnPos();
        double dx = pos.x() + 0.5 - (sp[0] + 0.5), dy = pos.y() - (sp[1] + 0.5), dz = pos.z() + 0.5 - (sp[2] + 0.5);
        if (dx * dx + dy * dy + dz * dz < 24.0 * 24.0) return false;
        return cx == (pos.x() >> 4) && cz == (pos.z() >> 4) || level.withinBorder(pos);
    }

    private static void spawnCategoryForPosition(MobCategory cat, ChunkLevel level, int cx, int cz, Pos start, SpawnState state) {
        int y = start.y();
        int at = level.getBlockState(start);
        if (B.behavior(at).isRedstoneConductor(at, level, start)) return;
        RandomSource rnd = level.random();
        int spawned = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            int x = start.x(), z = start.z();
            BiomeSpawns.SpawnerData data = null;
            Object group = null;
            int groupSize = (int) Math.ceil(rnd.nextFloat() * 4.0F);
            int inCluster = 0;
            for (int step = 0; step < groupSize; step++) {
                x += rnd.nextInt(6) - rnd.nextInt(6);
                z += rnd.nextInt(6) - rnd.nextInt(6);
                Pos pos = new Pos(x, y, z);
                double px = x + 0.5, pz = z + 0.5;
                Player player = level.getNearestPlayer(px, y, pz, -1.0, false);
                if (player == null) continue;
                double dx = player.x() - px, dy = player.y() - y, dz = player.z() - pz;
                double distSqr = dx * dx + dy * dy + dz * dz;
                if (!isRightDistanceToPlayerAndSpawnPoint(level, cx, cz, pos, distSqr)) continue;
                if (data == null) {
                    data = getRandomSpawnMobAt(level, cat, rnd, pos);
                    if (data == null) break;
                    groupSize = data.minCount() + rnd.nextInt(1 + data.maxCount() - data.minCount());
                }
                if (!isValidSpawnPositionForType(level, cat, data, pos, distSqr) || !state.canSpawn(data.type(), pos, level)) continue;
                Mob mob = getMobForSpawn(level, data.type());
                if (mob == null) return;
                mob.moveTo(px, y, pz, rnd.nextFloat() * 360.0F, 0.0F);
                if (isValidPositionForMob(level, mob, distSqr)) {
                    group = mob.finalizeSpawnGroup(rnd, level.getCurrentDifficultyAt(mob.blockPosition()), group);
                    spawned++;
                    inCluster++;
                    level.addFreshEntity(mob);
                    state.afterSpawn(mob, level);
                    if (spawned >= mob.getMaxSpawnClusterSize()) return;
                    if (mob.isMaxGroupSizeReached(inCluster)) break;
                }
            }
        }
    }

    private static BiomeSpawns.SpawnerData getRandomSpawnMobAt(ChunkLevel level, MobCategory cat, RandomSource rnd, Pos pos) {
        String biome = biomeName(level, pos);
        if (cat == MobCategory.WATER_AMBIENT && level.biomeIs(pos, "minecraft:reduce_water_ambient_spawns") && rnd.nextFloat() < 0.98F) return null;
        return BiomeSpawns.getRandom(mobsAt(level, cat, pos, biome), rnd);
    }

    /** ChunkGenerator.getMobsAt. TODO: 구조물 덮어쓰기. */
    private static List<BiomeSpawns.SpawnerData> mobsAt(ChunkLevel level, MobCategory cat, Pos pos, String biome) { return BiomeSpawns.mobs(biome, cat); }

    private static boolean isValidSpawnPositionForType(ChunkLevel level, MobCategory cat, BiomeSpawns.SpawnerData data, Pos pos, double distSqr) {
        EntityMeta.Info info = EntityMeta.of(data.type());
        if (info.category() == MobCategory.MISC) return false;
        if (!info.canSpawnFarFromPlayer() && distSqr > (double) cat.despawnDistance() * cat.despawnDistance()) return false;
        if (!info.canSummon() || !mobsAt(level, cat, pos, biomeName(level, pos)).contains(data)) return false;
        if (!SpawnPlacements.isSpawnPositionOk(data.type(), level, pos)) return false;
        if (!SpawnPlacements.checkSpawnRules(data.type(), level, SpawnReason.NATURAL, pos, level.random())) return false;
        EntityType t = EntityType.byName(data.type());
        return t == null || level.noCollision(t.makeBoundingBox(pos.x() + 0.5, pos.y(), pos.z() + 0.5));
    }

    private static Mob getMobForSpawn(ChunkLevel level, String type) {
        try {
            return Entities.create(level, type) instanceof Mob m ? m : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isValidPositionForMob(ChunkLevel level, Mob mob, double distSqr) {
        int far = mob.type() == null ? 128 : EntityMeta.category(mob.type().name()).despawnDistance();
        if (distSqr > (double) far * far && mob.removeWhenFarAway(distSqr)) return false;
        return mob.checkSpawnRules(level, SpawnReason.NATURAL) && mob.checkSpawnObstruction(level);
    }

    /**
     * NaturalSpawner.spawnMobsForChunkGeneration: 새로 만든 청크에 동물을 놓는다(청크 좌표·세계 시드로 정한 난수 — 바닐라와 같은 수열).
     * 바이옴은 청크 왼쪽 위 모서리 맨 위 칸, 확률은 바이옴의 creature_spawn_probability.
     */
    public static void spawnMobsForChunkGeneration(ChunkLevel level, int cx, int cz, long worldSeed) {
        int minX = cx * 16, minZ = cz * 16;
        String biome = biomeName(level, new Pos(minX, 319, minZ));
        if (biome == null) return;
        List<BiomeSpawns.SpawnerData> list = BiomeSpawns.mobs(biome, MobCategory.CREATURE);
        if (list.isEmpty()) return;
        // WorldgenRandom.setDecorationSeed(seed, minX, minZ)
        LegacyRandomSource rnd = new LegacyRandomSource(worldSeed);
        long a = rnd.nextLong() | 1L, b = rnd.nextLong() | 1L;
        rnd.setSeed(((long) minX * a + (long) minZ * b) ^ worldSeed);
        float probability = BiomeSpawns.creatureSpawnProbability(biome);
        while (rnd.nextFloat() < probability) {
            BiomeSpawns.SpawnerData data = BiomeSpawns.getRandom(list, rnd);
            if (data == null) continue;
            int count = data.minCount() + rnd.nextInt(1 + data.maxCount() - data.minCount());
            Object group = null;
            int x = minX + rnd.nextInt(16), z = minZ + rnd.nextInt(16);
            int ox = x, oz = z;
            EntityType type = EntityType.byName(data.type());
            for (int n = 0; n < count; n++) {
                boolean placed = false;
                for (int attempt = 0; !placed && attempt < 4; attempt++) {
                    Pos pos = topNonCollidingPos(level, data.type(), x, z);
                    if (type != null && EntityMeta.of(data.type()).canSummon() && SpawnPlacements.isSpawnPositionOk(data.type(), level, pos)) {
                        float w = type.width();
                        double d0 = Math.clamp((double) x, minX + (double) w, minX + 16.0 - w);
                        double d1 = Math.clamp((double) z, minZ + (double) w, minZ + 16.0 - w);
                        if (!level.noCollision(type.makeBoundingBox(d0, pos.y(), d1))
                                || !SpawnPlacements.checkSpawnRules(data.type(), level, SpawnReason.CHUNK_GENERATION,
                                        new Pos((int) Math.floor(d0), pos.y(), (int) Math.floor(d1)), level.random())) {
                            continue;
                        }
                        Mob mob = getMobForSpawn(level, data.type());
                        if (mob == null) continue;
                        mob.moveTo(d0, pos.y(), d1, rnd.nextFloat() * 360.0F, 0.0F);
                        if (mob.checkSpawnRules(level, SpawnReason.CHUNK_GENERATION) && mob.checkSpawnObstruction(level)) {
                            group = mob.finalizeSpawnGroup(level.random(), level.getCurrentDifficultyAt(mob.blockPosition()), group);
                            level.addFreshEntity(mob);
                            placed = true;
                        }
                    }
                    x += rnd.nextInt(5) - rnd.nextInt(5);
                    z += rnd.nextInt(5) - rnd.nextInt(5);
                    while (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16) {
                        x = ox + rnd.nextInt(5) - rnd.nextInt(5);
                        z = oz + rnd.nextInt(5) - rnd.nextInt(5);
                    }
                }
            }
        }
    }

    /** NaturalSpawner.getTopNonCollidingPos(오버월드: 천장 없음) + SpawnPlacementType.ON_GROUND.adjustSpawnPosition. 높이 지도는 오셀롯·앵무새만 MOTION_BLOCKING(잎 위). */
    private static Pos topNonCollidingPos(ChunkLevel level, String type, int x, int z) {
        boolean leavesCount = type.equals("minecraft:ocelot") || type.equals("minecraft:parrot");
        int y = leavesCount ? level.getMotionBlockingHeight(x, z) : level.getMotionBlockingNoLeavesHeight(x, z);
        Pos pos = new Pos(x, y, z);
        Pos below = pos.below();
        return org.domi.furnace.server.block.Pathfinding.isPathfindable(level.getBlockState(below), 1) ? below : pos;
    }
}
