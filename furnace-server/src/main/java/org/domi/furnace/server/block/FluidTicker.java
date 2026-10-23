// 유체 예약 틱·흐름(바닐라 FlowingFluid + WaterFluid·LavaFluid). 물·용암 한 갈래(원천·흐름)를 water 플래그로 고른다.
// 새 양 계산(getNewLiquid)·아래로/옆으로 퍼지기·구멍 찾기(getSlopeDistance)·벽 통과(mergedFaceOccludes)·담을 수 있는 블록(canHoldFluid)·
// 용암이 물을 만나 돌이 되기·물이 부수는 블록 드롭·용암 무작위 틱(불 번지기)까지 바닐라 순서 그대로.
package org.domi.furnace.server.block;

import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.block.types.BaseFireBlock;
import org.domi.furnace.server.block.types.DoorBlock;
import org.domi.furnace.server.block.types.LiquidBlock;
import org.domi.furnace.server.entity.ItemStack;

public final class FluidTicker {

    private FluidTicker() {}

    private static final int LADDER = B.id("ladder"), SUGAR_CANE = B.id("sugar_cane"), BUBBLE_COLUMN = B.id("bubble_column"),
            NETHER_PORTAL = B.id("nether_portal"), END_PORTAL = B.id("end_portal"), END_GATEWAY = B.id("end_gateway"),
            STRUCTURE_VOID = B.id("structure_void"), STONE = B.id("stone");
    /** 옆으로 퍼질 때 결과를 적용하는 순서 — EnumMap(Direction) 순회 = 서수 순서. */
    private static final int[] SPREAD_APPLY_ORDER = {Dir.NORTH, Dir.SOUTH, Dir.WEST, Dir.EAST};

    // ------------------------------------------------------------------ 갈래별 값(WaterFluid·LavaFluid)

    /** getType().isSame(this): 같은 갈래(물/흐르는 물, 용암/흐르는 용암)인가. */
    private static boolean same(int fluid, boolean water) { return water ? Fluids.isWater(fluid) : Fluids.isLava(fluid); }

    private static int flowingType(boolean water) { return water ? Fluids.T_FLOWING_WATER : Fluids.T_FLOWING_LAVA; }

    private static int source(boolean water) { return water ? Fluids.WATER_SOURCE : Fluids.LAVA_SOURCE; }

    /** getDropOff: 물 1, 용암 2(울트라웜 1). */
    private static int dropOff(BlockLevel level, boolean water) { return water || level.ultraWarm() ? 1 : 2; }

    /** getSlopeFindDistance: 물 4, 용암 2(울트라웜 4). */
    private static int slopeFindDistance(BlockLevel level, boolean water) { return water || level.ultraWarm() ? 4 : 2; }

    /** canConvertToSource: waterSourceConversion / lavaSourceConversion. */
    private static boolean canConvertToSource(BlockLevel level, boolean water) {
        return level.gameRule(water ? "waterSourceConversion" : "lavaSourceConversion");
    }

    private static int tickDelay(BlockLevel level, boolean water) { return water ? 5 : level.ultraWarm() ? 10 : 30; }

    /** isSourceBlockOfThisType. */
    private static boolean isSourceOfThisType(int fluid, boolean water) { return same(fluid, water) && Fluids.isSource(fluid); }

    /** FluidState.getHeight(level, pos): 위 칸이 같은 갈래면 1, 아니면 양 / 9. */
    public static float height(int fluid, BlockLevel level, Pos pos) {
        if (Fluids.isEmpty(fluid)) return 0.0F;
        boolean water = Fluids.isWater(fluid);
        return same(level.getFluidState(pos.above()), water) ? 1.0F : ownHeight(fluid);
    }

    /** getOwnHeight: 양 / 9. */
    public static float ownHeight(int fluid) { return Fluids.isEmpty(fluid) ? 0.0F : Fluids.amount(fluid) / 9.0F; }

    /** 수평 방향 순회 순서(Direction.Plane.HORIZONTAL): 북·동·남·서. */
    private static final int[] HORIZONTAL_PLANE = {Dir.NORTH, Dir.EAST, Dir.SOUTH, Dir.WEST};
    private static final int ICE = B.id("ice"), FROSTED_ICE = B.id("frosted_ice");

