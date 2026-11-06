// 플레이어 스폰 흩뿌리기(바닐라 ServerPlayer.adjustSpawnLocation + PlayerRespawnLogic.getOverworldRespawnPos).
// 월드 스폰 둘레 spawnRadius 칸 안에서 서로소 보폭으로 칸을 훑어 처음 설 수 있는 자리를 고른다. 시작 칸은 새 난수(RandomSource.create) 한 번.
// 레벨 소유 region 스레드에서 부른다(블록·높이 지도를 읽는다).
package org.domi.furnace.server.block;

import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.world.ChunkColumn;

public final class SpawnLocation {

    private SpawnLocation() {}

    /** RandomSource.create: 씨앗을 매번 새로 정한다(재현되지 않는 값 — 바닐라도 마찬가지). */
    private static final java.util.concurrent.atomic.AtomicLong UNIQUIFIER = new java.util.concurrent.atomic.AtomicLong(8682522807148012L);

    static long uniqueSeed() {
        return UNIQUIFIER.updateAndGet(v -> v * 1181783497276652981L) ^ System.nanoTime();
    }

    /**
     * adjustSpawnLocation(level, pos): 하늘빛이 있는 차원이고 월드 기본 게임 모드가 모험이 아니면 반경 안을 훑는다. 못 찾거나 해당 없으면 pos 에서
     * 충돌이 없어질 때까지 올리고, 아래 칸도 비어 있으면 내린다. 결과는 설 칸(발 밑 블록 위 칸).
     */
    public static Pos adjust(ChunkLevel level, Pos pos, int spawnRadius, boolean worldAdventure) {
        return adjust(level, pos, spawnRadius, worldAdventure, new LegacyRandomSource(uniqueSeed()));
    }

    static Pos adjust(ChunkLevel level, Pos pos, int spawnRadius, boolean worldAdventure, RandomSource random) {
        Pos result = pos;
        if (level.hasSkyLight() && !worldAdventure) {
            int r = Math.max(0, spawnRadius);
            int toBorder = (int) Math.floor(distanceToBorder(level, pos.x(), pos.z()));
            if (toBorder < r) r = toBorder;
            if (toBorder <= 1) r = 1;
            long side = r * 2 + 1;
            long area = side * side;
            int count = area > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) area;
            int step = count <= 16 ? count - 1 : 17;   // getCoprime
            int start = random.nextInt(count);
            for (int i = 0; i < count; i++) {
                int k = (start + step * i) % count;
                int dx = k % (r * 2 + 1), dz = k / (r * 2 + 1);
                Pos p = overworldRespawnPos(level, pos.x() + dx - r, pos.z() + dz - r);
                if (p != null && noCollision(level, p)) return p;
            }
            result = pos;
        }
        while (!noCollision(level, result) && result.y() < level.maxBuildHeight() - 1) result = result.above();
        while (noCollision(level, result.below()) && result.y() > level.minBuildHeight() + 1) result = result.below();
        return result;
    }

    /** WorldBorder.getDistanceToBorder(x, z): 네 변까지 거리 중 가장 짧은 것. */
    static double distanceToBorder(ChunkLevel level, double x, double z) {
        double[] b = level.borderBounds();
        double dz0 = z - b[1], dz1 = b[3] - z, dx0 = x - b[0], dx1 = b[2] - x;
        return Math.min(Math.min(dx0, dx1), Math.min(dz0, dz1));
    }

    /** 서 있는 플레이어 상자(0.6 × 1.8)를 칸 바닥 가운데에 놓았을 때 블록 충돌이 없는가(noCollision — 엔티티 충돌은 보지 않는다). */
    static boolean noCollision(ChunkLevel level, Pos p) {
        double x = p.x() + 0.5, z = p.z() + 0.5;
        return level.noCollision(new AABB(x - 0.3, p.y(), z - 0.3, x + 0.3, p.y() + 1.8, z + 0.3));
    }

    /**
     * PlayerRespawnLogic.getOverworldRespawnPos(level, x, z): 열의 MOTION_BLOCKING 꼭대기에서 아래로, 유체를 만나면 그만,
     * 충돌 모양 윗면이 가득 찬 블록 위 칸. 꼭대기가 월드 바닥보다 낮거나, 지표(WORLD_SURFACE)가 꼭대기 이하이면서 바다 바닥보다 높으면 없음.
     * 천장 있는 차원(생성기 getSpawnHeight)은 하늘빛이 있는 차원에서 생기지 않아 다루지 않는다(가정).
     */
    static Pos overworldRespawnPos(ChunkLevel level, int x, int z) {
        ChunkColumn col = level.chunk(x >> 4, z >> 4);
        if (col == null) return null;   // 바닐라는 청크를 불러온다 — 부르는 쪽이 반경만큼 미리 올린다
        int lx = x & 15, lz = z & 15;
        int top = col.motionBlockingBlockY(lx, lz);
        if (top < level.minBuildHeight()) return null;
        int surface = col.surfaceBlockY(lx, lz);
        if (surface <= top && surface > col.oceanFloorBlockY(lx, lz)) return null;
        for (int y = top + 1; y >= level.minBuildHeight(); y--) {
            Pos p = new Pos(x, y, z);
            int st = level.getBlockState(p);
            if (!Fluids.isEmpty(level.getFluidState(p))) break;
            if (B.behavior(st).getCollisionShape(st, level, p, null).isFaceFull(Dir.UP)) return p.above();
        }
        return null;
    }
}
