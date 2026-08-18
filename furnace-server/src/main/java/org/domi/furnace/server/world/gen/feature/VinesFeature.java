// vanilla VinesFeature 포팅 — 정글 벽면·나무 몸통에 붙는 단일 vine 배치
package org.domi.furnace.server.world.gen.feature;

import java.util.Set;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

// vanilla VinesFeature.place: origin이 빈 칸이고 DOWN을 제외한 방향(UP,NORTH,SOUTH,WEST,EAST 순) 중
// 첫 번째로 부착 가능한 이웃(canAttachTo = 지지면 또는 충돌면이 가득한 블록 — 잎도 충돌면이 가득이라 포함)이
// 있으면 그 방향 vine 하나를 놓는다. RNG 소비 없음.
//
// 부착 가능 판정은 vanilla의 셰이프 계산 대신 "부착 불가 블록 집합"(공기·유체·식물류·부분 블록) 블랙리스트
// 근사 — 집합은 BiomeFeatureData가 blockstates 테이블에서 구성한다. fixture 대조로 검증.
public final class VinesFeature implements ConfiguredFeature {
    private final Set<Integer> nonAttachable;
    private final int[] vineStates; // TreeFeature.Direction.horizontalIndex() 순서 (S,W,N,E)
    private final int vineUpStateId;

    public VinesFeature(Set<Integer> nonAttachable, int[] vineStates, int vineUpStateId) {
        this.nonAttachable = nonAttachable;
        this.vineStates = vineStates;
        this.vineUpStateId = vineUpStateId;
    }

    @Override
    public boolean place(FeatureWorldView view, WorldgenRandom random, int x, int y, int z) {
        if (!TreeFeature.isAir(view.getBlock(x, y, z))) {
            return false;
        }
        // vanilla Direction.values() 순서에서 DOWN 제외: UP, NORTH, SOUTH, WEST, EAST.
        // vine의 face 프로퍼티는 "부착된 면" = 이웃을 향한 방향.
        if (attachable(view, x, y + 1, z)) {
            view.setBlock(x, y, z, vineUpStateId);
            return true;
        }
        TreeFeature.Direction[] order = {
            TreeFeature.Direction.NORTH, TreeFeature.Direction.SOUTH, TreeFeature.Direction.WEST, TreeFeature.Direction.EAST
        };
        for (TreeFeature.Direction dir : order) {
            if (attachable(view, x + dir.dx(), y, z + dir.dz())) {
                view.setBlock(x, y, z, vineStates[dir.horizontalIndex()]);
                return true;
            }
        }
        return false;
    }

    private boolean attachable(FeatureWorldView view, int x, int y, int z) {
        return !nonAttachable.contains(view.getBlock(x, y, z));
    }
}
