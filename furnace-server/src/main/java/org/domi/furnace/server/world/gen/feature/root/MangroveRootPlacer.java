// vanilla MangroveRootPlacer(+RootPlacer) 포팅 — 맹그로브 뿌리 배치
package org.domi.furnace.server.world.gen.feature.root;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.feature.BlockPos;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.feature.TreeConfig;
import org.domi.furnace.server.world.gen.feature.TreeFeature;
import org.domi.furnace.server.world.gen.feature.TreeIntProvider;

// vanilla RootPlacer + MangroveRootPlacer를 하나로 합쳐 포팅(다른 root placer 타입이 없어 계층 불필요).
//
// canPlaceRoot의 canGrowThrough(#minecraft:mangrove_roots_can_grow_through) 분기는 의도적으로 미구현 —
// worldgen-oracle(OracleMain)이 이 태그를 바인딩하지 않아 oracle(ground truth) 기준으로도 항상 공집합이므로,
// 표준 validTreePos만으로 oracle과 byte-exact 일치한다(UpwardsBranchingTrunkPlacer와 동일 근거).
public final class MangroveRootPlacer {
    // vanilla Direction.Plane.HORIZONTAL 순회 순서 (NORTH, EAST, SOUTH, WEST).
    private static final TreeFeature.Direction[] HORIZONTALS = {
        TreeFeature.Direction.NORTH, TreeFeature.Direction.EAST, TreeFeature.Direction.SOUTH, TreeFeature.Direction.WEST
    };

    private final TreeIntProvider trunkOffsetY;
    private final int rootStateId;            // mangrove_roots waterlogged=false
    private final int rootStateIdWaterlogged; // mangrove_roots waterlogged=true
    private final float aboveRootPlacementChance;
    private final int aboveRootStateId;       // moss_carpet
    private final int maxRootWidth;
    private final int maxRootLength;
    private final float randomSkewChance;
    private final Set<Integer> muddyRootsIn;  // mud·muddy_mangrove_roots 전 상태
    private final int muddyRootsStateId;      // muddy_mangrove_roots axis=y

    public MangroveRootPlacer(
            TreeIntProvider trunkOffsetY, int rootStateId, int rootStateIdWaterlogged,
            float aboveRootPlacementChance, int aboveRootStateId,
            int maxRootWidth, int maxRootLength, float randomSkewChance,
            Set<Integer> muddyRootsIn, int muddyRootsStateId) {
        this.trunkOffsetY = trunkOffsetY;
        this.rootStateId = rootStateId;
        this.rootStateIdWaterlogged = rootStateIdWaterlogged;
        this.aboveRootPlacementChance = aboveRootPlacementChance;
        this.aboveRootStateId = aboveRootStateId;
        this.maxRootWidth = maxRootWidth;
        this.maxRootLength = maxRootLength;
        this.randomSkewChance = randomSkewChance;
        this.muddyRootsIn = muddyRootsIn;
        this.muddyRootsStateId = muddyRootsStateId;
    }

    public BlockPos getTrunkOrigin(BlockPos pos, WorldgenRandom random) {
        return new BlockPos(pos.x(), pos.y() + trunkOffsetY.sample(random), pos.z());
    }

    public boolean placeRoots(
            FeatureWorldView view, WorldgenRandom random, BlockPos origin, BlockPos trunkOrigin, TreeConfig config,
            List<BlockPos> roots) {
        for (int y = origin.y(); y < trunkOrigin.y(); y++) {
            if (!canPlaceRoot(view, new BlockPos(origin.x(), y, origin.z()), config)) {
                return false;
            }
        }

        List<BlockPos> list = new ArrayList<>();
        list.add(new BlockPos(trunkOrigin.x(), trunkOrigin.y() - 1, trunkOrigin.z()));

        for (TreeFeature.Direction dir : HORIZONTALS) {
            BlockPos rel = new BlockPos(trunkOrigin.x() + dir.dx(), trunkOrigin.y(), trunkOrigin.z() + dir.dz());
            List<BlockPos> branch = new ArrayList<>();
            if (!simulateRoots(view, random, rel, dir, trunkOrigin, branch, 0, config)) {
                return false;
            }
            list.addAll(branch);
            list.add(rel);
        }

        for (BlockPos pos : list) {
            placeRoot(view, random, pos, config, roots);
        }
        return true;
    }

    private boolean simulateRoots(
            FeatureWorldView view, WorldgenRandom random, BlockPos pos, TreeFeature.Direction dir, BlockPos trunkOrigin,
            List<BlockPos> branch, int length, TreeConfig config) {
        if (length == maxRootLength || branch.size() > maxRootLength) {
            return false;
        }
        for (BlockPos candidate : potentialRootPositions(pos, dir, random, trunkOrigin)) {
            if (canPlaceRoot(view, candidate, config)) {
                branch.add(candidate);
                if (!simulateRoots(view, random, candidate, dir, trunkOrigin, branch, length + 1, config)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<BlockPos> potentialRootPositions(
            BlockPos pos, TreeFeature.Direction dir, WorldgenRandom random, BlockPos trunkOrigin) {
        BlockPos below = new BlockPos(pos.x(), pos.y() - 1, pos.z());
        BlockPos side = new BlockPos(pos.x() + dir.dx(), pos.y(), pos.z() + dir.dz());
        int dist = Math.abs(pos.x() - trunkOrigin.x()) + Math.abs(pos.y() - trunkOrigin.y()) + Math.abs(pos.z() - trunkOrigin.z());
        if (dist > maxRootWidth - 3 && dist <= maxRootWidth) {
            return random.nextFloat() < randomSkewChance
                ? List.of(below, new BlockPos(side.x(), side.y() - 1, side.z()))
                : List.of(below);
        }
        if (dist > maxRootWidth) {
            return List.of(below);
        }
        if (random.nextFloat() < randomSkewChance) {
            return List.of(below);
        }
        return random.nextBoolean() ? List.of(side) : List.of(below);
    }

    private boolean canPlaceRoot(FeatureWorldView view, BlockPos pos, TreeConfig config) {
        return TreeFeature.validTreePos(view, pos, config);
    }

    private void placeRoot(FeatureWorldView view, WorldgenRandom random, BlockPos pos, TreeConfig config, List<BlockPos> roots) {
        int current = view.getBlock(pos.x(), pos.y(), pos.z());
        if (muddyRootsIn.contains(current)) {
            // muddy 분기 — muddy_mangrove_roots는 waterlogged 프로퍼티가 없어 waterlog 조정 없음, above 배치도 없음.
            view.setBlock(pos.x(), pos.y(), pos.z(), muddyRootsStateId);
            roots.add(pos);
            return;
        }
        if (!canPlaceRoot(view, pos, config)) return;
        int state = TreeFeature.isWater(current) ? rootStateIdWaterlogged : rootStateId;
        view.setBlock(pos.x(), pos.y(), pos.z(), state);
        roots.add(pos);
        // vanilla RootPlacer.placeRoot: above 배치 판정의 nextFloat는 root가 실제 놓일 때 항상 소비.
        if (random.nextFloat() < aboveRootPlacementChance
                && TreeFeature.isAir(view.getBlock(pos.x(), pos.y() + 1, pos.z()))) {
            BlockPos above = new BlockPos(pos.x(), pos.y() + 1, pos.z());
            view.setBlock(above.x(), above.y(), above.z(), aboveRootStateId);
            roots.add(above);
        }
    }
}
