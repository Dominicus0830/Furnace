// 바닐라 SpreadPlayersCommand 이식: /spreadplayers <중심 x z> <간격> <최대 범위> [under <최대 높이>] <팀 유지> <대상>.
// 알고리즘과 난수 호출 순서(초기 위치: 위치마다 x 다음 z, 충돌 시 재배치도 같은 순서)는 바닐라와 같다.
// 차이: 바닐라는 블록을 동기로 읽으며 청크를 불러오지만, 여기선 블록이 region 소유라서 칸이 속한 청크를 불러오고 그 region 에서
// 청크 한 장의 열 정보(지면 높이·안전 여부)를 한꺼번에 구해 캐시한다. 그래서 계산은 별도 스레드에서 돌고 결과는 완료 뒤에 알린다.
// 차이: 반환값은 위치 개수로 즉시 오지만 이동·성공·실패는 나중에 온다(context-notes 의 "반환값 차이").
package org.domi.furnace.server.command.builtin;

import static org.domi.furnace.server.command.Commands.argument;
import static org.domi.furnace.server.command.Commands.literal;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityArgument;
import org.domi.furnace.server.command.arguments.Vec2Argument;
import org.domi.furnace.server.command.brigadier.Arguments;
import org.domi.furnace.server.command.brigadier.CommandContext;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.Dynamic4CommandExceptionType;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.World;

public final class SpreadPlayersCommand {
    private SpreadPlayersCommand() { }

    private static final int MAX_ITERATION_COUNT = 10000;
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_TEAMS =
            new Dynamic4CommandExceptionType((a, b, c, d) -> Text.translatable("commands.spreadplayers.failed.teams", a, b, c, d));
    private static final Dynamic4CommandExceptionType ERROR_FAILED_TO_SPREAD_ENTITIES =
            new Dynamic4CommandExceptionType((a, b, c, d) -> Text.translatable("commands.spreadplayers.failed.entities", a, b, c, d));
    private static final Dynamic2CommandExceptionType ERROR_INVALID_MAX_HEIGHT =
            new Dynamic2CommandExceptionType((a, b) -> Text.translatable("commands.spreadplayers.failed.invalid.height", a, b));

    public static void register(CommandDispatcher<CommandSource> d) {
        var plain = argument("respectTeams", Arguments.bool())
                .then(argument("targets", EntityArgument.entities()).executes(c -> run(c, null)));
        var under = literal("under").then(argument("maxHeight", Arguments.integer())
                .then(argument("respectTeams", Arguments.bool())
                        .then(argument("targets", EntityArgument.entities()).executes(c -> run(c, c.getArgument("maxHeight", Integer.class))))));
        d.register(literal("spreadplayers").requires(s -> s.hasPermission(2))
                .then(argument("center", Vec2Argument.vec2())
                        .then(argument("spreadDistance", Arguments.floatArg(0.0F, Float.MAX_VALUE))
                                .then(argument("maxRange", Arguments.floatArg(1.0F, Float.MAX_VALUE)).then(plain).then(under)))));
    }

    private static int run(CommandContext<CommandSource> c, Integer maxHeight) throws CommandSyntaxException {
        CommandSource source = c.getSource();
        Vec2Argument.Vec2 center = Vec2Argument.getVec2(c, "center");
        float spreadDistance = c.getArgument("spreadDistance", Float.class);
        float maxRange = c.getArgument("maxRange", Float.class);
        boolean respectTeams = c.getArgument("respectTeams", Boolean.class);
        int maxY = maxHeight != null ? maxHeight : source.level().dimensionType().maxY();
        return org.domi.furnace.server.command.EntityOps.result(source, EntityArgument.withEntitiesAsync(c, "targets", targets -> spread(source, center, spreadDistance, maxRange, maxY, respectTeams, targets)));
    }

    // ------------------------------------------------------------------ 땅 조회

    /** 한 칸의 열 정보. 바닐라 Position.getSpawnY / isSafe 가 읽는 블록 상태를 대신 알려 준다. */
    interface Terrain {
        /** Position.getSpawnY(level, maxY): 위에서부터 공기 둘 위의 첫 단단한 칸 바로 위. */
        int spawnY(int x, int z);