    /**
     * FlowingFluid.getFlow(level, pos, state): 이웃과의 높이 차로 만든 흐름 방향(단위 벡터 {x, y, z}). 떨어지는 유체 옆에
     * 막힌 면이 있으면 아래로(-6) 기울인다. 엔티티를 미는 데 쓴다.
     */
    public static double[] flow(BlockLevel level, Pos pos, int fluid) {
        if (Fluids.isEmpty(fluid)) return new double[3];
        boolean water = Fluids.isWater(fluid);
        float own = ownHeight(fluid);
        double dx = 0.0, dz = 0.0;
        for (int dir : HORIZONTAL_PLANE) {
            Pos n = pos.relative(dir);
            int nf = level.getFluidState(n);
            if (!affectsFlow(nf, water)) continue;
            float f = ownHeight(nf);
            float g = 0.0F;
            if (f == 0.0F) {
                if (!BlockInfo.get().blocksMotion(level.getBlockState(n))) {
                    int bf = level.getFluidState(n.below());
                    if (affectsFlow(bf, water)) {
                        f = ownHeight(bf);
                        if (f > 0.0F) g = own - (f - 0.8888889F);
                    }
                }
            } else if (f > 0.0F) {
                g = own - f;
            }
            if (g != 0.0F) {
                dx += (double) ((float) Dir.DX[dir] * g);
                dz += (double) ((float) Dir.DZ[dir] * g);
            }
        }
        double[] v = {dx, 0.0, dz};
        if (Fluids.falling(fluid)) {
            for (int dir : HORIZONTAL_PLANE) {
                Pos n = pos.relative(dir);
                if (isSolidFace(level, n, dir, water) || isSolidFace(level, n.above(), dir, water)) {
                    v = normalize(v);
                    v[1] += -6.0;
                    break;
                }
            }
        }
        return normalize(v);
    }

    /** affectsFlow: 비었거나 같은 갈래. */
    private static boolean affectsFlow(int fluid, boolean water) { return Fluids.isEmpty(fluid) || same(fluid, water); }

    /** FlowingFluid.isSolidFace(level, pos, dir). */
    private static boolean isSolidFace(BlockLevel level, Pos pos, int dir, boolean water) {
        int s = level.getBlockState(pos);
        if (same(level.getFluidState(pos), water)) return false;
        if (dir == Dir.UP) return true;
        int b = B.block(s);
        if (b == ICE || b == FROSTED_ICE) return false;
        return B.isFaceSturdy(s, level, pos, dir);
    }

    /** Vec3.normalize: 길이가 1e-4 보다 작으면 0. */
    private static double[] normalize(double[] v) {
        double d = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return d < 1.0E-4 ? new double[3] : new double[] {v[0] / d, v[1] / d, v[2] / d};
    }

    // ------------------------------------------------------------------ 예약 틱(FlowingFluid.tick)

    public static void tick(BlockLevel level, Pos pos, int fluidState) {
        if (Fluids.isEmpty(fluidState)) return;
        boolean water = Fluids.isWater(fluidState);
        int state = fluidState;
        if (!Fluids.isSource(state)) {
            int newFluid = getNewLiquid(level, pos, level.getBlockState(pos), water);
            int delay = spreadDelay(level, pos, state, newFluid, water);
            if (Fluids.isEmpty(newFluid)) {
                state = newFluid;
                level.setBlock(pos, B.defaultState("minecraft:air"), 3);
            } else if (newFluid != state) {
                state = newFluid;
                int bs = Fluids.createLegacyBlock(newFluid);
                level.setBlock(pos, bs, 2);
                level.scheduleFluidTick(pos, Fluids.type(newFluid), delay);
                level.updateNeighborsAt(pos, B.block(bs));
            }
        }
        spread(level, pos, state, water);
    }

    /** getSpreadDelay: 물은 늘 5. 용암은 새 높이가 더 높아질 때 3/4 확률로 4배(level.getRandom). */
    private static int spreadDelay(BlockLevel level, Pos pos, int cur, int next, boolean water) {
        int delay = tickDelay(level, water);
        if (!water && !Fluids.isEmpty(cur) && !Fluids.isEmpty(next) && !Fluids.falling(cur) && !Fluids.falling(next)
                && height(next, level, pos) > height(cur, level, pos) && level.random().nextInt(4) != 0) {
            delay *= 4;
        }
        return delay;
    }

