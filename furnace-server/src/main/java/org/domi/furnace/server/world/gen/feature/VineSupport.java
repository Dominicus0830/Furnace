package org.domi.furnace.server.world.gen.feature;

import java.util.Set;

// vanilla VineBlock의 지지(support) 판정 — TreeFeature.updateShapeAtEdge가 가장자리 vine을 갱신할 때 쓴다.
// 부착 가능 여부는 VinesFeature와 같은 "부착 불가 블록 블랙리스트"(BiomeFeatureData.parseVines가 채운다) 근사.
// vine 상태 id는 east/north/south/up/west(알파벳 순, true가 먼저) 5비트 조합 — 6837 + (east?0:16)+(north?0:8)+(south?0:4)+(up?0:2)+(west?0:1).
public final class VineSupport {
    static final int FIRST = 6837;
    static final int LAST = 6868;
    private static volatile Set<Integer> nonAttachable = Set.of();

    private VineSupport() {}

    static void init(Set<Integer> blacklist) {
        nonAttachable = blacklist;
    }

    public static boolean isVine(int state) {
        return state >= FIRST && state <= LAST;
    }

    static boolean attachable(int state) {
        return !nonAttachable.contains(state);
    }

    private static boolean has(int state, int bit) {
        return ((state - FIRST) & bit) == 0; // bit가 0이면 true
    }

    private static final int EAST = 16, NORTH = 8, SOUTH = 4, UP = 2, WEST = 1;

    private static int bitOf(TreeFeature.Direction dir) {
        return switch (dir) {
            case EAST -> EAST;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            default -> UP;
        };
    }

    // vanilla VineBlock.updateShape(state, direction, ...) — DOWN은 변화 없음, 나머지는 getUpdatedState 후 면이 하나도
    // 없으면 공기. 반환값은 새 상태 id(공기는 0).
    static int updateShape(FeatureWorldView view, int state, TreeFeature.Direction direction, int x, int y, int z) {
        if (!isVine(state) || direction == TreeFeature.Direction.DOWN) return state;
        int idx = state - FIRST;
        // UP 면: 위 이웃이 아래 방향으로 부착 가능한지
        if ((idx & UP) == 0) {
            if (!attachable(view.getBlock(x, y + 1, z))) idx |= UP;
        }
        int above = view.getBlock(x, y + 1, z);
        for (TreeFeature.Direction dir : new TreeFeature.Direction[]{
            TreeFeature.Direction.NORTH, TreeFeature.Direction.EAST, TreeFeature.Direction.SOUTH, TreeFeature.Direction.WEST}) {
            int bit = bitOf(dir);
            if ((idx & bit) != 0) continue; // 면이 꺼져 있음
            boolean supported = attachable(view.getBlock(x + dir.dx(), y, z + dir.dz()))
                || (isVine(above) && has(above, bit));
            if (!supported) idx |= bit;
        }
        // 5면 모두 꺼졌으면(31) 공기
        return idx == 31 ? 0 : FIRST + idx;
    }
}