        /** Position.isSafe(level, maxY). */
        boolean safe(int x, int z);
    }

    /** 실제 월드: 청크 한 장씩 불러와 그 region 에서 256 열을 계산해 캐시한다. 호출 스레드는 region 스레드가 아니어야 한다(기다린다). */
    static final class WorldTerrain implements Terrain {
        private final World world;
        private final int maxY;
        private final Map<Long, int[]> cache = new HashMap<>();

        WorldTerrain(World world, int maxY) {
            this.world = world;
            this.maxY = maxY;
        }

        @Override
        public int spawnY(int x, int z) { return column(x, z) >> 1; }

        @Override
        public boolean safe(int x, int z) { return (column(x, z) & 1) != 0; }

        /** (spawnY << 1) | safe. */
        private int column(int x, int z) {
            int cx = x >> 4, cz = z >> 4;
            int[] data = cache.computeIfAbsent((long) cx << 32 | cz & 0xFFFFFFFFL, k -> fetch(cx, cz));
            return data[(z & 15) << 4 | x & 15];
        }

        private int[] fetch(int cx, int cz) {
            ChunkPos cp = new ChunkPos(cx, cz);
            RuntimeException last = null;
            for (int attempt = 0; attempt < 5; attempt++) {
                try {
                    int[] r = world.submitAtChunk(cp, region -> region.loadChunk(cp))
                            .thenCompose(f -> f)
                            .thenCompose(col -> world.submitAtChunk(cp, region -> compute(region.level(), cx, cz, maxY)))
                            .get(60, TimeUnit.SECONDS);
                    if (r != null) return r;
                } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
                    last = new IllegalStateException("청크 " + cp + " 를 불러오지 못했다", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            throw last != null ? last : new IllegalStateException("청크 " + cp + " 를 읽지 못했다");
        }
    }

    /** 청크 한 장의 열 정보를 구한다(주인 region 스레드). 청크가 없으면 null. */
    static int[] compute(org.domi.furnace.server.world.RegionLevel level, int cx, int cz, int maxY) {
        if (!level.hasChunkAt(new Pos(cx << 4, level.minBuildHeight(), cz << 4))) return null;
        int[] out = new int[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int x = cx << 4 | lx, z = cz << 4 | lz;
                int spawnY = spawnY(level, x, z, maxY);
                int below = level.getBlockState(new Pos(x, spawnY - 1, z));
                boolean safe = spawnY - 1 < maxY && !isLiquid(below) && !B.isTag(below, "minecraft:fire");
                out[lz << 4 | lx] = spawnY << 1 | (safe ? 1 : 0);
            }
        }
        return out;
    }

    /** BlockState.liquid(): 물·용암·거품 기둥. */
    private static boolean isLiquid(int state) { return B.isLiquid(state) || B.is(state, "bubble_column"); }

    /** 바닐라 Position.getSpawnY(level, maxY). */
    static int spawnY(org.domi.furnace.server.world.RegionLevel level, int x, int z, int maxY) {
        int y = maxY + 1;
        boolean above = B.isAir(level.getBlockState(new Pos(x, y, z)));
        y--;
        boolean here = B.isAir(level.getBlockState(new Pos(x, y, z)));
        while (y > level.minBuildHeight()) {
            y--;
            boolean below = B.isAir(level.getBlockState(new Pos(x, y, z)));
            if (!below && here && above) return y + 1;
            above = here;
            here = below;
        }
        return maxY + 1;
    }

    // ------------------------------------------------------------------ 알고리즘

    /** 바닐라 SpreadPlayersCommand.Position. */
    static final class Position {
        double x;
        double z;

        double dist(Position o) {
            double dx = x - o.x, dz = z - o.z;
            return Math.sqrt(dx * dx + dz * dz);
        }

        void normalize() {
            double len = length();
            x /= len;
            z /= len;
        }

        double length() { return Math.sqrt(x * x + z * z); }

        void moveAway(Position o) {
            x -= o.x;
            z -= o.z;
        }