    /** spread: 아래로 퍼질 수 있으면 아래로(둘레 원천이 3 이상이면 옆으로도), 아니면 원천이거나 아래가 구멍이 아닐 때 옆으로. */
    private static void spread(BlockLevel level, Pos pos, int state, boolean water) {
        if (Fluids.isEmpty(state)) return;
        int bs = level.getBlockState(pos);
        Pos below = pos.below();
        int belowState = level.getBlockState(below);
        int newFluid = getNewLiquid(level, below, belowState, water);
        if (canSpreadTo(level, pos, bs, Dir.DOWN, below, belowState, level.getFluidState(below), Fluids.type(newFluid))) {
            spreadTo(level, below, belowState, Dir.DOWN, newFluid, water);
            if (sourceNeighborCount(level, pos, water) >= 3) spreadToSides(level, pos, state, bs, water);
        } else if (Fluids.isSource(state) || !isWaterHole(level, Fluids.type(newFluid), pos, bs, below, belowState, water)) {
            spreadToSides(level, pos, state, bs, water);
        }
    }

    private static void spreadToSides(BlockLevel level, Pos pos, int state, int bs, boolean water) {
        int amount = Fluids.amount(state) - dropOff(level, water);
        if (Fluids.falling(state)) amount = 7;
        if (amount <= 0) return;
        int[] spreads = getSpread(level, pos, bs, water);
        for (int dir : SPREAD_APPLY_ORDER) {
            int f = spreads[dir];
            if (f < 0) continue;
            Pos np = pos.relative(dir);
            int ns = level.getBlockState(np);
            if (canSpreadTo(level, pos, bs, dir, np, ns, level.getFluidState(np), Fluids.type(f))) spreadTo(level, np, ns, dir, f, water);
        }
    }

    /** getNewLiquid: 옆 칸 최대 양 - 감소량, 원천 둘 이상 + 아래가 단단하거나 원천이면 새 원천, 위에 같은 갈래면 떨어지는 흐름. */
    static int getNewLiquid(BlockLevel level, Pos pos, int state, boolean water) {
        int maxAmount = 0, sources = 0;
        for (int dir : Dir.HORIZONTAL) {
            Pos np = pos.relative(dir);
            int ns = level.getBlockState(np);
            int nf = B.fluid(ns);
            if (same(nf, water) && canPassThroughWall(dir, level, pos, state, np, ns)) {
                if (Fluids.isSource(nf)) sources++;
                maxAmount = Math.max(maxAmount, Fluids.amount(nf));
            }
        }
        if (canConvertToSource(level, water) && sources >= 2) {
            int below = level.getBlockState(pos.below());
            if (B.isSolid(below) || isSourceOfThisType(B.fluid(below), water)) return source(water);
        }
        Pos above = pos.above();
        int as = level.getBlockState(above);
        int af = B.fluid(as);
        if (!Fluids.isEmpty(af) && same(af, water) && canPassThroughWall(Dir.UP, level, pos, state, above, as)) {
            return Fluids.flowing(water, 8, true);
        }
        int k = maxAmount - dropOff(level, water);
        return k <= 0 ? Fluids.EMPTY : Fluids.flowing(water, k, false);
    }

    /** canPassThroughWall: 두 칸의 맞닿은 충돌 모양 면을 합쳐도 면 전체를 막지 않는가. */
    private static boolean canPassThroughWall(int dir, BlockLevel level, Pos pos, int state, Pos np, int ns) {
        Shape a = B.behavior(state).getCollisionShape(state, level, pos, null);
        Shape b = B.behavior(ns).getCollisionShape(ns, level, np, null);
        return !Shape.mergedFaceOccludes(a, b, dir);
    }

    /** canHoldFluid: 유체를 담는 블록이면 그 블록에 묻고, 문·표지판·사다리·사탕수수·거품 기둥·차원문·구조물 공기는 거절, 나머지는 움직임을 막지 않으면. */
    private static boolean canHoldFluid(BlockLevel level, Pos pos, int state, int fluidType) {
        BlockBehavior b = B.behavior(state);
        if (b.isLiquidContainer(state)) return b.canPlaceLiquid(null, level, pos, state, fluidType);
        int block = B.block(state);
        if (b instanceof DoorBlock || B.isTag(state, "minecraft:signs") || block == LADDER || block == SUGAR_CANE || block == BUBBLE_COLUMN) return false;
        if (block == NETHER_PORTAL || block == END_PORTAL || block == END_GATEWAY || block == STRUCTURE_VOID) return false;
        return !B.INFO.blocksMotion(state);
    }

    /** canPassThrough: 같은 갈래의 원천이 아니고 벽을 지나며 담을 수 있는가. */
    private static boolean canPassThrough(BlockLevel level, int fluidType, Pos pos, int state, int dir, Pos np, int ns, int nf, boolean water) {
        return !isSourceOfThisType(nf, water) && canPassThroughWall(dir, level, pos, state, np, ns) && canHoldFluid(level, np, ns, fluidType);
    }

