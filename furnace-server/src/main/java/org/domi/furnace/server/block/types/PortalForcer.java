// 바닐라 PortalForcer: 목적 차원에서 지옥문 찾기(POI nether_portal, 반경 네더 16 · 오버월드 128)와 만들기(나선 16 안의 빈 자리, 없으면 공중에 단상).
package org.domi.furnace.server.block.types;

import java.util.List;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockUtil;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.poi.PoiManager;
import org.domi.furnace.server.world.poi.PoiRecord;
import org.domi.furnace.server.world.poi.PoiTypes;

public final class PortalForcer {
    private PortalForcer() {}

    private static final int SEARCH_NETHER = 16, SEARCH_OVERWORLD = 128, CREATE_RADIUS = 16;
    private static final int FRAME_HEIGHT = 5, FRAME_WIDTH = 4;
    private static final Prop AXIS = B.prop("axis");

    /** 탐색 반경(목적 차원이 네더면 16, 아니면 128). */
    public static int searchRadius(boolean toNether) { return toNether ? SEARCH_NETHER : SEARCH_OVERWORLD; }

    /** 만들기 때 쓰는 반경(나선 16 + 틀). */
    public static int createRadius() { return CREATE_RADIUS + FRAME_WIDTH; }

    /**
     * findClosestPortalPosition: 반경 안 nether_portal POI 중 보더 안이고 지금도 지옥문인 칸 — 거리²가 가장 작고, 같으면 낮은 y.
     * (바닐라는 POI 를 먼저 ensureLoadedAndValid 한다 — Furnace 는 탐색 범위 청크를 미리 올려 둔다.)
     */
    public static Pos findClosestPortalPosition(ChunkLevel level, Pos pos, boolean toNether) {
        int r = searchRadius(toNether);
        List<PoiRecord> list = level.poiManager().getInSquare(t -> t == PoiTypes.NETHER_PORTAL, pos, r, PoiManager.Occupancy.ANY);
        Pos best = null;
        double bestD = 0;
        for (PoiRecord rec : list) {
            Pos p = rec.pos();
            if (!level.withinBorder(p.x(), p.z())) continue;
            if (!B.is(level.getBlockState(p), "minecraft:nether_portal")) continue;   // hasProperty(HORIZONTAL_AXIS)
            double d = distSqr(p, pos);
            if (best == null || d < bestD || d == bestD && p.y() < best.y()) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    private static double distSqr(Pos a, Pos b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** createPortal: 지옥문(2×3 안, 흑요석 틀)을 만든다. 보더 밖이라 못 만들면 null. */
    public static BlockUtil.FoundRectangle createPortal(ChunkLevel level, Pos pos, int axis) {
        int dir = axis == Dir.AXIS_X ? Dir.EAST : Dir.SOUTH;   // Direction.get(POSITIVE, axis)
        double bestD = -1.0;
        Pos best = null;
        double fallbackD = -1.0;
        Pos fallback = null;
        int maxY = Math.min(level.maxBuildHeight(), level.minBuildHeight() + level.dimensionType().logicalHeight()) - 1;
        int minY = level.minBuildHeight();
        int sx = Dir.DX[dir], sz = Dir.DZ[dir];
        for (int[] c : spiralAround(pos, CREATE_RADIUS, Dir.EAST, Dir.SOUTH)) {
            int x = c[0], z = c[2];
            int top = Math.min(maxY, level.getMotionBlockingHeight(x, z));
            if (!level.withinBorder(x, z) || !level.withinBorder(x + sx, z + sz)) continue;
            for (int y = top; y >= minY; y--) {
                if (!isEmptyBlock(level, x, y, z)) continue;
                int base = y;
                // while (y > minY && isEmptyBlock(mutable.move(DOWN))) y-- — 칸은 조건을 보기 전에 내려간다
                while (y > minY && isEmptyBlock(level, x, y - 1, z)) y--;
                if (y + 4 > maxY) continue;
                int gap = base - y;
                if (gap > 0 && gap < 3) continue;
                Pos p = new Pos(x, y, z);
                if (!canHostFrame(level, p, dir, 0)) continue;
                double d = distSqr(pos, p);
                if (canHostFrame(level, p, dir, -1) && canHostFrame(level, p, dir, 1) && (bestD == -1.0 || bestD > d)) {
                    bestD = d;
                    best = p;
                }
                if (bestD == -1.0 && (fallbackD == -1.0 || fallbackD > d)) {
                    fallbackD = d;
                    fallback = p;
                }
            }
        }
        if (bestD == -1.0 && fallbackD != -1.0) {
            best = fallback;
            bestD = fallbackD;
        }
        int obsidian = B.defaultState("minecraft:obsidian"), air = B.defaultState("minecraft:air");
        if (bestD == -1.0) {
            int lo = Math.max(minY - -1, 70), hi = maxY - 9;
            if (hi < lo) return null;
            Pos p = new Pos(pos.x() - sx, Math.clamp(pos.y(), lo, hi), pos.z() - sz);
            best = level.clampToBorder(p);
            int cw = Dir.clockWise(dir);
            for (int i = -1; i < 2; i++) {
                for (int j = 0; j < 2; j++) {
                    for (int k = -1; k < 3; k++) {
                        int state = k < 0 ? obsidian : air;
                        level.setBlock(best.offset(j * sx + i * Dir.DX[cw], k, j * sz + i * Dir.DZ[cw]), state, 3);
                    }
                }
            }
        }
        for (int i = -1; i < 3; i++) {
            for (int j = -1; j < 4; j++) {
                if (i == -1 || i == 2 || j == -1 || j == 3) level.setBlock(best.offset(i * sx, j, i * sz), obsidian, 3);
            }
        }
        int portal = AXIS.with(B.defaultState("minecraft:nether_portal"), Dir.axisName(axis));
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) level.setBlock(best.offset(i * sx, j, i * sz), portal, 18);
        }
        return new BlockUtil.FoundRectangle(best, 2, 3);
    }

    /** canPortalReplaceBlock: 바꿀 수 있고 유체가 없다. */
    private static boolean isEmptyBlock(ChunkLevel level, int x, int y, int z) {
        Pos p = new Pos(x, y, z);
        int s = level.getBlockState(p);
        return BlockAttrs.get().isReplaceable(s) && Fluids.isEmpty(level.getFluidState(p));
    }

    /** canHostFrame: 틀 자리(4 × 5, 옆으로 offset) — 맨 아래 줄은 단단, 위는 빈 칸. */
    private static boolean canHostFrame(ChunkLevel level, Pos origin, int dir, int offset) {
        int cw = Dir.clockWise(dir);
        for (int i = -1; i < 3; i++) {
            for (int j = -1; j < 4; j++) {
                int x = origin.x() + Dir.DX[dir] * i + Dir.DX[cw] * offset;
                int y = origin.y() + j;
                int z = origin.z() + Dir.DZ[dir] * i + Dir.DZ[cw] * offset;
                if (j < 0 && !BlockAttrs.get().isSolid(level.getBlockState(new Pos(x, y, z)))) return false;
                if (j >= 0 && !isEmptyBlock(level, x, y, z)) return false;
            }
        }
        return true;
    }

    /**
     * BlockPos.spiralAround(center, size, rotation, expansion): 가운데부터 나선으로(각 변 길이 leg/2 + 1), 4·size 변까지.
     * 좌표 {x, y, z} 를 차례로.
     */
    static List<int[]> spiralAround(Pos center, int size, int rotation, int expansion) {
        int[] dirs = {rotation, expansion, Dir.opposite(rotation), Dir.opposite(expansion)};
        List<int[]> out = new java.util.ArrayList<>();
        int lx = center.x() + Dir.DX[expansion], ly = center.y(), lz = center.z() + Dir.DZ[expansion];
        int legs = 4 * size, leg = -1, legSize = 0, legIndex = 0;
        while (true) {
            int d = dirs[(leg + 4) % 4];
            lx += Dir.DX[d];
            lz += Dir.DZ[d];
            if (legIndex >= legSize) {
                if (leg >= legs) break;
                leg++;
                legIndex = 0;
                legSize = leg / 2 + 1;
            }
            legIndex++;
            out.add(new int[] {lx, ly, lz});
        }
        return out;
    }
}