        boolean clamp(double minX, double minZ, double maxX, double maxZ) {
            boolean changed = false;
            if (x < minX) {
                x = minX;
                changed = true;
            } else if (x > maxX) {
                x = maxX;
                changed = true;
            }
            if (z < minZ) {
                z = minZ;
                changed = true;
            } else if (z > maxZ) {
                z = maxZ;
                changed = true;
            }
            return changed;
        }

        /** Mth.nextDouble(random, min, max): min >= max 이면 난수를 쓰지 않는다. */
        void randomize(RandomSource random, double minX, double minZ, double maxX, double maxZ) {
            x = nextDouble(random, minX, maxX);
            z = nextDouble(random, minZ, maxZ);
        }
    }

    static double nextDouble(RandomSource random, double min, double max) { return min >= max ? min : random.nextDouble() * (max - min) + min; }

    static Position[] createInitialPositions(RandomSource random, int count, double minX, double minZ, double maxX, double maxZ) {
        Position[] out = new Position[count];
        for (int i = 0; i < count; i++) {
            Position p = new Position();
            p.randomize(random, minX, minZ, maxX, maxZ);
            out[i] = p;
        }
        return out;
    }

    /** 바닐라 spreadPositions: 퍼질 때까지(최대 10000 번) 밀어내고 가두고 안전하지 않은 칸은 다시 뽑는다. 못 퍼지면 failed.* 오류. */
    static void spreadPositions(float centerX, float centerZ, double spreadDistance, Terrain terrain, RandomSource random,
                                double minX, double minZ, double maxX, double maxZ, Position[] positions, boolean respectTeams)
            throws CommandSyntaxException {
        boolean again = true;
        double minDistance = Float.MAX_VALUE;
        int iteration;
        for (iteration = 0; iteration < MAX_ITERATION_COUNT && again; iteration++) {
            again = false;
            minDistance = Float.MAX_VALUE;
            for (int j = 0; j < positions.length; j++) {
                Position p = positions[j];
                int near = 0;
                Position delta = new Position();
                for (int k = 0; k < positions.length; k++) {
                    if (j == k) continue;
                    Position q = positions[k];
                    double dist = p.dist(q);
                    minDistance = Math.min(dist, minDistance);
                    if (dist < spreadDistance) {
                        near++;
                        delta.x += q.x - p.x;
                        delta.z += q.z - p.z;
                    }
                }
                if (near > 0) {
                    delta.x /= near;
                    delta.z /= near;
                    double len = delta.length();
                    if (len > 0.0) {
                        delta.normalize();
                        p.moveAway(delta);
                    } else {
                        p.randomize(random, minX, minZ, maxX, maxZ);
                    }
                    again = true;
                }
                if (p.clamp(minX, minZ, maxX, maxZ)) again = true;
            }
            if (!again) {
                for (Position p : positions) {
                    if (!terrain.safe((int) Math.floor(p.x), (int) Math.floor(p.z))) {
                        p.randomize(random, minX, minZ, maxX, maxZ);
                        again = true;
                    }
                }
            }
        }
        if (minDistance == Float.MAX_VALUE) minDistance = 0.0;
        if (iteration >= MAX_ITERATION_COUNT) {
            String s = String.format(Locale.ROOT, "%.2f", minDistance);
            throw (respectTeams ? ERROR_FAILED_TO_SPREAD_TEAMS : ERROR_FAILED_TO_SPREAD_ENTITIES).create(positions.length, centerX, centerZ, s);
        }
    }

    /** 대상이 놓일 위치 한 칸. */
    record Placement(Rider target, double x, double y, double z) { }