    /** canSpreadTo: 그 칸의 유체가 이 유체로 바뀔 수 있고 벽을 지나며 담을 수 있는가. */
    private static boolean canSpreadTo(BlockLevel level, Pos pos, int state, int dir, Pos np, int ns, int nf, int fluidType) {
        return canBeReplacedWith(nf, level, np, fluidType, dir) && canPassThroughWall(dir, level, pos, state, np, ns)
                && canHoldFluid(level, np, ns, fluidType);
    }

    /** FluidState.canBeReplacedWith: 빈 칸은 늘, 물은 아래쪽으로 물 아닌 것에, 용암은 높이 4/9 이상일 때 물에. */
    private static boolean canBeReplacedWith(int nf, BlockLevel level, Pos np, int fluidType, int dir) {
        if (Fluids.isEmpty(nf)) return true;
        boolean incomingWater = fluidType == Fluids.T_WATER || fluidType == Fluids.T_FLOWING_WATER;
        if (Fluids.isWater(nf)) return dir == Dir.DOWN && !incomingWater;
        return height(nf, level, np) >= 0.44444445F && incomingWater;
    }

    /** isWaterHole: 아래로 벽을 지나고, 아래가 같은 갈래이거나 이 유체를 담을 수 있는가. */
    private static boolean isWaterHole(BlockLevel level, int fluidType, Pos pos, int state, Pos below, int belowState, boolean water) {
        if (!canPassThroughWall(Dir.DOWN, level, pos, state, below, belowState)) return false;
        return same(B.fluid(belowState), water) || canHoldFluid(level, below, belowState, fluidType);
    }

    private static int sourceNeighborCount(BlockLevel level, Pos pos, boolean water) {
        int n = 0;
        for (int dir : Dir.HORIZONTAL) if (isSourceOfThisType(level.getFluidState(pos.relative(dir)), water)) n++;
        return n;
    }

    /** 원점 기준 상대 좌표 키(바닐라 getCacheKey — x, z 각 8비트). */
    private static short cacheKey(Pos origin, Pos p) {
        int dx = p.x() - origin.x(), dz = p.z() - origin.z();
        return (short) ((dx + 128 & 0xFF) << 8 | dz + 128 & 0xFF);
    }

    /** getSpread: 가장 가까운 구멍 쪽(거리가 같으면 모두)으로 퍼질 방향과 그 칸의 새 유체. 결과는 방향 번호로(-1 은 없음). */
    private static int[] getSpread(BlockLevel level, Pos pos, int state, boolean water) {
        int best = 1000;
        int[] out = {-1, -1, -1, -1, -1, -1};
        Map<Short, Integer> states = new HashMap<>();
        Map<Short, Boolean> holes = new HashMap<>();
        int flowing = flowingType(water);
        for (int dir : Dir.HORIZONTAL) {
            Pos np = pos.relative(dir);
            short key = cacheKey(pos, np);
            int ns = states.computeIfAbsent(key, k -> level.getBlockState(np));
            int nf = B.fluid(ns);
            int newFluid = getNewLiquid(level, np, ns, water);
            if (canPassThrough(level, Fluids.type(newFluid), pos, state, dir, np, ns, nf, water)) {
                Pos below = np.below();
                boolean hole = holes.computeIfAbsent(key, k -> isWaterHole(level, flowing, np, ns, below, level.getBlockState(below), water));
                int dist = hole ? 0 : slopeDistance(level, np, 1, Dir.opposite(dir), ns, pos, states, holes, water);
                if (dist < best) java.util.Arrays.fill(out, -1);
                if (dist <= best) {
                    out[dir] = newFluid;
                    best = dist;
                }
            }
        }
        return out;
    }

