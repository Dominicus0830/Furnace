// 바닐라 NaturalSpawner + LocalMobCapCalculator + PotentialCalculator: 플레이어 근처 청크마다 종류(MobCategory)별로 몹을 자연 스폰한다.
// 한 틱: 지역의 몹을 세어 종류별 상한(max × 청크 수 / 289)과 플레이어별 지역 상한을 보고, 청크마다 무작위 칸에서 최대 3묶음을 시도한다.
// 이 서버는 region 별로 돌아서 몹 수·상한 계산은 region 안의 엔티티와 청크만 본다(바닐라는 차원 전체) — region 경계 근처에서만 다르다.
// 구조물 스폰 덮어쓰기(요새 적 하드코딩 목록 + worldgen/structure 의 spawn_overrides)는 mobsAt. 특수 스포너는 PhantomSpawner·PatrolSpawner·CatSpawner·
// WanderingTraderSpawner·VillageSiege, 청크 생성 동물은 spawnMobsForChunkGeneration.
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
        /** 이 틱의 스폰 정책(바닐라면 VANILLA) — 상한은 틱 시작에 한 번 풀어 둔다. */
        private final SpawnPolicyState policy;
        private final WorldSpawnPolicy policyHolder;
        private final int[] capNow, playerCapNow;
        /** 이번 틱에 이미 뗀 필터(스냅샷은 틱 동안 그대로라 같은 틱에 다시 불리지 않게). */
        private List<SpawnPolicyState.Filter> droppedFilters;
        private Pos lastPos;
        private String lastType;
        private double lastCharge;

        SpawnState(int spawnableChunkCount, List<Player> players) { this(spawnableChunkCount, players, null); }

        SpawnState(int spawnableChunkCount, List<Player> players, WorldSpawnPolicy policyHolder) {
            this.spawnableChunkCount = spawnableChunkCount;
            this.players = players;
            this.policyHolder = policyHolder;
            this.policy = policyHolder == null ? SpawnPolicyState.VANILLA : policyHolder.state();
            this.capNow = policy.resolvedCaps();
            this.playerCapNow = policy.resolvedPlayerCaps();
        }

        // 바닐라 LocalMobCapCalculator.getPlayersNear 와 같은 판정(관전자 제외, 청크 중심 128블록). 예전엔 호출마다 목록을
        // 새로 만들어 플레이어가 많으면 틱 할당 상위였다(perf-c4) — 같은 순서로 바로 훑는다.
        private boolean near(Player p, int cx, int cz) {
            if (p.gameMode() == org.domi.furnace.server.world.GameMode.SPECTATOR) return false;
            double dx = p.x() - (cx * 16 + 8), dz = p.z() - (cz * 16 + 8);
            return dx * dx + dz * dz <= 128.0 * 128.0;
        }

        void addMobToLocalCaps(int cx, int cz, MobCategory cat) {
            for (Player p : players) {
                if (near(p, cx, cz)) playerCounts.computeIfAbsent(p, k -> new EnumMap<>(MobCategory.class)).merge(cat, 1, Integer::sum);
            }
        }

        private boolean localCanSpawn(MobCategory cat, int cx, int cz) {
            for (Player p : players) {
                if (!near(p, cx, cz)) continue;
                int cap = policy.hasPerPlayer() ? policy.playerCap(p.uuid(), cat) : playerCapNow[cat.ordinal()];
                if (cap <= 0) continue;   // 정책: 상한 0 이면 이 플레이어 근처는 새로 스폰하지 않는다(바닐라 상수는 늘 양수)
                var m = playerCounts.get(p);
                if (m == null || m.getOrDefault(cat, 0) < cap) return true;
            }
            return false;
        }

        boolean canSpawnForCategory(MobCategory cat, int cx, int cz) {
            int limit = (int) ((long) capNow[cat.ordinal()] * spawnableChunkCount / MAGIC_NUMBER);
            return counts.getOrDefault(cat, 0) < limit && localCanSpawn(cat, cx, cz);
        }

        /**
         * 플러그인 스폰 필터(World.spawnPolicy().addFilter). 없으면 바로 true — 바닐라 경로에는 목록 비었는지 보는 비용뿐이다.
         * 꺼진 플러그인·예외를 던진 필터는 빼고 다음으로 넘어간다.
         */
        boolean filtersAllow(MobCategory cat, String type, ChunkLevel level, Pos pos, Player nearest) {
            var filters = policy.filters();
            if (filters.isEmpty()) return true;
            String biome = biomeName(level, pos);
            for (var f : filters) {
                if (droppedFilters != null && droppedFilters.contains(f)) continue;
                if (!f.ownerEnabled().getAsBoolean()) {
                    dropFilter(f);
                    continue;
                }
                boolean ok;
                try {
                    ok = f.fn().allow(cat, type, level.dimension(), pos.x(), pos.y(), pos.z(), biome, nearest == null ? null : nearest.uuid());
                } catch (Throwable t) {
                    org.slf4j.LoggerFactory.getLogger(NaturalSpawner.class).warn("플러그인 {} 의 스폰 필터가 예외를 던져 뗀다", f.owner(), t);
                    dropFilter(f);
                    continue;
                }
                if (!ok) return false;
            }
            return true;
        }

        private void dropFilter(SpawnPolicyState.Filter f) {
            if (droppedFilters == null) droppedFilters = new ArrayList<>(2);
            droppedFilters.add(f);
            if (policyHolder != null) policyHolder.removeFilter(f);
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

    /** getRoughBiome: 전하 계산은 확대 지터 없는 quart 바이옴을 쓴다(바닐라 createState). */
    static String roughBiomeName(ChunkLevel level, Pos pos) {
        String b = level.roughBiomeAt(pos.x(), pos.y(), pos.z());
        return b == null ? null : b.indexOf(':') < 0 ? "minecraft:" + b : b;
    }

    /** NaturalSpawner.createState: region 의 엔티티를 세어 종류별 수·지역 상한·전하를 모은다. */
    public static SpawnState createState(int spawnableChunkCount, Iterable<Entity> entities, ChunkLevel level) {
        return createState(spawnableChunkCount, entities, level, null);
    }

    /** policy: 월드의 스폰 정책(null 이면 바닐라 상수). */
    public static SpawnState createState(int spawnableChunkCount, Iterable<Entity> entities, ChunkLevel level, WorldSpawnPolicy policy) {
        List<Player> players = new ArrayList<>();
        level.forEachPlayer(players::add);
        SpawnState state = new SpawnState(spawnableChunkCount, players, policy);
        for (Entity e : entities) {
            if (!(e instanceof Mob mob) || mob.isRemoved()) continue;
            if (mob.isPersistentForCaps()) continue;
            MobCategory cat = EntityMeta.category(e.type().name());
            if (cat == MobCategory.MISC) continue;
            Pos pos = e.blockPosition();
            BiomeSpawns.SpawnCost cost = BiomeSpawns.cost(roughBiomeName(level, pos), e.type().name());
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
                String biome = biomeName(level, pos);   // 한 위치에서 여러 번 묻지 않게 한 번만
                if (data == null) {
                    data = getRandomSpawnMobAt(level, cat, rnd, pos, biome);
                    if (data == null) break;
                    groupSize = data.minCount() + rnd.nextInt(1 + data.maxCount() - data.minCount());
                }
                if (!isValidSpawnPositionForType(level, cat, data, pos, distSqr, biome) || !state.canSpawn(data.type(), pos, level)
                        || !state.filtersAllow(cat, data.type(), level, pos, player)) continue;
                Mob mob = getMobForSpawn(level, data.type());
                if (mob == null) return;
                mob.moveTo(px, y, pz, rnd.nextFloat() * 360.0F, 0.0F);
                if (isValidPositionForMob(level, mob, distSqr)) {
                    group = mob.finalizeSpawnGroup(rnd, level.getCurrentDifficultyAt(mob.blockPosition()), group);
                    spawned++;
                    inCluster++;
                    level.addFreshEntityWithPassengers(mob.spawnReason("NATURAL"));
                    state.afterSpawn(mob, level);
                    if (spawned >= mob.getMaxSpawnClusterSize()) return;
                    if (mob.isMaxGroupSizeReached(inCluster)) break;
                }
            }
        }
    }

    private static BiomeSpawns.SpawnerData getRandomSpawnMobAt(ChunkLevel level, MobCategory cat, RandomSource rnd, Pos pos, String biome) {
        if (cat == MobCategory.WATER_AMBIENT && level.biomeIs(pos, "minecraft:reduce_water_ambient_spawns") && rnd.nextFloat() < 0.98F) return null;
        return BiomeSpawns.getRandom(mobsAt(level, cat, pos, biome), rnd);
    }

    /** NetherFortressStructure.FORTRESS_ENEMIES — 바닐라 하드코딩 목록(데이터 팩과 무관). */
    static final List<BiomeSpawns.SpawnerData> FORTRESS_ENEMIES = List.of(
        new BiomeSpawns.SpawnerData("minecraft:blaze", 10, 2, 3),
        new BiomeSpawns.SpawnerData("minecraft:zombified_piglin", 5, 4, 4),
        new BiomeSpawns.SpawnerData("minecraft:wither_skeleton", 8, 5, 5),
        new BiomeSpawns.SpawnerData("minecraft:skeleton", 2, 5, 5),
        new BiomeSpawns.SpawnerData("minecraft:magma_cube", 3, 4, 4));

    /** NaturalSpawner.mobsAt: 요새 바닥(네더 벽돌 위, 요새 전체 상자 안)이면 요새 적, 아니면 ChunkGenerator.getMobsAt. */
    static List<BiomeSpawns.SpawnerData> mobsAt(ChunkLevel level, MobCategory cat, Pos pos, String biome) {
        if (isInNetherFortressBounds(level, cat, pos)) return FORTRESS_ENEMIES;
        // ChunkGenerator.getMobsAt: 이 청크가 참조하는 구조물마다 그 종류 덮어쓰기가 있고, pos 가 조각(piece) 또는 전체 상자(full) 안이면 그 목록
        var refs = level.structuresAt(pos);
        for (int i = 0; i < refs.length; i++) {
            String id = refs[i].id();
            boolean seen = false;
            for (int j = 0; j < i; j++) if (refs[j].id().equals(id)) { seen = true; break; }
            if (seen) continue;   // 구조물마다 한 번(그 구조물의 시작점 전부를 함께 본다)
            StructureSpawnOverrides.Override o = StructureSpawnOverrides.get(id, cat);
            if (o == null) continue;
            for (int j = i; j < refs.length; j++) {
                var r = refs[j];
                if (!r.id().equals(id)) continue;
                if (o.piece() ? r.hasPieceAt(pos.x(), pos.y(), pos.z()) : r.isInside(pos.x(), pos.y(), pos.z())) return o.spawns();
            }
        }
        return BiomeSpawns.mobs(biome != null ? biome : biomeName(level, pos), cat);
    }

    /** NaturalSpawner.isInNetherFortressBounds */
    static boolean isInNetherFortressBounds(ChunkLevel level, MobCategory cat, Pos pos) {
        return cat == MobCategory.MONSTER && B.is(level.getBlockState(pos.below()), "minecraft:nether_bricks")
            && level.structureAt(pos, "minecraft:fortress") != null;
    }

    private static boolean isValidSpawnPositionForType(ChunkLevel level, MobCategory cat, BiomeSpawns.SpawnerData data, Pos pos, double distSqr, String biome) {
        EntityMeta.Info info = EntityMeta.of(data.type());
        if (info.category() == MobCategory.MISC) return false;
        if (!info.canSpawnFarFromPlayer() && distSqr > (double) cat.despawnDistance() * cat.despawnDistance()) return false;
        if (!info.canSummon() || !mobsAt(level, cat, pos, biome).contains(data)) return false;
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
                            level.addFreshEntityWithPassengers(mob.spawnReason("CHUNK_GEN"));
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
