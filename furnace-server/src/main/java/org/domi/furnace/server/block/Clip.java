// 바닐라 시선 판정: Item.getPlayerPOVHitResult → Level.clip(ClipContext) → BlockGetter.traverseBlocks(DDA) + VoxelShape.clip + AABB.clip.
// 블록은 OUTLINE 모양(상호작용 모양이 더 가까우면 그 면으로), 유체는 모드(NONE·SOURCE_ONLY·ANY·WATER)에 맞으면 높이 모양.
package org.domi.furnace.server.block;

public final class Clip {

    private Clip() {}

    public enum FluidMode { NONE, SOURCE_ONLY, ANY, WATER }

    /** 맞은 결과(빗나감이면 miss=true, pos 는 끝점 칸, face 는 끝→시작 방향에 가장 가까운 면). */
    public record Result(boolean miss, Pos pos, int face, double x, double y, double z, boolean inside) {
        public Hit hit() { return new Hit(pos, face, x, y, z, inside); }

        /** withPosition: 같은 지점·면, 다른 칸. */
        public Result withPos(Pos p) { return new Result(miss, p, face, x, y, z, inside); }
    }

    /** getPlayerPOVHitResult(level, player, fluidMode): 눈에서 시선 방향으로 블록 상호작용 거리(4.5, 크리에이티브 5)까지. */
    public static Result playerPOV(BlockLevel level, BlockActor player, FluidMode fluid) {
        double ex = player.x(), ey = player.y() + player.eyeHeight(), ez = player.z();
        double[] v = viewVector(player.xRot(), player.yRot());
        double r = player.isCreative() ? 5.0 : 4.5;
        return clip(level, ex, ey, ez, ex + v[0] * r, ey + v[1] * r, ez + v[2] * r, fluid);
    }

    /** Entity.calculateViewVector(xRot, yRot) — Mth.sin/cos 표. */
    public static double[] viewVector(float xRot, float yRot) {
        float f = xRot * (float) (Math.PI / 180.0);
        float f1 = -yRot * (float) (Math.PI / 180.0);
        float f2 = Dir.cos(f1), f3 = Dir.sin(f1), f4 = Dir.cos(f), f5 = Dir.sin(f);
        return new double[] {f3 * f4, -f5, f2 * f4};
    }

    /** BlockGetter.clip(ClipContext(from, to, OUTLINE, fluid)). */
    public static Result clip(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode fluid) {
        return clip(level, fx, fy, fz, tx, ty, tz, fluid, false);
    }

    /** BlockGetter.clip(ClipContext(from, to, COLLIDER, NONE)) — 몹 시야(hasLineOfSight)처럼 충돌 모양으로 본다. */
    public static Result clipCollider(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz) {
        return clip(level, fx, fy, fz, tx, ty, tz, FluidMode.NONE, true);
    }

    /** ClipContext(COLLIDER, fluid): 충돌 모양으로 보되 유체 모드를 고른다(NONE 또는 ANY). */
    public static Result clipCollider(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode fluid) {
        return clip(level, fx, fy, fz, tx, ty, tz, fluid, true);
    }

