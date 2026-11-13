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
        return clip(level, fx, fy, fz, tx, ty, tz, fluid, OUTLINE);
    }

    private static final int OUTLINE = 0, COLLIDER = 1, VISUAL = 2;

    /**
     * BlockGetter.clip(ClipContext(from, to, VISUAL, NONE, CollisionContext.empty())) — 시선 검사(시련 생성기·금고 감지기)처럼 시각 모양으로 본다.
     * 바닐라에서 VISUAL 을 쓰는 곳은 모두 문맥 없음이다.
     */
    public static Result clipVisual(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz) {
        return clip(level, fx, fy, fz, tx, ty, tz, FluidMode.NONE, VISUAL, CollisionContext.EMPTY);
    }

    /**
     * BlockGetter.clip(ClipContext(from, to, COLLIDER, NONE, entity)) — 몹 시야(hasLineOfSight)·투사체처럼 충돌 모양으로 본다.
     * ctx 는 ClipContext 를 만든 엔티티의 문맥(CollisionContext.of(entity)) — 비계는 그 엔티티가 위에 있을 때만 막는다.
     */
    public static Result clipCollider(BlockLevel level, CollisionContext ctx, double fx, double fy, double fz, double tx, double ty, double tz) {
        return clip(level, fx, fy, fz, tx, ty, tz, FluidMode.NONE, COLLIDER, ctx);
    }

    /** ClipContext(COLLIDER, fluid, entity): 충돌 모양으로 보되 유체 모드를 고른다(NONE·SOURCE_ONLY·ANY). */
    public static Result clipCollider(BlockLevel level, CollisionContext ctx, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode fluid) {
        return clip(level, fx, fy, fz, tx, ty, tz, fluid, COLLIDER, ctx);
    }

    private static Result clip(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode fluid, int shapeMode) {
        return clip(level, fx, fy, fz, tx, ty, tz, fluid, shapeMode, CollisionContext.EMPTY);
    }

    private static Result clip(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode fluid, int shapeMode,
            CollisionContext ctx) {
        return clip(level, fx, fy, fz, tx, ty, tz, fluid, shapeMode, ctx, null, false);
    }

    /**
     * 플러그인 API 광선(Paper CraftWorld.rayTraceBlocks): 충돌 모양(ignorePassable) 또는 윤곽 모양, 유체 모드, 칸 거르기(canCollide 가 false 면 그 칸은
     * 블록·유체 모두 건너뛴다 — Paper 의 Add predicate for blocks when raytracing), 로드되지 않은 청크에 닿으면 거기서 빗나간 것으로 끝낸다
     * (Paper 의 Prevent raytrace from loading chunks — 시작 칸이 그래도 같다).
     */
    public static Result clipApi(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode fluid, boolean ignorePassable,
            java.util.function.Predicate<Pos> canCollide) {
        // Paper: new ClipContext(…, CollisionContext.empty()) — 플러그인 광선은 문맥 없음
        return clip(level, fx, fy, fz, tx, ty, tz, fluid, ignorePassable ? COLLIDER : OUTLINE, CollisionContext.EMPTY, canCollide, true);
    }

    private static Result clip(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode fluid, int shapeMode,
            CollisionContext ctx, java.util.function.Predicate<Pos> canCollide, boolean stopUnloaded) {
        if (fx == tx && fy == ty && fz == tz) return miss(fx, fy, fz, tx, ty, tz);
        double ex = lerp(-1.0E-7, tx, fx), ey = lerp(-1.0E-7, ty, fy), ez = lerp(-1.0E-7, tz, fz);
        double sx = lerp(-1.0E-7, fx, tx), sy = lerp(-1.0E-7, fy, ty), sz = lerp(-1.0E-7, fz, tz);
        int i = (int) Math.floor(sx), j = (int) Math.floor(sy), k = (int) Math.floor(sz);
        Result r = test(level, new Pos(i, j, k), fx, fy, fz, tx, ty, tz, fluid, shapeMode, ctx, canCollide, stopUnloaded);
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
            r = test(level, new Pos(i, j, k), fx, fy, fz, tx, ty, tz, fluid, shapeMode, ctx, canCollide, stopUnloaded);
            if (r != null) return r;
        }
        return miss(fx, fy, fz, tx, ty, tz);
    }

    /** BlockGetter.isBlockInLine(ClipBlockStateContext(from, to, test)): 지나는 칸(시작 칸 포함) 중 상태가 test 에 맞는 것이 있는가. */
    public static boolean isBlockInLine(BlockLevel level, double fx, double fy, double fz, double tx, double ty, double tz,
            java.util.function.IntPredicate test) {
        if (fx == tx && fy == ty && fz == tz) return false;
        double ex = lerp(-1.0E-7, tx, fx), ey = lerp(-1.0E-7, ty, fy), ez = lerp(-1.0E-7, tz, fz);
        double sx = lerp(-1.0E-7, fx, tx), sy = lerp(-1.0E-7, fy, ty), sz = lerp(-1.0E-7, fz, tz);
        int i = (int) Math.floor(sx), j = (int) Math.floor(sy), k = (int) Math.floor(sz);
        if (test.test(level.getBlockState(new Pos(i, j, k)))) return true;
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
            if (test.test(level.getBlockState(new Pos(i, j, k)))) return true;
        }
        return false;
    }

    private static Result miss(double fx, double fy, double fz, double tx, double ty, double tz) {
        int face = nearest(fx - tx, fy - ty, fz - tz);
        return new Result(true, new Pos((int) Math.floor(tx), (int) Math.floor(ty), (int) Math.floor(tz)), face, tx, ty, tz, false);
    }

    /**
     * 칸 하나(바닐라 BlockGetter.clip(ClipContext, BlockPos)): 블록 모양(모드별 — 윤곽·충돌·시각)과 유체 모양 중 가까운 것.
     * 블록 모양은 상호작용 모양이 더 가까우면 그 면으로 바꾼다(clipWithInteractionOverride — 모드와 상관없이).
     */
    private static Result test(BlockLevel level, Pos pos, double fx, double fy, double fz, double tx, double ty, double tz, FluidMode mode, int shapeMode,
            CollisionContext ctx, java.util.function.Predicate<Pos> canCollide, boolean stopUnloaded) {
        if (stopUnloaded && !level.isOutsideBuildHeight(pos.y()) && !level.hasChunkAt(pos)) return miss(fx, fy, fz, tx, ty, tz);
        int state = level.getBlockState(pos);
        if (B.INFO.isAir(state) || canCollide != null && !canCollide.test(pos)) return null;
        BlockBehavior b = B.behavior(state);
        Shape shape = switch (shapeMode) {
            case COLLIDER -> b.getCollisionShape(state, level, pos, ctx);
            case VISUAL -> b.getVisualShape(state, level, pos, ctx);
            default -> b.getShape(state, level, pos);
        };
        Result block = clipShape(shape, pos, fx, fy, fz, tx, ty, tz);
        if (block != null) {
            Result inter = clipShape(B.INFO.interactionShape(state), pos, fx, fy, fz, tx, ty, tz);
            if (inter != null && dist2(inter, fx, fy, fz) < dist2(block, fx, fy, fz)) {
                block = new Result(false, block.pos, inter.face, block.x, block.y, block.z, block.inside);
            }
        }
        if (mode == FluidMode.NONE) return block;
        int fluid = level.getFluidState(pos);
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
