// 바닐라 MultifaceBlock + MultifaceSpreader 이식 (glow_lichen, sculk_vein). 두 블록은 속성 배치가 같아(down,east,north,south,up,
// waterlogged,west) "모두 false" state id 에서 뺀 값이 면 비트가 된다. sculk_vein 은 SculkVeinBlock.SculkVeinSpreaderConfig 의
// 교체 규칙(sculk·촉매·이동 피스톤 금지, 대각 모서리 막힘, 불 금지, canBeReplaced 허용)을 쓴다.
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class MultifaceSpreader {

    static final int[] DX = {0, 0, 0, 0, -1, 1};
    static final int[] DY = {-1, 1, 0, 0, 0, 0};
    static final int[] DZ = {0, 0, -1, 1, 0, 0};
    /** Direction 순서(DOWN, UP, NORTH, SOUTH, WEST, EAST)별 면 비트. */
    private static final int[] FACE_BIT = {64, 4, 16, 8, 1, 32};
    private static final int WATERLOGGED_BIT = 2;
    private static final int FACE_MASK = 64 | 4 | 16 | 8 | 1 | 32;

    static final int SAME_POSITION = 0, SAME_PLANE = 1, WRAP_AROUND = 2;
    static final int[] ALL_SPREAD_TYPES = {SAME_POSITION, SAME_PLANE, WRAP_AROUND};

    /** 레지스트리에서 뽑은 공용 블록 표. */
    public static final class Env {
        final BlockAttrs attrs = BlockAttrs.get();
        final FluidKinds fluids;
        final boolean[] waterBlock;
        final boolean[] sculkCatalyst;
        final boolean[] movingPiston;
        final boolean[] fire;
        final int sculk;
        final int glowLichenBase;
        final int sculkVeinBase;
        final BlockRegistry registry;

        public Env(BlockRegistry registry) {
            this.registry = registry;
            this.fluids = new FluidKinds(registry);
            int n = registry.totalStateCount();
            this.waterBlock = flags(registry, n, "minecraft:water");
            this.sculkCatalyst = flags(registry, n, "minecraft:sculk_catalyst");
            this.movingPiston = flags(registry, n, "minecraft:moving_piston");
            this.fire = flags(registry, n, BlockTags.get().blocksOf("minecraft:fire").toArray(new String[0]));
            this.sculk = registry.stateIdOrAir("minecraft:sculk");
            this.glowLichenBase = registry.stateIdOrAir("minecraft:glow_lichen");
            this.sculkVeinBase = registry.stateIdOrAir("minecraft:sculk_vein");
        }

        static boolean[] flags(BlockRegistry registry, int n, String... blocks) {
            boolean[] f = new boolean[n];
            for (String b : blocks) for (int id : registry.stateIdsForBlock(b)) f[id] = true;
            return f;
        }

        boolean flag(boolean[] f, int s) { return s >= 0 && s < f.length && f[s]; }

        boolean isAirOrWaterBlock(int s) { return attrs.isAir(s) || flag(waterBlock, s); }

        /** 물 블록이면서 source(level=0). */
        boolean isWaterSourceBlock(int s) { return flag(waterBlock, s) && fluids.of(s) == FluidKind.WATER; }

        /** MultifaceBlock.canAttachTo: 이웃의 dir.opposite 면이 지지 모양 또는 충돌 모양으로 가득. */
        boolean canAttachTo(int neighborState, int dir) {
            int o = dir ^ 1;
            return attrs.isFaceSturdy(neighborState, o) || attrs.isCollisionFaceFull(neighborState, o);
        }

        /** BlockState.isCollisionShapeFullBlock — 충돌 모양 6면이 모두 가득으로 근사. */
        boolean isCollisionShapeFullBlock(int s) {
            for (int d = 0; d < 6; d++) if (!attrs.isCollisionFaceFull(s, d)) return false;
            return true;
        }
    }

    final Env env;
    final int base;
    private final boolean vein;
    private final int[] spreadTypes;

    MultifaceSpreader(Env env, int base, boolean vein, int[] spreadTypes) {
        this.env = env;
        this.base = base;
        this.vein = vein;
        this.spreadTypes = spreadTypes;
    }

    // ------------------------------------------------------------------ state 비트

    boolean is(int s) { return s <= base && s > base - 128; }

    boolean hasFace(int s, int d) { return is(s) && ((base - s) & FACE_BIT[d]) != 0; }

    boolean hasAnyFace(int s) { return is(s) && ((base - s) & FACE_MASK) != 0; }

    int withFace(int s, int d, boolean v) {
        int f = base - s;
        f = v ? f | FACE_BIT[d] : f & ~FACE_BIT[d];
        return base - f;
    }

    /** MultifaceBlock.availableFaces — Direction 순서 비트 마스크(1 << ordinal). */
    int faceMask(int s) {
        int m = 0;
        for (int d = 0; d < 6; d++) if (hasFace(s, d)) m |= 1 << d;
        return m;
    }

    int waterloggedBase() { return base - WATERLOGGED_BIT; }

    // ------------------------------------------------------------------ MultifaceBlock

    /** isValidStateForPlacement: 같은 면이 이미 있으면 거짓, 아니면 그 면 쪽 이웃에 붙을 수 있는가. */
    boolean isValidStateForPlacement(FeatureWorldView v, int s, int x, int y, int z, int face) {
        if (hasFace(s, face)) return false;
        return env.canAttachTo(v.getBlock(x + DX[face], y + DY[face], z + DZ[face]), face);
    }

    /** getStateForPlacement(state, level, pos, face) — 불가면 -1. */
    int getStateForPlacement(FeatureWorldView v, int s, int x, int y, int z, int face) {
        if (!isValidStateForPlacement(v, s, x, y, z, face)) return -1;
        int b = is(s) ? s : env.fluids.of(s) == FluidKind.WATER ? waterloggedBase() : base;
        return withFace(b, face, true);
    }

    // ------------------------------------------------------------------ SpreadConfig

    private boolean isOtherBlockValidAsSource(int s) { return vein && !is(s); }

    private boolean canSpreadFrom(int s, int face) { return isOtherBlockValidAsSource(s) || hasFace(s, face); }

    private boolean stateCanBeReplaced(FeatureWorldView v, int sx, int sy, int sz, int px, int py, int pz, int face, int s) {
        if (vein) {
            int ns = v.getBlock(px + DX[face], py + DY[face], pz + DZ[face]);
            if (ns == env.sculk || env.flag(env.sculkCatalyst, ns) || env.flag(env.movingPiston, ns)) return false;
            if (Math.abs(sx - px) + Math.abs(sy - py) + Math.abs(sz - pz) == 2) {
                int o = face ^ 1;
                if (env.attrs.isFaceSturdy(v.getBlock(sx + DX[o], sy + DY[o], sz + DZ[o]), face)) return false;
            }
            FluidKind f = env.fluids.of(s);
            if (f != FluidKind.EMPTY && f != FluidKind.WATER) return false;
            if (env.flag(env.fire, s)) return false;
            if (env.attrs.isReplaceable(s)) return true;
        }
        return env.attrs.isAir(s) || is(s) || env.isWaterSourceBlock(s);
    }

    private boolean canSpreadInto(FeatureWorldView v, int sx, int sy, int sz, int px, int py, int pz, int face) {
        int s = v.getBlock(px, py, pz);
        return stateCanBeReplaced(v, sx, sy, sz, px, py, pz, face, s) && isValidStateForPlacement(v, s, px, py, pz, face);
    }

    // ------------------------------------------------------------------ MultifaceSpreader

    /** spreadAll(state, level, pos, markForPostprocessing) — 성공한 퍼짐 수. */
    long spreadAll(FeatureWorldView v, int state, int x, int y, int z, boolean mark) {
        long n = 0;
        for (int face = 0; face < 6; face++) {
            if (!canSpreadFrom(state, face)) continue;
            for (int dir = 0; dir < 6; dir++) if (spreadFromFaceTowardDirection(v, state, x, y, z, face, dir, mark)) n++;
        }
        return n;
    }

    /** spreadFromFaceTowardRandomDirection — Direction.allShuffled 순서로 첫 성공에서 멈춘다. */
    boolean spreadFromFaceTowardRandomDirection(FeatureWorldView v, int state, int x, int y, int z, int face, WorldgenRandom random, boolean mark) {
        int[] dirs = shuffledDirections(random);
        for (int dir : dirs) if (spreadFromFaceTowardDirection(v, state, x, y, z, face, dir, mark)) return true;
        return false;
    }

    private boolean spreadFromFaceTowardDirection(FeatureWorldView v, int state, int x, int y, int z, int startFace, int spreadDir, boolean mark) {
        if ((spreadDir >> 1) == (startFace >> 1)) return false;
        if (!(isOtherBlockValidAsSource(state) || hasFace(state, startFace) && !hasFace(state, spreadDir))) return false;
        for (int t : spreadTypes) {
            int px = x, py = y, pz = z, pf;
            switch (t) {
                case SAME_POSITION -> pf = spreadDir;
                case SAME_PLANE -> { px += DX[spreadDir]; py += DY[spreadDir]; pz += DZ[spreadDir]; pf = startFace; }
                default -> {
                    px += DX[spreadDir] + DX[startFace]; py += DY[spreadDir] + DY[startFace]; pz += DZ[spreadDir] + DZ[startFace];
                    pf = spreadDir ^ 1;
                }
            }
            if (canSpreadInto(v, x, y, z, px, py, pz, pf)) {
                int ns = getStateForPlacement(v, v.getBlock(px, py, pz), px, py, pz, pf);
                if (ns == -1) return false;
                if (mark) v.markPosForPostprocessing(px, py, pz);
                return v.trySetBlock(px, py, pz, ns);
            }
        }
        return false;
    }

    /** Direction.allShuffled(random) — Util.shuffle. */
    static int[] shuffledDirections(WorldgenRandom random) {
        int[] d = {0, 1, 2, 3, 4, 5};
        shuffle(d, d.length, random);
        return d;
    }

    /** Util.shuffle: i = size..2 에서 j = nextInt(i), swap(i-1, j). */
    static void shuffle(int[] a, int len, WorldgenRandom random) {
        for (int i = len; i > 1; i--) {
            int j = random.nextInt(i);
            int t = a[i - 1];
            a[i - 1] = a[j];
            a[j] = t;
        }
    }
}