    private static Result clip(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode fluid, boolean collider) {
        if (fx == tx && fy == ty && fz == tz) return miss(fx, fy, fz, tx, ty, tz);
        double ex = lerp(-1.0E-7, tx, fx), ey = lerp(-1.0E-7, ty, fy), ez = lerp(-1.0E-7, tz, fz);
        double sx = lerp(-1.0E-7, fx, tx), sy = lerp(-1.0E-7, fy, ty), sz = lerp(-1.0E-7, fz, tz);
        int i = (int) Math.floor(sx), j = (int) Math.floor(sy), k = (int) Math.floor(sz);
        Result r = test(level, new Pos(i, j, k), fx, fy, fz, tx, ty, tz, fluid, collider);
        if (r != null) return r;
        double dx = ex - sx, dy = ey - sy, dz = ez - sz;
        int lx = sign(dx), ly = sign(dy), lz = sign(dz);
        double stepX = lx == 0 ? Double.MAX_VALUE : lx / dx;
        double stepY = ly == 0 ? Double.MAX_VALUE : ly / dy;
        double stepZ = lz == 0 ? Double.MAX_VALUE : lz / dz;
        double tX = stepX * (lx > 0 ? 1.0 - frac(sx) : frac(sx));
        double tY = stepY * (ly > 0 ? 1.0 - frac(sy) : frac(sy));
        double tZ = stepZ * (lz > 0 ? 1.0 - frac(sz) : frac(sz));
        while (tX <= 1.0 || tY <= 1.0 || tZ <= 1.0) {
            if (tX < tY) {
                if (tX < tZ) { i += lx; tX += stepX; } else { k += lz; tZ += stepZ; }
            } else if (tY < tZ) {
                j += ly; tY += stepY;
            } else {
                k += lz; tZ += stepZ;
            }
            r = test(level, new Pos(i, j, k), fx, fy, fz, tx, ty, tz, fluid, collider);
            if (r != null) return r;
        }
        return miss(fx, fy, fz, tx, ty, tz);
    }

    private static Result miss(double fx, double fy, double fz, double tx, double ty, double tz) {
        int face = nearest(fx - tx, fy - ty, fz - tz);
        return new Result(true, new Pos((int) Math.floor(tx), (int) Math.floor(ty), (int) Math.floor(tz)), face, tx, ty, tz, false);
    }

    /** 칸 하나: 블록 OUTLINE(상호작용 모양 우선 면)과 유체 모양 중 가까운 것. */
    private static Result test(BlockLevel level, Pos pos, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode mode, boolean collider) {
        int state = level.getBlockState(pos);
        if (collider) return clipShape(B.behavior(state).getCollisionShape(state, level, pos, null), pos, fx, fy, fz, tx, ty, tz);
        int fluid = level.getFluidState(pos);
        BlockBehavior b = B.behavior(state);
        Result block = clipShape(b.getShape(state, level, pos), pos, fx, fy, fz, tx, ty, tz);
        if (block != null) {
            Result inter = clipShape(B.INFO.interactionShape(state), pos, fx, fy, fz, tx, ty, tz);
            if (inter != null && dist2(inter, fx, fy, fz) < dist2(block, fx, fy, fz)) {
                block = new Result(false, block.pos, inter.face, block.x, block.y, block.z, block.inside);
            }
        }
        Result fl = canPick(mode, fluid) ? clipShape(fluidShape(fluid, level, pos), pos, fx, fy, fz, tx, ty, tz) : null;
        double d0 = block == null ? Double.MAX_VALUE : dist2(block, fx, fy, fz);
        double d1 = fl == null ? Double.MAX_VALUE : dist2(fl, fx, fy, fz);
        return d0 <= d1 ? block : fl;
    }

    private static boolean canPick(FluidMode mode, int fluid) {
        return switch (mode) {
            case NONE -> false;
            case SOURCE_ONLY -> Fluids.isSource(fluid);
            case ANY -> !Fluids.isEmpty(fluid);
            case WATER -> Fluids.isWater(fluid);
        };
    }

    /** FluidState.getShape: 높이(위가 같은 갈래면 1)만큼의 상자. */
    private static Shape fluidShape(int fluid, BlockLevel level, Pos pos) {
        if (Fluids.isEmpty(fluid)) return Shape.EMPTY;
        double h = FluidTicker.height(fluid, level, pos);
        return new Shape(new double[] {0, 0, 0, 1, h, 1});
    }