    /**
     * 바닐라 setPlayerPositions 의 계산 부분: 대상마다 위치를 정하고(팀 유지면 같은 팀은 같은 위치 — 팀이 아직 없어 모두 한 팀이다),
     * 대상마다 가장 가까운 다른 위치까지의 거리의 평균을 돌려준다. 옮기는 일은 호출자가 한다.
     */
    static double place(List<Rider> targets, Terrain terrain, Position[] positions, boolean respectTeams, List<Placement> out) {
        double sum = 0.0;
        int index = 0;
        Map<Object, Position> byTeam = new HashMap<>();
        Object noTeam = new Object();
        for (Rider t : targets) {
            Position p;
            if (respectTeams) {
                Position found = byTeam.get(noTeam);   // 팀 시스템이 없어 모든 대상의 팀이 null
                if (found == null) {
                    found = positions[index++];
                    byTeam.put(noTeam, found);
                }
                p = found;
            } else {
                p = positions[index++];
            }
            int fx = (int) Math.floor(p.x), fz = (int) Math.floor(p.z);
            out.add(new Placement(t, fx + 0.5, terrain.spawnY(fx, fz), fz + 0.5));
            double nearest = Double.MAX_VALUE;
            for (Position q : positions) if (p != q) nearest = Math.min(nearest, p.dist(q));
            sum += nearest;
        }
        return targets.size() < 2 ? 0.0 : sum / targets.size();
    }

    /** 팀 수 — 팀이 없으니 대상이 있으면 1(모두 "팀 없음" 하나). */
    private static int teamCount(List<Rider> targets) { return targets.isEmpty() ? 0 : 1; }

    static CompletableFuture<Integer> spread(CommandSource source, Vec2Argument.Vec2 center, float spreadDistance, float maxRange, int maxHeight, boolean respectTeams,
                      List<Rider> targets) throws CommandSyntaxException {
        World world = source.level();
        int minY = world.dimensionType().minY();
        if (maxHeight < minY) throw ERROR_INVALID_MAX_HEIGHT.create(maxHeight, minY);
        String dimension = world.dimension();
        RandomSource random = RandomSource.createFor(org.domi.furnace.server.block.RandomSeeds.world(dimension, world.levelName()), org.domi.furnace.server.block.RandomSeeds.Purpose.SPREAD_PLAYERS);
        double[] avg = new double[1];
        double minX = center.x() - maxRange, minZ = center.y() - maxRange, maxX = center.x() + maxRange, maxZ = center.y() + maxRange;   // float 뺄셈 뒤 double
        Position[] positions = createInitialPositions(random, respectTeams ? teamCount(targets) : targets.size(), minX, minZ, maxX, maxZ);
        // 땅을 읽느라 기다리므로 region·연결 스레드를 막지 않게 따로 돌린다(가상 스레드). 옮김·메시지는 전역에서.
        CompletableFuture<List<Placement>> computed = CompletableFuture.supplyAsync(() -> {
            try {
                Terrain terrain = new WorldTerrain(world, maxHeight);
                spreadPositions(center.x(), center.y(), spreadDistance, terrain, random, minX, minZ, maxX, maxZ, positions, respectTeams);
                List<Placement> placements = new java.util.ArrayList<>();
                avg[0] = place(targets, terrain, positions, respectTeams, placements);
                return placements;
            } catch (CommandSyntaxException e) {
                throw new java.util.concurrent.CompletionException(e);
            } catch (RuntimeException e) {
                throw new java.util.concurrent.CompletionException(new org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType(
                        Text.text("/spreadplayers failed: " + e.getMessage())).create());
            }
        }, r -> Thread.startVirtualThread(() -> org.domi.furnace.server.world.CommandDrainGate.inCommandScope(r)));   // 땅 읽기 홉도 명령 범위(T-150)
        return org.domi.furnace.server.command.EntityOps.awaitAsync(source, computed, placements -> {
            List<CompletableFuture<Void>> moves = new java.util.ArrayList<>(placements.size());
            for (Placement p : placements) {
                float yaw = p.target().riderYRot(), pitch = p.target().riderXRot();
                moves.add(Teleports.teleport(source, p.target(), dimension, p.x(), p.y(), p.z(), yaw, pitch));
            }
            return org.domi.furnace.server.command.EntityOps.awaitAsync(source, CompletableFuture.allOf(moves.toArray(new CompletableFuture[0])), v -> {
                source.sendSuccess(() -> Text.translatable("commands.spreadplayers.success." + (respectTeams ? "teams" : "entities"),
                        positions.length, center.x(), center.y(), String.format(Locale.ROOT, "%.2f", avg[0])), true);
                return CompletableFuture.completedFuture(positions.length);
            });
        });
    }
}
