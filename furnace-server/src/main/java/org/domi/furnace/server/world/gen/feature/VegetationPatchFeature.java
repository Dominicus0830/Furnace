// vanilla VegetationPatchFeature(minecraft:vegetation_patch) / WaterloggedVegetationPatchFeature(minecraft:waterlogged_vegetation_patch) 포팅.
// 바닐라 디컴파일(edt, edw)과 RNG 소비 순서를 1:1로 맞춘다. 지면 위치 Set은 HashSet<BlockPos>(Vec3i 해시) 반복 순서까지 같다.
package org.domi.furnace.server.world.gen.feature;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import org.domi.furnace.server.world.BlockAttrs;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class VegetationPatchFeature implements ConfiguredFeature {

    /** CaveSurface: FLOOR → 방향 DOWN(지면이 아래), CEILING → UP. */
    public enum Surface { FLOOR(BlockAttrs.DOWN, -1), CEILING(BlockAttrs.UP, 1);
        final int direction; final int dy;
        Surface(int direction, int dy) { this.direction = direction; this.dy = dy; }
    }

    private final Set<Integer> replaceable;
    private final StateProvider groundState;
    private final PlacedFeature vegetationFeature;
    private final Surface surface;
    private final TreeIntProvider depth;
    private final float extraBottomBlockChance;
    private final int verticalRange;
    private final float vegetationChance;
    private final TreeIntProvider xzRadius;
    private final float extraEdgeColumnChance;
    private final boolean waterlogged;
    private final int waterState;
    private final BlockRegistry registry;

    public VegetationPatchFeature(Set<Integer> replaceable, StateProvider groundState, PlacedFeature vegetationFeature,
                                  Surface surface, TreeIntProvider depth, float extraBottomBlockChance, int verticalRange,
                                  float vegetationChance, TreeIntProvider xzRadius, float extraEdgeColumnChance,
                                  boolean waterlogged, int waterState, BlockRegistry registry) {
        this.replaceable = replaceable;
        this.groundState = groundState;
        this.vegetationFeature = vegetationFeature;
        this.surface = surface;
        this.depth = depth;
        this.extraBottomBlockChance = extraBottomBlockChance;
        this.verticalRange = verticalRange;
        this.vegetationChance = vegetationChance;
        this.xzRadius = xzRadius;
        this.extraEdgeColumnChance = extraEdgeColumnChance;
        this.waterlogged = waterlogged;
        this.waterState = waterState;
        this.registry = registry;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        BlockAttrs attrs = BlockAttrs.get();
        int xRadius = xzRadius.sample(random) + 1;
        int zRadius = xzRadius.sample(random) + 1;
        Set<BlockPos> ground = placeGroundPatch(view, attrs, random, x, y, z, xRadius, zRadius);
        if (waterlogged) ground = waterloggedFilter(view, attrs, ground);
        distributeVegetation(view, random, ground);
        return !ground.isEmpty();
    }

    private Set<BlockPos> placeGroundPatch(FeatureWorldView view, BlockAttrs attrs, WorldgenRandom random,
                                           int ox, int oy, int oz, int xRadius, int zRadius) {
        Set<BlockPos> set = new HashSet<>();
        for (int dx = -xRadius; dx <= xRadius; dx++) {
            boolean xEdge = dx == -xRadius || dx == xRadius;
            for (int dz = -zRadius; dz <= zRadius; dz++) {
                boolean zEdge = dz == -zRadius || dz == zRadius;
                boolean edge = xEdge || zEdge;
                boolean corner = xEdge && zEdge;
                boolean edgeNotCorner = edge && !corner;
                if (corner) continue;
                // 바닐라: !corner && (!edgeNotCorner || extraEdgeColumnChance != 0 && !(random.nextFloat() > extraEdgeColumnChance))
                if (edgeNotCorner && !(extraEdgeColumnChance != 0.0F && !(random.nextFloat() > extraEdgeColumnChance))) continue;
                int py = oy;
                int steps = 0;
                // 공기인 동안 지면 방향으로
                while (attrs.isAir(view.getBlock(ox + dx, py, oz + dz)) && steps < verticalRange) {
                    py += surface.dy;
                    steps++;
                }
                steps = 0;
                // 공기가 아닌 동안 반대 방향으로
                while (!attrs.isAir(view.getBlock(ox + dx, py, oz + dz)) && steps < verticalRange) {
                    py -= surface.dy;
                    steps++;
                }
                int belowY = py + surface.dy;
                int belowState = view.getBlock(ox + dx, belowY, oz + dz);
                int oppositeDir = surface == Surface.FLOOR ? BlockAttrs.UP : BlockAttrs.DOWN;
                if (attrs.isAir(view.getBlock(ox + dx, py, oz + dz)) && attrs.isFaceSturdy(belowState, oppositeDir)) {
                    int d = depth.sample(random)
                        + (extraBottomBlockChance > 0.0F && random.nextFloat() < extraBottomBlockChance ? 1 : 0);
                    BlockPos belowCopy = new BlockPos(ox + dx, belowY, oz + dz);
                    boolean placed = placeGround(view, random, ox + dx, belowY, oz + dz, d);
                    if (placed) set.add(belowCopy);
                }
            }
        }
        return set;
    }

    private boolean placeGround(FeatureWorldView view, WorldgenRandom random, int x, int y, int z, int d) {
        for (int i = 0; i < d; i++) {
            int ground = groundState.getState(random, x, y, z);
            int existing = view.getBlock(x, y, z);
            if (!sameBlock(ground, existing)) {
                if (!replaceable.contains(existing)) return i != 0;
                view.setBlock(x, y, z, ground);
                y += surface.dy;
            }
        }
        return true;
    }

    /** BlockState.is(Block): 같은 블록이면(속성 무관) true. */
    private static boolean sameBlock(int a, int b) {
        return BlockAttrs.get().blockIndex(a) == BlockAttrs.get().blockIndex(b);
    }

    private void distributeVegetation(FeatureWorldView view, WorldgenRandom random, Set<BlockPos> ground) {
        for (BlockPos pos : ground) {
            if (vegetationChance > 0.0F && random.nextFloat() < vegetationChance) {
                if (waterlogged) placeWaterloggedVegetation(view, random, pos);
                else placeVegetation(view, random, pos);
            }
        }
    }

    /** 식생은 지면 반대쪽(FLOOR면 위)으로 한 칸 올린 위치에서 배치(PlacedFeature.place — 배치 수정자 포함). */
    private boolean placeVegetation(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
        return vegetationFeature.placeAt(view, random, new BlockPos(pos.x(), pos.y() - surface.dy, pos.z()));
    }

    /** WaterloggedVegetationPatchFeature.placeVegetation: pos.below() 기준으로 배치한 뒤 그 칸이 waterlogged 가능하면 true로. */
    private boolean placeWaterloggedVegetation(FeatureWorldView view, WorldgenRandom random, BlockPos pos) {
        if (!placeVegetation(view, random, new BlockPos(pos.x(), pos.y() - 1, pos.z()))) return false;
        int state = view.getBlock(pos.x(), pos.y(), pos.z());
        int wet = withWaterlogged(state);
        if (wet != state) view.setBlock(pos.x(), pos.y(), pos.z(), wet);
        return true;
    }

    /** waterlogged=false 속성이 있는 state면 true 변형, 아니면 그대로. */
    private int withWaterlogged(int stateId) {
        String key = registry.canonicalKeyOf(stateId);
        if (key == null || !key.contains("waterlogged=false")) return stateId;
        int semi = key.indexOf(';');
        TreeMap<String, String> props = new TreeMap<>();
        for (String kv : key.substring(semi + 1).split(",")) {
            int eq = kv.indexOf('=');
            props.put(kv.substring(0, eq), kv.substring(eq + 1));
        }
        props.put("waterlogged", "true");
        return registry.stateIdOrAir(key.substring(0, semi), props);
    }

    // WaterloggedVegetationPatchFeature.placeGroundPatch: 주변(N,E,S,W,DOWN) 중 하나라도 이웃 면이 단단하지 않으면 물로 채운다.
    private Set<BlockPos> waterloggedFilter(FeatureWorldView view, BlockAttrs attrs, Set<BlockPos> ground) {
        Set<BlockPos> water = new HashSet<>();
        for (BlockPos pos : ground) {
            if (!anyNeighborOpen(view, attrs, pos)) water.add(pos);
        }
        for (BlockPos pos : water) view.setBlock(pos.x(), pos.y(), pos.z(), waterState);
        return water;
    }

    private static boolean anyNeighborOpen(FeatureWorldView view, BlockAttrs attrs, BlockPos pos) {
        // 바닐라 순서: NORTH, EAST, SOUTH, WEST, DOWN — 이웃 블록의 (방향 반대쪽) 면이 단단하지 않으면 "닫히지 않음"
        return neighborOpen(view, attrs, pos, 0, 0, -1, BlockAttrs.SOUTH)
            || neighborOpen(view, attrs, pos, 1, 0, 0, BlockAttrs.WEST)
            || neighborOpen(view, attrs, pos, 0, 0, 1, BlockAttrs.NORTH)
            || neighborOpen(view, attrs, pos, -1, 0, 0, BlockAttrs.EAST)
            || neighborOpen(view, attrs, pos, 0, -1, 0, BlockAttrs.UP);
    }

    private static boolean neighborOpen(FeatureWorldView view, BlockAttrs attrs, BlockPos pos, int dx, int dy, int dz, int oppositeDir) {
        return !attrs.isFaceSturdy(view.getBlock(pos.x() + dx, pos.y() + dy, pos.z() + dz), oppositeDir);
    }
}