    private static double dist2(Result r, double fx, double fy, double fz) {
        double dx = r.x - fx, dy = r.y - fy, dz = r.z - fz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** VoxelShape.clip(from, to, pos): 시작 직후 점이 모양 안이면 안쪽 맞음, 아니면 상자들과 교차. */
    static Result clipShape(Shape shape, Pos pos, double fx, double fy, double fz, double tx, double ty, double tz) {
        if (shape.isEmpty()) return null;
        double dx = tx - fx, dy = ty - fy, dz = tz - fz;
        if (dx * dx + dy * dy + dz * dz < 1.0E-7) return null;
        double px = fx + dx * 0.001, py = fy + dy * 0.001, pz = fz + dz * 0.001;
        for (int b = 0; b < shape.boxCount(); b++) {
            double[] a = shape.box(b);
            double lx = px - pos.x(), ly = py - pos.y(), lz = pz - pos.z();
            if (lx >= a[0] && lx < a[3] && ly >= a[1] && ly < a[4] && lz >= a[2] && lz < a[5]) {
                return new Result(false, pos, Dir.opposite(nearest(dx, dy, dz)), px, py, pz, true);
            }
        }
        double[] minT = {1.0};
        int face = -1;
        for (int b = 0; b < shape.boxCount(); b++) {
            double[] a = shape.box(b);
            face = clipBox(a[0] + pos.x(), a[1] + pos.y(), a[2] + pos.z(), a[3] + pos.x(), a[4] + pos.y(), a[5] + pos.z(),
                    fx, fy, fz, minT, face, dx, dy, dz);
        }
        if (face < 0) return null;
        double t = minT[0];
        return new Result(false, pos, face, fx + t * dx, fy + t * dy, fz + t * dz, false);
    }

    /** AABB.getDirection: 시작점에서 들어가는 면과 시간. */
    private static int clipBox(double x0, double y0, double z0, double x1, double y1, double z1, double sx, double sy, double sz,
                               double[] minT, int dir, double dx, double dy, double dz) {
        if (dx > 1.0E-7) dir = clipPoint(minT, dir, dx, dy, dz, x0, y0, y1, z0, z1, Dir.WEST, sx, sy, sz);
        else if (dx < -1.0E-7) dir = clipPoint(minT, dir, dx, dy, dz, x1, y0, y1, z0, z1, Dir.EAST, sx, sy, sz);
        if (dy > 1.0E-7) dir = clipPoint(minT, dir, dy, dz, dx, y0, z0, z1, x0, x1, Dir.DOWN, sy, sz, sx);
        else if (dy < -1.0E-7) dir = clipPoint(minT, dir, dy, dz, dx, y1, z0, z1, x0, x1, Dir.UP, sy, sz, sx);
        if (dz > 1.0E-7) dir = clipPoint(minT, dir, dz, dx, dy, z0, x0, x1, y0, y1, Dir.NORTH, sz, sx, sy);
        else if (dz < -1.0E-7) dir = clipPoint(minT, dir, dz, dx, dy, z1, x0, x1, y0, y1, Dir.SOUTH, sz, sx, sy);
        return dir;
    }

    private static int clipPoint(double[] minT, int prev, double dA, double dB, double dC, double plane, double minB, double maxB,
                                 double minC, double maxC, int dir, double sA, double sB, double sC) {
        double t = (plane - sA) / dA;
        double b = sB + t * dB, c = sC + t * dC;
        if (0.0 < t && t < minT[0] && minB - 1.0E-7 < b && b < maxB + 1.0E-7 && minC - 1.0E-7 < c && c < maxC + 1.0E-7) {
            minT[0] = t;
            return dir;
        }
        return prev;
    }

    /** Direction.getNearest(x, y, z): 방향 벡터와 내적이 가장 큰 방향(같으면 앞선 것 — DOWN, UP, NORTH, SOUTH, WEST, EAST). */
    static int nearest(double x, double y, double z) {
        int best = Dir.NORTH;
        double max = Float.MIN_VALUE;
        for (int d : Dir.ALL) {
            double dot = x * Dir.DX[d] + y * Dir.DY[d] + z * Dir.DZ[d];
            if (dot > max) {
                max = dot;
                best = d;
            }
        }
        return best;
    }

    private static double lerp(double delta, double a, double b) { return a + delta * (b - a); }

    private static int sign(double v) { return v == 0.0 ? 0 : v > 0.0 ? 1 : -1; }

    private static double frac(double v) { return v - Math.floor(v); }
}