    /** getSlopeDistance: 흐름이 지나갈 수 있는 칸을 따라 가장 가까운 구멍까지 거리(찾는 거리 안에 없으면 1000). */
    private static int slopeDistance(BlockLevel level, Pos pos, int depth, int excludedDir, int state, Pos origin,
                                     Map<Short, Integer> states, Map<Short, Boolean> holes, boolean water) {
        int best = 1000;
        int flowing = flowingType(water);
        for (int dir : Dir.HORIZONTAL) {
            if (dir == excludedDir) continue;
            Pos np = pos.relative(dir);
            short key = cacheKey(origin, np);
            int ns = states.computeIfAbsent(key, k -> level.getBlockState(np));
            int nf = B.fluid(ns);
            if (canPassThrough(level, flowing, pos, state, dir, np, ns, nf, water)) {
                boolean hole = holes.computeIfAbsent(key, k -> {
                    Pos below = np.below();
                    return isWaterHole(level, flowing, np, ns, below, level.getBlockState(below), water);
                });
                if (hole) return depth;
                if (depth < slopeFindDistance(level, water)) {
                    int d = slopeDistance(level, np, depth + 1, Dir.opposite(dir), ns, origin, states, holes, water);
                    if (d < best) best = d;
                }
            }
        }
        return best;
    }

    /** spreadTo: 유체를 담는 블록이면 placeLiquid, 아니면 (공기가 아니면 부수기 전 처리 후) 유체 블록으로. 용암이 아래 물을 만나면 돌. */
    private static void spreadTo(BlockLevel level, Pos pos, int state, int dir, int fluidState, boolean water) {
        if (!water && dir == Dir.DOWN && Fluids.isWater(level.getFluidState(pos))) {   // LavaFluid.spreadTo
            if (B.behavior(state) instanceof LiquidBlock) level.setBlock(pos, B.defaultState(STONE), 3);
            fizz(level, pos);
            return;
        }
        BlockBehavior b = B.behavior(state);
        if (b.isLiquidContainer(state)) {
            b.placeLiquid(level, pos, state, fluidState);
        } else {
            if (!B.isAir(state)) beforeDestroyingBlock(level, pos, state, water);
            level.setBlock(pos, Fluids.createLegacyBlock(fluidState), 3);
        }
    }

    /** beforeDestroyingBlock: 물은 그 블록 드롭(블록 엔티티 포함), 용암은 치익 소리(levelEvent 1501). */
    private static void beforeDestroyingBlock(BlockLevel level, Pos pos, int state, boolean water) {
        if (water) {
            Object be = B.INFO.hasBlockEntity(state) ? level.getBlockEntity(pos) : null;
            level.dropResources(state, pos, be, null, ItemStack.EMPTY);
        } else {
            fizz(level, pos);
        }
    }

    private static void fizz(BlockLevel level, Pos pos) { level.levelEvent(null, 1501, pos, 0); }

    // ------------------------------------------------------------------ 무작위 틱(LavaFluid.randomTick)

    /** FluidState.randomTick — 용암만: doFireTick 이면 위쪽 공기(가연성 이웃)나 옆 칸 위에 불. */
    public static void randomTick(BlockLevel level, Pos pos, int fluidState, RandomSource random) {
        if (!Fluids.isLava(fluidState) || !level.gameRule("doFireTick")) return;
        int i = random.nextInt(3);
        if (i > 0) {
            Pos p = pos;
            for (int j = 0; j < i; j++) {
                p = p.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                if (!isLoaded(level, p)) return;
                int s = level.getBlockState(p);
                if (B.isAir(s)) {
                    if (hasFlammableNeighbours(level, p)) {
                        level.setBlockAndUpdate(p, BaseFireBlock.getState(level, p));
                        return;
                    }
                } else if (B.INFO.blocksMotion(s)) {
                    return;
                }
            }
        } else {
            for (int k = 0; k < 3; k++) {
                Pos p = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (!isLoaded(level, p)) return;
                if (level.isEmptyBlock(p.above()) && isFlammable(level, p)) {
                    level.setBlockAndUpdate(p.above(), BaseFireBlock.getState(level, p));   // 바닐라도 불 모양은 아래 칸 기준
                }
            }
        }
    }

    /** Level.isLoaded: 높이 안이고 청크가 있다. */
    private static boolean isLoaded(BlockLevel level, Pos p) { return !level.isOutsideBuildHeight(p.y()) && level.hasChunkAt(p); }

    private static boolean hasFlammableNeighbours(BlockLevel level, Pos pos) {
        for (int d : Dir.ALL) if (isFlammable(level, pos.relative(d))) return true;
        return false;
    }

    /** isFlammable: 높이 안인데 청크가 없으면 아니고, 아니면 그 블록이 용암에 불붙는가. */
    private static boolean isFlammable(BlockLevel level, Pos pos) {
        if (!level.isOutsideBuildHeight(pos.y()) && !level.hasChunkAt(pos)) return false;
        return B.INFO.ignitedByLava(B.block(level.getBlockState(pos)));
    }
}
